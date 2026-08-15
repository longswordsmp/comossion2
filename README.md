# WitherStrikeCannon

A Paper 1.21.11 plugin. `/witherstrikecannon` hands you a fishing rod; cast it, and
wherever the bobber lands takes ~150 wither skulls straight down out of the sky.

- **150 wither skulls** per shot (configurable)
- **1.5 block circular spread** — skulls launch from, and rain into, a disc of that radius
- **Normal wither head speed** — skulls use the vanilla wither skull acceleration (0.1/tick),
  not a custom velocity, so they look and behave exactly like a real wither's shots
- Skulls are released in waves over 10 ticks instead of all in one tick, so 150 entities
  don't spike the server

## Build

Requires JDK 21 and Maven.

```bash
mvn clean package
```

The jar lands at `target/WitherStrikeCannon-1.0.0.jar`. Drop it in `plugins/` and restart.

> The build pulls `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` from
> `https://repo.papermc.io`. If you run a different Minecraft version, change the
> `<paper.version>` property in `pom.xml` — the plugin only uses long-stable API, so
> anything from 1.20.5 up should compile as-is.

## Commands

| Command | Permission | Default | What it does |
| --- | --- | --- | --- |
| `/witherstrikecannon` | `witherstrikecannon.command` | op | Gives you a cannon rod |
| `/witherstrikecannon give <player>` | `witherstrikecannon.give` | op | Gives someone else a cannon |
| `/witherstrikecannon reload` | `witherstrikecannon.reload` | op | Re-reads `config.yml` live |

Aliases: `/wsc`, `/witherstrike`, `/withercannon`.

Actually *firing* a cannon you're holding needs `witherstrikecannon.use` (default: everyone),
so you can hand rods to players without giving them the command.

## How firing works

**BOBBER mode (default).** Right click to cast. The plugin follows the bobber and fires the
moment it settles — on the ground, in water, or on a mob you hooked. Reel in before it lands
and nothing happens. Range is normal fishing rod range (~30 blocks).

**RAYTRACE mode.** Set `targeting.mode: RAYTRACE` for a long range version: right click and
the strike lands on whatever block you're looking at, up to `max-distance` blocks away. The
rod never casts a bobber in this mode.

Either way the target gets a soul fire ring and a wither roar while the skulls are inbound
(~2.5 seconds of fall time at the default 80 block launch height).

## Configuration

Everything below lives in `plugins/WitherStrikeCannon/config.yml`.

### `strike`

| Key | Default | Notes |
| --- | --- | --- |
| `skull-count` | `150` | Skulls per shot |
| `max-skull-count` | `1000` | Hard ceiling so a typo can't kill the server |
| `spread` | `1.5` | Radius in blocks of the launch/impact circle |
| `height` | `80.0` | Blocks above the target that skulls spawn (clamped to the build limit) |
| `waves` | `10` | Skulls are split across this many ticks |
| `wave-delay-ticks` | `1` | Ticks between waves |
| `scatter-impacts` | `true` | Each skull gets its own random impact point in the circle; `false` = a perfectly vertical column |
| `charged` | `false` | Blue wither skulls |
| `speed-multiplier` | `1.0` | `1.0` is exactly vanilla wither skull speed |
| `invulnerable` | `true` | Stops incoming skulls being popped by the explosions of the ones that landed first |
| `cooldown-seconds` | `5` | Per player, `0` to disable |
| `protect-shooter` | `true` | You don't take damage from your own barrage |
| `effects` | `true` | Sounds and the targeting ring |
| `disabled-worlds` | `[]` | Worlds where the cannon won't fire |

### `explosion`

| Key | Default | Notes |
| --- | --- | --- |
| `block-damage` | `true` | `false` keeps the damage but leaves terrain intact |
| `power` | `-1.0` | `-1` = the vanilla wither skull blast (and blocks only break when `mobGriefing` is on). Any value `> 0` replaces it with a custom explosion of that power that ignores `mobGriefing` — `4.0` is roughly TNT |
| `incendiary` | `false` | Custom explosions only: set fire on impact |

### `item` and `messages`

`item.name` / `item.lore` and every chat message are [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
formatted, so gradients and hex colours work. Message placeholders are `%player%`,
`%seconds%` and `%count%`.

## Tuning notes

150 skulls means 150 explosions in a couple of seconds — that's the point, but it is the
expensive part. If you see TPS drops:

- raise `waves` (spreading the same skulls over more ticks costs nothing visually)
- lower `skull-count`, or shrink `spread` so fewer separate explosion volumes get calculated
- set `explosion.block-damage: false` — most of the cost is block breaking, not the entities

For a survival server, the sane setup is `block-damage: false` plus a longer
`cooldown-seconds`, or just leave `witherstrikecannon.command` op-only.
