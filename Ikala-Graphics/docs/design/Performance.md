# Performance

Rules found while chasing hitches, memory and garbage collection, with the reason for each. A hitch is any frame
over 16.67 ms.

## Frame pacing

- Frames are presented by mailbox where the surface offers it (FIFO otherwise; `-Dikala.vulkan.presentMode`), and the
  main loop caps them at the monitor's refresh rate times 1.1 (`GraphicsSettings.targetFPS` 0). A frame a little
  late then doesn't wait a whole refresh, and a fast scene doesn't render thousands of frames.
- **Frames keep a fixed beat:** each deadline is one frame time after the last deadline, not after when the frame
  started, so a late frame is followed by an early one. The idle wait was waking 2 to 5 ms late, or a G1 pause
  landed in it.
- **Logging is asynchronous** (Ikala-Core `log4j2.xml`): a synchronous file write once stalled the render thread
  180 ms. Log messages are plain ASCII, since the Windows console garbles the rest.

## GPU memory

- The G-buffer's base color, normal and tangent are half floats.
- Geometry buffers double up to 256 MiB, then grow by a quarter: a growth allocates and copies the whole buffer, and
  on the Jumble walk one was a 60 ms stall going from 805 MB to 1.6 GB.
- The baked geometry arena compacts once live meshes fill a quarter or less of it. Only arenas whose users look
  meshes up every frame may compact; the LOD and skinned arenas can't, since GPU tables keep their offsets.
- The volume brick atlas grows by layers (512 bricks, 8 MiB each) as it fills, slot by slot so no brick moves, at a
  frame's start with the device idle: changing the bricks' set mid-frame spoils the command buffer.
- Each LOD draw list has room for twice what it last held, never shrinking.
- Point light shadow atlases and the cascades' translucent maps are created the first frame a scene needs them.
- Sharing per-frame render targets between the two frames in flight was dropped: about 165 MB at 1920 x 1017,
  against a barrier on every first write. Revisit for 4K, where it is about 700 MB.

## Garbage collection

- Launch scripts run G1 with `-XX:MaxGCPauseMillis=8 -XX:GCTimeRatio=4`: on the TERRAIN radius 32 stream flight,
  pauses over 16 ms went from 26 a minute to none. 8 ms alone grew the heap to 9 GB. Generational ZGC took three
  times the CPU on the radius 96 walk.
- **Worker pools that build LOD cells live as long as the store,** never one per batch, because per-thread scratch
  dies with its thread (a pool per batch meant 482 threads a minute). A wait that gives up cancels its futures
  instead of shutting a shared pool down.
- Scratch kept per thread has a size cap, so a rare huge input doesn't hold memory on every worker, and readers
  must copy out what they keep (`AssetFile.withFile`, `withBytes`).
- Hitch counts swing 44 to 64 between identical runs, so compare GC pause totals, pauses over 16 ms and sampled
  allocation instead.

## Streaming

- A parent LOD node is built once nothing under it is dirty or waiting for a bake, not once nothing anywhere is.
- Culled commands' instance counts belong to the GPU; the CPU never relies on rewriting them. The CPU writes only
  commands whose mesh or room changed since that frame in flight last wrote.
- A changed box says what changed, and each map takes only what it draws. The render thread's 110 to 300 ms spikes
  were the occupancy volume taking every changed box, a sphere per LOD cluster.
- The renderer applies an LOD top only if its consumer changed: streaming batches reach the root, so each was
  re-sending about 9,000 tops.
- A cell counts as laid out only if every section in it was; edge cells are laid out once in range. A section
  streamed back in continues its bake generations, because generations never go back at a position.

## Measuring

- Time CPU with `-Dikala.vulkan.validation=false`: the validation layer inflates CPU work (20 ms brick copies).
- GPU clocks land fast or slow between runs; check the OitIntegrate and ToneMap markers before comparing timings,
  and compare first builds back to back.
- Every build's `test` task skips `@Tag("slow")` tests and the `Temp*` harnesses; run `:Ikala-Graphics:slowTest`
  before committing LOD work, and `measure --tests` for a harness.
