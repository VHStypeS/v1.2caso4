package com.upgrader.config;

import com.google.gson.*;
import com.upgrader.Upgrader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * config/upgrader/upgrade.json - the Upgrader block.
 * Put N crates of one kind in -> with some chance you get ONE crate of the target kind (the N crates are used up).
 * Chances between the listed points are interpolated linearly (20 -> 20%, 30 -> 40%, 64 -> 75% by default).
 */
public final class UpgradeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Route(String from, String to) {}

    public record Point(int amount, double percent) {}

    public static final class Data {
        public int minAmount = 20;
        public int maxAmount = 64;
        public int resultAmount = 1;
        public List<Point> points = new ArrayList<>(List.of(new Point(20, 20), new Point(30, 40), new Point(64, 75)));
        public List<Route> routes = new ArrayList<>(List.of(
                new Route("common", "rare"), new Route("mid", "legendary"), new Route("rare", "mythic")));
    }

    private static volatile Data data;

    private UpgradeConfig() {}

    public static Data get() {
        Data d = data;
        if (d == null) {
            synchronized (UpgradeConfig.class) {
                if (data == null) load();
                d = data;
            }
        }
        return d;
    }

    public static synchronized void load() {
        Path file = CrateManager.dir().resolve("upgrade.json");
        Data d = new Data();
        try {
            if (Files.exists(file)) {
                JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (o.has("min_amount")) d.minAmount = Math.max(1, o.get("min_amount").getAsInt());
                if (o.has("max_amount")) d.maxAmount = Math.max(d.minAmount, o.get("max_amount").getAsInt());
                if (o.has("result_amount")) d.resultAmount = Math.max(1, o.get("result_amount").getAsInt());
                if (o.has("chances")) {
                    d.points = new ArrayList<>();
                    for (JsonElement e : o.getAsJsonArray("chances")) {
                        JsonObject p = e.getAsJsonObject();
                        d.points.add(new Point(p.get("amount").getAsInt(), p.get("percent").getAsDouble()));
                    }
                    d.points.sort((a, b) -> Integer.compare(a.amount(), b.amount()));
                }
                if (o.has("routes")) {
                    d.routes = new ArrayList<>();
                    for (JsonElement e : o.getAsJsonArray("routes")) {
                        JsonObject r = e.getAsJsonObject();
                        d.routes.add(new Route(r.get("from").getAsString(), r.get("to").getAsString()));
                    }
                }
            }
        } catch (Exception e) {
            Upgrader.LOGGER.error("[Upgrader] Cannot read upgrade.json, using defaults", e);
            d = new Data();
        }
        data = d;
        JsonObject o = new JsonObject();
        o.addProperty("min_amount", d.minAmount);
        o.addProperty("max_amount", d.maxAmount);
        o.addProperty("result_amount", d.resultAmount);
        JsonArray pts = new JsonArray();
        for (Point p : d.points) {
            JsonObject j = new JsonObject();
            j.addProperty("amount", p.amount());
            j.addProperty("percent", p.percent());
            pts.add(j);
        }
        o.add("chances", pts);
        JsonArray rs = new JsonArray();
        for (Route r : d.routes) {
            JsonObject j = new JsonObject();
            j.addProperty("from", r.from());
            j.addProperty("to", r.to());
            rs.add(j);
        }
        o.add("routes", rs);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(o));
        } catch (Exception e) {
            Upgrader.LOGGER.error("[Upgrader] Cannot write upgrade.json", e);
        }
    }

    /** Success chance in percent for this many crates (linear between the points, clamped to 0..100). */
    public static double chance(List<Point> pts, int amount) {
        if (pts.isEmpty()) return 0;
        if (amount <= pts.get(0).amount()) return clamp(pts.get(0).percent());
        for (int i = 1; i < pts.size(); i++) {
            Point a = pts.get(i - 1), b = pts.get(i);
            if (amount <= b.amount()) {
                double t = (amount - a.amount()) / (double) Math.max(1, b.amount() - a.amount());
                return clamp(a.percent() + (b.percent() - a.percent()) * t);
            }
        }
        return clamp(pts.get(pts.size() - 1).percent());
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(100, v));
    }
}
