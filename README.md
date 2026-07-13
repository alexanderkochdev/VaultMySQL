# VaultMySQL

**Lightweight Vault economy implementation backed by MySQL.**

Connect to any MySQL database and get a fully working server-wide economy – no extras, no fuss.  
Every plugin that speaks Vault can use it immediately.

---

## Features

- ✅ **Single-file deployment** – drop the JAR in `plugins/`
- ✅ **Server-wide balance** – all servers sharing the same DB share the economy
- ✅ **Auto-table creation** – no manual schema setup
- ✅ **Zero configuration on Bukkit** – just the DB credentials
- ✅ **Vault-compatible** – works with EssentialsX, CMI, ShopGUI+, ChestShop, Jobs, and every other Vault plugin
- ✅ **Auto-migration from EssentialsX** – existing balances are imported automatically on first start

---

## Installation

### Requirements

- **Java 21+**
- **Paper 1.21+** (or any compatible server software)
- **Vault** plugin (install on your server)
- **MySQL 8.0+** (or MariaDB 10.6+)

### Steps

1. Copy `VaultMySQL-1.0.0.jar` into your server's `plugins/` folder
2. Start the server – a default `config.yml` is generated
3. Edit `plugins/VaultMySQL/config.yml` with your database credentials
4. Restart the server
5. Done – all Vault plugins now use your MySQL database

---

## Configuration

```yaml
database:
  url: "jdbc:mysql://127.0.0.1:3306/vaultmysql"
  user: "admin"
  password: "PASSWORD"
migration:
  auto: true
```

| Option | Default | Description |
|--------|---------|-------------|
| `database.url` | `jdbc:mysql://127.0.0.1:3306/vaultmysql` | JDBC connection URL |
| `database.user` | `admin` | Database username |
| `database.password` | `PASSWORD` | Database password |
| `migration.auto` | `true` | Auto-migrate balances from EssentialsX flatfiles on first start |

---

## Database Schema

The plugin creates a single table automatically on startup:

```sql
CREATE TABLE IF NOT EXISTS accounts (
    uuid   VARCHAR(36) PRIMARY KEY,
    balance DOUBLE DEFAULT 0.0
);
```

- **`uuid`** – Player's UUID (Mojang format)
- **`balance`** – Current balance (double-precision float)

---

## Permissions

This plugin registers with Vault at `ServicePriority.Highest`.  
No custom permissions are required – use your economy plugin's existing permission setup.

---

## Migration from EssentialsX

When you replace EssentialsX' built-in economy with VaultMySQL, your players' existing balances don't have to be lost.

### How it works

1. VaultMySQL creates the `accounts` table
2. **If the table is empty** → it scans `plugins/Essentials/userdata/` for existing `.yml` files
3. Each player's balance (`money:` field) is imported into the database
4. **If the table already has data** → migration is skipped (only runs once)

### Disable auto-migration

Set `migration.auto: false` in `config.yml` if you want to migrate manually.

---

## Cross-Server Economy

Every server that shares the same MySQL database automatically sees the same balances.  
No Redis, no messaging – just a single database connection.

1. Install VaultMySQL on **all** backend servers
2. Point each server to the **same** database
3. Done – players have one balance across all servers

---

## Building from Source

```bash
mvn clean package
```

The compiled JAR is at `target/VaultMySQL-1.0.0.jar`.

---

## License

MIT License – see [LICENSE](LICENSE)
