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
 *   ③ Muted     — zarządzanie wyciszeniami
 */
public class AdminGUI implements Listener {

    private static final String TITLE_SETTINGS = "§6BellChat §8» §fSettings";
    private static final String TITLE_CHANNELS = "§6BellChat §8» §fChannels";
    private static final String TITLE_MUTED    = "§6BellChat §8» §fMuted Players";

    private final BellChat plugin;

    public AdminGUI(BellChat plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Alias dla kompatybilności z BellChatCommand.open(). */
    public void open(Player admin) { openSettings(admin); }

    // ═══════════════════════════════════════════════════════════
    //  ZAKŁADKA 1 — SETTINGS
    // ═══════════════════════════════════════════════════════════

    public void openSettings(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_SETTINGS);

        // Rząd 1 — funkcje v1.0
        inv.setItem(0, makeToggle(Material.SHIELD,       "Antispam",
                getConfigBool("antispam.enabled", false)));
        inv.setItem(1, makeToggle(Material.BOOK,         "Profanity Filter",
                getConfigBool("profanity-filter.enabled", false)));
        inv.setItem(2, makeToggle(Material.BELL,         "VIP Notification",
                getConfigBool("vip-notification.enabled", true),
                "Ogłasza na czacie gdy gracz",
                "z grupy VIP dołącza do serwera."));
        inv.setItem(3, makeChatLockButton(plugin.getChatStateManager().isChatLocked()));

        // Rząd 2 — funkcje v2.x
        inv.setItem(9,  makeToggle(Material.CHAIN,     "URL Filter",
                getConfigBool("url-filter.enabled", false),
                "Blokuje linki w chacie.",
                "Gracze z bellchat.url.bypass",
                "są pomijani."));
        inv.setItem(10, makeToggle(Material.LIME_DYE,  "Emoji",
                plugin.getEmojiManager().isEnabled(),
                "Zamienia :smile: na emoji.",
                "Wymaga uprawnienia:",
                "bellchat.emoji"));
        inv.setItem(11, makeToggle(Material.CLOCK,     "Auto-Broadcasts",
                getConfigBool("broadcasts.enabled", false),
                "Cykliczne wiadomości",
                "na cały serwer."));
        inv.setItem(12, makeToggle(Material.NAME_TAG,  "Hover/Click Nick",
                getConfigBool("chat.hover-click.enabled", true),
                "Kliknięcie na nick gracza",
                "otwiera /msg <nick>."));
        inv.setItem(13, makeToggle(Material.MUSIC_DISC_CAT, "Antispam Cooldown",
                getConfigBool("antispam.block-duplicate", true),
                "Blokuje wysłanie tej samej",
                "wiadomości dwa razy."));

        // Filler
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 18; i < 45; i++) inv.setItem(i, filler);

        // Pasek dolny
        inv.setItem(45, makeItem(Material.ENDER_PEARL,
                "§b§lKanały", List.of("§7Podgląd kanałów", "§7i liczby graczy")));
        inv.setItem(46, makeItem(Material.BARRIER,
                "§c§lWyciszeni", List.of("§7Zarządzaj wyciszonymi")));
        inv.setItem(53, makeItem(Material.ARROW,
                "§e§lReload", List.of("§7Przeładuj konfigurację")));

        admin.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════
    //  ZAKŁADKA 2 — CHANNELS
    // ═══════════════════════════════════════════════════════════

    public void openChannels(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_CHANNELS);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(4, makeItem(Material.COMPASS,
                "§6§lKanały czatu", List.of("§7Aktywne kanały i gracze online")));
        inv.setItem(45, makeItem(Material.ARROW, "§f§l← Powrót", List.of("§7Wróć do Settings")));

        int slot = 10;
        for (Channel ch : plugin.getChannelManager().getChannels().values()) {
            if (slot > 43) break;

            long count = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> plugin.getChannelManager()
                            .getPlayerChannel(p).getId().equals(ch.getId()))
                    .count();

            String perm = ch.getRequiredPermission() != null
                    ? "§7Uprawnienie: §f" + ch.getRequiredPermission()
                    : "§7Dostępny dla wszystkich";

            List<String> lore = new ArrayList<>();
            lore.add("§8Typ: §7" + ch.getType().name());
            lore.add("§8Graczy: §f" + count);
            lore.add(perm);
            if (ch.getType().name().equals("LOCAL"))
                lore.add("§7Promień: §f" + ch.getLocalRadius() + " bloków");
            lore.add(ch.isEnabled() ? "§aWłączony" : "§cWyłączony");

            Material mat = switch (ch.getType()) {
                case GLOBAL -> Material.GRASS_BLOCK;
                case LOCAL  -> Material.OAK_LOG;
                case VIP    -> Material.NETHER_STAR;
                case ADMIN  -> Material.COMMAND_BLOCK;
                case PARTY  -> Material.BLUE_BANNER;
            };

            inv.setItem(slot, makeItem(mat,
                    ch.getDisplayName().replace("&", "§") + " §8[§7" + ch.getId() + "§8]", lore));
            slot++;
        }

        admin.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════
    //  ZAKŁADKA 3 — MUTED
    // ═══════════════════════════════════════════════════════════

    public void openMuted(Player admin, int page) {
        List<MuteEntry> mutes = new ArrayList<>(
                plugin.getMuteManager().getAllMutes().values());

        int perPage = 28;
        int total   = mutes.size();
        int pages   = Math.max(1, (int) Math.ceil((double) total / perPage));
        page        = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MUTED);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(4, makeItem(Material.BARRIER, "§c§lWyciszeni gracze",
                List.of("§7Łącznie: §f" + total,
                        "§7Strona: §f" + (page + 1) + "/" + pages,
                        "§cPrawy klik = odcisz")));

        inv.setItem(45, makeItem(Material.ARROW, "§f§l← Powrót", List.of("§7Wróć do Settings")));

        if (page > 0)
            inv.setItem(48, makeItem(Material.ARROW, "§f§l← Poprzednia",
                    List.of("§7Strona " + page)));
        if (page < pages - 1)
            inv.setItem(50, makeItem(Material.ARROW, "§f§lNastępna →",
                    List.of("§7Strona " + (page + 2))));

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
        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (title.equals(TITLE_SETTINGS)) handleSettings(admin, event.getSlot());
        else if (title.equals(TITLE_CHANNELS) && event.getSlot() == 45)
            Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
        else if (title.equals(TITLE_MUTED)) handleMuted(admin, event);
    }

    private void handleSettings(Player admin, int slot) {
        switch (slot) {
            case 0  -> doToggle(admin, "antispam.enabled",              "Antispam");
            case 1  -> doToggle(admin, "profanity-filter.enabled",      "Profanity Filter");
            case 2  -> doToggle(admin, "vip-notification.enabled",      "VIP Notification");
            case 3  -> {
                // Chat lock — nie zapisuje do configu, trzymany w pamięci
                boolean locked = plugin.getChatStateManager().isChatLocked();
                plugin.getChatStateManager().setChatLocked(!locked);
                plugin.getMessageManager().send(admin,
                        !locked ? "chatlock-locked" : "chatlock-unlocked",
                        Map.of("player", admin.getName()));
                Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
            }
            case 9  -> doToggle(admin, "url-filter.enabled",            "URL Filter");
            case 10 -> {
                // Emoji — zapisuje do emojis.yml przez EmojiManager
                plugin.getEmojiManager().setEnabled(!plugin.getEmojiManager().isEnabled());
                admin.sendMessage(plugin.getMessageManager().getPrefix()
                        + "§fEmoji " + (plugin.getEmojiManager().isEnabled() ? "§awłączone" : "§cwłączone") + "§7.");
                Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
            }
            case 11 -> doToggle(admin, "broadcasts.enabled",            "Auto-Broadcasts");
            case 12 -> doToggle(admin, "chat.hover-click.enabled",      "Hover/Click Nick");
            case 13 -> doToggle(admin, "antispam.block-duplicate",      "Antispam Duplicate");
            case 45 -> Bukkit.getScheduler().runTask(plugin, () -> openChannels(admin));
            case 46 -> Bukkit.getScheduler().runTask(plugin, () -> openMuted(admin, 0));
            case 53 -> {
                plugin.reload();
                plugin.getMessageManager().send(admin, "reload-done");
                Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
            }
        }
    }

    private void handleMuted(Player admin, InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == 45) {
            Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
            return;
        }

        // Poprzednia / Następna strona
        if (slot == 48 || slot == 50) {
            var item = event.getCurrentItem();
            if (item != null && item.hasItemMeta()) {
                List<String> lore = item.getItemMeta().getLore();
                if (lore != null && !lore.isEmpty()) {
                    String s = lore.get(0).replaceAll("§.", "").replace("Strona ", "").trim();
                    try {
                        int pg = Integer.parseInt(s) - 1;
                        Bukkit.getScheduler().runTask(plugin, () -> openMuted(admin, pg));
                    } catch (NumberFormatException ignored) {}
                }
            }
            return;
        }

        // Prawy klik na głowie = odcisz
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
        if (target.getPlayer() != null)
            plugin.getMessageManager().send(target.getPlayer(), "unmute-success",
                    Map.of("player", target.getName()));
        Bukkit.getScheduler().runTask(plugin, () -> openMuted(admin, 0));
    }

    // ═══════════════════════════════════════════════════════════
    //  TOGGLE HELPER — kluczowa metoda
    // ═══════════════════════════════════════════════════════════

    /**
     * Toggleuje wartość w config.yml i od razu zapisuje + przeładowuje.
     *
     * WAŻNE: jeśli klucz nie istnieje w starym config.yml na serwerze
     * (bo jest to v1.0 config), set() go utworzy i saveConfig() zapisze.
     * Dzięki temu toggleowanie działa nawet na starym configu.
     */
    private void doToggle(Player admin, String key, String label) {
        boolean current = getConfigBool(key, false);
        boolean next    = !current;

        plugin.getConfig().set(key, next);
        plugin.saveConfig();

        // Przeładuj tylko odpowiedni manager — nie cały plugin
        reloadRelevantManager(key);

        admin.sendMessage(plugin.getMessageManager().getPrefix()
                + "§f" + label + " " + (next ? "§awłączony" : "§cwłączony") + "§7.");

        Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
    }

    /**
     * Przeładowuje tylko manager powiązany z danym kluczem configu.
     * Unikamy pełnego plugin.reload() który restartuje wszystko.
     */
    private void reloadRelevantManager(String key) {
        if (key.startsWith("antispam"))          plugin.getAntispamManager().reload();
        else if (key.startsWith("url-filter"))   plugin.getUrlFilterManager().reload();
        else if (key.startsWith("broadcasts"))   plugin.getBroadcastManager().reload();
        else if (key.startsWith("profanity"))    { /* odczytywane z config na bieżąco */ }
        else if (key.startsWith("vip-notif"))    { /* odczytywane z config na bieżąco */ }
        else if (key.startsWith("chat.hover"))   { /* odczytywane z config na bieżąco */ }
    }

    // ═══════════════════════════════════════════════════════════
    //  ITEM BUILDERS
    // ═══════════════════════════════════════════════════════════

    private ItemStack makeToggle(Material mat, String name, boolean enabled, String... desc) {
        List<String> lore = new ArrayList<>();
        lore.add(enabled ? "§aWłączony §a✔" : "§cWyłączony §c✘");
        lore.add("§8──────────────");
        for (String d : desc) lore.add("§7" + d);
        if (desc.length > 0) lore.add("§8──────────────");
        lore.add("§7Kliknij aby przełączyć");
        return makeItem(mat, "§f" + name, lore);
    }

    private ItemStack makeChatLockButton(boolean locked) {
        return makeItem(
                locked ? Material.BARRIER : Material.GREEN_STAINED_GLASS_PANE,
                "§fChat Lock",
                List.of(
                        locked ? "§cZablokowany §c✘" : "§aOdblokowany §a✔",
                        "§8──────────────",
                        "§7Blokuje pisanie na chacie",
                        "§7dla graczy bez uprawnienia",
                        "§7bellchat.chatlock.bypass",
                        "§8──────────────",
                        "§7Kliknij aby przełączyć"
                ));
    }

    private ItemStack makeMuteHead(MuteEntry entry) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getPlayerUUID());
        ItemStack skull  = new ItemStack(Material.PLAYER_HEAD);
        if (skull.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(op);
            meta.setDisplayName("§c" + (op.getName() != null
                    ? op.getName() : entry.getPlayerUUID().toString()));
            meta.setLore(List.of(
                    "§7Przez: §f" + entry.getMutedBy(),
                    "§7Powód: §f" + entry.getReason(),
                    "§7Wygasa: §f" + (entry.isPermanent()
                            ? "nigdy" : entry.getFormattedRemaining()),
                    "§8──────────────",
                    "§cPrawy klik = odcisz"
            ));
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

    // ═══════════════════════════════════════════════════════════
    //  CONFIG HELPER
    // ═══════════════════════════════════════════════════════════

    /**
     * Bezpieczny odczyt boolean z config.
     * Jeśli klucz nie istnieje (stary config) zwraca defaultValue.
     */
    private boolean getConfigBool(String key, boolean defaultValue) {
        if (!plugin.getConfig().contains(key)) return defaultValue;
        return plugin.getConfig().getBoolean(key, defaultValue);
    }
}
