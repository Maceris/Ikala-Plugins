package com.ikalagaming.factory.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    private int x = 0;
    private int y = 0;
    private int z = 0;
    private float xOffset = 0;
    private float yOffset = 0;
    private float zOffset = 0;
}
