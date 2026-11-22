package com.blockrotation;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * What each mob type puts into storage.
 *
 * Every 10 seconds a spawner "kills" 9-12 mobs per spawner in the stack (about
 * 128 kills in 2 minutes). With the skeleton table that's roughly 2 stacks of
 * bones and 2 stacks of arrows after 2 minutes for a single spawner, and it keeps
 * growing from there. Tune CYCLE_TICKS / KILLS / perKill to taste.
 */
public final class SpawnerLoot {
    private SpawnerLoot() {}

    public static final int CYCLE_TICKS = 200; // 10 seconds
    private static final int KILLS_MIN = 9, KILLS_MAX = 12;

    private record Drop(Item item, double perKill) {}
    private record Table(List<Drop> drops, int xpPerKill) {}

    private static final Map<EntityType<?>, Table> TABLES = new HashMap<>();

    private static void add(EntityType<?> type, int xp, Drop... drops) {
        TABLES.put(type, new Table(List.of(drops), xp));
    }

    static {
        add(EntityType.SKELETON, 5, new Drop(Items.BONE, 1.0), new Drop(Items.ARROW, 1.0));
        add(EntityType.STRAY, 5, new Drop(Items.BONE, 1.0), new Drop(Items.ARROW, 1.0));
        add(EntityType.ZOMBIE, 5, new Drop(Items.ROTTEN_FLESH, 1.0));
        add(EntityType.HUSK, 5, new Drop(Items.ROTTEN_FLESH, 1.0));
        add(EntityType.SPIDER, 5, new Drop(Items.STRING, 1.0), new Drop(Items.SPIDER_EYE, 0.33));
        add(EntityType.CAVE_SPIDER, 5, new Drop(Items.STRING, 1.0), new Drop(Items.SPIDER_EYE, 0.33));
        add(EntityType.CREEPER, 5, new Drop(Items.GUNPOWDER, 1.0));
        add(EntityType.BLAZE, 10, new Drop(Items.BLAZE_ROD, 0.5));
        add(EntityType.IRON_GOLEM, 0, new Drop(Items.IRON_INGOT, 4.0), new Drop(Items.POPPY, 1.0));
        add(EntityType.ZOMBIFIED_PIGLIN, 5, new Drop(Items.ROTTEN_FLESH, 1.0), new Drop(Items.GOLD_NUGGET, 1.0));
        add(EntityType.ENDERMAN, 5, new Drop(Items.ENDER_PEARL, 0.5));
        add(EntityType.SLIME, 4, new Drop(Items.SLIME_BALL, 1.0));
        add(EntityType.MAGMA_CUBE, 4, new Drop(Items.MAGMA_CREAM, 0.25));
        add(EntityType.PIG, 2, new Drop(Items.PORKCHOP, 2.0));
        add(EntityType.COW, 2, new Drop(Items.BEEF, 2.0), new Drop(Items.LEATHER, 1.0));
        add(EntityType.CHICKEN, 2, new Drop(Items.CHICKEN, 1.0), new Drop(Items.FEATHER, 1.0));
        add(EntityType.SHEEP, 2, new Drop(Items.MUTTON, 1.5), new Drop(Items.WHITE_WOOL, 1.0));
    }

    /** Storage size: one full page (45 stacks) per spawner in the stack. */
    public static final int STACKS_PER_SPAWNER = 45;

    public static long capacityStacks(SpawnerData d) {
        return (long) d.count * STACKS_PER_SPAWNER;
    }

    public static long stacksFor(Item item, long n) {
        int max = Math.max(1, new net.minecraft.item.ItemStack(item).getMaxCount());
        return (n + max - 1) / max;
    }

    public static long totalStacks(SpawnerData d) {
        long total = 0;
        for (Map.Entry<Item, Long> e : d.loot.entrySet()) total += stacksFor(e.getKey(), e.getValue());
        return total;
    }

    /** Adds one cycle's worth of loot. Stops once storage is full. */
    public static void generate(SpawnerData d, Random random) {
        Table t = TABLES.get(d.type);
        if (t == null || totalStacks(d) >= capacityStacks(d)) return;
        long kills = (long) d.count * (KILLS_MIN + random.nextInt(KILLS_MAX - KILLS_MIN + 1));
        for (Drop drop : t.drops()) {
            long n = Math.round(kills * drop.perKill() * (0.85 + 0.3 * random.nextDouble()));
            if (n > 0) d.loot.merge(drop.item(), n, Long::sum);
        }
        d.xp += kills * t.xpPerKill();
    }
}
