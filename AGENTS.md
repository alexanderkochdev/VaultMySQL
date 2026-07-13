# VaultMySQL — AI Context

## Overview

VaultMySQL is a lightweight Vault economy provider backed by MySQL.  
It implements the full `net.milkbowl.vault.economy.Economy` interface.

**Version**: 1.0.0

## Architecture

Single class, zero dependencies at runtime (MySQL Connector is shaded):

```
VaultMySQL/
├── pom.xml
├── README.md
├── CHANGELOG.md
├── AGENTS.md
├── LICENSE
├── .gitignore
└── src/
    └── main/
        ├── java/dev/alexanderkoch/vaultmysql/
        │   └── VaultMySQL.java          # Main class (JavaPlugin + Economy)
        └── resources/
            ├── plugin.yml
            └── config.yml
```

## How It Works

1. **onEnable()** → reads config, creates `accounts` table, **auto-migrates from EssentialsX** (if table is empty), registers with Vault `ServiceManager`
2. **Economy operations** → open a fresh JDBC connection, execute SQL, close it
3. **Auto-create accounts** → `hasAccount()` always returns `true` – accounts are created implicitly on first deposit

### Database

```sql
accounts (uuid VARCHAR(36) PRIMARY KEY, balance DOUBLE DEFAULT 0.0)
```

- `REPLACE INTO` for upsert (set balance)
- `SELECT ... WHERE uuid=?` for read
- Each operation opens its own connection (simple, no pooling overhead)

### Vault Methods

| Method | Behaviour |
|--------|-----------|
| `hasAccount()` | Always `true` (auto-create) |
| `getBalance()` | Reads from DB, defaults `0.0` |
| `depositPlayer()` | Adds to balance, creates row |
| `withdrawPlayer()` | Subtracts from balance, fails if insufficient |
| `bank*()` | All return `FAILURE` – no bank support |
| `hasBankSupport()` | `false` |
| `format()` | `String.format("%.2f", amount)` |
| `fractionalDigits()` | `2` |

## Conventions

- **Package**: `dev.alexanderkoch.vaultmysql`
- **Java 21**, 4-space indentation
- All DB operations use try-with-resources
- `OfflinePlayer` UUID is used as the primary key
- MySQL Connector is shaded to `dev.alexanderkoch.vaultmysql.libs.mysql`

## Dependencies

| Dependency | Scope | Notes |
|------------|-------|-------|
| Paper API 1.21 | Provided | Server software |
| VaultAPI 1.7 | Provided | Economy interface |
| MySQL Connector/J 8.2 | Compile | Shaded into JAR |

## Build

```bash
mvn clean package
```
Output: `target/VaultMySQL-1.0.0.jar`
