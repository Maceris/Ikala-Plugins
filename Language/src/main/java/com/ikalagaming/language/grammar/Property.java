package com.ikalagaming.language.grammar;

import lombok.NonNull;

import java.util.List;

public record Property(@NonNull String name, @NonNull List<String> values) {

}
