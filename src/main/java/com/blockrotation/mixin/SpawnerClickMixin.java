package com.blockrotation.mixin;

import com.blockrotation.BlockRotation;
import com.blockrotation.FakeSpawnerScreen;
import com.blockrotation.SpawnerManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Right-clicking a fake spawner is handled entirely on your client and the click is
 * NOT sent to the server (so nothing gets placed or used there):
 *   holding the fake spawner item -> add 1 to the stack (sneak: add the whole hand)
 *   otherwise (not sneaking)      -> open the spawner menu
 * Sneak-right-click with anything else behaves normally (e.g. placing blocks against it).
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class SpawnerClickMixin {
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void blockrotation$spawnerClick(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
                                         CallbackInfoReturnable<ActionResult> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (hand != Hand.MAIN_HAND || client.world == null) return;
        BlockPos pos = hit.getBlockPos();
        if (!client.world.getBlockState(pos).isOf(BlockRotation.FAKE_SPAWNER)) return;

        ItemStack held = player.getMainHandStack();
        if (held.isOf(BlockRotation.FAKE_SPAWNER.asItem())) {
            SpawnerManager.addToStack(player, pos, held);
            cir.setReturnValue(ActionResult.SUCCESS);
        } else if (!player.isSneaking()) {
            FakeSpawnerScreen.open(pos);
            cir.setReturnValue(ActionResult.SUCCESS);
        }
    }
}
