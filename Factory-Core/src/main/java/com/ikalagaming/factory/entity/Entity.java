package com.ikalagaming.factory.entity;

import com.ikalagaming.factory.inventory.Inventory;

import lombok.Data;
import lombok.NonNull;

@Data
public class Entity {
    private @NonNull Position position;
    private @NonNull String name;
    private Inventory inventory;
}
