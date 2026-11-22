package com.blockrotation.mixin;

import com.blockrotation.BlockRotation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The real block behind the fake spawner is solid, so the game normally hides
 * the faces of blocks touching it. Since a spawner is see-through, force those
 * faces to draw so you don't see holes into the void through the cage.
 */
@Mixin(Block.class)
public abstract class FakeSpawnerCullingMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void blockrotation$showThroughSpawner(BlockState state, BlockState otherState, Direction side,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (otherState.isOf(BlockRotation.FAKE_SPAWNER)) {
            cir.setReturnValue(true);
        }
    }
}
