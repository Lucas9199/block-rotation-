package com.blockrotation.mixin;

import com.blockrotation.BlockRotation;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.sound.BlockSoundGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Break, place, hit (mining) and footstep sounds all read the block state's
 * sound group, so swapping it here changes every sound you hear for these blocks.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockStateSoundMixin {
    @Shadow public abstract Block getBlock();

    @Inject(method = "getSoundGroup", at = @At("HEAD"), cancellable = true)
    private void blockrotation$swapSounds(CallbackInfoReturnable<BlockSoundGroup> cir) {
        BlockSoundGroup fake = BlockRotation.soundFor(this.getBlock());
        if (fake != null) {
            cir.setReturnValue(fake);
        }
    }
}
