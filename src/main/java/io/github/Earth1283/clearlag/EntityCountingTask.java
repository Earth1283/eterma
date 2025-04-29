package io.github.Earth1283.clearlag; // Corrected package name

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class EntityCountingTask implements Runnable {

    private final EntityCleanupPlugin plugin;

    public EntityCountingTask(EntityCleanupPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // This task runs asynchronously.
        // The core logic of counting entities requires accessing the world,
        // which must be done on the main server thread.

        // Schedule the entity counting logic to run synchronously on the main thread.
        // We are already in an async task, so we schedule a new sync task.
        Bukkit.getScheduler().runTask(plugin, () -> {
            // This code now runs on the main server thread.

            AtomicInteger currentEntityCount = new AtomicInteger(0);
            List<EntityType> entitiesToClear = plugin.getEntitiesToClear(); // Get the list from the plugin
            int maxEntities = plugin.getMaxEntitiesBeforeCleanup(); // Get the threshold

            // Iterate through all worlds (on the main thread - SAFE NOW)
            for (World world : Bukkit.getWorlds()) {
                try {
                    // Get all entities in the world (on the main thread - SAFE NOW)
                    // Create a copy of the list to avoid ConcurrentModificationException
                    List<Entity> entities = new java.util.ArrayList<>(world.getEntities());

                    // Iterate through entities and count undesired ones (on the main thread)
                    for (Entity entity : entities) {
                        // Check if the entity's type is in the configured list of entities to clear
                        if (entitiesToClear.contains(entity.getType())) {
                            currentEntityCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    // Log any errors during entity counting on the main thread
                    plugin.getLogger().severe("Error during entity counting in world " + world.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            int totalUndesiredEntities = currentEntityCount.get();

            // Log the current count (optional, for debugging/monitoring)
            // plugin.getLogger().info("当前世界中要清理的实体数量: " + totalUndesiredEntities);

            // Check if the entity count exceeds the threshold
            if (totalUndesiredEntities > maxEntities) {
                // Log the warning message with color codes for the console
                plugin.getLogger().warning("\u001B[31m实体数量 (\u001B[33m" + totalUndesiredEntities + "\u001B[31m) 已超过限制 (\u001B[33m" + maxEntities + "\u001B[31m)！正在触发立即清理。\u001B[0m"); // Red and Yellow ANSI colors

                // Send the threshold exceeded message to permitted players
                String thresholdMessage = plugin.getMessage("messages.threshold-exceeded")
                        .replace("%count%", String.valueOf(totalUndesiredEntities))
                        .replace("%limit%", String.valueOf(maxEntities));
                plugin.sendMessageToPermittedPlayers(thresholdMessage);


                // Trigger an immediate cleanup (this method will schedule the cleanup synchronously)
                plugin.triggerImmediateCleanup();
            }
        }); // End of synchronous task lambda
    }
}
