# SnipersComfortablePlots

`SnipersComfortablePlots` is a Spigot/Paper plot plugin for a dedicated void world with player-owned plots, trust-based visiting, admin plot tools, teleport polish, and physical marketplace chest shops.

Built against `spigot-api 1.21.3-R0.1-SNAPSHOT`.

## Features

- Dedicated `survivalplots` void world
- Deterministic player plot generation by UUID
- `/pfs` plot teleport and visiting commands
- Trust and untrust system
- Offline-safe player lookup by name or UUID
- Admin `/plotadmin` teleport and UUID translation tools
- Teleport titles, sounds, particles, and plot transition effects
- Plot boundary relocation back to plot origin
- Physical marketplace store chests with item-based currency

## Commands

### Player commands

- `/pfs`
  - Teleport to your plot
- `/pfs <player|uuid>`
  - Visit a plot you can access
- `/pfs trust <player>`
  - Trust a player to enter your plot
- `/pfs untrust <player>`
  - Remove a player's access
- `/pfs join <player>`
  - Join a plot where you are trusted
- `/pfs info [player|uuid]`
  - Show plot details
- `/pfs trusted [player|uuid]`
  - Show trusted players
- `/pfs where`
  - Show the plot you are currently standing in
- `/pfs market`
  - Show marketplace help
- `/pfs shop create <price> <amount>`
  - Register the chest you are looking at as a store chest
- `/pfs shop info`
  - Show the selected store chest details
- `/pfs shop remove`
  - Remove the selected store chest listing
- `/pfs shop collect`
  - Collect your marketplace earnings

### Admin commands

- `/plotadmin <player|uuid>`
  - Teleport to a player's plot even if they are offline
- `/plotadmin name <uuid>`
  - Translate UUID to player name
- `/plotadmin uuid <player>`
  - Translate player name to UUID

## Marketplace quick start

1. Teleport to your plot with `/pfs`.
2. Place a chest on your own plot.
3. Look at the chest.
4. Hold the item you want to sell in your main hand.
5. Run `/pfs shop create <price> <amount>`.
6. Stock the chest with that same item.
7. Buyers right-click the chest to purchase a bundle.
8. Collect earnings with `/pfs shop collect`.

Notes:

- Marketplace currency is item-based, not Vault-based.
- Default currency is `EMERALD`.
- Owners and admins can inspect a store chest without buying.
- Sneak-right-click can still be used to force the shop interaction on your own chest.

## Configuration

Main config file: `src/main/resources/config.yml`

Useful keys:

- `effects.teleport.*`
  - Teleport titles, sound, and particles
- `effects.transitions.*`
  - Plot enter effects
- `effects.boundary.*`
  - Boundary snap-back effects
- `marketplace.enabled`
  - Enable or disable chest shops
- `marketplace.currency`
  - Physical item used as currency, for example `EMERALD`
- `marketplace.shop-range`
  - Max range for selecting a chest with `/pfs shop ...`

## Build

Build the plugin jar with Maven:

```bash
mvn -DskipTests package
```

Output jar:

```text
target/SnipersComfortablePlots-9.2.jar
```

## Project layout

- `src/main/java/TSD/Plot/PlatformsPlugin.java`
  - Main plugin logic and commands
- `src/main/java/TSD/Plot/listeners/PlayerListener.java`
  - Plot movement and protection handling
- `src/main/java/TSD/Plot/listeners/BlockInteractListener.java`
  - Bed protection and store chest interactions
- `src/main/resources/plugin.yml`
  - Plugin metadata and commands
- `src/main/resources/config.yml`
  - Default configuration
