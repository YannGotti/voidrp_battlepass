package ru.voidrp.battlepass.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * HTTP client for syncing premium status with the FastAPI backend.
 * All calls are best-effort: failures are logged and local data is used as fallback.
 */
public final class BackendSyncClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String baseUrl;
    private final String gameAuthSecret;
    private final Logger log;
    private final HttpClient http;

    public BackendSyncClient(String baseUrl, String gameAuthSecret, Logger log) {
        this.baseUrl         = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.gameAuthSecret  = gameAuthSecret;
        this.log             = log;
        this.http            = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Fetch premium expiry for a player from the backend.
     * @return expiry epoch millis, or -1 on error, or 0 if no premium
     */
    public long fetchPremiumExpiry(String minecraftUuid) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/battlepass/premium/" + minecraftUuid))
                    .header("X-Game-Auth-Secret", gameAuthSecret)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) return 0L;
            if (resp.statusCode() != 200) {
                log.warning("[BattlePass] Backend premium check returned " + resp.statusCode() + " for " + minecraftUuid);
                return -1L;
            }
            JsonObject body = JsonParser.parseString(resp.body()).getAsJsonObject();
            boolean hasPremium = body.get("has_premium").getAsBoolean();
            if (!hasPremium) return 0L;
            String expiresAtStr = body.get("expires_at").getAsString();
            // ISO-8601 → epoch millis
            return java.time.Instant.parse(expiresAtStr).toEpochMilli();
        } catch (Exception e) {
            log.warning("[BattlePass] Backend unreachable for premium check (" + minecraftUuid + "): " + e.getMessage());
            return -1L;
        }
    }

    /**
     * Grant premium via backend.
     * @return expiry epoch millis, or -1 on error
     */
    public long grantPremium(String minecraftUuid, String minecraftNickname, int days, String note) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("minecraft_uuid",     minecraftUuid);
            body.addProperty("minecraft_nickname", minecraftNickname);
            body.addProperty("days", days);
            if (note != null) body.addProperty("note", note);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/battlepass/premium/grant"))
                    .header("Content-Type",       "application/json")
                    .header("X-Game-Auth-Secret", gameAuthSecret)
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 201) {
                log.warning("[BattlePass] Backend grant failed (" + resp.statusCode() + ") for " + minecraftUuid);
                return -1L;
            }
            JsonObject result = JsonParser.parseString(resp.body()).getAsJsonObject();
            String expiresAtStr = result.get("expires_at").getAsString();
            return java.time.Instant.parse(expiresAtStr).toEpochMilli();
        } catch (Exception e) {
            log.warning("[BattlePass] Backend grant error (" + minecraftUuid + "): " + e.getMessage());
            return -1L;
        }
    }

    /**
     * Revoke premium via backend.
     * @return true on success
     */
    public boolean revokePremium(String minecraftUuid) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/battlepass/premium/" + minecraftUuid))
                    .header("X-Game-Auth-Secret", gameAuthSecret)
                    .timeout(TIMEOUT)
                    .DELETE()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404) return true; // already gone
            return resp.statusCode() == 200 || resp.statusCode() == 204;
        } catch (Exception e) {
            log.warning("[BattlePass] Backend revoke error (" + minecraftUuid + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * Push player's current BP progress (level/xp/season) to the backend for profile display.
     */
    public void pushProgress(String minecraftUuid, String minecraftNickname, String season, int level, long xp) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("minecraft_uuid", minecraftUuid);
            body.addProperty("minecraft_nickname", minecraftNickname);
            body.addProperty("season", season);
            body.addProperty("level", level);
            body.addProperty("xp", xp);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/battlepass/progress"))
                    .header("Content-Type",       "application/json")
                    .header("X-Game-Auth-Secret", gameAuthSecret)
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 204) {
                log.warning("[BattlePass] Backend progress push failed (" + resp.statusCode() + ") for " + minecraftUuid);
            }
        } catch (Exception e) {
            log.warning("[BattlePass] Backend progress push error (" + minecraftUuid + "): " + e.getMessage());
        }
    }

    public boolean isConfigured() {
        return gameAuthSecret != null && !gameAuthSecret.isBlank();
    }
}
