package io.github.Earth1283.clearlag; // Corrected package name

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.command.CommandSender; // Import for command sender

// Import necessary BungeeCord components for sending messages
import net.md_5.bungee.api.chat.TextComponent;


import java.util.List;
import java.util.concurrent.atomic.AtomicInteger; // Import AtomicInteger

public class CleanupTask implements Runnable {

    private final EntityCleanupPlugin plugin;
    private final boolean isManualCommand; // Flag to indicate if triggered by command
    private CommandSender commandSender; // Store command sender if manual

    // Constructor for scheduled task
    public CleanupTask(EntityCleanupPlugin plugin, boolean isManualCommand) {
        this.plugin = plugin;
        this.isManualCommand = isManualCommand;
    }

    // Constructor for manual command (includes sender)
    public CleanupTask(EntityCleanupPlugin plugin, boolean isManualCommand, CommandSender sender) {
        this.plugin = plugin;
        this.isManualCommand = isManualCommand;
        this.commandSender = sender;
    }


    @Override
    public void run() {
        // This code now runs directly on the main server thread, as scheduled by the plugin.
        // All Bukkit API calls that interact with the world are safe here.

        // Send a message to all online players with permission before cleanup (only for scheduled task)
        // This message sending happens on the main thread.
        if (!isManualCommand) {
            String preCleanupMessage = plugin.getMessage("messages.pre-cleanup");
            plugin.sendMessageToPermittedPlayers(preCleanupMessage);
        }

        // Use AtomicInteger for thread-safe counting
        AtomicInteger removedCount = new AtomicInteger(0);
        List<EntityType> entitiesToClear = plugin.getEntitiesToClear(); // Get the list from the plugin

        // Iterate through all worlds (on the main thread - SAFE NOW)
        for (World world : Bukkit.getWorlds()) {
            // Get all entities in the world (on the main thread - SAFE NOW)
            // Create a copy of the list to avoid ConcurrentModificationException if entities are removed
            // while iterating.
            List<Entity> entities = new java.util.ArrayList<>(world.getEntities());

            // Iterate through entities and remove eligible ones (on the main thread)
            for (Entity entity : entities) {
                // --- Cleanup Logic ---
                // Check if the entity's type is in the configured list of entities to clear
                if (entitiesToClear.contains(entity.getType())) {
                    // If the entity is in the list, remove it directly on the main thread (SAFE NOW)
                    entity.remove();
                    // Increment the atomic counter
                    removedCount.incrementAndGet();
                }
            }
        }

        // Send a message after cleanup (on the main thread)
        String postCleanupMessage;
        if (isManualCommand) {
            // Message for the command sender
            postCleanupMessage = plugin.getMessage("messages.command-success").replace("%count%", String.valueOf(removedCount.get()));
            if (commandSender != null) {
                // Use Spigot's sendMessage method which handles TextComponents
                commandSender.spigot().sendMessage(new TextComponent(postCleanupMessage));
            }
            plugin.getLogger().info("手动实体清理完成。已移除 " + removedCount.get() + " 个实体。"); // Log cleanup
        } else {
            // Message for permitted players (scheduled task)
            postCleanupMessage = plugin.getMessage("messages.post-cleanup").replace("%count%", String.valueOf(removedCount.get()));
            plugin.sendMessageToPermittedPlayers(postCleanupMessage);
            plugin.getLogger().info("实体清理完成。已移除 " + removedCount.get() + " 个实体。"); // Log cleanup
        }
    }
}
