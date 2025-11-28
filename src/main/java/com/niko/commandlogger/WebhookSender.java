package com.niko.commandlogger;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class WebhookSender {

    private final JavaPlugin plugin;

    public WebhookSender(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendWebhook(String webhookUrl, ConfigurationSection template, Player player, String command, String discordMention, String group, Location loc) {
        // NOTE: The CommandWebhookListener ensures both webhookUrl and template are not null/empty
        // before calling this method.

        // Embed fields
        String title = template.getString("title", "");
        String description = template.getString("description", "");
        // Using a standard default color here, but configuration should define it.
        int color = template.getInt("color", plugin.getConfig().getInt("embed_defaults.color", 5814783)); 
        String footer = template.getString("footer", plugin.getConfig().getString("embed_defaults.footer_text", ""));
        boolean includeTimestamp = template.getBoolean("include_timestamp", plugin.getConfig().getBoolean("embed_defaults.include_timestamp", true));
        String footerIcon = plugin.getConfig().getString("embed_defaults.footer_icon_url", "");

        boolean includeMainImage = template.getBoolean("include_image", false);
        String mainImageUrl = template.getString("image_url", "");

        boolean includeThumbnail = template.getBoolean("include_thumbnail", false);
        String thumbnailUrl = template.getString("thumbnail_url", "");

        // Replace placeholders
        title = replacePlaceholders(title, player, command, discordMention, group, loc);
        description = replacePlaceholders(description, player, command, discordMention, group, loc);
        footer = replacePlaceholders(footer, player, command, discordMention, group, loc);
        mainImageUrl = replacePlaceholders(mainImageUrl, player, command, discordMention, group, loc);
        thumbnailUrl = replacePlaceholders(thumbnailUrl, player, command, discordMention, group, loc);

        String timestamp = includeTimestamp ? DateTimeFormatter.ISO_INSTANT.format(Instant.now()) : null;

        // Build JSON payload
        StringBuilder json = new StringBuilder();
        json.append("{\"embeds\":[{")
            .append("\"author\":{\"name\":\"").append(escapeJson(plugin.getConfig().getString("embed_defaults.author_name", "Command Logs"))).append("\"},")
            .append("\"title\":\"").append(escapeJson(title)).append("\",")
            .append("\"description\":\"").append(escapeJson(description)).append("\",")
            .append("\"color\":").append(color);

        if (footer != null && !footer.isEmpty()) {
            json.append(",\"footer\":{\"text\":\"").append(escapeJson(footer)).append("\"");
            if (footerIcon != null && !footerIcon.isEmpty()) json.append(",\"icon_url\":\"").append(escapeJson(footerIcon)).append("\"");
            json.append("}");
        }

        if (includeThumbnail && thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            json.append(",\"thumbnail\":{\"url\":\"").append(escapeJson(thumbnailUrl)).append("\"}");
        }

        if (includeMainImage && mainImageUrl != null && !mainImageUrl.isEmpty()) {
            json.append(",\"image\":{\"url\":\"").append(escapeJson(mainImageUrl)).append("\"}");
        }

        if (timestamp != null) json.append(",\"timestamp\":\"").append(timestamp).append("\"");

        json.append("}]}");

        // Send asynchronously to prevent blocking the main thread
        sendRawWebhookAsync(webhookUrl, json.toString());
    }

    private String replacePlaceholders(String text, Player player, String command, String discordMention, String group, Location loc) {
        if (text == null) return "";
        return text.replace("%player%", player.getName())
                   .replace("%command%", command)
                   .replace("%discord_mention%", discordMention)
                   .replace("%group%", group)
                   .replace("%world%", loc.getWorld().getName())
                   .replace("%x%", String.valueOf(loc.getBlockX()))
                   .replace("%y%", String.valueOf(loc.getBlockY()))
                   .replace("%z%", String.valueOf(loc.getBlockZ()))
                   .replace("%error%", "");
    }

    // New asynchronous method to prevent server lag
    private void sendRawWebhookAsync(String webhookUrl, String payload) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            sendRawWebhook(webhookUrl, payload);
        });
    }

    // Synchronous HTTP sender, now called exclusively from an async task
    private void sendRawWebhook(String webhookUrl, String payload) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(webhookUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // Write payload
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            
            // Log for debugging
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Webhook sent to " + webhookUrl + ". Response code: " + code);
            }

            // Check for Discord failure codes (e.g., 400 Bad Request, 404 Not Found)
            if (code >= 400) {
                String errorDetails = readErrorStream(conn);
                plugin.getLogger().warning("Failed to send webhook to " + webhookUrl + ". Response code: " + code + ". Details: " + errorDetails);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to connect or send webhook to " + webhookUrl + ": " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    // Helper to read the response body (used for error logging)
    private String readErrorStream(HttpURLConnection conn) {
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                 new InputStreamReader(conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        } catch (Exception e) {
            response.append("Error reading response body: ").append(e.getMessage());
        }
        return response.toString();
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        // Proper JSON escaping: backslash, double quote, and newline
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}