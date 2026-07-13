# Changelog

## [1.0.0] - 2026-07-13

### Added

- Initial release
- Vault economy implementation backed by MySQL
- Auto-creates `accounts` table on startup
- Per-player balance storage via UUID
- Full Vault API support (deposit, withdraw, balance, format)
- Bank-account methods return proper `EconomyResponse.FAILURE` (not supported)
- Configurable database connection via `config.yml`
- Cross-server economy through shared database
- **Auto-migration from EssentialsX** – imports existing balances from `plugins/Essentials/userdata/` on first start
- Config option `migration.auto: true/false` to control auto-migration
