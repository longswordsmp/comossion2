package com.longswordsmp.nolife;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;

import com.longswordsmp.nolife.config.PluginConfig;
import com.longswordsmp.nolife.util.Text;

/**
 * Sends the optional custom resource pack using Adventure's modern
 * {@code sendResourcePacks} API.
 */
public class ResourcePackService {

    private final JavaPlugin plugin;
    private final PluginConfig cfg;

    public ResourcePackService(JavaPlugin plugin, PluginConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    /** Send the pack on join, respecting the send-on-join toggle. */
    public void sendOnJoin(Player player) {
        if (cfg.resourcePackSendOnJoin()) {
            send(player);
        }
    }

    public void send(Player player) {
        if (!cfg.resourcePackEnabled()) {
            return;
        }
        String url = cfg.resourcePackUrl();
        if (url == null || url.isEmpty()) {
            plugin.getLogger().warning("Resource pack is enabled but no url is set in config.yml.");
            return;
        }
        try {
            URI uri = URI.create(url);
            UUID id = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));

            String sha1 = cfg.resourcePackSha1();
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
                    .id(id)
                    .uri(uri)
                    .hash(sha1 == null ? "" : sha1)
                    .build();

            ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .required(cfg.resourcePackForce())
                    .prompt(Text.parse(cfg.resourcePackPrompt()))
                    .build();

            player.sendResourcePacks(request);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not send resource pack: " + t.getMessage());
        }
    }

    /** Resend the pack to every online player (used on /nlreload). */
    public void resendAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player);
        }
    }
}
