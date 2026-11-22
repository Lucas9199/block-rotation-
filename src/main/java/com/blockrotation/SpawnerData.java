package com.blockrotation;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;

import java.util.LinkedHashMap;

/** Everything one fake spawner "has": mob, stack size, stored loot and XP. */
public final class SpawnerData {
    public EntityType<?> type;
    public int count = 1;
    public final LinkedHashMap<Item, Long> loot = new LinkedHashMap<>();
    public long xp;
    /** Ticks since the last loot cycle. */
    public int timer;

    public SpawnerData(EntityType<?> type) {
        this.type = type;
    }
}
