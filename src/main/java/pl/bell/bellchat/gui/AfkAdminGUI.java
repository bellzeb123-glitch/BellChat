package pl.bell.bellchat.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.bell.bellchat.BellChat;
import pl.bell.bellchat.managers.AfkConfigManager;
import pl.bell.bellchat.model.AfkGroupRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Admin GUI for per-group AFK / auto-kick rules (LuckPerms groups).
 */
public final class AfkAdminGUI implements Listener {

    public static final int SLOT_GLOBAL = 4;
    public static final int SLOT_BACK = 45;
    public static final int SLOT_PREV = 48;
    public static final int SLOT_NEXT = 50;

    public static final int DETAIL_AUTO_AFK_TOGGLE = 20;
    public static final int DETAIL_AUTO_AFK_TIME = 21;
    public static final int DETAIL_KICK_TOGGLE = 23;
    public static final int DETAIL_KICK_TIME = 24;
    public static final int DETAIL_RESET = 31;

    public enum View { GROUP_LIST, GROUP_DETAIL }

    public enum TimeField { AUTO_AFK, KICK }

    public static final class AfkGuiHolder implements InventoryHolder {
        private Inventory inventory;
        private final View view;
        private final String groupId;
        private final int page;

        public AfkGuiHolder(View view, String groupId, int page) {
            this.view = view;
            this.groupId = groupId;
            this.page = page;
        }

        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public View getView() { return view; }
        public String getGroupId() { return groupId; }
        public int getPage() { return page; }
    }

    private final BellChat plugin;
    private final Map<UUID, PendingInput> awaitingInput = new HashMap<>();

    public record PendingInput(String groupId, TimeField field) {}

    public AfkAdminGUI(BellChat plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openGroupList(Player admin) {
        openGroupList(admin, 0);
    }

    public void openGroupList(Player admin, int page) {
        AfkConfigManager cfg = plugin.getAfkConfigManager();
        List<String> groups = cfg.listManageableGroups();

        int perPage = 28;
        int pages = Math.max(1, (int) Math.ceil(groups.size() / (double) perPage));
        page = Math.max(0, Math.min(page, pages - 1));

        AfkGuiHolder holder = new AfkGuiHolder(View.GROUP_LIST, null, page);
        Inventory inv = Bukkit.createInventory(holder, 54, color(t("gui-title-afk")));
        holder.setInventory(inv);

        fillBorder(inv);

        inv.setItem(SLOT_GLOBAL, item(Material.CLOCK,
                color(t("gui-afk-global-name")),
                List.of(
                        color(cfg.isGlobalEnabled() ? t("gui-status-enabled") : t("gui-status-disabled")),
                        color(t("gui-separator")),
                        color("&7" + t("gui-desc-afk-global-1")),
                        color("&7" + t("gui-desc-afk-global-2")),
                        color(t("gui-separator")),
                        color(t("gui-click-toggle"))
                )));

        inv.setItem(SLOT_BACK, item(Material.ARROW, color(t("gui-btn-back")),
                List.of(color("&7" + t("gui-btn-back-desc")))));

        if (page > 0) {
            inv.setItem(SLOT_PREV, item(Material.ARROW, color(t("gui-muted-prev")),
                    List.of(color(t("gui-muted-page-info") + (page)))));
        }
        if (page < pages - 1) {
            inv.setItem(SLOT_NEXT, item(Material.ARROW, color(t("gui-muted-next")),
                    List.of(color(t("gui-muted-page-info") + (page + 2)))));
        }

        int start = page * perPage;
        int end = Math.min(start + perPage, groups.size());
        int slot = 10;
        for (int i = start; i < end; i++) {
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            if (slot > 44) break;
            inv.setItem(slot++, groupSummaryItem(groups.get(i), cfg));
        }

        admin.openInventory(inv);
    }

    public void openGroupDetail(Player admin, String groupId) {
        AfkConfigManager cfg = plugin.getAfkConfigManager();
        String id = groupId.toLowerCase(Locale.ROOT);
        AfkGroupRule rule = cfg.getConfiguredRule(id);
        boolean explicit = cfg.hasConfiguredGroup(id);
        boolean isDefault = "default".equals(id);

        AfkGuiHolder holder = new AfkGuiHolder(View.GROUP_DETAIL, id, 0);
        Inventory inv = Bukkit.createInventory(holder, 54,
                color(t("gui-title-afk-group").replace("{group}", id)));
        holder.setInventory(inv);
        fillBorder(inv);

        inv.setItem(4, item(Material.NAME_TAG, color("&e" + id),
                List.of(
                        color(explicit ? t("gui-afk-configured") : t("gui-afk-inherited-default")),
                        color(t("gui-separator")),
                        color(t("gui-afk-lp-detected"))
                )));

        inv.setItem(DETAIL_AUTO_AFK_TOGGLE, toggleItem(Material.OAK_DOOR,
                t("gui-afk-auto-toggle"), rule.isAutoAfkEnabled(),
                t("gui-desc-afk-auto-1"), t("gui-desc-afk-auto-2")));

        inv.setItem(DETAIL_AUTO_AFK_TIME, timeItem(Material.REPEATER,
                t("gui-afk-auto-time"), rule.getAutoAfkSeconds(),
                t("gui-desc-afk-time-1"), t("gui-desc-afk-time-2"), t("gui-desc-afk-time-3")));

        inv.setItem(DETAIL_KICK_TOGGLE, toggleItem(Material.IRON_DOOR,
                t("gui-afk-kick-toggle"), rule.isKickEnabled(),
                t("gui-desc-afk-kick-1"), t("gui-desc-afk-kick-2")));

        inv.setItem(DETAIL_KICK_TIME, timeItem(Material.COMPARATOR,
                t("gui-afk-kick-time"), rule.getKickSeconds(),
                t("gui-desc-afk-kick-time-1"), t("gui-desc-afk-kick-time-2"), t("gui-desc-afk-kick-time-3")));

        if (!isDefault) {
            inv.setItem(DETAIL_RESET, item(Material.BARRIER, color(t("gui-afk-reset")),
                    List.of(
                            color("&7" + t("gui-desc-afk-reset-1")),
                            color("&7" + t("gui-desc-afk-reset-2")),
                            color(t("gui-separator")),
                            color(t("gui-click-toggle"))
                    )));
        }

        inv.setItem(SLOT_BACK, item(Material.ARROW, color(t("gui-btn-back")),
                List.of(color("&7" + t("gui-afk-back-groups")))));

        admin.openInventory(inv);
    }

    public boolean handleChatInput(Player admin, String message) {
        PendingInput pending = awaitingInput.remove(admin.getUniqueId());
        if (pending == null) return false;

        if (message.equalsIgnoreCase("anuluj") || message.equalsIgnoreCase("cancel")) {
            plugin.getMessageManager().send(admin, "afk-gui-cancelled");
            Bukkit.getScheduler().runTask(plugin, () -> openGroupDetail(admin, pending.groupId()));
            return true;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(message.trim());
        } catch (NumberFormatException e) {
            plugin.getMessageManager().send(admin, "afk-gui-invalid-number");
            awaitingInput.put(admin.getUniqueId(), pending);
            return true;
        }

        if (seconds < 0 || seconds > 86_400) {
            plugin.getMessageManager().send(admin, "afk-gui-invalid-range");
            awaitingInput.put(admin.getUniqueId(), pending);
            return true;
        }

        AfkConfigManager cfg = plugin.getAfkConfigManager();
        AfkGroupRule rule = cfg.getConfiguredRule(pending.groupId());
        if (pending.field() == TimeField.AUTO_AFK) {
            rule.setAutoAfkSeconds(seconds);
        } else {
            rule.setKickSeconds(seconds);
        }
        cfg.saveGroupRule(pending.groupId(), rule);
        plugin.getAfkManager().reload();

        plugin.getMessageManager().send(admin, "afk-gui-time-set",
                Map.of("seconds", String.valueOf(seconds), "group", pending.groupId()));
        Bukkit.getScheduler().runTask(plugin, () -> openGroupDetail(admin, pending.groupId()));
        return true;
    }

    public boolean isAwaitingInput(Player player) {
        return awaitingInput.containsKey(player.getUniqueId());
    }

    public void cancelPendingInput(Player player) {
        awaitingInput.remove(player.getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        if (!(event.getInventory().getHolder() instanceof AfkGuiHolder holder)) return;

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = event.getRawSlot();

        if (holder.getView() == View.GROUP_LIST) {
            handleGroupList(admin, holder, slot, event.getClick());
        } else {
            handleGroupDetail(admin, holder, slot, event.getClick());
        }
    }

    private void handleGroupList(Player admin, AfkGuiHolder holder, int slot, ClickType click) {
        if (slot == SLOT_BACK) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getAdminGUI().openSettings(admin));
            return;
        }
        if (slot == SLOT_GLOBAL) {
            AfkConfigManager cfg = plugin.getAfkConfigManager();
            cfg.setGlobalEnabled(!cfg.isGlobalEnabled());
            plugin.getAfkManager().reload();
            sendToggle(admin, t("gui-afk-global-name"), cfg.isGlobalEnabled());
            Bukkit.getScheduler().runTask(plugin, () -> openGroupList(admin, holder.getPage()));
            return;
        }
        if (slot == SLOT_PREV || slot == SLOT_NEXT) {
            int page = holder.getPage();
            if (slot == SLOT_PREV) page--;
            else page++;
            int finalPage = page;
            Bukkit.getScheduler().runTask(plugin, () -> openGroupList(admin, finalPage));
            return;
        }
        if (slot < 10 || slot > 44) return;

        List<String> groups = plugin.getAfkConfigManager().listManageableGroups();
        String group = groupAtSlot(slot, holder.getPage(), groups);
        if (group == null) return;

        String target = group;
        Bukkit.getScheduler().runTask(plugin, () -> openGroupDetail(admin, target));
    }

    private void handleGroupDetail(Player admin, AfkGuiHolder holder, int slot, ClickType click) {
        String groupId = holder.getGroupId();
        AfkConfigManager cfg = plugin.getAfkConfigManager();
        AfkGroupRule rule = cfg.getConfiguredRule(groupId);

        if (slot == SLOT_BACK) {
            Bukkit.getScheduler().runTask(plugin, () -> openGroupList(admin, 0));
            return;
        }
        if (slot == DETAIL_RESET && !"default".equals(groupId)) {
            cfg.clearGroupOverride(groupId);
            plugin.getAfkManager().reload();
            plugin.getMessageManager().send(admin, "afk-gui-reset", Map.of("group", groupId));
            Bukkit.getScheduler().runTask(plugin, () -> openGroupDetail(admin, groupId));
            return;
        }
        if (slot == DETAIL_AUTO_AFK_TOGGLE) {
            if (rule.isAutoAfkEnabled()) {
                rule.setAutoAfkSeconds(0);
            } else {
                rule.setAutoAfkSeconds(cfg.getDefaultRule().getAutoAfkSeconds() > 0
                        ? cfg.getDefaultRule().getAutoAfkSeconds() : 180);
            }
            saveAndRefresh(admin, groupId, rule, t("gui-afk-auto-toggle"));
            return;
        }
        if (slot == DETAIL_KICK_TOGGLE) {
            rule.setKickEnabled(!rule.isKickEnabled());
            saveAndRefresh(admin, groupId, rule, t("gui-afk-kick-toggle"));
            return;
        }
        if (slot == DETAIL_AUTO_AFK_TIME) {
            adjustOrPrompt(admin, groupId, rule, TimeField.AUTO_AFK, click);
            return;
        }
        if (slot == DETAIL_KICK_TIME) {
            adjustOrPrompt(admin, groupId, rule, TimeField.KICK, click);
            return;
        }
    }

    private void adjustOrPrompt(Player admin, String groupId, AfkGroupRule rule,
                                TimeField field, ClickType click) {
        if (click.isShiftClick() && click.isLeftClick()) {
            admin.closeInventory();
            awaitingInput.put(admin.getUniqueId(), new PendingInput(groupId, field));
            String key = field == TimeField.AUTO_AFK ? "afk-gui-prompt-auto" : "afk-gui-prompt-kick";
            plugin.getMessageManager().send(admin, key, Map.of("group", groupId));
            return;
        }

        int step = click.isRightClick() ? -60 : 60;
        if (click.isShiftClick()) step *= 5;

        if (field == TimeField.AUTO_AFK) {
            rule.setAutoAfkSeconds(Math.max(0, rule.getAutoAfkSeconds() + step));
        } else {
            rule.setKickSeconds(Math.max(0, rule.getKickSeconds() + step));
        }
        saveAndRefresh(admin, groupId, rule, field == TimeField.AUTO_AFK
                ? t("gui-afk-auto-time") : t("gui-afk-kick-time"));
    }

    private void saveAndRefresh(Player admin, String groupId, AfkGroupRule rule, String label) {
        plugin.getAfkConfigManager().saveGroupRule(groupId, rule);
        plugin.getAfkManager().reload();
        plugin.getMessageManager().send(admin, "afk-gui-saved", Map.of("group", groupId));
        Bukkit.getScheduler().runTask(plugin, () -> openGroupDetail(admin, groupId));
    }

    private ItemStack groupSummaryItem(String group, AfkConfigManager cfg) {
        AfkGroupRule rule = cfg.getConfiguredRule(group);
        boolean explicit = cfg.hasConfiguredGroup(group);

        Material mat = switch (group.toLowerCase(Locale.ROOT)) {
            case "admin" -> Material.COMMAND_BLOCK;
            case "vip", "svip", "mvip" -> Material.NETHER_STAR;
            case "default" -> Material.PLAYER_HEAD;
            default -> Material.PAPER;
        };

        List<String> lore = new ArrayList<>();
        lore.add(color(explicit ? t("gui-afk-configured") : t("gui-afk-inherited-default")));
        lore.add(color(t("gui-separator")));
        lore.add(color(t("gui-afk-lore-auto") + formatSeconds(rule.getAutoAfkSeconds())));
        lore.add(color(t("gui-afk-lore-kick") + (rule.isKickEnabled()
                ? t("gui-status-enabled").replaceAll("§.", "")
                : t("gui-status-disabled").replaceAll("§.", ""))));
        if (rule.isKickEnabled()) {
            lore.add(color(t("gui-afk-lore-kick-time") + formatSeconds(rule.getKickSeconds())));
        }
        lore.add(color(t("gui-separator")));
        lore.add(color(t("gui-afk-click-edit")));

        return item(mat, color("&f" + group), lore);
    }

    private ItemStack toggleItem(Material mat, String name, boolean enabled, String... desc) {
        List<String> lore = new ArrayList<>();
        lore.add(color(enabled ? t("gui-status-enabled") : t("gui-status-disabled")));
        lore.add(color(t("gui-separator")));
        for (String line : desc) lore.add(color("&7" + line));
        lore.add(color(t("gui-separator")));
        lore.add(color(t("gui-click-toggle")));
        return item(mat, color("&f" + name), lore);
    }

    private ItemStack timeItem(Material mat, String name, int seconds, String... desc) {
        List<String> lore = new ArrayList<>();
        lore.add(color(t("gui-afk-current") + formatSeconds(seconds)));
        lore.add(color(t("gui-separator")));
        for (String line : desc) lore.add(color("&7" + line));
        lore.add(color(t("gui-separator")));
        lore.add(color(t("gui-afk-time-hint")));
        return item(mat, color("&f" + name), lore);
    }

    private String groupAtSlot(int slot, int page, List<String> groups) {
        int perPage = 28;
        int start = page * perPage;
        int end = Math.min(start + perPage, groups.size());
        int s = 10;
        for (int i = start; i < end; i++) {
            if (s == 17 || s == 26 || s == 35) s++;
            if (s == slot) return groups.get(i);
            s++;
        }
        return null;
    }

    private void fillBorder(Inventory inv) {
        ItemStack glass = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = 45; i < 54; i++) {
            if (i != SLOT_BACK && i != SLOT_PREV && i != SLOT_NEXT) {
                inv.setItem(i, glass);
            }
        }
    }

    private void sendToggle(Player admin, String feature, boolean enabled) {
        String msgKey = enabled ? "gui-toggle-enabled" : "gui-toggle-disabled";
        admin.sendMessage(plugin.getMessageManager().getPrefix()
                + color(t(msgKey).replace("{feature}", feature)));
    }

    private String formatSeconds(int seconds) {
        if (seconds <= 0) return t("gui-afk-off");
        if (seconds < 60) return seconds + "s";
        int min = seconds / 60;
        int sec = seconds % 60;
        if (sec == 0) return min + " min";
        return min + " min " + sec + "s";
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.setDisplayName(name);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private String t(String key) {
        return plugin.getMessageManager().getRaw(key);
    }

    private String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }
}
