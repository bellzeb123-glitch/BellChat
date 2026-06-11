package pl.bell.bellchat.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import pl.bell.bellchat.BellChat;
import pl.bell.bellchat.channel.Channel;
import pl.bell.bellchat.model.MuteEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * BellChat AdminGUI v2.3
 *
 * Zakładki:
 *   ① Settings  — toggle wszystkich funkcji czatu
 *   ② Channels  — widok kanałów + liczba graczy
 *   ③ Muted     — zarządzanie wyciszeniami (paginacja)
 *
 * Otwieranie: /bc gui
 */
public class AdminGUI implements Listener {

    // ── Tytuły inventory ─────────────────────────────────────
    private static final String TITLE_SETTINGS = "§6BellChat §8» §fSettings";
    private static final String TITLE_CHANNELS = "§6BellChat §8» §fChannels";
    private static final String TITLE_MUTED    = "§6BellChat §8» §fMuted Players";

    private final BellChat plugin;

    public AdminGUI(BellChat plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ═══════════════════════════════════════════════════════════
    //  ZAKŁADKA 1 — SETTINGS
    // ═══════════════════════════════════════════════════════════

    /**
     * Layout (54 sloty):
     *
     * [0  Antispam  ][1  Profanity ][2  VIP Notif][3  ChatLock][4  ──────────]
     * [5  URL Filter][6  Emoji     ][7  Broadcasts][8 Hover/Clk][──────────── ]
     * [──────────────────────────────── separator ─────────────────────────── ]
     * [──────────────────────────────── separator ─────────────────────────── ]
     * [──────────────────────────────── separator ─────────────────────────── ]
     * [45 Channels  ][46 Muted    ][── ── ── ── ──][53 Reload  ]
     */
    /** Alias dla kompatybilności z BellChatCommand który wywołuje adminGUI.open(player). */
    public void open(Player admin) { openSettings(admin); }

    public void openSettings(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_SETTINGS);
        var cfg = plugin.getConfig();

        // ── Rząd 1: funkcje v1.0 ──────────────────────────────
        inv.setItem(0, makeToggle(Material.SHIELD,
                "Antispam",
                cfg.getBoolean("antispam.enabled", false),
                "antispam.enabled"));

        inv.setItem(1, makeToggle(Material.BOOK,
                "Profanity Filter",
                cfg.getBoolean("profanity-filter.enabled", false),
                "profanity-filter.enabled"));

        inv.setItem(2, makeToggle(Material.BELL,
                "VIP Notification",
                cfg.getBoolean("vip-notification.enabled", true),
                "vip-notification.enabled"));

        inv.setItem(3, makeChatLockButton(plugin.getChatStateManager().isChatLocked()));

        // ── Rząd 2: funkcje v2.x ──────────────────────────────
        inv.setItem(9, makeToggle(Material.CHAIN,
                "URL Filter",
                cfg.getBoolean("url-filter.enabled", false),
                "url-filter.enabled"));

        inv.setItem(10, makeToggle(Material.LIME_DYE,
                "Emoji",
                plugin.getEmojiManager().isEnabled(),
                "emoji-in-emojis-yml")); // specjalny klucz — patrz onClick

        inv.setItem(11, makeToggle(Material.CLOCK,
                "Auto-Broadcasts",
                cfg.getBoolean("broadcasts.enabled", false),
                "broadcasts.enabled"));

        inv.setItem(12, makeToggle(Material.NAME_TAG,
                "Hover/Click na nicku",
                cfg.getBoolean("chat.hover-click.enabled", true),
                "chat.hover-click.enabled"));

        // ── Wypełnienie szarymi pane ───────────────────────────
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 18; i < 45; i++) inv.setItem(i, filler);

        // ── Pasek dolny — nawigacja ───────────────────────────
        inv.setItem(45, makeItem(Material.ENDER_PEARL,
                "§b§lKanały", List.of("§7Podgląd kanałów", "§7i liczby graczy")));

        inv.setItem(46, makeItem(Material.BARRIER,
                "§c§lWyciszeni", List.of("§7Zarządzaj wyciszonymi", "§7graczami")));

        inv.setItem(53, makeItem(Material.ARROW,
                "§e§lReload Config", List.of("§7Przeładuj konfigurację", "§7BellChat")));

        admin.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════
    //  ZAKŁADKA 2 — CHANNELS
    // ═══════════════════════════════════════════════════════════

    /**
     * Layout (54 sloty):
     * Każdy kanał zajmuje slot 9-44 (4 rzędy × 9).
     * Kanaał: ikona, nazwa, typ, liczba graczy, uprawnienie.
     *
     * Pasek dolny: ← Back
     */
    public void openChannels(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_CHANNELS);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Tytuł
        inv.setItem(4, makeItem(Material.COMPASS,
                "§6§lKanały czatu",
                List.of("§7Aktywne kanały i liczba graczy")));

        // ← Back
        inv.setItem(45, makeItem(Material.ARROW,
                "§f§l← Powrót", List.of("§7Wróć do Settings")));

        // Wyświetl kanały
        var channels = plugin.getChannelManager().getChannels();
        int slot = 10;

        for (Channel ch : channels.values()) {
            if (slot > 43) break;

            // Policz graczy w tym kanale
            long playerCount = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> plugin.getChannelManager()
                            .getPlayerChannel(p).getId().equals(ch.getId()))
                    .count();

            String displayName = ch.getDisplayName().replace("&", "§");
            String type        = ch.getType().name();
            String perm        = ch.getRequiredPermission() != null
                    ? "§7Uprawnienie: §f" + ch.getRequiredPermission()
                    : "§7Dostępny dla wszystkich";
            String radius      = ch.getType().name().equals("LOCAL")
                    ? "§7Promień: §f" + ch.getLocalRadius() + " bloków"
                    : null;
            String status      = ch.isEnabled()
                    ? "§aWłączony" : "§cWyłączony";

            List<String> lore = new ArrayList<>();
            lore.add("§8Typ: §7" + type);
            lore.add("§8Gracze: §f" + playerCount);
            lore.add(perm);
            if (radius != null) lore.add(radius);
            lore.add("§8Status: " + status);

            Material mat = switch (ch.getType()) {
                case GLOBAL -> Material.GRASS_BLOCK;
                case LOCAL  -> Material.OAK_LOG;
                case VIP    -> Material.NETHER_STAR;
                case ADMIN  -> Material.COMMAND_BLOCK;
                case PARTY  -> Material.BLUE_BANNER;
            };

            inv.setItem(slot, makeItem(mat, displayName + " §8[§7" + ch.getId() + "§8]", lore));
            slot++;
        }

        admin.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════
    //  ZAKŁADKA 3 — MUTED PLAYERS
    // ═══════════════════════════════════════════════════════════

    public void openMuted(Player admin, int page) {
        var muteManager = plugin.getMuteManager();
        Collection<MuteEntry> allMutes = muteManager.getAllMutes().values();
        List<MuteEntry> mutes = new ArrayList<>(allMutes);

        int perPage = 28;
        int total   = mutes.size();
        int pages   = Math.max(1, (int) Math.ceil((double) total / perPage));
        page        = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MUTED);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(4, makeItem(Material.BARRIER,
                "§c§lWyciszeni gracze",
                List.of("§7Łącznie: §f" + total,
                        "§7Strona: §f" + (page + 1) + "/" + pages,
                        "§7Prawy klik = odcisz")));

        // ← Back
        inv.setItem(45, makeItem(Material.ARROW,
                "§f§l← Powrót", List.of("§7Wróć do Settings")));

        // Nawigacja stron
        if (page > 0) {
            inv.setItem(48, makeItem(Material.ARROW,
                    "§f§l← Poprzednia", List.of("§7Strona " + page)));
        }
        if (page < pages - 1) {
            inv.setItem(50, makeItem(Material.ARROW,
                    "§f§lNastępna →", List.of("§7Strona " + (page + 2))));
        }

        // Głowy wyciszonych
        int start = page * perPage;
        int end   = Math.min(start + perPage, total);
        int slot  = 10;

        for (int i = start; i < end; i++) {
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            if (slot > 44) break;
            inv.setItem(slot, makeMuteHead(mutes.get(i)));
            slot++;
        }

        admin.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════
    //  CLICK HANDLER
    // ═══════════════════════════════════════════════════════════

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        String title = event.getView().getTitle();
        if (!title.equals(TITLE_SETTINGS)
                && !title.equals(TITLE_CHANNELS)
                && !title.equals(TITLE_MUTED)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        // ── Settings ──────────────────────────────────────────
        if (title.equals(TITLE_SETTINGS)) {
            handleSettings(admin, event.getSlot(), event.isRightClick());
        }

        // ── Channels — tylko nawigacja ←  ─────────────────────
        else if (title.equals(TITLE_CHANNELS)) {
            if (event.getSlot() == 45) openSettings(admin);
        }

        // ── Muted ─────────────────────────────────────────────
        else if (title.equals(TITLE_MUTED)) {
            handleMuted(admin, event);
        }
    }

    private void handleSettings(Player admin, int slot, boolean rightClick) {
        var mm = plugin.getMessageManager();

        switch (slot) {
            // Toggle antispam
            case 0 -> toggleConfig(admin, "antispam.enabled",
                    "Antispam");

            // Toggle profanity
            case 1 -> toggleConfig(admin, "profanity-filter.enabled",
                    "Profanity Filter");

            // Toggle VIP notif
            case 2 -> toggleConfig(admin, "vip-notification.enabled",
                    "VIP Notification");

            // Toggle chat lock
            case 3 -> {
                boolean locked = plugin.getChatStateManager().isChatLocked();
                plugin.getChatStateManager().setChatLocked(!locked);
                mm.send(admin, !locked ? "chatlock-locked" : "chatlock-unlocked",
                        Map.of("player", admin.getName()));
                Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
            }

            // Toggle URL filter
            case 9 -> toggleConfig(admin, "url-filter.enabled",
                    "URL Filter");

            // Toggle emoji — zapisz do emojis.yml
            case 10 -> {
                boolean cur = plugin.getEmojiManager().isEnabled();
                plugin.getEmojiManager().setEnabled(!cur);
                admin.sendMessage(mm.getPrefix() + (cur ? "§cEmoji §7wyłączone."
                        : "§aEmoji §7włączone."));
                Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
            }

            // Toggle broadcasts
            case 11 -> toggleConfig(admin, "broadcasts.enabled",
                    "Auto-Broadcasts");

            // Toggle hover/click
            case 12 -> toggleConfig(admin, "chat.hover-click.enabled",
                    "Hover/Click");

            // Otwórz Channels
            case 45 -> Bukkit.getScheduler().runTask(plugin, () -> openChannels(admin));

            // Otwórz Muted
            case 46 -> Bukkit.getScheduler().runTask(plugin, () -> openMuted(admin, 0));

            // Reload
            case 53 -> {
                plugin.reload();
                mm.send(admin, "reload-done");
                Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
            }
        }
    }

    private void handleMuted(Player admin, InventoryClickEvent event) {
        int slot = event.getSlot();

        // ← Powrót
        if (slot == 45) {
            Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
            return;
        }

        // Poprzednia / Następna strona
        String pageInfo = "";
        if (event.getView().getTitle().equals(TITLE_MUTED)) {
            var item = event.getCurrentItem();
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String name = item.getItemMeta().getDisplayName();
                if (name.contains("Poprzednia") && slot == 48) {
                    // Wyciągnij numer strony z lore
                    List<String> lore = item.getItemMeta().getLore();
                    if (lore != null && !lore.isEmpty()) {
                        String stripped = lore.get(0).replaceAll("§.", "").replace("Strona ", "");
                        try {
                            int page = Integer.parseInt(stripped.trim()) - 1;
                            Bukkit.getScheduler().runTask(plugin, () -> openMuted(admin, page));
                        } catch (NumberFormatException ignored) {}
                    }
                    return;
                }
                if (name.contains("Następna") && slot == 50) {
                    List<String> lore = item.getItemMeta().getLore();
                    if (lore != null && !lore.isEmpty()) {
                        String stripped = lore.get(0).replaceAll("§.", "").replace("Strona ", "");
                        try {
                            int page = Integer.parseInt(stripped.trim()) - 1;
                            Bukkit.getScheduler().runTask(plugin, () -> openMuted(admin, page));
                        } catch (NumberFormatException ignored) {}
                    }
                    return;
                }
            }
        }

        // Prawy klik na głowie gracza = odcisz
        if (!event.isRightClick()) return;
        if (slot < 10 || slot > 44) return;

        var item = event.getCurrentItem();
        if (item == null || item.getType() != Material.PLAYER_HEAD) return;
        if (!(item.getItemMeta() instanceof SkullMeta skull)) return;

        OfflinePlayer target = skull.getOwningPlayer();
        if (target == null) return;

        plugin.getMuteManager().unmute(target.getUniqueId());
        admin.sendMessage(plugin.getMessageManager().getPrefix()
                + "§aOdciszono gracza §f" + target.getName() + "§a.");

        if (target.getPlayer() != null && target.getPlayer().isOnline()) {
            plugin.getMessageManager().send(target.getPlayer(), "unmute-success",
                    Map.of("player", target.getName()));
        }

        Bukkit.getScheduler().runTask(plugin, () -> openMuted(admin, 0));
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    /**
     * Toggle wartości boolean w config.yml.
     * Specjalny przypadek: "emoji-in-emojis-yml" → EmojiManager.setEnabled()
     */
    private void toggleConfig(Player admin, String configKey, String label) {
        boolean current = plugin.getConfig().getBoolean(configKey, false);
        plugin.getConfig().set(configKey, !current);
        plugin.saveConfig();
        plugin.reload();

        String status = !current ? "§awłączony" : "§cwłączony";
        admin.sendMessage(plugin.getMessageManager().getPrefix()
                + "§f" + label + " " + status + "§7.");

        Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
    }

    private ItemStack makeToggle(Material mat, String name, boolean enabled, String configPath) {
        String status = enabled ? "§aWłączony ✔" : "§cWyłączony ✘";
        return makeItem(mat, "§f" + name, List.of(
                status,
                "§8──────────────",
                "§7Kliknij aby przełączyć"
        ));
    }

    private ItemStack makeChatLockButton(boolean locked) {
        Material mat = locked ? Material.BARRIER : Material.GREEN_STAINED_GLASS_PANE;
        String status = locked ? "§cZablokowany ✘" : "§aOdblokowany ✔";
        return makeItem(mat, "§fChat Lock", List.of(
                status,
                "§8──────────────",
                "§7Kliknij aby przełączyć"
        ));
    }

    private ItemStack makeMuteHead(MuteEntry entry) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getPlayerUUID());
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (skull.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(op);
            meta.setDisplayName("§c" + (op.getName() != null ? op.getName() : entry.getPlayerUUID().toString()));
            List<String> lore = new ArrayList<>();
            lore.add("§7Wyciszony przez: §f" + entry.getMutedBy());
            lore.add("§7Powód: §f" + entry.getReason());
            lore.add("§7Wygasa: §f" + (entry.isPermanent() ? "nigdy" : entry.getFormattedRemaining()));
            lore.add("§8──────────────");
            lore.add("§cPrawy klik = odcisz");
            meta.setLore(lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}