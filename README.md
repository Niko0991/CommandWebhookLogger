CommandWebhookLogger

A highly accurate Minecraft command logger utilizing ProtocolLib for real-time packet interception.

Highly accurate Spigot command logger sending executed, denied, and unknown commands to Discord webhooks. Leverages ProtocolLib packet interception to capture the true command string in real-time. Asynchronous webhook sending ensures zero server lag. Optional support for LuckPerms and DiscordSRV placeholders.

🛠️ Key Features & Advanced Functionality

This plugin is built to provide detailed and reliable command tracking, which is essential for managing a smooth server environment.

1. Core Command Logging

The plugin offers three distinct log categories, each sending data to a separate, dedicated webhook URL for better organization:

Executed Command Logging: Captures commands that successfully ran on the server. Useful for tracking player actions and staff usage.

Permission Denied Logging: Records every time a player attempts a command but is blocked due to missing permissions. Helps monitor potential permission confusion or abuse attempts.

Unknown Command Logging: Logs any text starting with a slash (/) that the server doesn't recognize. Excellent for catching typos or probing attempts by malicious users.

2. Utility Features

Fully Customizable Embeds: You have full control over the Discord message appearance! You can customize titles, colors (using the Discord decimal integer format), footers, and timestamps for all three log categories in the config.yml.

In-Game Configuration Reload: Update your settings easily without restarting the server. Use the command /commandwebhooklogger reload.

🤝 Dependencies & Integrations

CommandWebhookLogger uses a core technical dependency to work and offers optional integration with two popular plugins for richer data.

Core Requirement (Hard Dependency)

Plugin

Requirement

Description

ProtocolLib

REQUIRED

This is the engine of the plugin. CommandWebhookLogger uses ProtocolLib's advanced packet interception to capture the true command string immediately, ensuring maximum accuracy even if other plugins cancel the command later.





Download ProtocolLib here: https://www.spigotmc.org/resources/protocollib.1997/

Optional Addons (Soft Dependencies)

These plugins are completely optional. If they are installed on the server, the plugin will automatically use them to fill in extra details.

Discord Mentions

Required Addon: DiscordSRV

Placeholder: %discord_mention%

Benefit: Displays the player's Discord ID, allowing for quick mentions and contact within your Discord log channel.

Permission Group

Required Addon: LuckPerms

Placeholder: %group%

Benefit: Shows the player's primary group name (e.g., 'Mod' or 'VIP') directly in the log embed for quick context.

⚙️ Installation & Setup

Prerequisites: Ensure ProtocolLib is installed on your server.

Webhooks: Create separate webhooks in your Discord server for the log channels you want (e.g., Executed, Denied, Unknown). Copy the URL for each.

Configuration: Start the plugin once to generate the config.yml. Paste your webhook URLs into the corresponding sections.

Reload: Use /commandwebhooklogger reload or restart your server to apply the changes.

❤️ Support and Contribution

Source Code

The complete source code is available here on GitHub! Developers are welcome to explore the packet interception mechanism, submit bug reports, or contribute new features. Feel free to ask questions about the code right here in the Issues section!

Contact and Feedback

If you have questions, need support, or wish to connect:

Ask a Question: Use the Issues tab here on GitHub.

Discord: Add me on Discord! (Insert your Discord invite link here)

As I work to keep my plugins free, any optional support is always appreciated and helps me continue developing. Feel free to contact me via Discord to support the project or provide feedback!
