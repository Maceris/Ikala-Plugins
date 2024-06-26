package com.ikalagaming.factory.crafting;

import com.ikalagaming.factory.FactoryPlugin;
import com.ikalagaming.util.SafeResourceLoader;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a crafting recipe.
 *
 * @param inputs The ingredients for the recipe.
 * @param outputs The results of the recipe.
 * @param time The base time for the recipe, in ticks.
 */
@Slf4j
public record Recipe(
        @NonNull List<Ingredient> inputs, @NonNull List<Ingredient> outputs, long time) {

    /**
     * Start a builder for more readable recipe creation.
     *
     * @return The builder.
     */
    public static RecipeBuilder builder() {
        return new RecipeBuilder();
    }

    /** Used for building recipes step-by-step. */
    public static class RecipeBuilder {
        /** Used to track the last list that was modified, to support {@link #and(Ingredient)}. */
        @AllArgsConstructor
        private enum LastList {
            INPUT("RECIPE_LIST_INPUT"),
            OUTPUT("RECIPE_LIST_OUTPUT");

            final String i18nKey;

            @Override
            public String toString() {
                return SafeResourceLoader.getString(i18nKey, FactoryPlugin.getResourceBundle());
            }
        }

        private long time;
        private List<Ingredient> inputs;
        private List<Ingredient> outputs;

        /** The last list that we update, for use with {@link #and(Ingredient)}. */
        private LastList lastList;

        /** Non-public constructor so that it's not called directly. */
        RecipeBuilder() {
            // NOTE(ches) nothing special, we just want only the builder method to create these
        }

        /**
         * Add to the last list we had added an ingredient to. Intended to be used like {@code
         * .withInput(...).and(...)}.
         *
         * @param ingredient The ingredient to add to the list.
         * @return The builder.
         * @throws UnsupportedOperationException If we have not started adding to a list.
         * @throws IllegalArgumentException If the last list we added to did not accept ingredients.
         */
        public RecipeBuilder and(@NonNull Ingredient ingredient) {
            if (lastList == null) {
                var message =
                        SafeResourceLoader.getString(
                                "NOT_STARTED_LIST", FactoryPlugin.getResourceBundle());
                log.warn(message);
                throw new UnsupportedOperationException(message);
            }
            var list =
                    switch (lastList) {
                        case INPUT -> this.inputs;
                        case OUTPUT -> this.outputs;
                    };
            Consumer<Ingredient> check =
                    switch (lastList) {
                        case INPUT -> this::validateInput;
                        case OUTPUT -> this::validateOutput;
                    };
            check.accept(ingredient);
            list.add(ingredient);
            return this;
        }

        /**
         * Construct a recipe given the previous builder steps. Any lists that have not been set
         * will be created as empty lists.
         *
         * @return The constructed recipe.
         */
        public Recipe build() {
            if (this.inputs == null) {
                this.inputs = new ArrayList<>();
            }
            if (this.outputs == null) {
                this.outputs = new ArrayList<>();
            }
            return new Recipe(List.copyOf(inputs), List.copyOf(outputs), time);
        }

        /**
         * Check that the provided input is valid, and throw an exception if it is not.
         *
         * @param ingredient The ingredient to check.
         * @throws IllegalArgumentException If the input is invalid.
         */
        private void validateInput(@NonNull Ingredient ingredient) {
            if (ingredient.type == IngredientType.ITEM_OUTPUT) {
                var message =
                        SafeResourceLoader.getStringFormatted(
                                "INVALID_INPUT_TYPE",
                                FactoryPlugin.getResourceBundle(),
                                ingredient.type.toString());
                log.warn(message);
                throw new IllegalArgumentException(message);
            }
        }

        /**
         * Check that the provided output is valid, and throw an exception if it is not.
         *
         * @param ingredient The ingredient to check.
         * @throws IllegalArgumentException If the input is invalid.
         */
        private void validateOutput(@NonNull Ingredient ingredient) {
            if (ingredient.type == IngredientType.ITEM_INPUT) {
                var message =
                        SafeResourceLoader.getStringFormatted(
                                "INVALID_OUTPUT_TYPE",
                                FactoryPlugin.getResourceBundle(),
                                ingredient.type.toString());
                log.warn(message);
                throw new IllegalArgumentException(message);
            }
        }

        /**
         * Adds the input to the list of inputs, creating a list if none exists. This can be
         * chained, but also see {@link #and(Ingredient)}.
         *
         * @param input The input we want to have.
         * @return The builder.
         * @throws IllegalArgumentException If the input is not valid (for example, is an explicitly
         *     output type).
         */
        public RecipeBuilder withInput(@NonNull Ingredient input) {
            if (this.inputs == null) {
                this.inputs = new ArrayList<>();
            }
            this.validateInput(input);
            this.inputs.add(input);
            this.lastList = LastList.INPUT;
            return this;
        }

        /**
         * Set the list of inputs for the recipe. Should only be done once per builder, as this will
         * replace any existing list with the provided one.
         *
         * @param inputs The inputs we want to use for the recipe.
         * @return The builder.
         * @throws IllegalArgumentException If the input is not valid (for example, is an explicitly
         *     output type).
         */
        public RecipeBuilder withInputs(@NonNull List<Ingredient> inputs) {
            inputs.forEach(this::validateInput);
            this.inputs = inputs;
            return this;
        }

        /**
         * Adds the input to the list of outputs, creating a list if none exists. This can be
         * chained, but also see {@link #and(Ingredient)}.
         *
         * @param output The output we want to have.
         * @return The builder.
         * @throws IllegalArgumentException If the input is not valid (for example, is an explicitly
         *     input type).
         */
        public RecipeBuilder withOutput(@NonNull Ingredient output) {
            if (this.outputs == null) {
                this.outputs = new ArrayList<>();
            }
            this.validateOutput(output);
            this.outputs.add(output);
            this.lastList = LastList.OUTPUT;
            return this;
        }

        /**
         * Set the list of outputs for the recipe. Should only be done once per builder, as this
         * will replace any existing list with the provided one.
         *
         * @param outputs The outputs we want to use for the recipe.
         * @return The builder.
         * @throws IllegalArgumentException If the outputs is not valid (for example, is an
         *     explicitly input type).
         */
        public RecipeBuilder withOutputs(@NonNull List<Ingredient> outputs) {
            outputs.forEach(this::validateOutput);
            this.outputs = outputs;
            return this;
        }

        /**
         * Specify the time in ticks for the recipe.
         *
         * @param time The time, in ticks.
         * @return The builder.
         * @throws IllegalArgumentException If time is negative.
         */
        public RecipeBuilder withTime(long time) {
            if (time < 0) {
                var message =
                        SafeResourceLoader.getString(
                                "RECIPE_NEGATIVE_TIME", FactoryPlugin.getResourceBundle());
                log.warn(message);
                throw new IllegalArgumentException(message);
            }
            this.time = time;
            return this;
        }
    }
}
