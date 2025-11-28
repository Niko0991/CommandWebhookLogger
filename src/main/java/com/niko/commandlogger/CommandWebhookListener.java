package com.niko.commandlogger;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import github.scarsz.discordsrv.DiscordSRV;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor; // Need ChatColor for stripping color codes
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

// Removed GSON/json-simple imports to simplify dependencies

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CommandWebhookListener implements Listener {

    private final JavaPlugin plugin;
    private final WebhookSender webhookSender;
    private final Map<String, Command> knownCommands;
    private final Map<UUID, PendingCommand> pendingCommands = new ConcurrentHashMap<>();

    public CommandWebhookListener(JavaPlugin plugin, ProtocolManager protocolManager, WebhookSender webhookSender) {
        this.plugin = plugin;
        this.webhookSender = webhookSender;
        this.knownCommands = getKnownCommands();
        
        registerPacketListener(protocolManager);
    }

    // --- BUKKIT EVENT LISTENER: Command Execution Trigger ---
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String fullCommand = event.getMessage();
        // Use the configured wait time, default to 2 ticks
        int waitTicks = plugin.getConfig().getInt("wait-ticks-after-execute", 2);

        // 1. Store the command and set status to PENDING
        PendingCommand pending = new PendingCommand(fullCommand);
        pendingCommands.put(player.getUniqueId(), pending);

        // 2. Schedule final logging (allowing time for the packet listener to run)
        new BukkitRunnable() {
            @Override
            public void run() {
                // Check if the packet listener has set a definitive result
                if (pending.result == Result.PENDING) {
                    // Fallback check: use Bukkit command map to check for base failures
                    pending.result = detectCommandStatus(player, pending.command);
                }
                
                finalizeCommand(player, pending);
            }
        }.runTaskLater(plugin, waitTicks);
    }
    
    // --- BUKKIT EVENT LISTENER: Cleanup on Quit ---
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up any pending command data when a player leaves
        pendingCommands.remove(event.getPlayer().getUniqueId());
    }

    // --- PROTOCOLLIB PACKET LISTENER: Intercept Server Messages ---
    private void registerPacketListener(ProtocolManager protocolManager) {
        protocolManager.addPacketListener(new PacketAdapter(
            plugin, 
            ListenerPriority.LOWEST, // Give other plugins a chance to modify/cancel first
            PacketType.Play.Server.SYSTEM_CHAT, // Used for command feedback (1.19.1+)
            PacketType.Play.Server.CHAT // Used for command feedback (older versions)
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (player == null) return;
                
                PendingCommand pending = pendingCommands.get(player.getUniqueId());
                // Only process the packet if the player has a command pending and is still waiting for result
                if (pending == null || pending.result != Result.PENDING) return;
                
                PacketContainer packet = event.getPacket();
                String message = null;
                
                // --- MESSAGE EXTRACTION LOGIC ---
                // FIX: Use getJson() and then simple string manipulation (no external JSON libraries)
                WrappedChatComponent component = packet.getChatComponents().readSafely(0);
                if (component != null) {
                    try {
                        String jsonString = component.getJson();
                        message = extractPlainTextFromJson(jsonString);
                        
                        if (message == null || message.isEmpty()) {
                            // Fallback 1: If simple JSON parsing fails, read the raw string field (older protocols)
                            message = packet.getStrings().readSafely(0);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to extract text from WrappedChatComponent JSON for player " + player.getName() + ": " + e.getMessage());
                        // Fallback 2: If anything throws an exception, read the raw string field
                        message = packet.getStrings().readSafely(0);
                    }
                } else {
                     // Fallback 3: If no component was found, read the raw string field
                    message = packet.getStrings().readSafely(0);
                }

                if (message == null || message.isEmpty()) {
                    return; // Message is empty or couldn't be extracted, ignore.
                }
                
                // Strip all formatting codes before checking for keywords
                String lowerCaseMessage = ChatColor.stripColor(message).toLowerCase();
                
                // Check for denial messages (Permission failure from command usage or sub-command checks)
                if (lowerCaseMessage.contains("no permission") || 
                    lowerCaseMessage.contains("you do not have permission") ||
                    lowerCaseMessage.contains("you do not have access") ||
                    lowerCaseMessage.contains("denied") ||
                    lowerCaseMessage.contains("missing permission") ||
                    lowerCaseMessage.contains("you don't have permission") ||
                    lowerCaseMessage.contains("i'm sorry, but you do not have permission")) 
                {
                    pending.result = Result.NO_PERMISSION;
                } else if (lowerCaseMessage.contains("unknown command")) {
                    pending.result = Result.UNKNOWN;
                }
            }
        });
    }
    
    // Simple utility to extract the primary text from a basic chat component JSON string
    // This method avoids external JSON dependencies (like GSON/json-simple) by using simple string manipulation.
    // NOTE: This is HIGHLY FRAGILE and only works for simple {"text":"message"} components.
    private String extractPlainTextFromJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) return null;
        
        // Trim surrounding spaces
        String trimmed = jsonString.trim();
        
        // Check for simple {"text":"..."} structure.
        if (trimmed.startsWith("{\"text\":\"") && trimmed.endsWith("}")) {
            try {
                // Find the start of the text value: "text":"
                int start = trimmed.indexOf("\"text\":\"") + 8; 
                // Find the end quote for the text field, which is followed by the closing brace: "}
                int end = trimmed.lastIndexOf("\"}"); 
                
                if (start != -1 && end != -1 && start < end) {
                    String rawText = trimmed.substring(start, end);
                    
                    // Un-escape commonly escaped characters like "
                    return rawText.replace("\\\"", "\"");
                }
            } catch (Exception e) {
                // If substring manipulation fails for any reason
                plugin.getLogger().warning("Simple string extraction failed for: " + trimmed);
            }
        }
        
        // Return null if the simple structure wasn't matched or manipulation failed.
        // This triggers the fallback to packet.getStrings().readSafely(0) in the listener.
        return null; 
    }


    // --- Core Logic ---

    private void finalizeCommand(Player player, PendingCommand pending) {
        Result result = pending.result; 

        String resultKey;
        switch (result) {
            case NO_PERMISSION -> resultKey = "no-permission";
            case UNKNOWN -> resultKey = "unknown-command";
            case EXECUTED -> resultKey = "executed";
            default -> { // Should only happen if the command was pending and no packet was sent (i.e., successfully executed)
                resultKey = "executed";
            }
        }

        sendWebhook(player, pending.command, resultKey);
        pendingCommands.remove(player.getUniqueId());
    }

    private Result detectCommandStatus(Player player, String fullCommand) {
        // This is the fallback, primarily to handle commands that might not send a message on success/failure, 
        // or to distinguish UNKNOWN/NO_BASE_PERMISSION if the packet listener missed it.
        String commandLabel = fullCommand.split(" ")[0].substring(1).toLowerCase();
        Command command = knownCommands.get(commandLabel);

        if (command == null) return Result.UNKNOWN;
        
        // If the base permission fails, it's definitely a NO_PERMISSION event.
        if (!command.testPermissionSilent(player)) return Result.NO_PERMISSION;

        // Otherwise, assume it executed successfully (as the packet listener should have caught sub-permission failures)
        return Result.EXECUTED; 
    }
    
    // --- Utility Methods ---

    private void sendWebhook(Player player, String fullCommand, String resultKey) {
        // Note: The configuration keys are expected to use hyphens (e.g., "no-permission") 
        // to match the keys defined in finalizeCommand.
        String webhookUrl = plugin.getConfig().getString("webhooks." + resultKey);
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        var template = plugin.getConfig().getConfigurationSection("templates." + resultKey);
        if (template == null) return;

        String discordId = DiscordSRV.getPlugin() != null
                ? DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId())
                : null;
        String discordMention = discordId != null ? "<@" + discordId + ">" : "Not linked";

        String group = getPrimaryGroup(player);
        Location loc = player.getLocation();

        // Assuming WebhookSender has a compatible sendWebhook method
        webhookSender.sendWebhook(webhookUrl, template, player, fullCommand, discordMention, group, loc);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands() {
        try {
            // Uses reflection to grab the server's command map
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);

            return (Map<String, Command>) knownCommandsField.get(commandMap);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get known commands: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String getPrimaryGroup(Player player) {
        try {
            LuckPerms api = LuckPermsProvider.get();
            User user = api.getUserManager().getUser(player.getUniqueId());
            if (user == null) return "No user data";

            String primaryGroup = user.getPrimaryGroup();
            // CORRECTED LOGIC: Only return primaryGroup if it is NOT null AND NOT empty.
            if (primaryGroup != null && !primaryGroup.isEmpty()) return primaryGroup;

            return user.getNodes().stream()
                    .filter(node -> node instanceof InheritanceNode)
                    .map(node -> ((InheritanceNode) node).getGroupName())
                    .findFirst().orElse("No group");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get LuckPerms primary group: " + e.getMessage());
            return "Error fetching group";
        }
    }

    private enum Result { PENDING, EXECUTED, NO_PERMISSION, UNKNOWN }

    private static class PendingCommand {
        final String command;
        Result result = Result.PENDING; // Initial state

        PendingCommand(String command) {
            this.command = command;
        }
        
        public Result getResult() {
            return result;
        }
        
        public void setResult(Result result) {
            this.result = result;
        }
        
        public String getCommand() {
            return command;
        }
    }
}