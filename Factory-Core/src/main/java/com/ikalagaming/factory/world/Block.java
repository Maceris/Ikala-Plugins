package com.ikalagaming.factory.world;

import com.ikalagaming.factory.kvt.KVT;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@EqualsAndHashCode
public class Block {
    /** The fully qualified name. */
    private final @NonNull String name;

    private KVT data;
}
