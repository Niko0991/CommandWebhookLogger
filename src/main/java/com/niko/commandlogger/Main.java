package com.niko.commandlogger;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements CommandExecutor {

    private static Main instance;
    private WebhookSender webhookSender;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Check for ProtocolLib
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            getLogger().severe("ProtocolLib not found! Disabling CommandWebhookLogger.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        this.webhookSender = new WebhookSender(this);

        // Register listener
        getServer().getPluginManager().registerEvents(
                new CommandWebhookListener(this, protocolManager, this.webhookSender), this
        );

        // Register /commandwebhooklogger
        if (getCommand("commandwebhooklogger") != null) {
            getCommand("commandwebhooklogger").setExecutor(this);
        } else {
            getLogger().warning("'/commandwebhooklogger' command not found in plugin.yml.");
        }

        getLogger().info("CommandWebhookLogger enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("CommandWebhookLogger disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!cmd.getName().equalsIgnoreCase("commandwebhooklogger"))
            return false;

        // No args → show usage
        if (args.length == 0) {
            sender.sendMessage("§eUsage: §6/commandwebhooklogger reload");
            return true;
        }

        // /commandwebhooklogger reload
        if (args[0].equalsIgnoreCase("reload")) {

            if (!sender.hasPermission("commandwebhooklogger.reload")) {
                sender.sendMessage("§cYou do not have permission to reload this plugin.");
                return true;
            }

            reloadConfig();
            sender.sendMessage("§aCommandWebhookLogger configuration reloaded successfully!");
            getLogger().info(sender.getName() + " reloaded the configuration.");

            return true;
        }

        // Unknown subcommand
        sender.sendMessage("§eUnknown subcommand. Usage: §6/commandwebhooklogger reload");
        return true;
    }

    public static Main getInstance() {
        return instance;
    }
}
