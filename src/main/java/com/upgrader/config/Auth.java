package com.upgrader.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.upgrader.Upgrader;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Editor password. Checked on the server only.
 * The plain password is never stored: config/upgrader/auth.json holds its SHA-256.
 * To change it: put the SHA-256 of the new password into "password_sha256".
 */
public final class Auth {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_HASH = "4316c6227e86b37b847c9737cb95f33b540634ffa85041ec69690e05a267974b";

    private static final Set<UUID> AUTHED = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> FAILS = new ConcurrentHashMap<>();

    private Auth() {}

    private static String expectedHash() {
        Path f = CrateManager.dir().resolve("auth.json");
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                JsonObject o = new JsonObject();
                o.addProperty("password_sha256", DEFAULT_HASH);
                Files.writeString(f, GSON.toJson(o));
                return DEFAULT_HASH;
            }
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            return o.get("password_sha256").getAsString().trim().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            Upgrader.LOGGER.error("[Upgrader] Cannot read auth.json", e);
            return null;
        }
    }

    public static boolean tryLogin(ServerPlayer player, String password) {
        String expected = expectedHash();
        if (expected == null) return false;
        boolean ok = MessageDigest.isEqual(
                sha256(password).getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
        UUID id = player.getUUID();
        if (ok) {
            AUTHED.add(id);
            FAILS.remove(id);
        } else {
            FAILS.merge(id, 1, Integer::sum);
            Upgrader.LOGGER.warn("[Upgrader] Wrong editor password from {}", player.getGameProfile().getName());
        }
        return ok;
    }

    public static boolean isAuthed(UUID id) {
        return AUTHED.contains(id);
    }

    public static int fails(UUID id) {
        return FAILS.getOrDefault(id, 0);
    }

    public static void logout(UUID id) {
        AUTHED.remove(id);
        FAILS.remove(id);
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
