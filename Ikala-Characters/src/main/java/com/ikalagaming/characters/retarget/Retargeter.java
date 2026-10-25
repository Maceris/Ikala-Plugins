package com.ikalagaming.characters.retarget;

import com.ikalagaming.characters.definition.Rig;
import com.ikalagaming.characters.definition.RigMap;
import com.ikalagaming.characters.gltf.ImportedCharacter;
import com.ikalagaming.characters.pose.Clip;
import com.ikalagaming.characters.pose.Pose;
import com.ikalagaming.characters.pose.PoseSolver;
import com.ikalagaming.characters.pose.Proportions;
import com.ikalagaming.characters.pose.Skeleton;

import lombok.NonNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves a character made on another skeleton onto a rig, through a {@link RigMap}, so it can use
 * the rig's clips, parts and sliders. The result is a variant of the rig: the rig's bones, with the
 * character's proportions.
 *
 * <ul>
 *   <li><b>Rest pose</b>: a rig bone with a source bone, its driver, takes the driver's position,
 *       and the reference rest's rotation turned to point the way the driver does, so the rig's
 *       twist conventions hold and its clips still fit. A bone with no driver keeps the reference's
 *       offset from its parent, so a spine between mapped hips and chest, or a jaw, lands where the
 *       reference puts it.
 *   <li><b>Weights</b> move to the mapped bones; several source bones mapping to one, like four
 *       fingers to a mitten chain, merge. Vertices don't move: they are already in model space.
 *   <li><b>Clips</b> move as each driver's change from rest in model space, applied to its bone's
 *       rest, which works across bones that point or twist differently. Bones without drivers keep
 *       their rest rotation. The source root's motion moves the bone it maps to.
 * </ul>
 *
 * <p>The first source bone listed for a rig bone drives it; the rest only merge their weights.
 *
 * @author Ches Burks
 */
public final class Retargeter {
    /** Samples per second for a clip without keys to follow, as a single pose. */
    public static final int SAMPLE_RATE = 30;

    /** A bone's own axis, which it points along, as Blender's bones do. */
    private static final Vector3f BONE_AXIS = new Vector3f(0, 1, 0);

    private Retargeter() {}

    /**
     * Move a character onto a rig.
     *
     * @param source The character on its own skeleton.
     * @param map How its bones map onto the rig.
     * @param target The rig.
     * @param reference A rest pose for the rig, like the mannequin's, for the bones and twists the
     *     source doesn't give.
     * @return The character on the rig.
     * @throws IllegalArgumentException If a source bone is neither mapped nor dropped, or the map
     *     names bones the rig doesn't have.
     */
    public static ImportedCharacter retarget(
            @NonNull ImportedCharacter source,
            @NonNull RigMap map,
            @NonNull Rig target,
            @NonNull Map<String, Skeleton.Rest> reference) {
        Skeleton sourceSkeleton = source.skeleton();
        Skeleton referenceSkeleton = new Skeleton(target, reference);
        final int sourceCount = sourceSkeleton.size();
        final int targetCount = target.bones().size();

        // Which source bone drives each rig bone, the first mapped to it
        int[] driver = new int[targetCount];
        java.util.Arrays.fill(driver, -1);
        Map<String, Integer> targetIndex = new HashMap<>();
        for (int j = 0; j < targetCount; ++j) {
            targetIndex.put(target.bones().get(j).name(), j);
        }
        for (Map.Entry<String, String> entry : map.bones().entrySet()) {
            int from = sourceSkeleton.index(entry.getKey());
            Integer to = targetIndex.get(entry.getValue());
            if (to == null) {
                throw new IllegalArgumentException(
                        "The map names " + entry.getValue() + ", which " + target.id() + " lacks");
            }
            if (from >= 0 && driver[to] < 0) {
                driver[to] = from;
            }
        }
        // Where each source bone's weights go: its mapped bone, or its nearest mapped ancestor's
        int[] weightTarget = new int[sourceCount];
        for (int i = 0; i < sourceCount; ++i) {
            String name = sourceSkeleton.name(i);
            String mapped = map.bones().get(name);
            if (mapped == null && !map.drop().contains(name)) {
                throw new IllegalArgumentException(
                        "The source bone "
                                + name
                                + " is neither mapped nor dropped by "
                                + map.id());
            }
            weightTarget[i] =
                    mapped != null
                            ? targetIndex.get(mapped)
                            : (i == 0 ? 0 : weightTarget[sourceSkeleton.parent(i)]);
        }

        Matrix4f[] sourceRest = restModel(sourceSkeleton);
        Matrix4f[] referenceRest = restModel(referenceSkeleton);
        Quaternionf[] sourceRestRotation = rotations(sourceRest);

        // The new rest pose, in model space, then relative to parents
        Matrix4f[] targetModel = new Matrix4f[targetCount];
        Map<String, Skeleton.Rest> targetRest = new LinkedHashMap<>();
        Quaternionf[] restLocalRotation = new Quaternionf[targetCount];
        Vector3f[] restLocalTranslation = new Vector3f[targetCount];
        for (int j = 0; j < targetCount; ++j) {
            int parent = referenceSkeleton.parent(j);
            if (driver[j] >= 0) {
                Quaternionf referenceRotation =
                        referenceRest[j].getNormalizedRotation(new Quaternionf());
                Vector3f referenceAxis = referenceRotation.transform(new Vector3f(BONE_AXIS));
                Vector3f sourceAxis =
                        sourceRestRotation[driver[j]].transform(new Vector3f(BONE_AXIS));
                Quaternionf rotation =
                        new Quaternionf()
                                .rotationTo(referenceAxis, sourceAxis)
                                .mul(referenceRotation);
                targetModel[j] =
                        new Matrix4f()
                                .translationRotate(
                                        sourceRest[driver[j]].getTranslation(new Vector3f()),
                                        rotation);
            } else {
                Matrix4f local =
                        new Matrix4f()
                                .translationRotate(
                                        referenceSkeleton.restTranslation(j, new Vector3f()),
                                        referenceSkeleton.restRotation(j, new Quaternionf()));
                targetModel[j] = parent < 0 ? local : new Matrix4f(targetModel[parent]).mul(local);
            }
            Matrix4f local =
                    parent < 0
                            ? new Matrix4f(targetModel[j])
                            : new Matrix4f(targetModel[parent]).invert().mul(targetModel[j]);
            restLocalTranslation[j] = local.getTranslation(new Vector3f());
            restLocalRotation[j] = local.getNormalizedRotation(new Quaternionf());
            targetRest.put(
                    target.bones().get(j).name(),
                    new Skeleton.Rest(restLocalTranslation[j], restLocalRotation[j]));
        }
        Quaternionf[] targetRestRotation = rotations(targetModel);

        List<ImportedCharacter.Part> parts = new ArrayList<>();
        for (ImportedCharacter.Part part : source.parts()) {
            int[] joints = part.joints().clone();
            for (int k = 0; k < joints.length; ++k) {
                joints[k] = weightTarget[joints[k]];
            }
            parts.add(
                    new ImportedCharacter.Part(
                            part.name(),
                            part.vertices(),
                            joints,
                            part.weights(),
                            part.indices(),
                            part.material(),
                            part.min(),
                            part.max()));
        }

        Context context =
                new Context(
                        sourceSkeleton,
                        target,
                        referenceSkeleton,
                        driver,
                        sourceRest,
                        sourceRestRotation,
                        targetModel,
                        targetRestRotation,
                        restLocalRotation,
                        restLocalTranslation);
        List<ImportedCharacter.ClipData> clips = new ArrayList<>();
        for (ImportedCharacter.ClipData clip : source.clips()) {
            clips.add(retargetClip(clip, context));
        }
        return new ImportedCharacter(
                source.name(),
                target,
                targetRest,
                List.copyOf(parts),
                List.copyOf(clips),
                source.materials(),
                source.images());
    }

    /**
     * What retargeting clips needs.
     *
     * @param source The source skeleton.
     * @param target The rig.
     * @param targetSkeleton A skeleton of the rig, for parents.
     * @param driver Each rig bone's driving source bone, or -1.
     * @param sourceRest The source bones' rest transforms in model space.
     * @param sourceRestRotation Their rotations.
     * @param targetRest The rig bones' new rest transforms in model space.
     * @param targetRestRotation Their rotations.
     * @param restLocalRotation The rig bones' new rest rotations relative to their parents.
     * @param restLocalTranslation The rig bones' new rest offsets from their parents.
     */
    private record Context(
            Skeleton source,
            Rig target,
            Skeleton targetSkeleton,
            int[] driver,
            Matrix4f[] sourceRest,
            Quaternionf[] sourceRestRotation,
            Matrix4f[] targetRest,
            Quaternionf[] targetRestRotation,
            Quaternionf[] restLocalRotation,
            Vector3f[] restLocalTranslation) {}

    /**
     * Retarget one clip by sampling it and moving each sample.
     *
     * @param clip The source clip.
     * @param context What retargeting needs.
     * @return The clip on the rig.
     */
    private static ImportedCharacter.ClipData retargetClip(
            ImportedCharacter.ClipData clip, Context context) {
        Skeleton source = context.source();
        // Held rather than looped, so a key at the very end isn't read as the start
        Clip bound =
                new Clip(
                        source,
                        clip.name(),
                        clip.duration(),
                        false,
                        clip.rotations(),
                        clip.translations());
        Pose pose = new Pose(source);
        PoseSolver solver = new PoseSolver(source);
        Proportions neutral = Proportions.neutral(source);
        final int targetCount = context.target().bones().size();
        // Sampled at the source's own key times, so each key carries over exactly
        float[] keyTimes = keyTimes(clip);
        final int samples = keyTimes.length;
        String sourceRoot = source.name(0);
        boolean rootMoves =
                clip.translations().stream().anyMatch(track -> track.bone().equals(sourceRoot));

        float[] times = new float[samples];
        float[][] rotationKeys = new float[targetCount][];
        for (int j = 0; j < targetCount; ++j) {
            if (context.driver()[j] >= 0) {
                rotationKeys[j] = new float[samples * 4];
            }
        }
        int rootTarget = -1;
        for (int j = 0; j < targetCount; ++j) {
            if (context.driver()[j] == 0) {
                rootTarget = j;
            }
        }
        float[] rootKeys = rootMoves && rootTarget >= 0 ? new float[samples * 3] : null;

        Quaternionf[] modelRotation = new Quaternionf[targetCount];
        Matrix4f[] model = new Matrix4f[targetCount];
        for (int j = 0; j < targetCount; ++j) {
            modelRotation[j] = new Quaternionf();
            model[j] = new Matrix4f();
        }
        Quaternionf delta = new Quaternionf();
        Quaternionf local = new Quaternionf();
        Vector3f position = new Vector3f();
        Vector3f sourcePosition = new Vector3f();
        Vector3f sourceRestPosition = new Vector3f();
        for (int s = 0; s < samples; ++s) {
            float time = keyTimes[s];
            times[s] = time;
            pose.reset();
            bound.sample(time, pose);
            solver.solve(pose, neutral);
            for (int j = 0; j < targetCount; ++j) {
                int parent = context.targetSkeleton().parent(j);
                int drive = context.driver()[j];
                if (drive >= 0) {
                    // The driver's change from rest in model space, applied to this bone's rest
                    solver.model(drive).getNormalizedRotation(delta);
                    delta.mul(new Quaternionf(context.sourceRestRotation()[drive]).invert());
                    delta.mul(context.targetRestRotation()[j], modelRotation[j]);
                } else if (parent < 0) {
                    modelRotation[j].set(context.restLocalRotation()[j]);
                } else {
                    modelRotation[parent].mul(context.restLocalRotation()[j], modelRotation[j]);
                }

                if (j == rootTarget && rootKeys != null) {
                    // The rest position, moved as far as the source root moved
                    solver.model(0).getTranslation(sourcePosition);
                    context.sourceRest()[0].getTranslation(sourceRestPosition);
                    context.targetRest()[j]
                            .getTranslation(position)
                            .add(sourcePosition)
                            .sub(sourceRestPosition);
                } else if (parent < 0) {
                    position.set(context.restLocalTranslation()[j]);
                } else {
                    model[parent].transformPosition(context.restLocalTranslation()[j], position);
                }
                model[j].translationRotate(position, modelRotation[j]);

                if (rotationKeys[j] != null) {
                    if (parent < 0) {
                        local.set(modelRotation[j]);
                    } else {
                        new Quaternionf(modelRotation[parent])
                                .invert()
                                .mul(modelRotation[j], local);
                    }
                    local.normalize();
                    rotationKeys[j][s * 4] = local.x;
                    rotationKeys[j][s * 4 + 1] = local.y;
                    rotationKeys[j][s * 4 + 2] = local.z;
                    rotationKeys[j][s * 4 + 3] = local.w;
                }
                if (j == rootTarget && rootKeys != null) {
                    Vector3f offset =
                            parent < 0
                                    ? new Vector3f(position)
                                    : new Matrix4f(model[parent])
                                            .invert()
                                            .transformPosition(new Vector3f(position));
                    rootKeys[s * 3] = offset.x;
                    rootKeys[s * 3 + 1] = offset.y;
                    rootKeys[s * 3 + 2] = offset.z;
                }
            }
        }

        List<Clip.RotationTrack> rotations = new ArrayList<>();
        for (int j = 0; j < targetCount; ++j) {
            if (rotationKeys[j] != null) {
                rotations.add(
                        new Clip.RotationTrack(
                                context.target().bones().get(j).name(), times, rotationKeys[j]));
            }
        }
        List<Clip.TranslationTrack> translations = new ArrayList<>();
        if (rootKeys != null) {
            translations.add(
                    new Clip.TranslationTrack(
                            context.target().bones().get(rootTarget).name(), times, rootKeys));
        }
        return new ImportedCharacter.ClipData(
                clip.name(), clip.duration(), clip.looping(), rotations, translations);
    }

    /**
     * Every time any of a clip's tracks has a key, in order, or evenly spaced times if it has fewer
     * than two.
     *
     * @param clip The clip.
     * @return The times.
     */
    private static float[] keyTimes(ImportedCharacter.ClipData clip) {
        java.util.TreeSet<Float> times = new java.util.TreeSet<>();
        clip.rotations()
                .forEach(
                        track -> {
                            for (float time : track.times()) {
                                times.add(time);
                            }
                        });
        clip.translations()
                .forEach(
                        track -> {
                            for (float time : track.times()) {
                                times.add(time);
                            }
                        });
        if (times.size() < 2) {
            final int count = Math.max(2, (int) Math.ceil(clip.duration() * SAMPLE_RATE) + 1);
            float[] even = new float[count];
            for (int i = 0; i < count; ++i) {
                even[i] = clip.duration() * i / (count - 1);
            }
            return even;
        }
        float[] sorted = new float[times.size()];
        int i = 0;
        for (float time : times) {
            sorted[i++] = time;
        }
        return sorted;
    }

    /**
     * The rest transform of every bone in model space.
     *
     * @param skeleton The skeleton.
     * @return The transforms, by bone index.
     */
    private static Matrix4f[] restModel(Skeleton skeleton) {
        PoseSolver solver = new PoseSolver(skeleton);
        solver.solve(new Pose(skeleton), Proportions.neutral(skeleton));
        Matrix4f[] model = new Matrix4f[skeleton.size()];
        for (int i = 0; i < model.length; ++i) {
            model[i] = new Matrix4f(solver.model(i));
        }
        return model;
    }

    /**
     * The rotations of transforms.
     *
     * @param matrices The transforms.
     * @return Their rotations.
     */
    private static Quaternionf[] rotations(Matrix4f[] matrices) {
        Quaternionf[] rotations = new Quaternionf[matrices.length];
        for (int i = 0; i < matrices.length; ++i) {
            rotations[i] = matrices[i].getNormalizedRotation(new Quaternionf());
        }
        return rotations;
    }
}
