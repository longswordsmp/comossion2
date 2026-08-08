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

Admin commands require `nolife.admin` (default: OP). `/lives` and `/nlrecipes`
are available to everyone.

| Command | Description |
|---------|-------------|
| `/nolife` (`/nl`) | Open the **admin menu** — a GUI hub linking to every action below. |
| `/setlives <player> <amount\|admin>` | Set a player's lives. `0` eliminates; `admin` grants **infinite lives** (never loses a life, shown in a distinct colour). Any number turns it back off. |
| `/revive <player>` | Remove the death-ban and revive the player at your location (2 lives). |
| `/eliminate <player>` | Immediately death-ban a player. |
| `/nlgive [player] <book\|gem> [amount]` | Give a Book of Life or Life Gem. Omit the player to give to yourself. |
| `/nlreload` | Reload `config.yml`, `messages.yml`, `recipes.yml` and re-send the resource pack. |
| `/lives [player]` | Show your (or, as admin, another player's) lives as a heart display. |
| `/nlrecipes` | Show the Book of Life and Life Gem crafting recipes in a GUI. |

### Giving yourself a Book of Life

Three ways:
- **Command:** `/nlgive book` (yourself) or `/nlgive <player> book 3` (someone else, 3 of them). `gem` works the same.
- **GUI:** `/nolife` → **Give Items** → left-click for yourself, right-click to pick a player.
- **Craft it:** see `/nlrecipes` (or `/nolife` → **Recipes**).

### The admin menu (`/nolife`)

Every command has a GUI: **Manage Lives** (pick player → pick a life count),
**Revive Player** (pick from eliminated players → confirm), **Eliminate Player**
(pick → confirm), **Give Items**, **Recipes**, **Players** (overview of everyone's
lives → click to manage one), and **Reload**. All menus paginate and have back
buttons.

**Editing recipes in-game:** open **Recipes** (or `/nlrecipes`) and, as an admin,
click **Edit Book recipe** / **Edit Gem recipe**. Drop items into the 3×3 grid to
set the crafting shape, then click **Save** — the recipe is written to
`recipes.yml` and registered instantly (no restart), and a toggle enables/disables
it. Your items are handed back when you close the editor.

## Permissions

| Permission | Default | Grants |
|------------|---------|--------|
| `nolife.admin` | op | All admin commands + the `/nolife` menu. |
| `nolife.use.book` | true | Using the Book of Life. |
| `nolife.use.lifegem` | true | Using a Life Gem. |
| `nolife.lives` | true | `/lives` (own life count). |
| `nolife.recipes` | true | `/nlrecipes` (recipe viewer). |

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

## Resource pack

A ready-made pack lives in [`resourcepack/`](resourcepack/) and gives the Book
of Life and Life Gem detailed **64×64** custom textures (a gold-trimmed crimson
tome with a glowing heart, and a faceted heart-cut ruby). It ships with **both**
model systems so it works across 1.21.x:

- legacy `custom_model_data` overrides (`models/item/*.json`) for ≤ 1.21.3, and
- the new item model definitions (`items/*.json`, `range_dispatch`) for ≥ 1.21.4.

The custom model ids (`1000001` book, `1000002` gem) already match
`items.*.custom-model-data` in `config.yml`, so the plugin's items carry them
automatically.

**To use it:**

1. Zip the **contents** of `resourcepack/` (so `pack.mcmeta` sits at the zip
   root, not inside a folder):
   ```bash
   cd resourcepack && zip -r ../NoLife-ResourcePack.zip pack.mcmeta assets
   ```
2. Host the zip somewhere with a direct download URL.
3. Compute its SHA-1 (`sha1sum NoLife-ResourcePack.zip`) and put both in
   `config.yml`:
   ```yaml
   resource-pack:
     enabled: true
     url: "https://your-host/NoLife-ResourcePack.zip"
     sha1: "<the sha1 hex>"
     send-on-join: true
   ```
4. `/nlreload` to push it to everyone online.

`pack.mcmeta` sets `pack_format: 46` (1.21.4) with a wide `supported_formats`
range so newer clients still load it; bump `pack_format` to match your server if
the client complains it was made for a different version.

## Notes

- Name-tag / tab colouring uses the server's main scoreboard teams
  (`nl_three`, `nl_two`, `nl_one`). If another plugin manages those teams for
  colouring, expect a conflict.
- Elimination blocks login via `PlayerLoginEvent`; players are not added to the
  vanilla ban list, so `/revive` (or a Book of Life) is the only way back in.
