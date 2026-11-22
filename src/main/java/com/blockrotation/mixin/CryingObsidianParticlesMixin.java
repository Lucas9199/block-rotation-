package com.blockrotation.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.CryingObsidianBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops the purple "tear" drips, which would give away the fake netherite block. */
@Mixin(CryingObsidianBlock.class)
public abstract class CryingObsidianParticlesMixin {
    @Inject(method = "randomDisplayTick", at = @At("HEAD"), cancellable = true)
    private void blockrotation$noTears(BlockState state, World world, BlockPos pos, Random random, CallbackInfo ci) {
        ci.cancel();
    }
}
