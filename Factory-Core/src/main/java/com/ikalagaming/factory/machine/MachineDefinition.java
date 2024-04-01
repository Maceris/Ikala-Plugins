package com.ikalagaming.factory.machine;

import lombok.NonNull;

import java.util.List;

/**
 * The definition of a machine.
 *
 * @param tags The tags that apply to the machine.
 * @param powerInput Whether the machine can accept power as an input.
 * @param fluidInputs The number of fluid input ports/tanks that the machine can make use of.
 * @param itemInput Whether the machine can accept items as an input.
 * @param powerOutput Whether the machine can produce power as an output.
 * @param fluidOutputs The number of fluid output ports/tanks that the machine can make use of.
 * @param itemOutput Whether the machine can produce items as an output.
 */
public record MachineDefinition(
        @NonNull List<String> tags,
        boolean powerInput,
        int fluidInputs,
        boolean itemInput,
        boolean powerOutput,
        int fluidOutputs,
        boolean itemOutput) {}
