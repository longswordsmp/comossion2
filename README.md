# WitherStrikeCannon

A Paper 1.21.11 plugin. `/witherstrikecannon` hands you a fishing rod; right click it and
**2000 wither skulls** fall out of the sky onto whatever you were looking at, up to 300 blocks
away, scarring a **90 block wide** area — wide and shallow, only **4 blocks deep**.

- **2000 wither skulls** per shot, raining into a 45 block radius circle
- **Normal wither head speed** — skulls use the vanilla wither skull acceleration, not a
  custom velocity
- **Shallow by design** — the blast is a squashed bowl, not a sphere, so a huge barrage
  scars the surface instead of drilling a bottomless pit
- Released in 100 waves over 5 seconds, so it lands as a sustained bombardment instead of
  one server-killing tick

## Build

Requires JDK 21 and Maven.

```bash
mvn clean package     # -> target/WitherStrikeCannon-1.0.0.jar
```

Drop the jar in `plugins/` and restart.

> **Upgrading?** Delete `plugins/WitherStrikeCannon/config.yml` and restart, otherwise your
> old values are kept and the new `blast-depth` / `blast-height` options won't exist.

## Commands

| Command | Permission | Default | What it does |
| --- | --- | --- | --- |
| `/witherstrikecannon` | `witherstrikecannon.command` | op | Gives you a cannon rod |
| `/witherstrikecannon give <player>` | `witherstrikecannon.give` | op | Gives someone else a cannon |
| `/witherstrikecannon reload` | `witherstrikecannon.reload` | op | Re-reads `config.yml` live |

Aliases: `/wsc`, `/witherstrike`, `/withercannon`. Firing a rod you're holding needs
`witherstrikecannon.use` (default: everyone).

## Why the skulls used to fly off into the sky

The old version let each skull explode normally. Vanilla explosions apply **knockback to every
entity in range, including projectiles** — so the first skulls to land blasted the ones still
falling back upward. Because they were also spawned `invulnerable`, they survived the shove
instead of popping, and sailed away. A tight column made it as bad as possible.

`explosion.mode: CUSTOM` (the default now) fixes it at the root: the vanilla blast is
**cancelled entirely**, so there is no knockback to fling anything. The plugin carves the
crater itself instead.

That's also what makes the destruction affordable. 2000 vanilla explosions is 2000 separate
ray-cast passes; carving is one deduplicated set of block removals, drained at a fixed budget
per tick. Impacts are snapped to a grid first, so 2000 skulls become a few dozen bowl carves
rather than 2000 overlapping ones.

**Caveat:** because it doesn't go through the explosion event, CUSTOM mode does **not** respect
region protection plugins like WorldGuard. If you need those honoured, set
`explosion.mode: VANILLA` — you get much less destruction, and the skull-launching behaviour
comes back.

## Targeting

**RAYTRACE (default).** Right click; the strike lands on the block you're looking at, up to
`max-distance` (300) blocks away. This is the default because a 90 block wide strike zone is
not something you want to be standing in.

**BOBBER.** `targeting.mode: BOBBER` restores the original feel — cast the rod, and the strike
lands where the bobber lands. Fishing rod range is ~30 blocks, so with the current radius
**you will be inside your own crater**. Damage is cancelled for the shooter, and at 4 blocks
deep it's survivable now, but you'll still be standing in a hole.

## Crater shape

Each impact carves a **squashed bowl**, not a sphere, which is what keeps a 2000 skull
barrage from turning into a pit:

- `blast-radius` (10) — how wide each impact reaches
- `blast-depth` (4) — how far **down** it digs. **This is the dial to turn if craters are
  too deep.**
- `blast-height` (12) — how far **up** it reaches, so buildings and trees on the surface
  are still erased. Costs almost nothing, because everything above ground is mostly air.

Measured by simulating the carve geometry against flat ground:

| `spread` | `blast-radius` | `blast-depth` | blocks dug | crater | carve time |
| --- | --- | --- | --- | --- | --- |
| 45 | 10 | 2 | 17,700 | 90 wide, 2 deep | 1.1 s |
| **45** | **10** | **4** | **34,300** | **90 wide, 4 deep** | **1.2 s** |
| 45 | 10 | 8 | 66,600 | 90 wide, 8 deep | 1.6 s |
| 60 | 16 | 16 | 228,500 | 120 wide, 16 deep | 3.7 s |

That last row is the previous version's setting — the one that dug too far down. Note the
skull count doesn't change any of this: you can keep 2000 skulls in the sky and still only
take the top two layers off, because how it *looks* and how much it *destroys* are
separate dials.

## Performance dials

`explosion.blocks-per-tick` (default 5000) is the single most important one — it caps how much
terrain work happens per tick, so a giant crater takes longer to form rather than freezing the
server. Lower it if you see stutter, raise it for faster destruction.

Others worth knowing:

- `strike.waves` — more waves spreads entity spawning over more ticks (free, do this first)
- `strike.impact-effect-every` — explosion particles are only drawn every Nth impact; raise it
  if clients chug
- `explosion.carve-liquids` — leave `false`; carved water and lava immediately start flowing
  back, which is its own TPS problem
- `strike.skull-count` is capped by `strike.max-skull-count` (5000) so a typo can't nuke the box

## Damage

Instead of 2000 individual explosion damage calculations, everything living in the strike zone
takes `explosion.damage-per-pulse` (10) damage every `damage-pulse-interval-ticks` (10) for
`damage-pulses` (10) pulses — 100 damage total across the barrage. The shooter is skipped when
`strike.protect-shooter` is on. Skulls still deal their normal direct-hit damage and wither
effect on top of that.

## Configuration

Everything lives in `plugins/WitherStrikeCannon/config.yml`, which is commented in full.
`item.name`, `item.lore` and all messages are
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatted; message placeholders
are `%player%`, `%seconds%` and `%count%`.
