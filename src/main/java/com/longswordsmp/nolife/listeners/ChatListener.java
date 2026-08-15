package com.longswordsmp.nolife.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;

import com.longswordsmp.nolife.NoLifePlugin;
import com.longswordsmp.nolife.util.Text;

/**
 * Prepends a red {@code ADMIN} tag to the chat messages of opped players.
 *
 * <p>This only touches the chat line: the tab list and name-tag are left
 * untouched, so the tag never appears there. Non-ops keep whatever chat format
 * the server (or another plugin) already uses, because we wrap the existing
 * renderer instead of replacing it.</p>
 */
public class ChatListener implements Listener {

    private final NoLifePlugin plugin;

    public ChatListener(NoLifePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.config().adminChatPrefixEnabled()) {
            return;
        }
        if (!event.getPlayer().isOp()) {
            return; // non-ops keep the default chat format
        }
        Component prefix = Text.parse(plugin.config().adminChatPrefix());
        ChatRenderer previous = event.renderer();
        // Empty root so the bold/red of the prefix stays on the prefix and does
        // not bleed into the rendered message (they are siblings, not nested).
        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.empty()
                        .append(prefix)
                        .append(previous.render(source, sourceDisplayName, message, viewer)));
    }
}
