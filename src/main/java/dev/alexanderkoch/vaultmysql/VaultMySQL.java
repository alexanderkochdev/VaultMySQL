package dev.alexanderkoch.vaultmysql;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;

public class VaultMySQL extends JavaPlugin implements Economy {

    private static final EconomyResponse BANK_NOT_SUPPORTED = new EconomyResponse(
            0, 0, EconomyResponse.ResponseType.FAILURE,
            "VaultMySQL does not support bank accounts"
    );

    private String dbUrl;
    private String dbUser;
    private String dbPass;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dbUrl = getConfig().getString("database.url", "jdbc:mysql://127.0.0.1:3306/vaultmysql");
        dbUser = getConfig().getString("database.user", "admin");
        dbPass = getConfig().getString("database.password", "PASSWORD");

        // Test connection and create table
        try (Connection c = getDB()) {
            if (c == null) {
                throw new RuntimeException("getDB() returned null – connection failed");
            }
            try (PreparedStatement s = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS accounts (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "balance DOUBLE DEFAULT 0.0" +
                            ")"
            )) {
                s.execute();
            }
        } catch (Exception e) {
            getLogger().severe("Database connection failed: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Auto-migrate from EssentialsX flatfiles if accounts table is empty
        migrateFromEssentials();

        Bukkit.getServicesManager().register(Economy.class, this, this, ServicePriority.Highest);
        getLogger().info("VaultMySQL enabled! Connected to " + dbUrl);
    }

    // ---------------------------------------------------------------------------------
    // Database
    // ---------------------------------------------------------------------------------

    private Connection getDB() {
        try {
            return DriverManager.getConnection(dbUrl, dbUser, dbPass);
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Database connection failed", e);
            return null;
        }
    }

    private double getBal(String uuid) {
        try (Connection c = getDB()) {
            if (c == null) return 0.0;
            try (PreparedStatement s = c.prepareStatement("SELECT balance FROM accounts WHERE uuid=?")) {
                s.setString(1, uuid);
                try (ResultSet rs = s.executeQuery()) {
                    if (rs.next()) return rs.getDouble("balance");
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to get balance for " + uuid, e);
        }
        return 0.0;
    }

    private void setBal(String uuid, double amt) {
        try (Connection c = getDB()) {
            if (c == null) return;
            try (PreparedStatement s = c.prepareStatement("REPLACE INTO accounts (uuid,balance) VALUES (?,?)")) {
                s.setString(1, uuid);
                s.setDouble(2, amt);
                s.execute();
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to set balance for " + uuid, e);
        }
    }

    private boolean hasBal(String uuid, double amount) {
        return getBal(uuid) >= amount;
    }

    // ---------------------------------------------------------------------------------
    // Economy implementation
    // ---------------------------------------------------------------------------------

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return String.format("%.2f", amount);
    }

    @Override
    public String currencyNamePlural() {
        return "Dollars";
    }

    @Override
    public String currencyNameSingular() {
        return "Dollar";
    }

    // ---------------------------------------------------------------------------------
    // Player accounts (UUID-based)
    // ---------------------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public double getBalance(String playerName) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
        return getBal(p.getUniqueId().toString());
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return getBal(player.getUniqueId().toString());
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return hasBal(player.getUniqueId().toString(), amount);
    }

    @Override
    @SuppressWarnings("deprecation")
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amount");
        }
        OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
        String uuid = p.getUniqueId().toString();
        double balance = getBal(uuid);
        if (balance < amount) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        setBal(uuid, balance - amount);
        return new EconomyResponse(amount, balance - amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amount");
        }
        String uuid = player.getUniqueId().toString();
        double balance = getBal(uuid);
        if (balance < amount) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        setBal(uuid, balance - amount);
        return new EconomyResponse(amount, balance - amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    @SuppressWarnings("deprecation")
    public EconomyResponse depositPlayer(String playerName, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amount");
        }
        OfflinePlayer p = Bukkit.getOfflinePlayer(playerName);
        String uuid = p.getUniqueId().toString();
        double balance = getBal(uuid);
        setBal(uuid, balance + amount);
        return new EconomyResponse(amount, balance + amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amount");
        }
        String uuid = player.getUniqueId().toString();
        double balance = getBal(uuid);
        setBal(uuid, balance + amount);
        return new EconomyResponse(amount, balance + amount, EconomyResponse.ResponseType.SUCCESS, null);
    }

    // ---------------------------------------------------------------------------------
    // Bank accounts (not supported)
    // ---------------------------------------------------------------------------------

    @Override
    public EconomyResponse createBank(String name, String player) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return BANK_NOT_SUPPORTED;
    }

    @Override
    public List<String> getBanks() {
        return new ArrayList<>();
    }

    // ---------------------------------------------------------------------------------
    // Player accounts (deprecated String overloads – delegated to OfflinePlayer)
    // These exist purely for backward compatibility with Vault consumers.
    // ---------------------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAccount(String playerName) {
        return true; // auto-create account on first transaction
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    @SuppressWarnings("deprecation")
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    @SuppressWarnings("deprecation")
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean createPlayerAccount(String playerName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean createPlayerAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return true;
    }

    // ---------------------------------------------------------------------------------
    // Migration from EssentialsX flatfiles
    // ---------------------------------------------------------------------------------

    private void migrateFromEssentials() {
        if (!getConfig().getBoolean("migration.auto", true)) {
            getLogger().info("Auto-migration disabled in config – skipping");
            return;
        }

        // Check if accounts table is empty
        try (Connection c = getDB()) {
            if (c == null) return;
            try (PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM accounts");
                 ResultSet rs = s.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    getLogger().info("Accounts table already has data – skipping migration");
                    return;
                }
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Failed to check accounts table count", e);
            return;
        }

        // Check for EssentialsX userdata folder
        File essentialsDir = new File(getDataFolder().getParentFile(), "Essentials/userdata");
        if (!essentialsDir.isDirectory()) {
            getLogger().info("No EssentialsX userdata found at " + essentialsDir + " – skipping migration");
            return;
        }

        File[] files = essentialsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            getLogger().info("No EssentialsX userdata files found – skipping migration");
            return;
        }

        getLogger().info("Found " + files.length + " EssentialsX userdata files – starting migration...");

        int migrated = 0;
        int failed = 0;

        for (File file : files) {
            String uuid = file.getName().replace(".yml", "");

            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                if (!config.contains("money")) continue;

                double balance = config.getDouble("money");

                try (Connection c = getDB()) {
                    if (c == null) {
                        failed++;
                        continue;
                    }
                    try (PreparedStatement s = c.prepareStatement(
                            "REPLACE INTO accounts (uuid, balance) VALUES (?, ?)")) {
                        s.setString(1, uuid);
                        s.setDouble(2, balance);
                        s.execute();
                        migrated++;
                    }
                }
            } catch (Exception e) {
                failed++;
                getLogger().log(Level.WARNING, "Failed to migrate " + uuid, e);
            }
        }

        getLogger().info("Migration complete: " + migrated + " accounts migrated from EssentialsX"
                + (failed > 0 ? " (" + failed + " failed)" : ""));
    }
}
