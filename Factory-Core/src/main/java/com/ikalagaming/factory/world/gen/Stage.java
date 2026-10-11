package com.ikalagaming.factory.world.gen;

/**
 * The stages of generating a chunk, in order. Each only adds to what the stages before it made, so
 * a chunk can be generated up to any stage to see what that stage does. Stages up to {@link
 * #BLOCK_RULES} are pure functions that can be evaluated anywhere; the later ones pull in nearby
 * starts and aren't built yet.
 */
public enum Stage {
    /** The parameters biomes are chosen by. Nothing visible in the chunk. */
    PARAMETERS,
    /** The biome of each 4^3 cell. */
    BIOMES,
    /** The terrain shape: the default block where solid, air elsewhere. */
    TERRAIN,
    /** Open space filled with fluid. */
    FLUIDS,
    /** Each solid block chosen by its biome's block rules. */
    BLOCK_RULES,
    /** Large placed things. Not built yet. */
    STRUCTURES,
    /** Small placed things. Not built yet. */
    FEATURES,
    /** Final touches. Not built yet. */
    DECORATION;

    /**
     * Whether generation can run this stage yet.
     *
     * @return True for the stages that are built.
     */
    public boolean isBuilt() {
        return ordinal() <= BLOCK_RULES.ordinal();
    }

    /**
     * Whether generating up to this stage includes another one.
     *
     * @param stage The other stage.
     * @return True if {@code stage} runs at or before this one.
     */
    public boolean includes(Stage stage) {
        return stage.ordinal() <= ordinal();
    }
}
