package com.longswordsmp.nolife.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.Component;

import com.longswordsmp.nolife.NoLifePlugin;
import com.longswordsmp.nolife.config.RecipeConfig;
import com.longswordsmp.nolife.data.PlayerData;
import com.longswordsmp.nolife.util.Text;

/**
 * Every menu in the plugin. Each {@code openX} builds a {@link Menu} and opens
 * it; click handlers defer follow-up opens to the next tick so switching
 * inventories from inside a click is safe.
 */
public final class Guis {

    private static final int PER_PAGE = 45;

    @FunctionalInterface
    private interface PageOpener {
        void open(int page);
    }

    private Guis() {
    }

    // =====================================================================
    //  Admin hub
    // =====================================================================

    public static void openAdminHub(NoLifePlugin plugin, Player admin) {
        Menu menu = new Menu(title("&8&lNoLife &8Admin"), 3);
        menu.fill(filler());

        menu.button(10, icon(Material.EXPERIENCE_BOTTLE, "&a&lManage Lives", "&7Set a player's life count"),
                (v, e) -> open(plugin, () -> openPlayerPicker(plugin, v, "&8Manage Lives", 0,
                        (id, name) -> openAmountSelector(plugin, v, id, name), () -> openAdminHub(plugin, v))));

        menu.button(11, icon(Material.TOTEM_OF_UNDYING, "&a&lRevive Player", "&7Bring an eliminated player back"),
                (v, e) -> open(plugin, () -> openReviveList(plugin, v, true, 0)));

        menu.button(12, icon(Material.BARRIER, "&c&lEliminate Player", "&7Death-ban a player"),
                (v, e) -> open(plugin, () -> openPlayerPicker(plugin, v, "&8Eliminate", 0,
                        (id, name) -> openEliminateConfirm(plugin, v, id, name), () -> openAdminHub(plugin, v))));

        menu.button(13, icon(Material.CHEST, "&6&lGive Items", "&7Book of Life or Life Gem"),
                (v, e) -> open(plugin, () -> openGiveMenu(plugin, v)));

        menu.button(14, icon(Material.CRAFTING_TABLE, "&b&lRecipes", "&7How to craft the items"),
                (v, e) -> open(plugin, () -> openRecipes(plugin, v)));

        menu.button(15, icon(Material.PAPER, "&f&lPlayers", "&7Everyone's lives"),
                (v, e) -> open(plugin, () -> openPlayersOverview(plugin, v, 0)));

        menu.button(16, icon(Material.COMPARATOR, "&e&lReload", "&7Reload config + resource pack"),
                (v, e) -> {
                    plugin.reloadAll();
                    v.sendMessage(plugin.config().msg("reloaded", "%version%", plugin.config().resourcePackVersion()));
                });

        menu.open(admin);
    }

    // =====================================================================
    //  Player picker (online players) with a caller-supplied action
    // =====================================================================

    public static void openPlayerPicker(NoLifePlugin plugin, Player viewer, String titleStr, int page,
                                        BiConsumer<UUID, String> onPick, Runnable onBack) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        if (players.isEmpty()) {
            viewer.sendMessage(Text.parse("&cNo players are online."));
            return;
        }

        List<ItemStack> icons = new ArrayList<>();
        List<MenuClick> clicks = new ArrayList<>();
        for (Player p : players) {
            UUID id = p.getUniqueId();
            String name = p.getName();
            int lives = plugin.lives().isEliminated(id) ? 0 : plugin.lives().getLivesOrDefault(id);
            icons.add(head(id, name, "&e" + name, List.of("&7Lives: &f" + lives, "&aClick to select")));
            clicks.add((v, e) -> open(plugin, () -> onPick.accept(id, name)));
        }

        openPaginated(plugin, viewer, titleStr, page, icons, clicks, onBack,
                (p) -> openPlayerPicker(plugin, viewer, titleStr, p, onPick, onBack));
    }

    // =====================================================================
    //  Set-lives amount selector
    // =====================================================================

    public static void openAmountSelector(NoLifePlugin plugin, Player admin, UUID id, String name) {
        int max = plugin.config().maxLives();
        boolean elim = plugin.lives().isEliminated(id);
        int current = elim ? 0 : plugin.lives().getLivesOrDefault(id);

        int count = max + 1;                              // buttons 0..max
        int perRow = 7;
        int gridRows = Math.max(1, (count + perRow - 1) / perRow);
        int rows = Math.min(6, 1 + gridRows + 1);         // header + grid + back row

        Menu menu = new Menu(title("&8Lives: &f" + name), rows);
        menu.fill(filler());
        menu.set(4, head(id, name, "&e" + name,
                List.of(elim ? "&cELIMINATED" : "&7Current: &f" + current)));

        int backRowStart = menu.size() - 9;
        for (int n = 0; n <= max; n++) {
            final int val = n;
            int slot = (1 + n / perRow) * 9 + 1 + (n % perRow);
            if (slot >= backRowStart) {
                break; // never spill into the back row (extreme max-lives)
            }
            ItemStack it;
            if (n == 0) {
                it = icon(Material.BARRIER, "&c&lEliminate", "&7Set to 0 lives (death-ban)");
            } else {
                Material pane = n == 1 ? Material.RED_STAINED_GLASS_PANE
                        : n == 2 ? Material.YELLOW_STAINED_GLASS_PANE
                        : Material.LIME_STAINED_GLASS_PANE;
                it = icon(pane, "&fSet to &e" + n + "&f lives");
                it.setAmount(Math.max(1, Math.min(64, n)));
            }
            menu.button(slot, it, (v, e) -> {
                if (val <= 0) {
                    plugin.lives().eliminate(id, name, true);
                    v.sendMessage(plugin.config().msg("eliminate-done", "%player%", name));
                } else {
                    int applied = plugin.lives().setLives(id, val, name);
                    v.sendMessage(plugin.config().msg("setlives-done", "%player%", name, "%lives%", String.valueOf(applied)));
                    Player online = Bukkit.getPlayer(id);
                    if (online != null) {
                        online.sendMessage(plugin.config().msg("setlives-target", "%lives%", String.valueOf(applied)));
                    }
                }
                open(plugin, () -> openAmountSelector(plugin, v, id, name));
            });
        }
        menu.button(menu.size() - 5, icon(Material.ARROW, "&cBack"),
                (v, e) -> open(plugin, () -> openPlayerPicker(plugin, v, "&8Manage Lives", 0,
                        (i2, n2) -> openAmountSelector(plugin, v, i2, n2), () -> openAdminHub(plugin, v))));
        menu.open(admin);
    }

    // =====================================================================
    //  Eliminate confirm
    // =====================================================================

    public static void openEliminateConfirm(NoLifePlugin plugin, Player admin, UUID id, String name) {
        boolean elim = plugin.lives().isEliminated(id);
        Menu menu = new Menu(title("&8Eliminate &f" + name + "&8?"), 3);
        menu.fill(filler());
        menu.set(13, head(id, name, "&e" + name, List.of(elim ? "&cAlready eliminated" : "&7Currently alive")));
        menu.button(11, icon(Material.RED_STAINED_GLASS_PANE, "&c&lCONFIRM", "&7Death-ban this player"), (v, e) -> {
            if (elim) {
                v.sendMessage(plugin.config().msg("eliminate-already", "%player%", name));
            } else {
                plugin.lives().eliminate(id, name, true);
                v.sendMessage(plugin.config().msg("eliminate-done", "%player%", name));
            }
            close(plugin, v);
        });
        menu.button(15, icon(Material.LIME_STAINED_GLASS_PANE, "&a&lCANCEL"),
                (v, e) -> open(plugin, () -> openAdminHub(plugin, v)));
        menu.open(admin);
    }

    // =====================================================================
    //  Revive list + confirm (adminMode = revive at your location, no book)
    // =====================================================================

    public static void openReviveList(NoLifePlugin plugin, Player viewer, boolean adminMode, int page) {
        List<PlayerData> dead = plugin.lives().getEliminatedSorted();
        if (dead.isEmpty()) {
            viewer.sendMessage(plugin.config().msg("no-eliminated"));
            return;
        }
        List<ItemStack> icons = new ArrayList<>();
        List<MenuClick> clicks = new ArrayList<>();
        for (PlayerData d : dead) {
            UUID id = d.getUuid();
            String name = d.getName();
            icons.add(head(id, name, "&e" + name, List.of("&7Eliminated", "&aClick to revive")));
            clicks.add((v, e) -> open(plugin, () -> openReviveConfirm(plugin, v, id, name, adminMode)));
        }
        String titleStr = adminMode ? "&8Revive a Player" : "&8Book of Life";
        Runnable back = adminMode ? () -> openAdminHub(plugin, viewer) : null;
        openPaginated(plugin, viewer, titleStr, page, icons, clicks, back,
                (p) -> openReviveList(plugin, viewer, adminMode, p));
    }

    public static void openReviveConfirm(NoLifePlugin plugin, Player viewer, UUID id, String name, boolean adminMode) {
        Menu menu = new Menu(title("&8Revive &f" + name + "&8?"), 3);
        menu.fill(filler());
        menu.set(13, head(id, name, "&e" + name, List.of("&7Eliminated")));
        menu.button(11, icon(Material.LIME_STAINED_GLASS_PANE, "&a&lCONFIRM REVIVE",
                adminMode ? "&7Revive at your location" : "&7Consumes one Book of Life"), (v, e) -> {
            if (!plugin.lives().isEliminated(id)) {
                v.sendMessage(plugin.config().msg("already-revived"));
                close(plugin, v);
                return;
            }
            if (!adminMode && !plugin.items().consumeOneBookOfLife(v)) {
                v.sendMessage(plugin.config().msg("revive-need-book"));
                close(plugin, v);
                return;
            }
            plugin.lives().revive(id, v.getLocation(), v.getName());
            close(plugin, v);
        });
        menu.button(15, icon(Material.RED_STAINED_GLASS_PANE, "&cCANCEL"),
                (v, e) -> open(plugin, () -> openReviveList(plugin, v, adminMode, 0)));
        menu.open(viewer);
    }

    // =====================================================================
    //  Give items
    // =====================================================================

    public static void openGiveMenu(NoLifePlugin plugin, Player admin) {
        Menu menu = new Menu(title("&8Give Items"), 3);
        menu.fill(filler());
        menu.button(11, icon(Material.BOOK, plugin.config().bookName(),
                "&7Left-click: &fgive yourself one", "&7Right-click: &fgive to a player"), (v, e) -> {
            if (e.isRightClick()) {
                open(plugin, () -> openPlayerPicker(plugin, v, "&8Give Book of Life", 0,
                        (id, name) -> giveTo(plugin, v, id, name, true), () -> openGiveMenu(plugin, v)));
            } else {
                plugin.items().give(v, plugin.items().createBookOfLife(1));
                v.sendMessage(plugin.config().msg("give-received", "%amount%", "1", "%item%", "Book of Life"));
            }
        });
        menu.button(15, icon(Material.HEART_OF_THE_SEA, plugin.config().gemName(),
                "&7Left-click: &fgive yourself one", "&7Right-click: &fgive to a player"), (v, e) -> {
            if (e.isRightClick()) {
                open(plugin, () -> openPlayerPicker(plugin, v, "&8Give Life Gem", 0,
                        (id, name) -> giveTo(plugin, v, id, name, false), () -> openGiveMenu(plugin, v)));
            } else {
                plugin.items().give(v, plugin.items().createLifeGem(1));
                v.sendMessage(plugin.config().msg("give-received", "%amount%", "1", "%item%", "Life Gem"));
            }
        });
        menu.button(22, icon(Material.ARROW, "&cBack"), (v, e) -> open(plugin, () -> openAdminHub(plugin, v)));
        menu.open(admin);
    }

    private static void giveTo(NoLifePlugin plugin, Player giver, UUID targetId, String targetName, boolean isBook) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            giver.sendMessage(plugin.config().msg("give-offline", "%player%", targetName));
            return;
        }
        ItemStack item = isBook ? plugin.items().createBookOfLife(1) : plugin.items().createLifeGem(1);
        String label = isBook ? "Book of Life" : "Life Gem";
        plugin.items().give(target, item);
        giver.sendMessage(plugin.config().msg("give-sent", "%player%", targetName, "%amount%", "1", "%item%", label));
        target.sendMessage(plugin.config().msg("give-received", "%amount%", "1", "%item%", label));
    }

    // =====================================================================
    //  Recipes
    // =====================================================================

    public static void openRecipes(NoLifePlugin plugin, Player viewer) {
        Menu menu = new Menu(title("&8Recipes"), 6);
        menu.fill(filler());
        placeRecipe(menu, 0, plugin.config().bookRecipe(), plugin.items().createBookOfLife(1), "&6&lBook of Life");
        placeRecipe(menu, 3, plugin.config().gemRecipe(), plugin.items().createLifeGem(1), "&c&lLife Gem");
        menu.open(viewer);
    }

    private static void placeRecipe(Menu menu, int rowStart, RecipeConfig def, ItemStack result, String label) {
        List<String> shape = def.shape();
        Map<Character, String> ing = def.ingredients();
        for (int r = 0; r < 3; r++) {
            String row = (r < shape.size() && shape.get(r) != null) ? shape.get(r) : "";
            for (int c = 0; c < 3; c++) {
                char ch = c < row.length() ? row.charAt(c) : ' ';
                int slot = (rowStart + r) * 9 + c + 1; // grid starts at column 1
                if (ch == ' ') {
                    menu.set(slot, icon(Material.BLACK_STAINED_GLASS_PANE, " "));
                } else {
                    String matName = ing.get(ch);
                    Material m = matName == null ? null : Material.matchMaterial(matName);
                    if (m == null) {
                        menu.set(slot, icon(Material.BARRIER, "&c" + matName));
                    } else {
                        menu.set(slot, new ItemStack(m));
                    }
                }
            }
        }
        menu.set((rowStart + 1) * 9 + 5, icon(Material.ARROW, "&7crafts"));
        menu.set((rowStart + 1) * 9 + 6, result);
        menu.set(rowStart * 9 + 6, icon(Material.NAME_TAG, label + (def.enabled() ? "" : " &c(disabled)")));
    }

    // =====================================================================
    //  Players overview + manage a single player
    // =====================================================================

    public static void openPlayersOverview(NoLifePlugin plugin, Player admin, int page) {
        List<PlayerData> all = new ArrayList<>(plugin.data().all());
        all.sort(Comparator.comparing(d -> d.getName() == null ? "" : d.getName(), String.CASE_INSENSITIVE_ORDER));
        if (all.isEmpty()) {
            admin.sendMessage(Text.parse("&7No players are tracked yet."));
            return;
        }
        List<ItemStack> icons = new ArrayList<>();
        List<MenuClick> clicks = new ArrayList<>();
        for (PlayerData d : all) {
            UUID id = d.getUuid();
            String name = d.getName();
            String status = d.isEliminated() ? "&cELIMINATED" : "&aLives: &f" + d.getLives();
            icons.add(head(id, name, "&e" + name, List.of(status, "&7Click to manage")));
            clicks.add((v, e) -> open(plugin, () -> openManagePlayer(plugin, v, id, name)));
        }
        openPaginated(plugin, admin, "&8Players", page, icons, clicks,
                () -> openAdminHub(plugin, admin), (p) -> openPlayersOverview(plugin, admin, p));
    }

    public static void openManagePlayer(NoLifePlugin plugin, Player admin, UUID id, String name) {
        boolean elim = plugin.lives().isEliminated(id);
        int lives = elim ? 0 : plugin.lives().getLivesOrDefault(id);
        Menu menu = new Menu(title("&8Manage &f" + name), 3);
        menu.fill(filler());
        menu.set(4, head(id, name, "&e" + name, List.of(elim ? "&cELIMINATED" : "&aLives: &f" + lives)));

        menu.button(10, icon(Material.EXPERIENCE_BOTTLE, "&aSet Lives"),
                (v, e) -> open(plugin, () -> openAmountSelector(plugin, v, id, name)));
        if (elim) {
            menu.button(12, icon(Material.TOTEM_OF_UNDYING, "&aRevive"),
                    (v, e) -> open(plugin, () -> openReviveConfirm(plugin, v, id, name, true)));
        } else {
            menu.button(12, icon(Material.BARRIER, "&cEliminate"),
                    (v, e) -> open(plugin, () -> openEliminateConfirm(plugin, v, id, name)));
        }
        menu.button(14, icon(Material.BOOK, "&6Give Book of Life"),
                (v, e) -> giveTo(plugin, v, id, name, true));
        menu.button(16, icon(Material.HEART_OF_THE_SEA, "&cGive Life Gem"),
                (v, e) -> giveTo(plugin, v, id, name, false));
        menu.button(22, icon(Material.ARROW, "&cBack"),
                (v, e) -> open(plugin, () -> openPlayersOverview(plugin, v, 0)));
        menu.open(admin);
    }

    // =====================================================================
    //  Lives view (player-facing)
    // =====================================================================

    public static void openLives(NoLifePlugin plugin, Player viewer, UUID id, String name) {
        int max = plugin.config().maxLives();
        boolean elim = plugin.lives().isEliminated(id);
        int lives = elim ? 0 : plugin.lives().getLivesOrDefault(id);

        int heartRows = Math.max(1, (max + 8) / 9);
        int rows = Math.min(6, 1 + heartRows);            // header + heart rows

        Menu menu = new Menu(title("&8" + name + "'s Lives"), rows);
        menu.fill(filler());
        menu.set(4, head(id, name, "&e" + name,
                List.of(elim ? "&cELIMINATED" : "&aLives: &f" + lives + "&7/" + max)));

        for (int i = 0; i < max; i++) {
            int col = (max <= 9) ? (9 - max) / 2 + (i % 9) : (i % 9);
            int slot = (1 + i / 9) * 9 + col;
            if (slot >= menu.size()) {
                break;
            }
            boolean filledHeart = i < lives;
            menu.set(slot, icon(filledHeart ? Material.RED_DYE : Material.GRAY_DYE,
                    filledHeart ? "&c❤ Life" : "&8♡ Lost"));
        }
        menu.open(viewer);
    }

    // =====================================================================
    //  Shared paginated list
    // =====================================================================

    private static void openPaginated(NoLifePlugin plugin, Player viewer, String titleStr, int page,
                                      List<ItemStack> icons, List<MenuClick> clicks, Runnable onBack, PageOpener reopen) {
        int total = icons.size();
        int totalPages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        if (page < 0) {
            page = 0;
        }
        if (page >= totalPages) {
            page = totalPages - 1;
        }
        int start = page * PER_PAGE;
        int end = Math.min(start + PER_PAGE, total);

        Menu menu = new Menu(title(titleStr), 6);
        for (int i = start; i < end; i++) {
            menu.button(i - start, icons.get(i), clicks.get(i));
        }

        ItemStack f = filler();
        for (int s = 45; s < 54; s++) {
            menu.set(s, f);
        }
        if (onBack != null) {
            menu.button(49, icon(Material.BARRIER, "&cBack"), (v, e) -> open(plugin, onBack));
        }
        if (page > 0) {
            int prev = page - 1;
            menu.button(45, icon(Material.ARROW, "&a◀ Previous"), (v, e) -> open(plugin, () -> reopen.open(prev)));
        }
        if (page < totalPages - 1) {
            int next = page + 1;
            menu.button(53, icon(Material.ARROW, "&aNext ▶"), (v, e) -> open(plugin, () -> reopen.open(next)));
        }
        if (totalPages > 1) {
            menu.set(48, icon(Material.PAPER, "&7Page &f" + (page + 1) + "&7/&f" + totalPages));
        }
        menu.open(viewer);
    }

    // =====================================================================
    //  Item / scheduling helpers
    // =====================================================================

    private static Component title(String s) {
        return Text.parse(s);
    }

    private static void open(NoLifePlugin plugin, Runnable opener) {
        Bukkit.getScheduler().runTask(plugin, opener);
    }

    private static void close(NoLifePlugin plugin, Player viewer) {
        Bukkit.getScheduler().runTask(plugin, viewer::closeInventory);
    }

    private static ItemStack filler() {
        return icon(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private static ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.item(name));
            if (lore.length > 0) {
                List<Component> lines = new ArrayList<>();
                for (String s : lore) {
                    lines.add(Text.item(s));
                }
                meta.lore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack head(UUID id, String name, String displayName, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        List<Component> lines = new ArrayList<>();
        for (String s : lore) {
            lines.add(Text.item(s));
        }
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(id));
            skull.displayName(Text.item(displayName));
            skull.lore(lines);
            head.setItemMeta(skull);
        } else if (meta != null) {
            meta.displayName(Text.item(displayName));
            meta.lore(lines);
            head.setItemMeta(meta);
        }
        return head;
    }
}
