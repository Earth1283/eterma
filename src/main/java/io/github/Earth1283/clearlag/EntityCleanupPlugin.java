package io.github.Earth1283.clearlag;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class EntityCleanupPlugin extends JavaPlugin {

    private BukkitTask cleanupTask;
    private long cleanupIntervalTicks;
    private long cleanupDelayTicks;
    private List<EntityType> entitiesToClear; // List of entity types to clear

    private static final String MESSAGE_PERMISSION = "clearlag.message";
    private static final String COMMAND_PERMISSION = "clearlag.command";

    @Override
    public void onEnable() {
        // Load configuration files
        saveDefaultConfig(); // Creates config.yml if it doesn't exist
        saveResource("messages.yml", false); // Creates messages.yml if it doesn't exist

        // Load configuration values
        loadConfig();

        // Schedule the asynchronous cleanup task
        scheduleCleanupTask();

        // Register the command executor
        Objects.requireNonNull(getCommand("clearlag")).setExecutor(this);

        getLogger().info("实体清理插件已启用!"); // Plugin enabled message
    }

    @Override
    public void onDisable() {
        // Cancel the scheduled task when the plugin is disabled
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }

        getLogger().info("实体清理插件已禁用!"); // Plugin disabled message
    }

    /**
     * Loads configuration values from config.yml and messages.yml.
     */
    public void loadConfig() {
        // Reload configs in case they were modified
        reloadConfig();
        this.reloadResource("messages.yml");

        // Get cleanup interval and delay from config.yml
        // Convert seconds to ticks (20 ticks per second)
        this.cleanupIntervalTicks = getConfig().getLong("cleanup-interval-seconds", 300) * 20L; // Default 5 minutes
        this.cleanupDelayTicks = getConfig().getLong("cleanup-delay-seconds", 60) * 20L; // Default 1 minute

        // Ensure interval and delay are positive
        if (this.cleanupIntervalTicks <= 0) {
            getLogger().warning("config.yml 中的 cleanup-interval-seconds 无效。使用默认值 300 秒。");
            this.cleanupIntervalTicks = 300 * 20L;
        }
        if (this.cleanupDelayTicks < 0) {
            getLogger().warning("config.yml 中的 cleanup-delay-seconds 无效。使用默认值 60 秒。");
            this.cleanupDelayTicks = 60 * 20L;
        }

        // Load entities to clear from config.yml
        loadEntitiesToClear();

        getLogger().info("配置已加载。清理间隔: " + (cleanupIntervalTicks / 20) + " 秒, 清理延迟: " + (cleanupDelayTicks / 20) + " 秒.");
    }

    /**
     * Loads the list of entity types to clear from config.yml.
     */
    private void loadEntitiesToClear() {
        this.entitiesToClear = new ArrayList<>();
        List<String> entityTypeNames = getConfig().getStringList("entities-to-clear");

        if (entityTypeNames == null || entityTypeNames.isEmpty()) {
            getLogger().warning("config.yml 中未配置 'entities-to-clear' 列表或为空。将不会自动清理任何实体。");
            return;
        }

        for (String typeName : entityTypeNames) {
            try {
                EntityType entityType = EntityType.valueOf(typeName.toUpperCase());
                this.entitiesToClear.add(entityType);
            } catch (IllegalArgumentException e) {
                getLogger().warning("config.yml 中无效的实体类型: " + typeName + ". 已跳过.");
            }
        }

        getLogger().info("已加载要清理的实体类型: " + this.entitiesToClear.stream().map(Enum::name).collect(Collectors.joining(", ")));
    }


    /**
     * Schedules the asynchronous entity cleanup task.
     */
    private void scheduleCleanupTask() {
        // Cancel any existing task before scheduling a new one
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }

        // Create the cleanup task
        CleanupTask task = new CleanupTask(this, false); // False indicates scheduled task

        // Schedule the task to run asynchronously with a delay and repeating interval
        // The first run will happen after cleanupDelayTicks, then repeat every cleanupIntervalTicks
        this.cleanupTask = getServer().getScheduler().runTaskTimerAsynchronously(this, task, cleanupDelayTicks, cleanupIntervalTicks);

        getLogger().info("实体清理任务已安排。首次运行将在 " + (cleanupDelayTicks / 20) + " 秒后，之后每 " + (cleanupIntervalTicks / 20) + " 秒运行一次。");
    }

    /**
     * Helper method to reload a resource file (like messages.yml).
     *
     * @param resourceName The name of the resource file.
     */
    private void reloadResource(String resourceName) {
        // Get the file from the plugin data folder
        java.io.File resourceFile = new java.io.File(getDataFolder(), resourceName);

        // If the file exists, load it
        if (resourceFile.exists()) {
            try {
                // Load the YAML file
                org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(resourceFile);
                // Set this as the messages config (or handle differently if you have multiple resource files)
                // For simplicity, we'll just log if it loads. In a real plugin, you'd store this config object.
                getLogger().info(resourceName + " 已重新加载。");
            } catch (Exception e) {
                getLogger().severe("无法加载 " + resourceName + ": " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            getLogger().warning(resourceName + " 不存在。正在保存默认文件。");
            saveResource(resourceName, false); // Save the default if it doesn't exist
        }
    }

    /**
     * Gets a message from messages.yml.
     *
     * @param key The key for the message.
     * @return The message string, or a default message if not found.
     */
    public String getMessage(String key) {
        // Load messages.yml explicitly to get its configuration
        java.io.File messagesFile = new java.io.File(getDataFolder(), "messages.yml");
        org.bukkit.configuration.file.YamlConfiguration messagesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile);

        // Get the message string, colorize it, and return
        String message = messagesConfig.getString(key, "§c消息键 '" + key + "' 未在 messages.yml 中找到.");
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(message));
    }

    /**
     * Gets the list of entity types configured for cleanup.
     *
     * @return The list of entity types to clear.
     */
    public List<EntityType> getEntitiesToClear() {
        return entitiesToClear;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("clearlag")) {
            // Check for command permission
            if (!sender.hasPermission(COMMAND_PERMISSION)) {
                sender.sendMessage(getMessage("messages.command-no-permission"));
                return true;
            }

            // Check for correct command usage
            if (args.length != 0) {
                sender.sendMessage(getMessage("messages.command-usage"));
                return true;
            }

            // Run the cleanup task manually
            // We run it asynchronously to avoid blocking the main thread
            CleanupTask task = new CleanupTask(this, true); // True indicates manual command
            getServer().getScheduler().runTaskAsynchronously(this, task);

            // The task will send the success message upon completion

            return true;
        }
        return false; // Return false if the command is not handled here
    }

    /**
     * Sends a message to all online players who have the required permission.
     *
     * @param message The message to send.
     */
    public void sendMessageToPermittedPlayers(String message) {
        Bukkit.getScheduler().runTask(this, () -> {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission(MESSAGE_PERMISSION)) {
                    player.sendMessage(message);
                }
            }
        });
    }
}
