package com.blockrotation.mixin;

import net.minecraft.block.AbstractSkullBlock;
import net.minecraft.block.SkullBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Heads are drawn by a block-entity renderer, not a block model, so a resource
 * override can't change placed heads. The renderer picks the model and texture
 * from the skull type, so reporting ZOMBIE as DRAGON draws a dragon head
 * (floor and wall versions, and when worn on a head).
 */
@Mixin(AbstractSkullBlock.class)
public abstract class SkullTypeMixin {
    @Inject(method = "getSkullType", at = @At("RETURN"), cancellable = true)
    private void blockrotation$zombieToDragon(CallbackInfoReturnable<SkullBlock.SkullType> cir) {
        if (cir.getReturnValue() == SkullBlock.Type.ZOMBIE) {
            cir.setReturnValue(SkullBlock.Type.DRAGON);
        }
    }
}
