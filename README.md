# MobAI - Advanced Mob Intelligence

## Project Identity
- **Name:** MobAI - Advanced Mob Intelligence
- **Mod ID:** `mobai`
- **Version:** `${version}` (Resolved at build time)

## Technical Summary
The **MobAI** mod completely overhauls the intelligence and capabilities of standard Minecraft hostile mobs. The core logic relies on heavily modifying the Entity AI Goal system, granting zombies and skeletons the ability to pathfind through obstacles by breaking blocks (respecting material tiers like wood, stone, and deepslate) or climbing over them. Furthermore, the mod introduces a robust JSON-driven `WaveManager` that coordinates massive mob invasions towards a globally defined objective coordinate. It also hooks into entity death events to process a built-in "Kill Money" reward system via vanilla scoreboards.

## Feature Breakdown
- **Advanced Mob AI Goals:** Custom AI allows zombies and skeletons to actively mine blocks blocking their path and climb obstacles to reach their target.
- **Wave Invasion System:** Dynamically loads JSON wave definitions to orchestrate coordinated attacks. Supports wave series, mini-boss scaling, and specific coordinate targeting (`objectiveX`, `Y`, `Z`).
- **Kamikaze & Variants:** Introduces explosive kamikaze adult zombies and three distinct sub-types of baby zombies (vanilla, boosted stats, and kamikaze).
- **Day/Night Cycle Integration:** Waves can be strictly bound to nighttime, with automatic despawn protocols triggering at dawn (configurable tick thresholds).
- **Kill Money Integration:** Built-in economy feature (`kill_money.json`) that directly updates a scoreboard objective when specific entity types are slain.

## Command Registry
*Note: All commands require OP Permission Level 2.*

| Command | Description | Permission Level |
| :--- | :--- | :--- |
| `/mobai wave start <number>` | Manually initiates a specific wave. | OP (2) |
| `/mobai wave stop` | Instantly stops the active wave. | OP (2) |
| `/mobai wave list` | Lists all available loaded wave definitions. | OP (2) |
| `/mobai wave info <number>` | Displays details about a specific wave configuration. | OP (2) |
| `/mobai wave reload` | Reloads wave JSONs from the configuration directory. | OP (2) |
| `/mobai wave next` | Starts the subsequent wave in the numerical order. | OP (2) |
| `/mobai wave series <start> <end>` | Starts an automated sequence of waves. | OP (2) |
| `/mobai wave series-status` | Displays the status of the currently active wave series. | OP (2) |
| `/mobai config set <param> <value>`| Modifies a configuration parameter dynamically. | OP (2) |
| `/mobai config get <param>` | Fetches the current value of a configuration parameter. | OP (2) |
| `/mobai config reload` | Reloads the configuration files from the disk. | OP (2) |
| `/mobai config list` | Lists the most critical AI and global configuration parameters. | OP (2) |
| `/mobai status` | Displays the overall status of the mod, including active wave and objective. | OP (2) |
| `/mobai help` | Displays the help menu. | OP (2) |

## Configuration Schema
The mod generates its primary configuration at `config/mobai/mobai_config.json`:

```json
{
  "zombieClimbHeight": 1.5,
  "zombieBreakWood": true,
  "zombieBreakLevel": "deepslate",
  "zombieBreakSpeed": 60,
  "zombieDamageMultiplier": 1.0,
  "zombieMemoryRange": 128.0,
  "zombieSpeed": 0.23,
  "zombieCanSwim": true,
  "zombieBonusHealth": 0.0,
  "skeletonBreakBlocks": true,
  "skeletonBreakLevel": "deepslate",
  "skeletonBreakSpeed": 80,
  "skeletonDamageMultiplier": 1.0,
  "skeletonMemoryRange": 128.0,
  "skeletonSpeed": 0.25,
  "skeletonShootRange": 15.0,
  "kamikazeEnabled": true,
  "kamikazeExplosionRadius": 3.0,
  "kamikazeBreaksBlocks": false,
  "kamikazeTriggerDistance": 2.5,
  "kamikazeCountdownSeconds": 2,
  "miniVanillaEnabled": true,
  "miniBoostedEnabled": true,
  "miniBoostedSpeedMult": 1.3,
  "miniBoostedDamageMult": 1.2,
  "miniBoostedHealthMult": 0.6,
  "miniKamikazeEnabled": true,
  "miniKamikazeExplosion": 2.0,
  "miniKamikazeTriggerDist": 2.0,
  "globalHealthMultiplier": 1.0,
  "globalDamageMultiplier": 1.0,
  "globalCanSwim": true,
  "objectiveX": -2147483648,
  "objectiveY": -2147483648,
  "objectiveZ": -2147483648,
  "objectiveSpeed": 1.0,
  "waveCooldownSeconds": 30,
  "waveAutoNext": false,
  "waveSpawnRadius": 24,
  "waveSpawnMinRadius": 8,
  "waveAnnounce": true,
  "nightOnlyStart": true,
  "despawnAtDawn": true,
  "dawnTick": 23000,
  "duskTick": 13000,
  "miniBossHealthMultiplier": 5.0,
  "miniBossDamageMultiplier": 2.5,
  "miniBossSpeedMultiplier": 1.2
}
```

*Note: The mod also generates `config/mobai/kill_money.json` to manage specific monetary rewards for slaying different entity IDs.*

## Developer Info
- **Author:** el_this_boy
- **Platform:** Fabric 1.21.1
