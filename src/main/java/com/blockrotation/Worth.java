package com.blockrotation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds a Donut-style "Worth: $1.2K" line to item tooltips, on your screen only.
 * Prices live in src/main/resources/blockrotation/worth.json (price per single item).
 * Disguised blocks show the worth of what they look like.
 */
public final class Worth {
    private Worth() {}

    private static final Map<Item, Double> PRICES = new HashMap<>();
    private static final Map<Item, Item> DISGUISE = new HashMap<>();
    /** GUI button items that shouldn't get a worth line. */
    public static final Set<ItemStack> NO_WORTH = Collections.newSetFromMap(new IdentityHashMap<>());

    private static final Style LABEL = Style.EMPTY.withColor(0xFFFFFF).withItalic(false);
    private static final Style VALUE = Style.EMPTY.withColor(0x55FF55).withItalic(false);

    public static void load() {
        DISGUISE.put(Items.CRYING_OBSIDIAN, Items.NETHERITE_BLOCK);
        DISGUISE.put(Items.OBSIDIAN, Items.ANCIENT_DEBRIS);
        DISGUISE.put(Items.ZOMBIE_HEAD, Items.DRAGON_HEAD);
        DISGUISE.put(Items.COBBLESTONE, Items.GILDED_BLACKSTONE);
        DISGUISE.put(Items.DEEPSLATE_TILES, Items.DIAMOND_BLOCK);
        DISGUISE.put(Items.DEEPSLATE_BRICKS, Items.SPAWNER);
        DISGUISE.put(Items.END_STONE, Items.EMERALD_BLOCK);

        try (InputStream in = Worth.class.getResourceAsStream("/blockrotation/worth.json")) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                Identifier id = Identifier.tryParse(e.getKey());
                if (id == null) continue;
                Registries.ITEM.getOptionalValue(id).ifPresent(item -> PRICES.put(item, e.getValue().getAsDouble()));
            }
        } catch (Exception ex) {
            BlockRotationClient.LOGGER.warn("Could not read worth.json", ex);
        }
    }

    /** Price of one item, or -1 if it has no worth. */
    public static double unit(Item item) {
        Double p = PRICES.get(DISGUISE.getOrDefault(item, item));
        return p == null ? -1 : p;
    }

    public static void appendTooltip(ItemStack stack, List<Text> lines) {
        if (stack.isEmpty() || NO_WORTH.contains(stack)) return;
        double u = unit(stack.getItem());
        if (u < 0) return;
        lines.add(Text.literal("Worth: ").setStyle(LABEL)
                .append(Text.literal("$" + format(u * stack.getCount())).setStyle(VALUE)));
    }

    /** 950 -> 950, 1234 -> 1.23K, 2500000 -> 2.5M */
    public static String format(double v) {
        String[] suffix = {"", "K", "M", "B", "T"};
        int i = 0;
        while (v >= 1000 && i < suffix.length - 1) {
            v /= 1000;
            i++;
        }
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + suffix[i];
    }
}
