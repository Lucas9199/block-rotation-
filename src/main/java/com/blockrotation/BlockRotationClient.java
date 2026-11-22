package com.blockrotation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class BlockRotationClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("blockrotation");

    @Override
    public void onInitializeClient() {
        // The spawner texture has see-through gaps; draw the fake spawner in the cutout layer.
        BlockRenderLayerMap.putBlock(BlockRotation.FAKE_SPAWNER, BlockRenderLayer.CUTOUT);

        Worth.load();
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> Worth.appendTooltip(stack, lines));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SpawnerManager.tick(client);
            if (client.currentScreen instanceof FakeSpawnerScreen screen) screen.refresh();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SpawnerManager.save());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SpawnerManager.save());

        // Client-only commands (never sent to the server):
        //   /fakespawner <mob>        look at a spawner to change it, otherwise sets the default for new ones
        //   /fakespawner count <n>    set the stack size of the spawner you're looking at
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("fakespawner")
                .then(ClientCommandManager.literal("count")
                    .then(ClientCommandManager.argument("n", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            BlockPos pos = lookedAtSpawner();
                            if (pos == null) {
                                ctx.getSource().sendError(Text.literal("Look at a fake spawner first"));
                                return 0;
                            }
                            int n = IntegerArgumentType.getInteger(ctx, "n");
                            SpawnerManager.setCount(pos, n);
                            ctx.getSource().sendFeedback(Text.literal("Spawner stack set to " + n));
                            return 1;
                        })))
                .then(ClientCommandManager.argument("mob", StringArgumentType.word())
                    .executes(BlockRotationClient::setMob))));
    }

    private static int setMob(CommandContext<FabricClientCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "mob");
        Identifier id = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
        Optional<EntityType<?>> type = id == null ? Optional.empty() : Registries.ENTITY_TYPE.getOptionalValue(id);
        if (type.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Unknown mob: " + name));
            return 0;
        }
        BlockPos pos = lookedAtSpawner();
        if (pos != null) {
            SpawnerManager.setType(pos, type.get());
            ctx.getSource().sendFeedback(Text.literal("This spawner is now: " + id.getPath()));
        } else {
            BlockRotation.spawnerMob = type.get();
            ctx.getSource().sendFeedback(Text.literal("New fake spawners will be: " + id.getPath()));
        }
        return 1;
    }

    private static BlockPos lookedAtSpawner() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null && client.crosshairTarget instanceof BlockHitResult hit
                && client.world.getBlockState(hit.getBlockPos()).isOf(BlockRotation.FAKE_SPAWNER)) {
            return hit.getBlockPos();
        }
        return null;
    }
}
