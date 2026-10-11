#ifndef CLUSTERS_GLSL
#define CLUSTERS_GLSL

// The cluster grid lights are sorted into by light_cull.comp. ClusterMath has it in Java; keep them
// in step.
const uint CLUSTERS_X = 16;
const uint CLUSTERS_Y = 9;
const uint CLUSTERS_Z = 24;
const uint CLUSTER_COUNT = CLUSTERS_X * CLUSTERS_Y * CLUSTERS_Z;
const uint MAX_LIGHTS_PER_CLUSTER = 256;
const float FIRST_SLICE_DEPTH = 1.0;

#endif
