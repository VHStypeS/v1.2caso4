package com.upgrader.config;

import com.google.gson.*;
import com.upgrader.Upgrader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat announcement for LEGENDARY-rarity items (the only crate drop that is announced). No chances are printed.
 * Templates live in config/upgrader/messages.json (key "legendary_item") and can be edited freely.
 * Placeholders: {player} {item} {count} {crate}
 */
public final class Messages {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Pattern TOKEN = Pattern.compile("\\{(player|item|count|crate)}");
    private static final String KEY = "legendary_item";
    private static volatile List<String> data = defaults();

    private Messages() {}

    private static List<String> defaults() {
        return List.of(
                "ЛЕГЕНДАРНАЯ НАХОДКА! {player} получает {item} из {crate}!",
                "НЕУЖЕЛИ УДАЧА НА СТОРОНЕ {player}?! Легендарный {item} из {crate}!",
                "ВСЕ СЮДА! {player} выбил легендарный {item} из {crate}!",
                "Казино в шоке: {player}, {item} и {crate} — легенда родилась!",
                "{player} открывает {crate} — и оттуда выпадает легендарный {item}!");
    }

    public static synchronized void load() {
        Path file = CrateManager.dir().resolve("messages.json");
        List<String> d = defaults();
        try {
            boolean write = true;
            if (Files.exists(file)) {
                JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (o.has(KEY)) {
                    List<String> list = new ArrayList<>();
                    for (JsonElement el : o.getAsJsonArray(KEY)) list.add(el.getAsString());
                    if (!list.isEmpty()) d = list;
                    write = false;
                }
                // an old (pre-1.4) file has rare/epic/legendary lists with chances in them: it is replaced once
            }
            if (write) {
                JsonObject o = new JsonObject();
                JsonArray arr = new JsonArray();
                d.forEach(arr::add);
                o.add(KEY, arr);
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(o));
            }
        } catch (Exception e) {
            Upgrader.LOGGER.error("[Upgrader] Cannot read messages.json, using defaults", e);
            d = defaults();
        }
        data = d;
    }

    /** The broadcast for a legendary-rarity drop. */
    public static Component announceLegendary(String player, ItemStack stack, CrateDef crate, RandomSource rnd) {
        List<String> list = data;
        String tpl = list.get(rnd.nextInt(list.size()));
        Style base = Style.EMPTY.applyFormat(ChatFormatting.GOLD).withBold(true);

        MutableComponent out = Component.empty();
        Matcher m = TOKEN.matcher(tpl);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) out.append(Component.literal(tpl.substring(last, m.start())).withStyle(base));
            out.append(token(m.group(1), player, stack, crate));
            last = m.end();
        }
        if (last < tpl.length()) out.append(Component.literal(tpl.substring(last)).withStyle(base));
        return out;
    }

    private static Component token(String name, String player, ItemStack stack, CrateDef crate) {
        return switch (name) {
            case "player" -> Component.literal(player).withStyle(Style.EMPTY.applyFormat(ChatFormatting.WHITE).withBold(true));
            case "item" -> Component.literal(stack.getHoverName().getString() + (stack.getCount() > 1 ? " x" + stack.getCount() : ""))
                    .withStyle(Style.EMPTY.applyFormat(ChatFormatting.AQUA).withBold(true));
            case "count" -> Component.literal(String.valueOf(stack.getCount())).withStyle(ChatFormatting.AQUA);
            default -> Component.literal(crate.name).withStyle(Style.EMPTY.withColor(crate.rgb()).withBold(true));
        };
    }
}
