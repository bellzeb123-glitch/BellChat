package pl.bell.bellchat.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import pl.bell.bellchat.BellChat;
import pl.bell.bellchat.model.MuteEntry;

import java.util.*;

/**
 * Admin GUI — two tabs:
 *   Tab 1 (BOOK):     Chat Settings (antispam, profanity filter, chatlock toggle)
 *   Tab 2 (BARRIER):  Muted Players — browse & unmute
 */
public class AdminGUI implements Listener {

    public static final String TITLE_SETTINGS = "§8[§6BellChat§8] §fSettings";
    public static final String TITLE_MUTED    = "§8[§6BellChat§8] §fMuted Players";

    private final BellChat plugin;

    public AdminGUI(BellChat plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player admin) {
        openSettings(admin);
    }

    // ── Settings Tab ──────────────────────────────────────────

    public void openSettings(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_SETTINGS);
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        boolean antispam  = plugin.getConfig().getBoolean("antispam.enabled", false);
        boolean profanity  = plugin.getConfig().getBoolean("profanity-filter.enabled", false);
        boolean chatLocked = plugin.getChatStateManager().isChatLocked();
        boolean vipNotif   = plugin.getConfig().getBoolean("vip-notification.enabled", true);

        inv.setItem(10, makeToggle(Material.SHIELD,        "Antispam",          antispam,  "antispam.enabled"));
        inv.setItem(12, makeToggle(Material.BOOK,          "Profanity Filter",  profanity, "profanity-filter.enabled"));
        inv.setItem(14, makeToggle(Material.BELL,          "VIP Notification",  vipNotif,  "vip-notification.enabled"));
        inv.setItem(16, makeChatLockButton(chatLocked));
        inv.setItem(22, makeItem(Material.BARRIER, "§c§lMuted Players →",
                List.of("§7Click to view and manage", "§7muted players")));
        inv.setItem(26, makeItem(Material.ARROW, "§eReload Config",
                List.of("§7Reload BellChat configuration")));

        admin.openInventory(inv);
    }

    // ── Muted Players Tab ─────────────────────────────────────

    public void openMuted(Player admin, int page) {
        List<MuteEntry> mutes = new ArrayList<>(plugin.getMuteManager().getAllMutes().values());
        int total = mutes.size();
        int pages = Math.max(1, (int) Math.ceil((double) total / 45));
        page = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_MUTED);
        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        int start = page * 45;
        int end   = Math.min(start + 45, total);
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, makeMuteHead(mutes.get(i)));
        }

        if (page > 0) inv.setItem(45, makeItem(Material.ARROW, "§e◀ Previous", null));
        inv.setItem(49, makeItem(Material.BOOK, "§6Page " + (page + 1) + "/" + pages,
                List.of("§7Total muted: §f" + total, "", "§cRMB on player to unmute")));
        if (page < pages - 1) inv.setItem(53, makeItem(Material.ARROW, "§eNext ▶", null));
        inv.setItem(48, makeItem(Material.ARROW, "§7← Back to Settings", null));

        admin.openInventory(inv);
        // Store page for navigation
        admin.setMetadata("bchat_muted_page",
                new org.bukkit.metadata.FixedMetadataValue(plugin, page));
    }

    // ── Click Handler ─────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        String title = event.getView().getTitle();
        if (!title.equals(TITLE_SETTINGS) && !title.equals(TITLE_MUTED)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        var msg  = plugin.getMessageManager();

        if (title.equals(TITLE_SETTINGS)) {
            handleSettings(admin, slot);
        } else {
            handleMuted(admin, slot, event.isRightClick());
        }
    }

    private void handleSettings(Player admin, int slot) {
        var msg = plugin.getMessageManager();
        switch (slot) {
            case 10 -> toggleConfig(admin, "antispam.enabled",         "Antispam");
            case 12 -> toggleConfig(admin, "profanity-filter.enabled", "Profanity Filter");
            case 14 -> toggleConfig(admin, "vip-notification.enabled", "VIP Notification");
            case 16 -> {
                // Chat lock toggle
                boolean locked = !plugin.getChatStateManager().isChatLocked();
                plugin.getChatStateManager().setChatLocked(locked);
                String key = locked ? "chatlock-locked" : "chatlock-unlocked";
                for (Player p : Bukkit.getOnlinePlayers())
                    msg.send(p, key, Map.of("player", admin.getName()));
                Bukkit.getScheduler().runTaskLater(plugin, () -> openSettings(admin), 1L);
            }
            case 22 -> Bukkit.getScheduler().runTaskLater(plugin, () -> openMuted(admin, 0), 1L);
            case 26 -> {
                plugin.reload();
                msg.send(admin, "reload-done");
                Bukkit.getScheduler().runTaskLater(plugin, () -> openSettings(admin), 1L);
            }
        }
    }

    private void handleMuted(Player admin, int slot, boolean isRight) {
        var msg = plugin.getMessageManager();
        int page = admin.hasMetadata("bchat_muted_page")
                ? (int) admin.getMetadata("bchat_muted_page").get(0).value() : 0;

        if (slot == 45) { Bukkit.getScheduler().runTaskLater(plugin, () -> openMuted(admin, page - 1), 1L); return; }
        if (slot == 53) { Bukkit.getScheduler().runTaskLater(plugin, () -> openMuted(admin, page + 1), 1L); return; }
        if (slot == 48) { Bukkit.getScheduler().runTaskLater(plugin, () -> openSettings(admin), 1L); return; }
        if (slot >= 45)  return;

        // Player head slot
        if (!isRight) return; // LMB = info only, already in lore
        List<MuteEntry> mutes = new ArrayList<>(plugin.getMuteManager().getAllMutes().values());
        int index = page * 45 + slot;
        if (index >= mutes.size()) return;

        MuteEntry entry = mutes.get(index);
        plugin.getMuteManager().unmute(entry.getPlayerUUID());
        msg.send(admin, "unmute-success", Map.of("player", entry.getPlayerName()));
        Player target = Bukkit.getPlayer(entry.getPlayerUUID());
        if (target != null) target.sendMessage(msg.getPrefix() + "§aYou have been unmuted.");
        Bukkit.getScheduler().runTaskLater(plugin, () -> openMuted(admin, page), 1L);
    }

    private void toggleConfig(Player admin, String path, String name) {
        boolean current = plugin.getConfig().getBoolean(path, false);
        plugin.getConfig().set(path, !current);
        plugin.saveConfig();
        plugin.reload();
        admin.sendMessage(plugin.getMessageManager().getPrefix() +
                "§7" + name + ": " + (!current ? "§aEnabled" : "§cDisabled"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> openSettings(admin), 1L);
    }

    // ── Item Builders ─────────────────────────────────────────

    private ItemStack makeToggle(Material mat, String name, boolean enabled, String configPath) {
        Material m = enabled ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        String status = enabled ? "§aEnabled" : "§cDisabled";
        return makeItem(m, "§f" + name + ": " + status,
                List.of("§7Click to toggle", "§7Config: §e" + configPath));
    }

    private ItemStack makeChatLockButton(boolean locked) {
        Material m = locked ? Material.RED_CONCRETE : Material.GREEN_CONCRETE;
        String label = locked ? "§c§lChat: LOCKED" : "§a§lChat: UNLOCKED";
        return makeItem(m, label, List.of("§7Click to " + (locked ? "unlock" : "lock") + " chat"));
    }

    private ItemStack makeMuteHead(MuteEntry entry) {
        @SuppressWarnings("deprecation")
        var op = Bukkit.getOfflinePlayer(entry.getPlayerUUID());
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(op);
        meta.setDisplayName("§e" + entry.getPlayerName());
        meta.setLore(List.of(
                "§7Muted by: §f" + entry.getMutedBy(),
                "§7Reason: §f" + entry.getReason(),
                "§7Expires: §f" + entry.getFormattedRemaining(),
                "",
                "§cRMB §7— Unmute"
        ));
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
