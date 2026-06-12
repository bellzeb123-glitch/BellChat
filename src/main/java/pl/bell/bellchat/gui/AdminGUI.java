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
import pl.bell.bellchat.managers.MessageManager;
import pl.bell.bellchat.model.MuteEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BellChat AdminGUI v3.0
 *
 * Wszystkie teksty pobierane z MessageManager (plik językowy).
 * Brak hardkodowanych stringów — zmiana języka w /bch lang
 * natychmiast wpływa na całe GUI.
 *
 * Zakładki:
 *   ① Settings  — toggle funkcji
 *   ② Channels  — widok kanałów
 *   ③ Muted     — wyciszenia
 */
public class AdminGUI implements Listener {

    private final BellChat plugin;

    public AdminGUI(BellChat plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ── Pomocnicze gettery dla tytułów (zmieniają się z językiem) ──
    private String titleSettings() { return color(t("gui-title-settings")); }
    private String titleChannels() { return color(t("gui-title-channels")); }
    private String titleMuted()    { return color(t("gui-title-muted")); }

    public void open(Player admin) { openSettings(admin); }

    // ═══════════════════════════════════════════════════════════
    //  ZAKŁADKA 1 — SETTINGS
    // ═══════════════════════════════════════════════════════════

    public void openSettings(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, titleSettings());

        // Rząd 1
        inv.setItem(0, toggle(Material.SHIELD,    t("gui-toggle-antispam"),
                getBool("antispam.enabled", false)));
        inv.setItem(1, toggle(Material.BOOK,      t("gui-toggle-profanity"),
                getBool("profanity-filter.enabled", false)));
        inv.setItem(2, toggle(Material.BELL,      t("gui-toggle-vip-notif"),
                getBool("vip-notification.enabled", true),
                t("gui-desc-vip-notif-1"), t("gui-desc-vip-notif-2")));
        inv.setItem(3, chatLockButton(plugin.getChatStateManager().isChatLocked()));

        // Rząd 2
        inv.setItem(9,  toggle(Material.CHAIN,    t("gui-toggle-url-filter"),
                getBool("url-filter.enabled", false),
                t("gui-desc-url-filter-1"), t("gui-desc-url-filter-2"), t("gui-desc-url-filter-3")));
        inv.setItem(10, toggle(Material.LIME_DYE, t("gui-toggle-emoji"),
                plugin.getEmojiManager().isEnabled(),
                t("gui-desc-emoji-1"), t("gui-desc-emoji-2"), t("gui-desc-emoji-3")));
        inv.setItem(11, toggle(Material.CLOCK,    t("gui-toggle-broadcasts"),
                getBool("broadcasts.enabled", false),
                t("gui-desc-broadcasts-1"), t("gui-desc-broadcasts-2")));
        inv.setItem(12, toggle(Material.NAME_TAG, t("gui-toggle-hover-click"),
                getBool("chat.hover-click.enabled", true),
                t("gui-desc-hover-1"), t("gui-desc-hover-2")));
        inv.setItem(13, toggle(Material.COMPARATOR, t("gui-toggle-duplicate"),
                getBool("antispam.block-duplicate", true),
                t("gui-desc-duplicate-1"), t("gui-desc-duplicate-2")));

        // Filler
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 18; i < 45; i++) inv.setItem(i, filler);

        // Pasek dolny
        inv.setItem(45, item(Material.ENDER_PEARL, color(t("gui-btn-channels")),
                List.of(color("&7" + t("gui-btn-channels-desc-1")),
                        color("&7" + t("gui-btn-channels-desc-2")))));
        inv.setItem(46, item(Material.BARRIER, color(t("gui-btn-muted")),
                List.of(color("&7" + t("gui-btn-muted-desc")))));
        inv.setItem(53, item(Material.ARROW, color(t("gui-btn-reload")),
                List.of(color("&7" + t("gui-btn-reload-desc")))));

        admin.openInventory(inv);
    }

    // ═══════════════════════════════════════════════════════════
    //  ZAKŁADKA 2 — CHANNELS
    // ═══════════════════════════════════════════════════════════

    public void openChannels(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, titleChannels());

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(4, item(Material.COMPASS, color(t("gui-channels-title")),
                List.of(color(t("gui-channels-subtitle")))));
        inv.setItem(45, item(Material.ARROW, color(t("gui-btn-back")),
                List.of(color("&7" + t("gui-btn-back-desc")))));

        int slot = 10;
        for (Channel ch : plugin.getChannelManager().getChannels().values()) {
            if (slot > 43) break;

            long count = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> plugin.getChannelManager()
                            .getPlayerChannel(p).getId().equals(ch.getId()))
                    .count();

            String perm = ch.getRequiredPermission() != null
                    ? color(t("gui-channels-permission") + ch.getRequiredPermission())
                    : color(t("gui-channels-open"));

            List<String> lore = new ArrayList<>();
            lore.add(color(t("gui-channels-type") + ch.getType().name()));
            lore.add(color(t("gui-channels-players") + count));
            lore.add(perm);
            if (ch.getType().name().equals("LOCAL"))
                lore.add(color(t("gui-channels-radius") + ch.getLocalRadius() + t("gui-channels-blocks")));
            lore.add(color(ch.isEnabled() ? t("gui-status-enabled") : t("gui-status-disabled")));

            Material mat = switch (ch.getType()) {
                case GLOBAL -> Material.GRASS_BLOCK;
                case LOCAL  -> Material.OAK_LOG;
                case VIP    -> Material.NETHER_STAR;
                case ADMIN  -> Material.COMMAND_BLOCK;
                case PARTY  -> Material.BLUE_BANNER;
            };

            inv.setItem(slot, item(mat,
                    color(ch.getDisplayName()) + " §8[§7" + ch.getId() + "§8]", lore));
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

        Inventory inv = Bukkit.createInventory(null, 54, titleMuted());

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(4, item(Material.BARRIER, color(t("gui-muted-title")),
                List.of(
                        color(t("gui-muted-total") + total),
                        color(t("gui-muted-page") + (page + 1) + "/" + pages),
                        color(t("gui-muted-right-click"))
                )));

        inv.setItem(45, item(Material.ARROW, color(t("gui-btn-back")),
                List.of(color("&7" + t("gui-btn-back-desc")))));

        if (page > 0)
            inv.setItem(48, item(Material.ARROW, color(t("gui-muted-prev")),
                    List.of(color(t("gui-muted-page-info") + page))));
        if (page < pages - 1)
            inv.setItem(50, item(Material.ARROW, color(t("gui-muted-next")),
                    List.of(color(t("gui-muted-page-info") + (page + 2)))));

        int start = page * perPage;
        int end   = Math.min(start + perPage, total);
        int slot  = 10;

        for (int i = start; i < end; i++) {
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            if (slot > 44) break;
            inv.setItem(slot, muteHead(mutes.get(i)));
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

        boolean isOurs = title.equals(titleSettings())
                || title.equals(titleChannels())
                || title.equals(titleMuted());
        if (!isOurs) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        if (title.equals(titleSettings())) handleSettings(admin, event.getSlot());
        else if (title.equals(titleChannels()) && event.getSlot() == 45)
            Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
        else if (title.equals(titleMuted())) handleMuted(admin, event);
    }

    private void handleSettings(Player admin, int slot) {
        switch (slot) {
            case 0  -> doToggle(admin, "antispam.enabled",         t("gui-toggle-antispam"));
            case 1  -> doToggle(admin, "profanity-filter.enabled", t("gui-toggle-profanity"));
            case 2  -> doToggle(admin, "vip-notification.enabled", t("gui-toggle-vip-notif"));
            case 3  -> {
                boolean locked = plugin.getChatStateManager().isChatLocked();
                plugin.getChatStateManager().setChatLocked(!locked);
                plugin.getMessageManager().send(admin,
                        !locked ? "chatlock-locked" : "chatlock-unlocked",
                        Map.of("player", admin.getName()));
                Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
            }
            case 9  -> doToggle(admin, "url-filter.enabled",       t("gui-toggle-url-filter"));
            case 10 -> {
                plugin.getEmojiManager().setEnabled(!plugin.getEmojiManager().isEnabled());
                sendToggleMsg(admin, t("gui-toggle-emoji"), plugin.getEmojiManager().isEnabled());
                Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
            }
            case 11 -> doToggle(admin, "broadcasts.enabled",       t("gui-toggle-broadcasts"));
            case 12 -> doToggle(admin, "chat.hover-click.enabled", t("gui-toggle-hover-click"));
            case 13 -> doToggle(admin, "antispam.block-duplicate", t("gui-toggle-duplicate"));
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

        if (slot == 48 || slot == 50) {
            var clicked = event.getCurrentItem();
            if (clicked != null && clicked.hasItemMeta()) {
                List<String> lore = clicked.getItemMeta().getLore();
                if (lore != null && !lore.isEmpty()) {
                    String pageInfo = t("gui-muted-page-info").replaceAll("§.", "");
                    String s = lore.get(0).replaceAll("§.", "").replace(pageInfo, "").trim();
                    try {
                        int pg = Integer.parseInt(s) - 1;
                        Bukkit.getScheduler().runTask(plugin, () -> openMuted(admin, pg));
                    } catch (NumberFormatException ignored) {}
                }
            }
            return;
        }

        if (!event.isRightClick()) return;
        if (slot < 10 || slot > 44) return;
        var clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        if (!(clicked.getItemMeta() instanceof SkullMeta skull)) return;
        OfflinePlayer target = skull.getOwningPlayer();
        if (target == null) return;

        plugin.getMuteManager().unmute(target.getUniqueId());
        admin.sendMessage(plugin.getMessageManager().getPrefix()
                + color(t("gui-muted-unmuted").replace("{player}", target.getName())));
        if (target.getPlayer() != null)
            plugin.getMessageManager().send(target.getPlayer(), "unmute-success",
                    Map.of("player", target.getName()));
        Bukkit.getScheduler().runTask(plugin, () -> openMuted(admin, 0));
    }

    // ═══════════════════════════════════════════════════════════
    //  TOGGLE HELPER
    // ═══════════════════════════════════════════════════════════

    private void doToggle(Player admin, String key, String label) {
        boolean current = getBool(key, false);
        boolean next    = !current;

        plugin.getConfig().set(key, next);
        plugin.saveConfig();
        reloadRelevant(key);

        sendToggleMsg(admin, label, next);
        Bukkit.getScheduler().runTask(plugin, () -> openSettings(admin));
    }

    private void sendToggleMsg(Player admin, String feature, boolean enabled) {
        String msgKey = enabled ? "gui-toggle-enabled" : "gui-toggle-disabled";
        String msg = t(msgKey).replace("{feature}", feature);
        admin.sendMessage(plugin.getMessageManager().getPrefix() + color(msg));
    }

    private void reloadRelevant(String key) {
        if (key.startsWith("antispam"))        plugin.getAntispamManager().reload();
        else if (key.startsWith("url-filter")) plugin.getUrlFilterManager().reload();
        else if (key.startsWith("broadcasts")) plugin.getBroadcastManager().reload();
    }

    // ═══════════════════════════════════════════════════════════
    //  ITEM BUILDERS
    // ═══════════════════════════════════════════════════════════

    private ItemStack toggle(Material mat, String name, boolean enabled, String... desc) {
        List<String> lore = new ArrayList<>();
        lore.add(color(enabled ? t("gui-status-enabled") : t("gui-status-disabled")));
        lore.add(color(t("gui-separator")));
        for (String d : desc) lore.add(color("&7" + d));
        if (desc.length > 0) lore.add(color(t("gui-separator")));
        lore.add(color(t("gui-click-toggle")));
        return item(mat, "§f" + name, lore);
    }

    private ItemStack chatLockButton(boolean locked) {
        return item(
                locked ? Material.BARRIER : Material.GREEN_STAINED_GLASS_PANE,
                "§f" + t("gui-toggle-chatlock"),
                List.of(
                        color(locked ? t("gui-status-locked") : t("gui-status-unlocked")),
                        color(t("gui-separator")),
                        color("&7" + t("gui-desc-chatlock-1")),
                        color("&7" + t("gui-desc-chatlock-2")),
                        color(t("gui-separator")),
                        color(t("gui-click-toggle"))
                ));
    }

    private ItemStack muteHead(MuteEntry entry) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getPlayerUUID());
        ItemStack skull  = new ItemStack(Material.PLAYER_HEAD);
        if (skull.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(op);
            meta.setDisplayName("§c" + (op.getName() != null
                    ? op.getName() : entry.getPlayerUUID().toString()));
            meta.setLore(List.of(
                    color(t("gui-muted-by") + entry.getMutedBy()),
                    color(t("gui-muted-reason") + entry.getReason()),
                    color(t("gui-muted-expires") + (entry.isPermanent()
                            ? t("gui-muted-never") : entry.getFormattedRemaining())),
                    color(t("gui-separator")),
                    color(t("gui-muted-right-click"))
            ));
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    /** Skrót — tłumaczenie z MessageManager. */
    private String t(String key) {
        // Surowy klucz bez kolorowania (kolory dodawane w miejscu wywołania)
        return plugin.getMessageManager().getRaw(key);
    }

    /** Kolory & → § */
    private String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }

    private boolean getBool(String key, boolean def) {
        if (!plugin.getConfig().contains(key)) return def;
        return plugin.getConfig().getBoolean(key, def);
    }
}
