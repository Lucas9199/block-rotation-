package com.blockrotation.mixin;

import com.blockrotation.SpawnerManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The display mob sits inside the spawner block. Make the crosshair pass through it
 * (so you can still mine/right-click the spawner) and stop it pushing you.
 */
@Mixin(LivingEntity.class)
public abstract class FakeMobNoHitMixin {
    @Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
    private void blockrotation$noHit(CallbackInfoReturnable<Boolean> cir) {
        if (SpawnerManager.isFake((Entity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void blockrotation$noPush(CallbackInfoReturnable<Boolean> cir) {
        if (SpawnerManager.isFake((Entity) (Object) this)) cir.setReturnValue(false);
    }
}
