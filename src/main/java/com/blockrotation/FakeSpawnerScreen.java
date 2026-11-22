package com.blockrotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Donut-style spawner menu. It's a normal-looking 6-row chest screen, but every
 * click is handled here and nothing is ever sent to the server.
 *
 * Rows 1-5: stored loot (click to take).  Bottom row:
 *   [prev]  .  .  [sell all] [drop page] [mob head: contents + how full]  .  .  [next]
 */
public class FakeSpawnerScreen extends GenericContainerScreen {
    /** A window id the server never uses, so nothing can ever match it. */
    private static final int SYNC_ID = 0x7F4B_0F4B;
    private static final int PAGE_SIZE = 45;
    private static final int PREV = 45, SELL = 48, DROP = 49, INFO = 50, NEXT = 53;

    private final BlockPos pos;
    private final SimpleInventory storage;
    private int page;

    public static void open(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        SpawnerData d = SpawnerManager.ensure(pos);
        PlayerInventory playerInv = client.player.getInventory();
        SimpleInventory storage = new SimpleInventory(54);
        GenericContainerScreenHandler handler =
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, SYNC_ID, playerInv, storage, 6);
        FakeSpawnerScreen screen = new FakeSpawnerScreen(handler, playerInv, pos, storage, title(d));
        screen.refresh();
        client.setScreen(screen);
        client.player.playSound(SoundEvents.BLOCK_CHEST_OPEN, 0.5f, 1f);
    }

    private FakeSpawnerScreen(GenericContainerScreenHandler handler, PlayerInventory inv, BlockPos pos,
                              SimpleInventory storage, Text title) {
        super(handler, inv, title);
        this.pos = pos;
        this.storage = storage;
    }

    // ------------------------------------------------------------ contents

    /** Called every client tick while open so loot visibly piles up. */
    public void refresh() {
        SpawnerData d = SpawnerManager.get(pos);
        if (d == null) {          // spawner was broken
            close();
            return;
        }
        long totalStacks = SpawnerLoot.totalStacks(d);
        int pages = (int) Math.max(1, (totalStacks + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);

        List<ItemStack> shown = pageStacks(d, page);
        for (int i = 0; i < PAGE_SIZE; i++) storage.setStack(i, i < shown.size() ? shown.get(i) : ItemStack.EMPTY);

        Worth.NO_WORTH.clear();
        for (int i = PAGE_SIZE; i < 54; i++) storage.setStack(i, ItemStack.EMPTY);
        if (page > 0) storage.setStack(PREV, button(Items.ARROW, "Previous Page", 0xFFFFFF, "Page " + page + "/" + pages));
        if (page < pages - 1) storage.setStack(NEXT, button(Items.ARROW, "Next Page", 0xFFFFFF, "Page " + (page + 2) + "/" + pages));
        storage.setStack(SELL, button(Items.GOLD_INGOT, "Sell All", 0x55FF55,
                "Click to sell everything", "Value: $" + Worth.format(lootValue(d))));
        storage.setStack(DROP, button(Items.DROPPER, "Drop Page", 0xFFAA00, "Click to drop this page"));
        storage.setStack(INFO, infoButton(d, totalStacks));
    }

    /** The mob head: what's stored and how full the spawner is. */
    private static ItemStack infoButton(SpawnerData d, long usedStacks) {
        long capacity = SpawnerLoot.capacityStacks(d);
        double pct = Math.min(100.0, usedStacks * 100.0 / Math.max(1, capacity));
        List<String> lore = new ArrayList<>();
        if (d.loot.isEmpty()) lore.add("Empty");
        for (Map.Entry<Item, Long> e : d.loot.entrySet()) {
            lore.add(new ItemStack(e.getKey()).getName().getString() + ": " + String.format("%,d", e.getValue()));
        }
        lore.add("");
        lore.add(String.format(java.util.Locale.ROOT, "Storage: %.1f%% full", pct));
        lore.add(bar(pct) + "  " + usedStacks + "/" + capacity + " stacks");
        String name = d.type.getName().getString() + " Spawner";
        return button(headFor(d.type), name, 0xFFFFFF, lore.toArray(new String[0]));
    }

    private static String bar(double pct) {
        int filled = (int) Math.round(pct / 10.0);
        return "[" + "|".repeat(filled) + ".".repeat(10 - filled) + "]";
    }

    private static Item headFor(EntityType<?> type) {
        if (type == EntityType.SKELETON || type == EntityType.STRAY) return Items.SKELETON_SKULL;
        if (type == EntityType.WITHER_SKELETON) return Items.WITHER_SKELETON_SKULL;
        if (type == EntityType.CREEPER) return Items.CREEPER_HEAD;
        if (type == EntityType.PIGLIN) return Items.PIGLIN_HEAD;
        // zombie heads are disguised as dragon heads by this mod, so other mobs use a spawner icon
        return Items.SPAWNER;
    }

    private static List<ItemStack> pageStacks(SpawnerData d, int page) {
        List<ItemStack> out = new ArrayList<>();
        long skip = (long) page * PAGE_SIZE;
        for (Map.Entry<Item, Long> e : d.loot.entrySet()) {
            Item item = e.getKey();
            int max = Math.max(1, new ItemStack(item).getMaxCount());
            long n = e.getValue();
            long stacks = (n + max - 1) / max;
            if (skip >= stacks) {
                skip -= stacks;
                continue;
            }
            for (long s = skip; s < stacks && out.size() < PAGE_SIZE; s++) {
                long left = n - s * max;
                out.add(new ItemStack(item, (int) Math.min(max, left)));
            }
            skip = 0;
            if (out.size() >= PAGE_SIZE) break;
        }
        return out;
    }

    private static ItemStack button(Item item, String name, int color, String... lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).setStyle(Style.EMPTY.withColor(color).withItalic(false)));
        List<Text> lines = new ArrayList<>();
        for (String l : lore) lines.add(Text.literal(l).setStyle(Style.EMPTY.withColor(0xAAAAAA).withItalic(false)));
        s.set(DataComponentTypes.LORE, new LoreComponent(lines));
        Worth.NO_WORTH.add(s);
        return s;
    }

    private static double lootValue(SpawnerData d) {
        double total = 0;
        for (Map.Entry<Item, Long> e : d.loot.entrySet()) {
            double u = Worth.unit(e.getKey());
            if (u > 0) total += u * e.getValue();
        }
        return total;
    }

    // ------------------------------------------------------------ clicks (never sent to the server)

    @Override
    protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
        if (slot == null || slot.id < 0 || slot.id >= 54) return; // your own inventory: ignore
        SpawnerData d = SpawnerManager.get(pos);
        ClientPlayerEntity player = this.client.player;
        if (d == null || player == null) return;

        int id = slot.id;
        if (id < PAGE_SIZE) {
            ItemStack shown = storage.getStack(id);
            if (shown.isEmpty()) return;
            ItemStack give = shown.copy();
            int before = give.getCount();
            player.getInventory().insertStack(give); // client-side "ghost" items
            int moved = before - give.getCount();
            if (moved > 0) {
                remove(d, shown.getItem(), moved);
                player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.3f, 1.4f);
            }
        } else if (id == PREV && page > 0) {
            page--;
            click(player);
        } else if (id == NEXT) {
            page++;
            click(player);
        } else if (id == SELL) {
            double value = lootValue(d);
            long items = d.loot.values().stream().mapToLong(Long::longValue).sum();
            d.loot.clear();
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            player.sendMessage(Text.literal("You sold " + String.format("%,d", items) + " items for ")
                    .setStyle(Style.EMPTY.withColor(0xFFFFFF))
                    .append(Text.literal("$" + Worth.format(value)).setStyle(Style.EMPTY.withColor(0x55FF55))), true);
        } else if (id == DROP) {
            for (int i = 0; i < PAGE_SIZE; i++) {
                ItemStack s = storage.getStack(i);
                if (!s.isEmpty()) remove(d, s.getItem(), s.getCount());
            }
            player.playSound(SoundEvents.BLOCK_DISPENSER_DISPENSE, 0.6f, 1f);
        } else {
            return;
        }
        SpawnerManager.markDirty();
        refresh();
    }

    private static void remove(SpawnerData d, Item item, long amount) {
        d.loot.computeIfPresent(item, (k, v) -> v - amount <= 0 ? null : v - amount);
    }

    private static void click(ClientPlayerEntity player) {
        player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1f);
    }

    /** Close without telling the server (it never knew this window was open). */
    @Override
    public void close() {
        this.client.setScreen(null);
    }

    // ------------------------------------------------------------ title

    private static Text title(SpawnerData d) {
        String mob = d.type.getName().getString();
        String s = d.count + " " + mob + " spawner" + (d.count == 1 ? "" : "s");
        return Text.literal(smallCaps(s));
    }

    private static final String NORMAL = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL  = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private static String smallCaps(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) {
            int i = NORMAL.indexOf(c);
            b.append(i >= 0 ? SMALL.charAt(i) : c);
        }
        return b.toString();
    }
}
