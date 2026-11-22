package com.blockrotation;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.sound.BlockSoundGroup;

/**
 * Central list of disguises. Textures, item icons and names are swapped by the
 * resource files in assets/minecraft; this class handles sounds, which block is
 * the fake spawner, and which mob spins inside it.
 *
 *   real block        -> looks like          (hardness real / fake)
 *   crying obsidian   -> netherite block     (50  / 50)
 *   obsidian          -> ancient debris      (50  / 30)
 *   zombie head       -> dragon head         (1   / 1)
 *   cobblestone       -> gilded blackstone   (2   / 1.5)
 *   deepslate tiles   -> diamond block       (3.5 / 5)
 *   deepslate bricks  -> monster spawner     (3.5 / 5)
 *   end stone         -> emerald block       (3   / 5)
 */
public final class BlockRotation {
    private BlockRotation() {}

    /** The block that is drawn as a monster spawner. */
    public static final Block FAKE_SPAWNER = Blocks.DEEPSLATE_BRICKS;

    /** Default mob for newly found fake spawners. Change in-game with /fakespawner <mob>. */
    public static EntityType<?> spawnerMob = EntityType.SKELETON;

    /** Returns the sound group to play instead, or null to keep the real one. */
    public static BlockSoundGroup soundFor(Block block) {
        if (block == Blocks.CRYING_OBSIDIAN)  return BlockSoundGroup.NETHERITE;
        if (block == Blocks.OBSIDIAN)         return BlockSoundGroup.ANCIENT_DEBRIS;
        if (block == Blocks.COBBLESTONE)      return BlockSoundGroup.GILDED_BLACKSTONE;
        if (block == Blocks.DEEPSLATE_TILES)  return BlockSoundGroup.METAL;   // diamond block
        if (block == Blocks.END_STONE)        return BlockSoundGroup.METAL;   // emerald block
        if (block == FAKE_SPAWNER)            return BlockSoundGroup.SPAWNER;
        return null;
    }
}
