package com.blockrotation;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Tracks every fake spawner in your loaded (rendered) chunks: its spinning display
 * mob, its stack size, and the loot/XP it builds up. All of it lives only on your
 * client and is saved to config/blockrotation-spawners.json so it survives restarts.
 */
public final class SpawnerManager {
    private SpawnerManager() {}

    private static final int ID_START = -1_000_000;   // far below any real entity id
    private static final int RESCAN_EVERY_TICKS = 10;
    private static final int SAVE_EVERY_TICKS = 600;
    private static final float SPIN_DEGREES_PER_TICK = 6f;
    private static final Random RANDOM = new Random();

    /** world key -> (block pos as long -> data) */
    private static final Map<String, Map<Long, SpawnerData>> ALL = new HashMap<>();
    private static final Map<Long, Entity> MOBS = new HashMap<>();
    private static Set<Long> active = new HashSet<>();

    private static ClientWorld lastWorld;
    private static String worldKey;
    private static boolean loaded, dirty;
    private static int nextId = ID_START, ticks;
    private static float spin;

    public static boolean isFake(Entity e) {
        return e.getId() <= ID_START;
    }

    // ---------------------------------------------------------------- data access

    private static Map<Long, SpawnerData> current() {
        return worldKey == null ? new HashMap<>() : ALL.computeIfAbsent(worldKey, k -> new HashMap<>());
    }

    public static SpawnerData get(BlockPos pos) {
        return current().get(pos.asLong());
    }

    public static SpawnerData ensure(BlockPos pos) {
        dirty = true;
        return current().computeIfAbsent(pos.asLong(), k -> new SpawnerData(BlockRotation.spawnerMob));
    }

    public static void markDirty() {
        dirty = true;
    }

    /** Right-clicked a fake spawner while holding the fake spawner item: stack it. */
    public static void addToStack(ClientPlayerEntity player, BlockPos pos, ItemStack held) {
        SpawnerData d = ensure(pos);
        int add = player.isSneaking() ? held.getCount() : 1;
        d.count += add;
        held.decrement(add); // client-side only; the server still thinks you hold them
        player.playSound(BlockSoundGroup.SPAWNER.getPlaceSound(), 1f, 1f);
        player.sendMessage(Text.literal("Spawner stack: " + d.count).formatted(Formatting.GREEN), true);
    }

    /** /fakespawner <mob> while looking at a spawner. */
    public static void setType(BlockPos pos, EntityType<?> type) {
        SpawnerData d = ensure(pos);
        if (d.type != type) {
            d.type = type;
            d.loot.clear();
            d.xp = 0;
            removeMob(pos.asLong());
        }
    }

    public static void setCount(BlockPos pos, int count) {
        ensure(pos).count = Math.max(1, count);
    }

    // ---------------------------------------------------------------- tick

    public static void tick(MinecraftClient client) {
        if (!loaded) {
            load();
            loaded = true;
        }
        ClientWorld world = client.world;
        if (world != lastWorld) {        // joined, left, or changed dimension
            if (dirty) save();
            MOBS.clear();
            active = new HashSet<>();
            lastWorld = world;
            worldKey = world == null ? null : worldKey(client, world);
        }
        if (world == null || client.player == null) return;

        ticks++;
        MOBS.values().removeIf(Entity::isRemoved);
        if (ticks % RESCAN_EVERY_TICKS == 0) rescan(client, world);

        // loot builds up for every spawner in your loaded chunks
        Map<Long, SpawnerData> map = current();
        for (long key : active) {
            SpawnerData d = map.get(key);
            if (d != null && ++d.timer >= SpawnerLoot.CYCLE_TICKS) {
                d.timer = 0;
                SpawnerLoot.generate(d, RANDOM);
                dirty = true;
            }
        }

        spin = (spin + SPIN_DEGREES_PER_TICK) % 360f;
        for (Entity e : MOBS.values()) {
            e.setYaw(spin);
            if (e instanceof LivingEntity living) {
                living.setBodyYaw(spin);
                living.setHeadYaw(spin);
            }
        }

        if (dirty && ticks % SAVE_EVERY_TICKS == 0) save();
    }

    private static void rescan(MinecraftClient client, ClientWorld world) {
        int radius = client.options.getViewDistance().getValue() + 1;
        int ccx = client.player.getBlockPos().getX() >> 4;
        int ccz = client.player.getBlockPos().getZ() >> 4;
        Set<Long> found = new HashSet<>();

        for (int cx = ccx - radius; cx <= ccx + radius; cx++) {
            for (int cz = ccz - radius; cz <= ccz + radius; cz++) {
                if (!world.getChunkManager().isChunkLoaded(cx, cz)) continue;
                WorldChunk chunk = world.getChunk(cx, cz);
                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    ChunkSection section = sections[i];
                    if (section == null || section.isEmpty()
                            || !section.hasAny(s -> s.isOf(BlockRotation.FAKE_SPAWNER))) continue;
                    int baseY = chunk.sectionIndexToCoord(i) << 4;
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++)
                            for (int x = 0; x < 16; x++)
                                if (section.getBlockState(x, y, z).isOf(BlockRotation.FAKE_SPAWNER))
                                    found.add(BlockPos.asLong((cx << 4) + x, baseY + y, (cz << 4) + z));
                }
            }
        }

        Map<Long, SpawnerData> map = current();

        // spawner broken (its chunk is loaded but the block is gone): forget it
        for (Iterator<Map.Entry<Long, SpawnerData>> it = map.entrySet().iterator(); it.hasNext(); ) {
            BlockPos p = BlockPos.fromLong(it.next().getKey());
            if (world.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)
                    && !world.getBlockState(p).isOf(BlockRotation.FAKE_SPAWNER)) {
                it.remove();
                dirty = true;
            }
        }

        // remove display mobs that are out of range or the wrong type
        for (Iterator<Map.Entry<Long, Entity>> it = MOBS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Entity> e = it.next();
            SpawnerData d = map.get(e.getKey());
            if (!found.contains(e.getKey()) || d == null || e.getValue().getType() != d.type) {
                world.removeEntity(e.getValue().getId(), Entity.RemovalReason.DISCARDED);
                it.remove();
            }
        }

        // new spawners get data + a mob
        for (long key : found) {
            SpawnerData d = map.computeIfAbsent(key, k -> {
                dirty = true;
                return new SpawnerData(BlockRotation.spawnerMob);
            });
            if (!MOBS.containsKey(key)) {
                Entity mob = createMob(world, BlockPos.fromLong(key), d.type);
                if (mob != null) MOBS.put(key, mob);
            }
        }
        active = found;
    }

    private static void removeMob(long key) {
        Entity e = MOBS.remove(key);
        if (e != null && lastWorld != null) lastWorld.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
    }

    private static Entity createMob(ClientWorld world, BlockPos pos, EntityType<?> type) {
        Entity mob = type.create(world, SpawnReason.SPAWNER);
        if (mob == null) return null;

        // same sizing rule the vanilla spawner uses for its display mob
        float scale = 0.53125f;
        float biggest = Math.max(type.getWidth(), type.getHeight());
        if (biggest > 1.0f) scale /= biggest;

        mob.setId(nextId--);
        mob.setSilent(true);
        mob.setNoGravity(true);
        if (mob instanceof LivingEntity living) {
            EntityAttributeInstance attr = living.getAttributeInstance(EntityAttributes.SCALE);
            if (attr != null) attr.setBaseValue(scale);
        }
        mob.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, spin, 0f);
        world.addEntity(mob);
        return mob;
    }

    // ---------------------------------------------------------------- save / load

    private static String worldKey(MinecraftClient client, ClientWorld world) {
        String base;
        if (client.getServer() != null) base = "sp:" + client.getServer().getSaveProperties().getLevelName();
        else if (client.getCurrentServerEntry() != null) base = "mp:" + client.getCurrentServerEntry().address;
        else base = "unknown";
        return base + "|" + world.getRegistryKey().getValue();
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("blockrotation-spawners.json");
    }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Map<Long, SpawnerData>> w : ALL.entrySet()) {
            JsonObject wo = new JsonObject();
            for (Map.Entry<Long, SpawnerData> e : w.getValue().entrySet()) {
                SpawnerData d = e.getValue();
                JsonObject o = new JsonObject();
                o.addProperty("type", Registries.ENTITY_TYPE.getId(d.type).toString());
                o.addProperty("count", d.count);
                o.addProperty("xp", d.xp);
                o.addProperty("timer", d.timer);
                JsonObject loot = new JsonObject();
                d.loot.forEach((item, n) -> loot.addProperty(Registries.ITEM.getId(item).toString(), n));
                o.add("loot", loot);
                wo.add(Long.toString(e.getKey()), o);
            }
            root.add(w.getKey(), wo);
        }
        try {
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(root));
            dirty = false;
        } catch (Exception ex) {
            BlockRotationClient.LOGGER.warn("Could not save fake spawners", ex);
        }
    }

    private static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> w : root.entrySet()) {
                Map<Long, SpawnerData> map = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : w.getValue().getAsJsonObject().entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    Identifier typeId = Identifier.tryParse(o.get("type").getAsString());
                    EntityType<?> type = typeId == null ? BlockRotation.spawnerMob
                            : Registries.ENTITY_TYPE.getOptionalValue(typeId).orElse(BlockRotation.spawnerMob);
                    SpawnerData d = new SpawnerData(type);
                    d.count = o.get("count").getAsInt();
                    d.xp = o.get("xp").getAsLong();
                    d.timer = o.get("timer").getAsInt();
                    for (Map.Entry<String, JsonElement> l : o.getAsJsonObject("loot").entrySet()) {
                        Identifier itemId = Identifier.tryParse(l.getKey());
                        if (itemId == null) continue;
                        Registries.ITEM.getOptionalValue(itemId)
                                .ifPresent((Item item) -> d.loot.put(item, l.getValue().getAsLong()));
                    }
                    map.put(Long.parseLong(e.getKey()), d);
                }
                ALL.put(w.getKey(), map);
            }
        } catch (Exception ex) {
            BlockRotationClient.LOGGER.warn("Could not read fake spawners", ex);
        }
    }
}
