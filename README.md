# DevReforgePlus

A Spigot/Paper plugin that adds a fully configurable **item reforge system** on top of [MMOItems](https://gitlab.com/phoenix-dvpmt/mmoitems).  
Players open a reforge GUI, place their MMOItems weapon/gear in the slot, and roll a random modifier that permanently applies stat bonuses to the item.

---

## Features

- **Weighted modifier pool** — define any number of modifiers per item type with configurable weights and percent chances
- **FLAT & SCALE stat bonuses** — flat additions or base-stat multipliers; both integrate cleanly with MMOItems' `StatHistory` pipeline
- **Reforge tracker** — tracks which modifier is currently on an item and how many times it has been reforged, persisted via NBT
- **Dynamic GUI lore** — modifier list and modifier info panels update live as items are inserted or removed
- **Spam protection** — configurable per-player reforge cooldown
- **Localized stat display** — `lang/stats.yml` maps each MMOItems stat ID to a MiniMessage-formatted lore string per stat type
- **Hot-reloadable configs** — modifier and type configs load from per-file YAML folders

---

## Requirements

| Dependency | Version |
|------------|---------|
| Paper / Spigot | 1.21+ |
| [MMOItems](https://www.spigotmc.org/resources/mmoitems-premium.39267/) | 6.10+ |
| [MythicLib](https://www.spigotmc.org/resources/mythiclib.90306/) | (bundled with MMOItems) |

---

## Installation

1. Drop `DevReforgePlus-x.x.jar` into your server's `plugins/` folder.
2. Restart the server. Default config files will be generated automatically.
3. Edit `modifiers/` and `types/` as desired (see below), then reload.

---

## Configuration

### `config.yml`

```yaml
Economy:
  Base: 10000       # Base reforge cost
  Multiplier: 1.25  # Cost multiplier per attempt
  Max: 1000000      # Cost cap

ReforgeDelay: 0.1   # Seconds between allowed reforges per player

ReforgeDisplay:
  LoreLine: '<white>ヸ Reforge Bonus: <white><reforge_display>'
  Stats:
    Format: '<white>    <stat_format>'   # <stat_format> is populated from lang/stats.yml

GUI:
  Reforge:
    ...  # Full GUI layout, slot assignments, and item state definitions
```

### `modifiers/<name>.yml`

Defines a single reforge modifier — what it is and what it does.

```yaml
id: savage
displayName: '<red>Savage'

stats:
  ATTACK_DAMAGE:
    Type: FLAT    # FLAT  = add a flat value
    Value: 15.0
  CRITICAL_STRIKE_CHANCE:
    Type: SCALE   # SCALE = percent of the item's base stat
    Value: 0.10   # e.g. 0.10 = 10% of base ATTACK_DAMAGE
```

### `types/<name>.yml`

Maps MMOItems type strings to their rollable modifier pool.

```yaml
type:
  - SWORD    # MMOItems type ID (can list multiple)

reforges:
  'savage': 35     # modifier id: weight (higher = more likely)
  'agile':  25
  'sturdy': 30
```

### `lang/stats.yml`

Localized formatting for each MMOItems stat and each value type.

```yaml
stats:
  DEFAULT:                             # Fallback for unlisted stats
    FLAT:          '<#00FF00>+%value% <gray>%stat%'
    FLAT_NEGATIVE: '<#FF0000>-%abs_value% <gray>%stat%'
    SCALE:         '<#00FF00>+%percent%% <gray>%stat%'
    SCALE_NEGATIVE:'<#FF0000>-%abs_value%% <gray>%stat%'
  ATTACK_DAMAGE:
    FLAT: '<white>Attack Damage: <#00FF00>+%value%'
    ...
```

**Placeholders:** `%value%`, `%abs_value%`, `%percent%`, `%stat%`

---

## File Layout

```
plugins/DevReforgePlus/
├── config.yml             Main configuration
├── lang/
│   └── stats.yml          Stat display language file
├── modifiers/             One .yml per reforge modifier
│   ├── savage.yml
│   ├── agile.yml
│   └── sturdy.yml
└── types/                 One .yml per item-type pool
    └── sword.yml
```

---

## GUI Overview

The reforge menu is a 6-row inventory with three functional panels:

| Panel | Default Slot | Purpose |
|---|---|---|
| Input | 22 | Place the item to reforge |
| Reforge Button | 31 | Click to reforge; shows validity/cost state |
| Modifier Info | 24 | Shows current modifier + reforge count |
| Modifier List | 20 | Shows all possible modifiers and chances |

All slots and item states (materials, display names, lore) are fully configurable in `config.yml` under `GUI.Reforge.Options`.

---

## Modifier Display on Items

When an item is reforged, the modifier header and per-stat lore lines are injected directly into the item's MMOItems lore via `TrackerStat.whenApplied()`. The format comes from `ReforgeDisplay` in `config.yml` and the stat strings from `lang/stats.yml`.

Example result on a sword with the *Savage* modifier:

```
ヸ Reforge Bonus: Savage
    Attack Damage: +15
    Critical Strike Chance: +10%
    Critical Strike Power: +20
```

---

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/devreforge` | `devreforge.use` | Opens the reforge GUI |

---

## Building from Source

```bash
./gradlew build
```

Output jar will be in `build/libs/`.
