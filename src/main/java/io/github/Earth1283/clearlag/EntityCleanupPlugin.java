package io.github.Earth1283.clearlag; // Corrected package name

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

// Import necessary BungeeCord components for clickable text
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatColor; // Use BungeeCord ChatColor for consistent color codes

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class EntityCleanupPlugin extends JavaPlugin {

    private BukkitTask scheduledCleanupTimerTask; // This task will schedule the synchronous cleanup
    private BukkitTask countingTask; // New task for counting entities

    private long cleanupIntervalTicks;
    private long cleanupDelayTicks;
    private long countingIntervalTicks; // New interval for counting
    private int maxEntitiesBeforeCleanup; // New threshold

    private List<EntityType> entitiesToClear; // List of entity types to clear

    private static final String MESSAGE_PERMISSION = "clearlag.message";
    private static final String COMMAND_PERMISSION = "clearlag.command";
    private static final String QQ_COMMAND_PERMISSION = "clearlag.qq"; // New permission for /qq command

    @Override
    public void onEnable() {
        // Load configuration files
        saveDefaultConfig(); // Creates config.yml if it doesn't exist
        saveResource("messages.yml", false); // Creates messages.yml if it doesn't exist

        // Load configuration values
        loadConfig();

        // Schedule the asynchronous cleanup timer task
        scheduleScheduledCleanupTimerTask(); // This schedules the task that *will* schedule the cleanup

        // Schedule the asynchronous counting task
        scheduleCountingTask(); // New task scheduling

        // Register command executors
        Objects.requireNonNull(getCommand("clearlag")).setExecutor(this);
        Objects.requireNonNull(getCommand("qq")).setExecutor(this); // Register the /qq command

        getLogger().info("实体清理插件已启用!"); // Plugin enabled message
    }

    @Override
    public void onDisable() {
        // Cancel the scheduled tasks when the plugin is disabled
        if (scheduledCleanupTimerTask != null && !scheduledCleanupTimerTask.isCancelled()) {
            scheduledCleanupTimerTask.cancel();
        }
        if (countingTask != null && !countingTask.isCancelled()) {
            countingTask.cancel();
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

        // Get counting interval and threshold from config.yml
        this.countingIntervalTicks = getConfig().getLong("counting-interval-seconds", 15) * 20L; // Default 15 seconds
        this.maxEntitiesBeforeCleanup = getConfig().getInt("max-entities-before-cleanup", 150); // Default 150

        // Ensure intervals and delay are positive
        if (this.cleanupIntervalTicks <= 0) {
            getLogger().warning("config.yml 中的 cleanup-interval-seconds 无效。使用默认值 300 秒。");
            this.cleanupIntervalTicks = 300 * 20L;
        }
        if (this.cleanupDelayTicks < 0) {
            getLogger().warning("config.yml 中的 cleanup-delay-seconds 无效。使用默认值 60 秒。");
            this.cleanupDelayTicks = 60 * 20L;
        }
        if (this.countingIntervalTicks <= 0) {
            getLogger().warning("config.yml 中的 counting-interval-seconds 无效。使用默认值 15 秒。");
            this.countingIntervalTicks = 15 * 20L;
        }
        if (this.maxEntitiesBeforeCleanup < 0) {
            getLogger().warning("config.yml 中的 max-entities-before-cleanup 无效。使用默认值 150。");
            this.maxEntitiesBeforeCleanup = 150;
        }


        // Load entities to clear from config.yml
        loadEntitiesToClear();

        getLogger().info("配置已加载。清理间隔: " + (cleanupIntervalTicks / 20) + " 秒, 清理延迟: " + (cleanupDelayTicks / 20) + " 秒.");
        getLogger().info("实体计数间隔: " + (countingIntervalTicks / 20) + " 秒, 触发清理的实体阈值: " + maxEntitiesBeforeCleanup);
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
     * Schedules the asynchronous task that will trigger the synchronous entity cleanup.
     */
    private void scheduleScheduledCleanupTimerTask() {
        // Cancel any existing task before scheduling a new one
        if (scheduledCleanupTimerTask != null && !scheduledCleanupTimerTask.isCancelled()) {
            scheduledCleanupTimerTask.cancel();
        }

        // Schedule an asynchronous task that will, when run, schedule the synchronous cleanup.
        this.scheduledCleanupTimerTask = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            // This code runs asynchronously. We now schedule the actual cleanup synchronously.
            Bukkit.getScheduler().runTask(this, new CleanupTask(this, false));
        }, cleanupDelayTicks, cleanupIntervalTicks);

        getLogger().info("实体清理定时器任务已安排。首次触发将在 " + (cleanupDelayTicks / 20) + " 秒后，之后每 " + (cleanupIntervalTicks / 20) + " 秒触发一次。");
    }

    /**
     * Schedules the asynchronous entity counting task.
     */
    private void scheduleCountingTask() {
        // Cancel any existing task before scheduling a new one
        if (countingTask != null && !countingTask.isCancelled()) {
            countingTask.cancel();
        }

        // Create the counting task
        EntityCountingTask task = new EntityCountingTask(this);

        // Schedule the task to run asynchronously with a repeating interval
        // The first run will happen after countingIntervalTicks, then repeat every countingIntervalTicks
        this.countingTask = getServer().getScheduler().runTaskTimerAsynchronously(this, task, countingIntervalTicks, countingIntervalTicks);

        getLogger().info("实体计数任务已安排。每 " + (countingIntervalTicks / 20) + " 秒运行一次。");
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
        // Use Bukkit ChatColor for standard messages
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

    /**
     * Gets the maximum number of undesired entities allowed before immediate cleanup.
     *
     * @return The maximum entity threshold.
     */
    public int getMaxEntitiesBeforeCleanup() {
        return maxEntitiesBeforeCleanup;
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

            // Schedule the cleanup task to run synchronously on the main thread
            Bukkit.getScheduler().runTask(this, new CleanupTask(this, true, sender));

            // The task will send the success message upon completion

            return true;
        } else if (command.getName().equalsIgnoreCase("qq")) { // Handle the /qq command
            // Check for command permission
            if (!sender.hasPermission(QQ_COMMAND_PERMISSION)) {
                sender.sendMessage(getMessage("messages.command-no-permission"));
                return true;
            }

            // Create the chat message with clickable text
            TextComponent message = new TextComponent("QQ群号：");
            message.setColor(ChatColor.GREEN); // Set the color for "QQ群号：" to light green

            TextComponent qqNumber = new TextComponent("1035332547");
            qqNumber.setColor(ChatColor.AQUA); // Set the color for the number to light blue
            qqNumber.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "1035332547")); // Make it copy to clipboard on click

            message.addExtra(qqNumber); // Add the clickable number component

            // Send the message to the command sender
            sender.spigot().sendMessage(message);

            return true;
        }
        return false; // Return false if the command is not handled here
    }

    /**
     * Sends a message to all online players who have the required permission.
     * Uses BungeeCord TextComponent for potentially richer messages in the future.
     *
     * @param message The message to send.
     */
    public void sendMessageToPermittedPlayers(String message) {
        Bukkit.getScheduler().runTask(this, () -> {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission(MESSAGE_PERMISSION)) {
                    // Use Spigot's sendMessage method which handles TextComponents
                    player.spigot().sendMessage(new TextComponent(message));
                }
            }
        });
    }

    /**
     * Triggers an immediate entity cleanup and resets the scheduled timer.
     * This method is called from an asynchronous task (EntityCountingTask).
     * It schedules the cleanup to run synchronously.
     */
    public void triggerImmediateCleanup() {
        // Log the message with color codes for the console
        getLogger().info("\u001B[31m实体数量超过阈值，触发立即清理！\u001B[0m"); // Red ANSI color

        // Cancel the currently scheduled cleanup timer task
        if (scheduledCleanupTimerTask != null && !scheduledCleanupTimerTask.isCancelled()) {
            scheduledCleanupTimerTask.cancel();
            getLogger().info("已取消原定的实体清理定时器任务。");
        }

        // Schedule an immediate cleanup task to run synchronously on the main thread
        Bukkit.getScheduler().runTask(this, new CleanupTask(this, false)); // Treat as scheduled for message purposes

        // Reschedule the main cleanup timer task to start again after the initial delay
        scheduleScheduledCleanupTimerTask(); // This will schedule a new task starting after cleanupDelayTicks
    }
}
