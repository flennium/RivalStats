package org.flennn;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RivalStats extends JavaPlugin implements CommandExecutor, Listener {

    private static final String DEBUG_PREFIX = "[RIVALSTATSDEBUG] ";
    private static final String MESSAGE_PREFIX = "§l§6ʀɪᴠᴀʟᴄᴏʀᴇ »§r ";
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private HikariDataSource dataSource;
    private String statsTable;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        String serverName = config.getString("servername");
        if (!Objects.equals(serverName, "NOT DEFINED")) {
            statsTable = "stats_" + serverName;
            setupDatabase();
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("reloadstats").setExecutor(this);
    }

    @Override
    public void onDisable() {
        if (dataSource != null) {
            dataSource.close();
        }
        executor.shutdown();
    }

    private void setupDatabase() {
        FileConfiguration config = getConfig();
        String host = config.getString("mysql.host");
        String database = config.getString("mysql.database");
        String user = config.getString("mysql.user");
        String password = config.getString("mysql.password");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + host + "/" + database);
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setIdleTimeout(30000);
        hikariConfig.setMaxLifetime(600000);
        hikariConfig.setConnectionTimeout(30000);

        dataSource = new HikariDataSource(hikariConfig);

        executor.execute(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS " + statsTable + " (" +
                                 "uuid VARCHAR(36) PRIMARY KEY," +
                                 "username VARCHAR(16)," +
                                 "kills INT DEFAULT 0," +
                                 "deaths INT DEFAULT 0," +
                                 "joins INT DEFAULT 0," +
                                 "quits INT DEFAULT 0," +
                                 "blocks_placed INT DEFAULT 0," +
                                 "blocks_broken INT DEFAULT 0," +
                                 "items_picked_up INT DEFAULT 0," +
                                 "food_eaten INT DEFAULT 0," +
                                 "damage_dealt INT DEFAULT 0," +
                                 "damage_taken INT DEFAULT 0" +
                                 ")")) {
                stmt.execute();
            } catch (SQLException e) {
                getLogger().severe("Database table creation failed: " + e.getMessage());
            }
        });

        executor.execute(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "CREATE TABLE IF NOT EXISTS levels (" +
                                 "uuid VARCHAR(36) PRIMARY KEY," +
                                 "username VARCHAR(16)," +
                                 "xp DOUBLE DEFAULT 0," +
                                 "level INT DEFAULT 1" +
                                 ")")) {
                stmt.execute();
            } catch (SQLException e) {
                getLogger().severe("Failed to create levels table: " + e.getMessage());
            }
        });
    }

    private void updateStats(UUID uuid, String username, int kills, int deaths, int joins, int quits,
                             int blocksPlaced, int blocksBroken, int itemsPickedUp, int foodEaten,
                             int damageDealt, int damageTaken) {
        executor.execute(() -> {
            if (dataSource == null) {
                getLogger().severe("Database is not initialized!");
                return;
            }
            String sql = "INSERT INTO " + statsTable + " (uuid, username, kills, deaths, joins, quits, blocks_placed, " +
                    "blocks_broken, items_picked_up, food_eaten, damage_dealt, damage_taken) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "username = VALUES(username), " +
                    "kills = kills + ?, deaths = deaths + ?, joins = joins + ?, quits = quits + ?, " +
                    "blocks_placed = blocks_placed + ?, blocks_broken = blocks_broken + ?, " +
                    "items_picked_up = items_picked_up + ?, food_eaten = food_eaten + ?, " +
                    "damage_dealt = damage_dealt + ?, damage_taken = damage_taken + ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {

                stmt.setString(1, uuid.toString());
                stmt.setString(2, username);
                stmt.setInt(3, kills);
                stmt.setInt(4, deaths);
                stmt.setInt(5, joins);
                stmt.setInt(6, quits);
                stmt.setInt(7, blocksPlaced);
                stmt.setInt(8, blocksBroken);
                stmt.setInt(9, itemsPickedUp);
                stmt.setInt(10, foodEaten);
                stmt.setInt(11, damageDealt);
                stmt.setInt(12, damageTaken);

                stmt.setInt(13, kills);
                stmt.setInt(14, deaths);
                stmt.setInt(15, joins);
                stmt.setInt(16, quits);
                stmt.setInt(17, blocksPlaced);
                stmt.setInt(18, blocksBroken);
                stmt.setInt(19, itemsPickedUp);
                stmt.setInt(20, foodEaten);
                stmt.setInt(21, damageDealt);
                stmt.setInt(22, damageTaken);

                stmt.executeUpdate();
            } catch (SQLException e) {
                getLogger().severe("Failed to update stats: " + e.getMessage());
            }
        });
    }


    private void addXPToLevel(Player player, double xpGain) {
        executor.execute(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "INSERT INTO levels (uuid, username, xp, level) VALUES (?, ?, ?, ?) " +
                                 "ON DUPLICATE KEY UPDATE xp = xp + ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, player.getName());
                stmt.setDouble(3, xpGain);
                stmt.setInt(4, 1);
                stmt.setDouble(5, xpGain);
                stmt.executeUpdate();
            } catch (SQLException e) {
                getLogger().severe("Failed to update XP in levels table: " + e.getMessage());
            }
            checkLevelUpForPlayer(player);
            displayXpProgress(player, xpGain);
        });
    }


    private void checkLevelUpForPlayer(Player player) {
        executor.execute(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT xp, level FROM levels WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    double xp = rs.getDouble("xp");
                    int level = rs.getInt("level");
                    int xpRequired = getXpForNextLevel(level);
                    if (xp >= xpRequired) {
                        int newLevel = level + 1;
                        try (PreparedStatement updateStmt = connection.prepareStatement(
                                "UPDATE levels SET level = ?, xp = xp - ? WHERE uuid = ?")) {
                            updateStmt.setInt(1, newLevel);
                            updateStmt.setDouble(2, xpRequired);
                            updateStmt.setString(3, player.getUniqueId().toString());
                            updateStmt.executeUpdate();
                        }
                        Bukkit.getScheduler().runTask(this, () -> {
                            player.playSound(player.getLocation(), Sound.ENTITY_VINDICATOR_CELEBRATE, 1.0f, 1.0f);

                            Firework firework = (Firework) player.getWorld().spawnEntity(player.getLocation(), org.bukkit.entity.EntityType.FIREWORK);
                            FireworkMeta meta = firework.getFireworkMeta();
                            FireworkEffect effect = FireworkEffect.builder()
                                    .with(FireworkEffect.Type.STAR)
                                    .withColor(org.bukkit.Color.ORANGE)
                                    .withFade(org.bukkit.Color.RED)
                                    .flicker(true)
                                    .trail(true)
                                    .build();
                            meta.addEffect(effect);
                            meta.setPower(1);
                            firework.setFireworkMeta(meta);
                        });
                    }
                }
                rs.close();
            } catch (SQLException e) {
                getLogger().severe("Failed to check level up in levels table: " + e.getMessage());
            }
        });
    }


    private int getXpForNextLevel(int currentLevel) {
        int baseXP = 80;
        double multiplier = 1.90;
        return (int) Math.round(baseXP * Math.pow(multiplier, currentLevel - 1));
    }

    private void displayXpProgress(Player player, double xpGain) {
        executor.execute(() -> {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(
                         "SELECT xp, level FROM levels WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    double currentXp = rs.getDouble("xp");
                    int level = rs.getInt("level");
                    int xpRequired = getXpForNextLevel(level);
                    double percentRaw = (currentXp / xpRequired) * 100.0;
                    double percent = Math.round(Math.min(100, percentRaw) * 10) / 10.0;
                    int progressBarPercent = (int) Math.floor(percent);
                    String progressBar = getProgressBar(progressBarPercent);
                    String message = ChatColor.GREEN + "+" + Math.round(xpGain) + " XP " +
                            ChatColor.YELLOW + progressBar + " " +
                            ChatColor.AQUA + String.format("%.1f", percent) + "% to next level";
                    Bukkit.getScheduler().runTask(this, () -> {
                        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                new net.md_5.bungee.api.chat.TextComponent(message));
                    });
                }
                rs.close();
            } catch (SQLException e) {
                getLogger().severe("Failed to display XP progress: " + e.getMessage());
            }
        });
    }


    private String getProgressBar(double percent) {
        int totalBlocks = 10;
        int filledBlocks = (int) Math.round(totalBlocks * (percent / 100.0));
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < filledBlocks; i++) {
            bar.append("█");
        }
        for (int i = filledBlocks; i < totalBlocks; i++) {
            bar.append("░");
        }
        return bar.toString();
    }


    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (getConfig().getBoolean("settings.track_deaths")) {
            Player player = event.getEntity();
            UUID uuid = player.getUniqueId();
            Player killer = player.getKiller();
            if (player.getGameMode() != GameMode.SURVIVAL) {
                return;
            }
            if (getConfig().getBoolean("settings.debug")) {
                getLogger().info(DEBUG_PREFIX + "Player Death: " + player.getName());
            }

            if (killer != null && getConfig().getBoolean("settings.track_kills")) {
                if (getConfig().getBoolean("settings.debug")) {
                    getLogger().info(DEBUG_PREFIX + "Player Kill: " + killer.getName() + " killed " + player.getName());
                }
                updateStats(killer.getUniqueId(), killer.getName(), 1, 0, 0, 0, 0, 0, 0, 0, 0, 0);
                addXPToLevel(killer, 5.0);
            }
            updateStats(uuid, player.getName(), 0, 1, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (getConfig().getBoolean("settings.track_joins")) {
            Player player = event.getPlayer();
            if (player.getGameMode() != GameMode.SURVIVAL) {
                return;
            }
            if (getConfig().getBoolean("settings.debug")) {
                getLogger().info(DEBUG_PREFIX + "Player Join: " + player.getName());
            }

            updateStats(player.getUniqueId(), player.getName(), 0, 0, 1, 0, 0, 0, 0, 0, 0, 0);
            Bukkit.getScheduler().runTaskTimer(this, () -> addXPToLevel(player, 5.0), 1200L, 1200L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (getConfig().getBoolean("settings.track_quits")) {
            Player player = event.getPlayer();
            if (player.getGameMode() != GameMode.SURVIVAL) {
                return;
            }
            if (getConfig().getBoolean("settings.debug")) {
                getLogger().info(DEBUG_PREFIX + "Player Quit: " + player.getName());
            }

            updateStats(player.getUniqueId(), player.getName(), 0, 0, 0, 1, 0, 0, 0, 0, 0, 0);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (getConfig().getBoolean("settings.track_blocks_placed")) {
            Player player = event.getPlayer();
            if (player.getGameMode() != GameMode.SURVIVAL) {
                return;
            }
            if (getConfig().getBoolean("settings.debug")) {
                getLogger().info(DEBUG_PREFIX + "Block Placed by: " + player.getName());
            }

            updateStats(player.getUniqueId(), player.getName(), 0, 0, 0, 0, 1, 0, 0, 0, 0, 0);
            addXPToLevel(player, 0.2);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (getConfig().getBoolean("settings.track_blocks_broken")) {
            Player player = event.getPlayer();
            if (player.getGameMode() != GameMode.SURVIVAL) {
                return;
            }
            if (getConfig().getBoolean("settings.debug")) {
                getLogger().info(DEBUG_PREFIX + "Block Broken by: " + player.getName());
            }

            updateStats(player.getUniqueId(), player.getName(), 0, 0, 0, 0, 0, 1, 0, 0, 0, 0);
            addXPToLevel(player, 0.3);
        }
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        if (getConfig().getBoolean("settings.track_food_eaten")) {
            Player player = event.getPlayer();
            if (player.getGameMode() != GameMode.SURVIVAL) {
                return;
            }
            if (getConfig().getBoolean("settings.debug")) {
                getLogger().info(DEBUG_PREFIX + "Food Consumed by: " + player.getName());
            }

            updateStats(player.getUniqueId(), player.getName(), 0, 0, 0, 0, 0, 0, 0, 1, 0, 0);
            addXPToLevel(player, 0.3);
        }
    }


    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (getConfig().getBoolean("settings.track_damage_taken")) {
            if (attacker.getGameMode() != GameMode.SURVIVAL) {
                return;
            }
            if (getConfig().getBoolean("settings.debug")) {
                getLogger().info(DEBUG_PREFIX + "Player " + victim.getName() + " took " + event.getDamage() + " damage.");
            }
            updateStats(victim.getUniqueId(), victim.getName(), 0, 0, 0, 0, 0, 0, 0, 0, 0, (int) event.getDamage());
        }

        if (getConfig().getBoolean("settings.track_damage_dealt")) {
            if (attacker.getGameMode() != GameMode.SURVIVAL) {
                return;
            }
            if (getConfig().getBoolean("settings.debug")) {
                getLogger().info(DEBUG_PREFIX + "Player " + attacker.getName() + " dealt " + event.getDamage() + " damage.");
            }
            updateStats(attacker.getUniqueId(), attacker.getName(), 0, 0, 0, 0, 0, 0, 0, 0, (int) event.getDamage(), 0);
            addXPToLevel(attacker, event.getDamage() * 0.1);
        }
    }


    @EventHandler
    public void onItemPickup(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();

        if (!getConfig().getBoolean("settings.track_items_picked_up")) {
            return;
        }
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (getConfig().getBoolean("settings.debug")) {
            getLogger().info(DEBUG_PREFIX + "Item Picked Up by: " + player.getName());
        }

        updateStats(player.getUniqueId(), player.getName(), 0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
        addXPToLevel(player, 0.1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("reloadstats")) {
            if (sender instanceof Player && !sender.hasPermission("rivalstats.reload")) {
                sender.sendMessage(MESSAGE_PREFIX + ChatColor.RED + "You do not have permission to use this command!");
                return true;
            }

            sender.sendMessage(MESSAGE_PREFIX + ChatColor.YELLOW + "Reloading configuration and database connection...");

            reloadConfig();

            if (dataSource != null) {
                dataSource.close();
            }

            setupDatabase();

            sender.sendMessage(MESSAGE_PREFIX + ChatColor.GREEN + "Stats and config reloaded successfully!");
            return true;
        }
        return false;
    }
}
