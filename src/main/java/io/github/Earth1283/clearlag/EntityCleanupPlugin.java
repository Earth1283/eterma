package io.github.Earth1283.clearlag;
// just kill me please screw this inport
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.io.File;


public class EntityCleanupPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultMessages();
        startCleanupTask();
    }

    private void saveDefaultMessages() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            getLogger().info("好像 messages.yml 掉线了，弄一个出来");
            saveResource("messages.yml", false);
        }
    }

    private void startCleanupTask() {
        long interval = getConfig().getLong("cleanup-interval") * 20L; // Convert seconds to ticks
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupEntities();
            }
        }.runTaskTimerAsynchronously(this, 0L, interval);
    }

    private void cleanupEntities() {
        Bukkit.getScheduler().runTask(this, () -> {
            getLogger().info(getMessage("cleanup-start"));

            List<Entity> entities = Bukkit.getWorlds().get(0).getEntities(); // Get entities in the world
            int removedCount = 0;

            for (Entity entity : entities) {
                if (shouldRemoveEntity(entity)) {
                    entity.remove();
                    removedCount++;
                }
            }

            if (removedCount > 0) {
                notifyPlayers(getMessage("cleanup-complete").replace("{removed_count}", String.valueOf(removedCount)));
            } else {
                notifyPlayers(getMessage("cleanup-no-entities"));
            }
        });
    }

    private boolean shouldRemoveEntity(Entity entity) {
        EntityType entityType = entity.getType();
        List<String> entitiesToClear = getConfig().getStringList("cleared-entities");
        long cleanupIntervalMillis = getConfig().getLong("cleanup-interval") * 1000; // Convert seconds to milliseconds

        // Remove if entity is older than the cleanup interval and matches the specified types
        return !(entity instanceof org.bukkit.entity.Player) &&
                entitiesToClear.contains(entityType.name()) &&
                (System.currentTimeMillis() - entity.getWorld().getFullTime() * 50L) > cleanupIntervalMillis;
    }

    private void notifyPlayers(String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("clearlag.message")) {
                player.sendMessage(message);
            }
        }
    }

    private String getMessage(String path) {
        return getConfig().getString(path, path); // Default to the path if no message is found
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("clearlag")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (!player.hasPermission("clearlag.command")) {
                    player.sendMessage(getMessage("cleanup-command-permission"));
                    return false;
                }
            }

            Bukkit.getScheduler().runTask(this, () -> {
                try {
                    cleanupEntities();
                    int removedCount = getRemovedEntityCount();
                    if (sender instanceof Player) {
                        sender.sendMessage(getMessage("cleanup-manual-clear").replace("{removed_count}", String.valueOf(removedCount)));
                    } else {
                        getLogger().info("手动清理实体完成! 清理了 " + removedCount + " 个实体.");
                    }
                } catch (Exception e) {
                    if (sender instanceof Player) {
                        sender.sendMessage(getMessage("cleanup-manual-failed"));
                    } else {
                        getLogger().warning("手动清理时发生错误。");
                    }
                }
            });
            return true;
        }
        return false;
    }

    private int getRemovedEntityCount() {
        // Implement your logic to count removed entities if needed.
        // For simplicity's sake, we will use a placeholder here.
        return 10; // Placeholder value
    }
}
