package com.ikalagaming.graphics.vulkan;

import static com.ikalagaming.graphics.vulkan.VulkanInstance.checkError;
import static org.lwjgl.vulkan.VK13.*;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The global array of bindless textures. Every stage that samples textures binds the same
 * descriptor set at {@link ShaderBindings.BindlessTextures#SET}, and shaders index into it with the
 * slot that was assigned when the texture was registered.
 *
 * <p>Images must be in {@link VK13#VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL} whenever a shader samples
 * them, since that is the layout written into the descriptors. Slots can be written while frames
 * using the set are in flight, as long as those frames don't sample the slot being written.
 */
@Slf4j
public class BindlessTextures {

    /** VkDescriptorSetLayout for the bindless set, to include in pipeline layouts. */
    @Getter private long descriptorSetLayout;

    /** VkDescriptorPool the set is allocated from. */
    private long descriptorPool;

    /** The VkDescriptorSet holding the texture array. */
    @Getter private long descriptorSet;

    /** The number of slots in the array. */
    @Getter private final int capacity;

    /**
     * Guards {@link #freeSlots} and {@link #nextUnusedSlot}, since slots are reserved from any
     * thread that loads a texture.
     */
    private final ReentrantLock slotLock = new ReentrantLock();

    /** Slots that were released and can be handed out again. */
    private final Deque<Integer> freeSlots = new ArrayDeque<>();

    /** The lowest slot that has never been handed out. */
    private int nextUnusedSlot = 0;

    /**
     * Create the descriptor set layout, pool, and set.
     *
     * @param state The Vulkan state, with a logical device already created.
     */
    public BindlessTextures(@NonNull VulkanState state) {
        capacity = state.device.physical.maxBindlessImages;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer longOutput = stack.callocLong(1);

            VkDescriptorSetLayoutBindingFlagsCreateInfo bindingFlags =
                    VkDescriptorSetLayoutBindingFlagsCreateInfo.calloc(stack)
                            .sType$Default()
                            .pBindingFlags(
                                    stack.ints(
                                            /* We size the array when allocating the set */
                                            VK_DESCRIPTOR_BINDING_VARIABLE_DESCRIPTOR_COUNT_BIT
                                                    /* Not every slot will be filled */
                                                    | VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT
                                                    /* Textures load while frames are in flight */
                                                    | VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT
                                                    | VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT));

            VkDescriptorSetLayoutBinding.Buffer bindings =
                    VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.get(0)
                    .binding(ShaderBindings.BindlessTextures.BINDING)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(capacity)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            VkDescriptorSetLayoutCreateInfo layoutCreateInfo =
                    VkDescriptorSetLayoutCreateInfo.calloc(stack)
                            .sType$Default()
                            .pNext(bindingFlags)
                            .flags(VK_DESCRIPTOR_SET_LAYOUT_CREATE_UPDATE_AFTER_BIND_POOL_BIT)
                            .pBindings(bindings);
            checkError(
                    vkCreateDescriptorSetLayout(
                            state.device.logical, layoutCreateInfo, null, longOutput));
            descriptorSetLayout = longOutput.get(0);

            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes
                    .get(0)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(capacity);
            VkDescriptorPoolCreateInfo poolCreateInfo =
                    VkDescriptorPoolCreateInfo.calloc(stack)
                            .sType$Default()
                            .flags(VK_DESCRIPTOR_POOL_CREATE_UPDATE_AFTER_BIND_BIT)
                            .maxSets(1)
                            .pPoolSizes(poolSizes);
            checkError(
                    vkCreateDescriptorPool(state.device.logical, poolCreateInfo, null, longOutput));
            descriptorPool = longOutput.get(0);

            VkDescriptorSetVariableDescriptorCountAllocateInfo variableCount =
                    VkDescriptorSetVariableDescriptorCountAllocateInfo.calloc(stack)
                            .sType$Default()
                            .pDescriptorCounts(stack.ints(capacity));
            VkDescriptorSetAllocateInfo allocateInfo =
                    VkDescriptorSetAllocateInfo.calloc(stack)
                            .sType$Default()
                            .pNext(variableCount)
                            .descriptorPool(descriptorPool)
                            .pSetLayouts(stack.longs(descriptorSetLayout));
            checkError(vkAllocateDescriptorSets(state.device.logical, allocateInfo, longOutput));
            descriptorSet = longOutput.get(0);
        }
    }

    /**
     * Bind the bindless set for a pipeline layout that includes it at {@link
     * ShaderBindings.BindlessTextures#SET}.
     *
     * @param commandBuffer The command buffer to record into.
     * @param bindPoint The VkPipelineBindPoint.
     * @param pipelineLayout The VkPipelineLayout of the pipeline being used.
     */
    public void bind(@NonNull VkCommandBuffer commandBuffer, int bindPoint, long pipelineLayout) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            vkCmdBindDescriptorSets(
                    commandBuffer,
                    bindPoint,
                    pipelineLayout,
                    ShaderBindings.BindlessTextures.SET,
                    stack.longs(descriptorSet),
                    null);
        }
    }

    /** Destroy the Vulkan objects. The set is freed along with the pool. */
    public void cleanup(@NonNull VulkanState state) {
        vkDestroyDescriptorPool(state.device.logical, descriptorPool, null);
        descriptorPool = VK_NULL_HANDLE;
        descriptorSet = VK_NULL_HANDLE;
        vkDestroyDescriptorSetLayout(state.device.logical, descriptorSetLayout, null);
        descriptorSetLayout = VK_NULL_HANDLE;
    }

    /**
     * Give a texture a slot in the array and write its descriptor, on the render thread. The slot
     * is stored in {@link TextureInfoVulkan#bindlessIndex}.
     *
     * @param state The Vulkan state.
     * @param texture The texture, which must have a view and sampler.
     * @return The slot, or {@link ShaderBindings.BindlessTextures#DEFAULT_TEXTURE_INDEX} if we ran
     *     out of slots.
     */
    public int register(@NonNull VulkanState state, @NonNull TextureInfoVulkan texture) {
        if (!takeSlot(texture)) {
            return texture.bindlessIndex;
        }
        update(state, texture);
        return texture.bindlessIndex;
    }

    /**
     * Give a texture a slot in the array without writing its descriptor yet. Safe from any thread.
     * The descriptor must be written with {@link #update(VulkanState, TextureInfoVulkan)} on the
     * render thread before any shader samples the slot. The slot is stored in {@link
     * TextureInfoVulkan#bindlessIndex}.
     *
     * @param texture The texture.
     * @return The slot, or {@link ShaderBindings.BindlessTextures#DEFAULT_TEXTURE_INDEX} if we ran
     *     out of slots.
     */
    public int reserve(@NonNull TextureInfoVulkan texture) {
        takeSlot(texture);
        return texture.bindlessIndex;
    }

    /**
     * Take a free slot and store it in the texture. The default texture is the first thing
     * registered, so it gets {@link ShaderBindings.BindlessTextures#DEFAULT_TEXTURE_INDEX} as a
     * real slot. Once that is taken, the same index means we ran out.
     *
     * @param texture The texture.
     * @return True if a slot was taken, false if there were none left, in which case the texture
     *     gets the default texture's index.
     */
    private boolean takeSlot(@NonNull TextureInfoVulkan texture) {
        int slot = -1;
        slotLock.lock();
        try {
            if (!freeSlots.isEmpty()) {
                slot = freeSlots.pop();
            } else if (nextUnusedSlot < capacity) {
                slot = nextUnusedSlot;
                nextUnusedSlot += 1;
            }
        } finally {
            slotLock.unlock();
        }
        if (slot < 0) {
            log.error("Ran out of bindless texture slots, the limit is {}", capacity);
            texture.bindlessIndex = ShaderBindings.BindlessTextures.DEFAULT_TEXTURE_INDEX;
            return false;
        }
        texture.bindlessIndex = slot;
        return true;
    }

    /**
     * Release a texture's slot so that it can be reused. Reuse is deferred until no frame in flight
     * can still be sampling the old texture. The default texture slot is never released.
     *
     * @param state The Vulkan state.
     * @param texture The texture whose slot to release. Its index is reset afterward.
     */
    public void release(@NonNull VulkanState state, @NonNull TextureInfoVulkan texture) {
        final int slot = texture.bindlessIndex;
        texture.bindlessIndex = TextureInfoVulkan.NO_BINDLESS_INDEX;
        if (slot == TextureInfoVulkan.NO_BINDLESS_INDEX
                || slot == ShaderBindings.BindlessTextures.DEFAULT_TEXTURE_INDEX) {
            return;
        }
        state.deferFree(
                () -> {
                    slotLock.lock();
                    try {
                        freeSlots.push(slot);
                    } finally {
                        slotLock.unlock();
                    }
                });
    }

    /**
     * Write the descriptor for a texture that already has a slot, such as one from {@link
     * #reserve(TextureInfoVulkan)} or after its view was recreated. Render thread only, since the
     * descriptor set must be externally synchronized.
     *
     * @param state The Vulkan state.
     * @param texture The texture, which must have a view, sampler, and slot.
     */
    public void update(@NonNull VulkanState state, @NonNull TextureInfoVulkan texture) {
        if (texture.bindlessIndex == TextureInfoVulkan.NO_BINDLESS_INDEX) {
            log.warn("Trying to update a bindless texture that has no slot");
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo
                    .get(0)
                    .sampler(texture.sampler)
                    .imageView(texture.view)
                    .imageLayout(VK_IMAGE_LAYOUT_READ_ONLY_OPTIMAL);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(1, stack);
            writes.get(0)
                    .sType$Default()
                    .dstSet(descriptorSet)
                    .dstBinding(ShaderBindings.BindlessTextures.BINDING)
                    .dstArrayElement(texture.bindlessIndex)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(imageInfo);
            vkUpdateDescriptorSets(state.device.logical, writes, null);
        }
    }
}
