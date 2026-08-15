# WitherStrikeCannon

A Paper 1.21.11 plugin. `/witherstrikecannon` hands you a fishing rod; right click it and
**2000 wither skulls** fall out of the sky onto whatever you were looking at, up to 300 blocks
away, leaving a crater roughly **120 blocks wide and 32 deep — about 440,000 blocks removed**.

- **2000 wither skulls** per shot, raining into a 60 block radius circle
- **Normal wither head speed** — skulls use the vanilla wither skull acceleration, not a
  custom velocity
- **~300x the destruction** of a vanilla-explosion approach, at a fraction of the CPU cost
- Released in 120 waves over 6 seconds, so it lands as a sustained bombardment instead of
  one server-killing tick

## Build

Requires JDK 21 and Maven.

```bash
mvn clean package     # -> target/WitherStrikeCannon-1.0.0.jar
```

Drop the jar in `plugins/` and restart.

> **Upgrading?** Delete `plugins/WitherStrikeCannon/config.yml` and restart, otherwise your
> old (much smaller) values are kept and none of the new options exist.

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
per tick. Impacts are snapped to a grid first, so 2000 skulls become ~90 sphere carves rather
than 2000 overlapping ones.

**Caveat:** because it doesn't go through the explosion event, CUSTOM mode does **not** respect
region protection plugins like WorldGuard. If you need those honoured, set
`explosion.mode: VANILLA` — you get much less destruction, and the skull-launching behaviour
comes back.

## Targeting

**RAYTRACE (default).** Right click; the strike lands on the block you're looking at, up to
`max-distance` (300) blocks away. This is the default because a 120 block wide crater is not
something you want to be standing in.

**BOBBER.** `targeting.mode: BOBBER` restores the original feel — cast the rod, and the strike
lands where the bobber lands. Fishing rod range is ~30 blocks, so with the current radius
**you will be inside your own crater**. Damage is cancelled for the shooter, but you'll still
be falling into a very deep hole.

## Scaling it up or down

The three dials that matter, and what they actually produce (measured by simulating the
carve geometry):

| skulls | `spread` | `blast-radius` | blocks destroyed | crater | carve time |
| --- | --- | --- | --- | --- | --- |
| 1500 | 40 | 12 | 162,000 | 80 wide, 24 deep | 2.0 s |
| **2000** | **60** | **16** | **441,000** | **120 wide, 32 deep** | **3.7 s** |
| 2500 | 60 | 18 | 555,000 | 120 wide, 36 deep | 4.6 s |
| 3000 | 80 | 20 | 1,037,000 | 160 wide, 40 deep | 6.5 s |

That last row is the "delete the whole base" setting. If you use it, also raise
`explosion.max-blocks-per-strike` (the queue costs ~56 bytes per block, so a million-block
crater is ~56 MB of heap while it's being carved) and expect the carve to take a few seconds.

## Performance dials

`explosion.blocks-per-tick` (default 6000) is the single most important one — it caps how much
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
takes `explosion.damage-per-pulse` (15) damage every `damage-pulse-interval-ticks` (10) for
`damage-pulses` (16) pulses — 240 damage total across the barrage. The shooter is skipped when
`strike.protect-shooter` is on. Skulls still deal their normal direct-hit damage and wither
effect on top of that.

## Configuration

Everything lives in `plugins/WitherStrikeCannon/config.yml`, which is commented in full.
`item.name`, `item.lore` and all messages are
[MiniMessage](https://docs.advntr.dev/minimessage/format.html) formatted; message placeholders
are `%player%`, `%seconds%` and `%count%`.
