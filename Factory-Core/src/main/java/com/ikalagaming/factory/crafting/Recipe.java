package com.ikalagaming.factory.crafting;

import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a crafting recipe.
 *
 * @author Ches Burks
 */
public record Recipe(
        @NonNull List<Ingredient> inputs,
        @NonNull List<Ingredient> outputs,
        @NonNull List<String> machines,
        long time) {

    public static RecipeBuilder builder() {
        return new RecipeBuilder();
    }

    public static class RecipeBuilder {
        private List<String> machines;
        private long time;
        private List<Ingredient> inputs;
        private List<Ingredient> outputs;
        private LastList lastList;

        private enum LastList {
            INPUT,
            OUTPUT,
            MACHINES;
        }

        RecipeBuilder() {
            // NOTE(ches) nothing special, we just want only the builder method to create these
        }

        public RecipeBuilder withMachines(@NonNull List<String> machines) {
            this.machines = machines;
            return this;
        }

        public RecipeBuilder withMachine(@NonNull String machine) {
            if (this.machines == null) {
                this.machines = new ArrayList<>();
            }
            this.machines.add(machine);
            return this;
        }

        public RecipeBuilder withTime(long time) {
            this.time = time;
            return this;
        }

        public RecipeBuilder withInputs(@NonNull List<Ingredient> inputs) {
            this.inputs = inputs;
            // TODO(ches) check ingredient type
            return this;
        }

        public RecipeBuilder withInput(@NonNull Ingredient input) {
            if (this.inputs == null) {
                this.inputs = new ArrayList<>();
            }
            this.inputs.add(input);
            // TODO(ches) check ingredient type
            this.lastList = LastList.INPUT;
            return this;
        }

        public RecipeBuilder withOutputs(@NonNull List<Ingredient> outputs) {
            this.outputs = outputs;
            // TODO(ches) check ingredient type
            return this;
        }

        public RecipeBuilder withOutput(@NonNull Ingredient output) {
            if (this.outputs == null) {
                this.outputs = new ArrayList<>();
            }
            this.outputs.add(output);
            // TODO(ches) check ingredient type
            this.lastList = LastList.OUTPUT;
            return this;
        }

        public RecipeBuilder and(@NonNull String machine) {
            if (lastList == null) {
                // TODO(ches) error handling
                return this;
            }
            var list =
                    switch (lastList) {
                        case INPUT, OUTPUT -> throw new IllegalArgumentException();
                            // TODO(ches) localize
                        case MACHINES -> this.machines;
                    };
            list.add(machine);
            return this;
        }

        public RecipeBuilder and(@NonNull Ingredient ingredient) {
            if (lastList == null) {
                // TODO(ches) error handling
                return this;
            }
            var list =
                    switch (lastList) {
                        case INPUT -> this.inputs;
                        case OUTPUT -> this.outputs;
                            // TODO(ches) localize
                        case MACHINES -> throw new IllegalArgumentException();
                    };
            // TODO(ches) check ingredient type
            list.add(ingredient);
            return this;
        }

        public Recipe build() {
            if (this.inputs == null) {
                this.inputs = new ArrayList<>();
            }
            if (this.outputs == null) {
                this.outputs = new ArrayList<>();
            }
            if (this.machines == null) {
                this.machines = new ArrayList<>();
            }
            // TODO(ches) error handling
            return new Recipe(inputs, outputs, machines, time);
        }
    }
}
