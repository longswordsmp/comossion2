# NoLife

A three-life survival (SMP) plugin for Paper. Every player starts with **3
lives**. Lose them all and you are permanently **death-banned** until another
player revives you with a crafted **Book of Life**. Revived players return with
**2 lives** and can climb back to the max of **3** by crafting **Life Gems**.

Life count is shown by colouring each player's **name-tag** and **tab-list**
entry:

| Lives | Colour |
|------:|--------|
| 3 | 🟢 Green |
| 2 | 🟡 Yellow |
| 1 | 🔴 Red |

---

## Features

- **Lives & colours** – automatic name-tag / tab-list colouring via scoreboard
  teams, updated the instant lives change.
- **Elimination** – dying on your last life death-bans you, broadcasts a
  configurable message, and blocks re-login until you are revived.
- **Book of Life** – right-click to open a GUI listing every eliminated player.
  Click one → confirm → they are unbanned, given 2 lives, teleported to you,
  and greeted with a Totem of Undying sound and particles. A revive broadcast
  is sent. Works even if the target is offline (they are teleported and get the
  effects the next time they join).
- **Life Gem** – right-click to gain **+1 life**, up to the max of 3.
- **Custom crafting recipes** for both items (fully configurable).
- **Optional resource pack** sent on join and re-sent on `/nlreload`, with
  `custom-model-data` on both items so you can give them custom textures.
- **Persistent data** – saved on join, quit, death, revive, reload and
  shutdown.

---

## Commands

All admin commands require the `nolife.admin` permission (default: OP).

| Command | Description |
|---------|-------------|
| `/setlives <player> <amount>` | Set a player's lives (0 = eliminate). |
| `/revive <player>` | Remove the death-ban and revive the player at your location (2 lives). |
| `/eliminate <player>` | Immediately death-ban a player. |
| `/nlreload` | Reload `config.yml`, `messages.yml`, `recipes.yml` and re-send the resource pack. |

## Permissions

| Permission | Default | Grants |
|------------|---------|--------|
| `nolife.admin` | op | All admin commands. |
| `nolife.use.book` | true | Using the Book of Life. |
| `nolife.use.lifegem` | true | Using a Life Gem. |

---

## Configuration

Three files are generated in `plugins/NoLife/` on first run. All text uses
legacy colour codes (`&c`, `&l`, …) and hex (`&#ff0000`).

- **`config.yml`** – lives, colours, item appearance, GUI titles, resource pack.
- **`messages.yml`** – every player-facing message and broadcast.
- **`recipes.yml`** – the 3×3 shaped crafting recipes.

### Default recipes

**Book of Life** — `G` gold ingot, `T` totem of undying, `B` book:

```
G T G
T B T
G T G
```

**Life Gem** — `D` diamond, `E` emerald, `N` nether star:

```
D E D
E N E
D E D
```

Edit `recipes.yml` to change the shape or ingredients, then `/nlreload`.

---

## Building

The plugin targets **Paper `26.1.2`** (the `paper-26.1.2-74` server build). The
version is a single property in `pom.xml`:

```xml
<paper.version>26.1.2-R0.1-SNAPSHOT</paper.version>
```

Because that build is not published to the public Paper Maven repository,
install your local server jar into your Maven cache once:

```bash
mvn install:install-file \
  -Dfile=paper-26.1.2-74.jar \
  -DgroupId=io.papermc.paper \
  -DartifactId=paper-api \
  -Dversion=26.1.2-R0.1-SNAPSHOT \
  -Dpackaging=jar
```

Then build:

```bash
mvn clean package
```

The compiled plugin is written to `target/NoLife-1.0.0.jar`. Drop it into your
server's `plugins/` folder and start the server (Java 21+ required).

> **Targeting a public Paper release instead?** Set `paper.version` to e.g.
> `1.21.7-R0.1-SNAPSHOT` (or `1.21.6` / `1.21.4`) and Maven will download it
> from the Paper repository — no `install-file` step needed. `api-version` in
> `plugin.yml` stays `1.21` for any 1.21.x server; if your custom build rejects
> that value, change it to match your server.

---

## Notes

- Name-tag / tab colouring uses the server's main scoreboard teams
  (`nl_three`, `nl_two`, `nl_one`). If another plugin manages those teams for
  colouring, expect a conflict.
- Elimination blocks login via `PlayerLoginEvent`; players are not added to the
  vanilla ban list, so `/revive` (or a Book of Life) is the only way back in.
