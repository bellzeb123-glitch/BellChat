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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin GUI for auto-broadcast slots (messages, interval, random mode).
 */
public final class BroadcastAdminGUI implements Listener {

    public enum View { SLOT_LIST, SLOT_DETAIL }

    public enum InputType { NEW_SLOT_ID, NEW_MESSAGE, INTERVAL }

    public record PendingInput(InputType type, String slotKey) {}

    public static final class BroadcastGuiHolder implements InventoryHolder {
        private Inventory inventory;
        private final View view;
        private final String slotKey;

        public BroadcastGuiHolder(View view, String slotKey) {
            this.view = view;
            this.slotKey = slotKey;
        }

        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public View getView() { return view; }
        public String getSlotKey() { return slotKey; }
    }

    private static final int SLOT_BACK = 45;
    private static final int SLOT_ADD = 53;

    private final BellChat plugin;
    private final Map<UUID, PendingInput> awaitingInput = new HashMap<>();

    public BroadcastAdminGUI(BellChat plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openSlotList(Player admin) {
        BroadcastGuiHolder holder = new BroadcastGuiHolder(View.SLOT_LIST, null);
        Inventory inv = Bukkit.createInventory(holder, 54, color(t("gui-title-broadcast")));
        holder.setInventory(inv);
        fillBorder(inv);

        inv.setItem(4, item(Material.CLOCK, color(t("gui-broadcast-title")),
                List.of(
                        color(t("gui-broadcast-subtitle")),
                        color(t("gui-separator")),
                        color(getBool("broadcasts.enabled", false)
                                ? t("gui-status-enabled") : t("gui-status-disabled"))
                )));

        List<String> keys = plugin.getBroadcastManager().getSlotKeys();
        int slot = 10;
        for (String key : keys) {
            if (slot > 43) break;
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            inv.setItem(slot++, slotSummaryItem(key));
        }

        inv.setItem(SLOT_BACK, item(Material.ARROW, color(t("gui-btn-back")),
                List.of(color("&7" + t("gui-btn-back-desc")))));
        inv.setItem(SLOT_ADD, item(Material.LIME_DYE, color(t("gui-broadcast-add-slot")),
                List.of(
                        color("&7" + t("gui-broadcast-add-desc-1")),
                        color("&7" + t("gui-broadcast-add-desc-2")),
                        color(t("gui-separator")),
                        color(t("gui-click-open"))
                )));

        admin.openInventory(inv);
    }

    public void openSlotDetail(Player admin, String slotKey) {
        var bm = plugin.getBroadcastManager();
        BroadcastGuiHolder holder = new BroadcastGuiHolder(View.SLOT_DETAIL, slotKey);
        Inventory inv = Bukkit.createInventory(holder, 54,
                color(t("gui-title-broadcast-slot").replace("{slot}", slotKey)));
        holder.setInventory(inv);
        fillBorder(inv);

        inv.setItem(4, item(Material.PAPER, color("&e" + slotKey),
                List.of(color(t("gui-broadcast-messages-count")
                        + bm.getMessages(slotKey).size()))));

        inv.setItem(20, toggleItem(Material.LEVER, t("gui-status-enabled"),
                bm.isSlotEnabled(slotKey), t("gui-desc-broadcast-slot-enable")));
        inv.setItem(21, item(Material.REPEATER, color(t("gui-broadcast-interval")),
                List.of(
                        color(t("gui-broadcast-current") + bm.getIntervalSeconds(slotKey) + "s"),
                        color(t("gui-separator")),
                        color("&7" + t("gui-desc-broadcast-interval-1")),
                        color("&7" + t("gui-desc-broadcast-interval-2")),
                        color(t("gui-separator")),
                        color(t("gui-afk-time-hint"))
                )));
        inv.setItem(23, toggleItem(Material.COMPARATOR, t("gui-broadcast-random"),
                bm.isRandom(slotKey), t("gui-desc-broadcast-random")));
        inv.setItem(24, item(Material.EMERALD, color(t("gui-broadcast-test")),
                List.of(color("&7" + t("gui-desc-broadcast-test")))));
        inv.setItem(31, item(Material.BARRIER, color(t("gui-broadcast-delete-slot")),
                List.of(
                        color("&7" + t("gui-desc-broadcast-delete")),
                        color(t("gui-separator")),
                        color(t("gui-click-toggle"))
                )));

        List<String> messages = bm.getMessages(slotKey);
        int msgSlot = 28;
        for (int i = 0; i < messages.size() && msgSlot <= 34; i++) {
            String preview = messages.get(i);
            if (preview.length() > 40) preview = preview.substring(0, 37) + "...";
            List<String> lore = new ArrayList<>();
            lore.add(color("&7" + stripColor(messages.get(i))));
            lore.add(color(t("gui-separator")));
            lore.add(color(t("gui-broadcast-msg-right-remove")));
            lore.add(color(t("gui-broadcast-msg-shift-add")));
            inv.setItem(msgSlot++, item(Material.WRITABLE_BOOK, color("&f#" + (i + 1)), lore));
        }

        inv.setItem(40, item(Material.WRITABLE_BOOK, color(t("gui-broadcast-add-message")),
                List.of(color("&7" + t("gui-desc-broadcast-add-msg")))));

        inv.setItem(SLOT_BACK, item(Material.ARROW, color(t("gui-btn-back")),
                List.of(color("&7" + t("gui-broadcast-back-slots")))));

        admin.openInventory(inv);
    }

    public boolean handleChatInput(Player admin, String message) {
        PendingInput pending = awaitingInput.remove(admin.getUniqueId());
        if (pending == null) return false;

        if (message.equalsIgnoreCase("anuluj") || message.equalsIgnoreCase("cancel")) {
            plugin.getMessageManager().send(admin, "gui-input-cancelled");
            reopenAfterInput(admin, pending);
            return true;
        }

        return switch (pending.type()) {
            case NEW_SLOT_ID -> handleNewSlotId(admin, message, pending);
            case NEW_MESSAGE -> handleNewMessage(admin, message, pending);
            case INTERVAL -> handleInterval(admin, message, pending);
        };
    }

    private boolean handleNewSlotId(Player admin, String message, PendingInput pending) {
        String id = message.trim().toLowerCase();
        if (!id.matches("[a-z][a-z0-9_]{1,20}")) {
            plugin.getMessageManager().send(admin, "gui-broadcast-invalid-slot-id");
            awaitingInput.put(admin.getUniqueId(), pending);
            return true;
        }
        if (plugin.getBroadcastManager().hasSlot(id)) {
            plugin.getMessageManager().send(admin, "gui-broadcast-slot-exists");
            awaitingInput.put(admin.getUniqueId(), pending);
            return true;
        }
        plugin.getBroadcastManager().createSlot(id);
        plugin.getMessageManager().send(admin, "gui-broadcast-slot-created", Map.of("slot", id));
        Bukkit.getScheduler().runTask(plugin, () -> openSlotDetail(admin, id));
        return true;
    }

    private boolean handleNewMessage(Player admin, String message, PendingInput pending) {
        if (message.isBlank()) {
            awaitingInput.put(admin.getUniqueId(), pending);
            return true;
        }
        plugin.getBroadcastManager().addMessage(pending.slotKey(), message);
        plugin.getMessageManager().send(admin, "gui-broadcast-message-added");
        Bukkit.getScheduler().runTask(plugin, () -> openSlotDetail(admin, pending.slotKey()));
        return true;
    }

    private boolean handleInterval(Player admin, String message, PendingInput pending) {
        int seconds;
        try {
            seconds = Integer.parseInt(message.trim());
        } catch (NumberFormatException e) {
            plugin.getMessageManager().send(admin, "afk-gui-invalid-number");
            awaitingInput.put(admin.getUniqueId(), pending);
            return true;
        }
        if (seconds < 30 || seconds > 86_400) {
            plugin.getMessageManager().send(admin, "gui-broadcast-invalid-interval");
            awaitingInput.put(admin.getUniqueId(), pending);
            return true;
        }
        plugin.getBroadcastManager().setIntervalSeconds(pending.slotKey(), seconds);
        plugin.getMessageManager().send(admin, "gui-broadcast-interval-set",
                Map.of("seconds", String.valueOf(seconds)));
        Bukkit.getScheduler().runTask(plugin, () -> openSlotDetail(admin, pending.slotKey()));
        return true;
    }

    private void reopenAfterInput(Player admin, PendingInput pending) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pending.type() == InputType.NEW_SLOT_ID) openSlotList(admin);
            else openSlotDetail(admin, pending.slotKey());
        });
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
        if (!(event.getInventory().getHolder() instanceof BroadcastGuiHolder holder)) return;

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        if (holder.getView() == View.SLOT_LIST) {
            handleSlotList(admin, slot, event.getClick());
        } else {
            handleSlotDetail(admin, holder.getSlotKey(), slot, event.getClick());
        }
    }

    private void handleSlotList(Player admin, int slot, ClickType click) {
        if (slot == SLOT_BACK) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getAdminGUI().openSettings(admin));
            return;
        }
        if (slot == SLOT_ADD) {
            admin.closeInventory();
            awaitingInput.put(admin.getUniqueId(), new PendingInput(InputType.NEW_SLOT_ID, null));
            plugin.getMessageManager().send(admin, "gui-broadcast-prompt-slot-id");
            return;
        }
        if (slot < 10 || slot > 43) return;

        List<String> keys = plugin.getBroadcastManager().getSlotKeys();
        String key = slotKeyAt(keys, slot);
        if (key == null) return;
        String target = key;
        Bukkit.getScheduler().runTask(plugin, () -> openSlotDetail(admin, target));
    }

    private void handleSlotDetail(Player admin, String slotKey, int slot, ClickType click) {
        var bm = plugin.getBroadcastManager();

        if (slot == SLOT_BACK) {
            Bukkit.getScheduler().runTask(plugin, () -> openSlotList(admin));
            return;
        }
        if (slot == 20) {
            bm.setSlotEnabled(slotKey, !bm.isSlotEnabled(slotKey));
            sendToggle(admin, t("gui-status-enabled"), bm.isSlotEnabled(slotKey));
            Bukkit.getScheduler().runTask(plugin, () -> openSlotDetail(admin, slotKey));
            return;
        }
        if (slot == 21) {
            if (click.isShiftClick() && click.isLeftClick()) {
                admin.closeInventory();
                awaitingInput.put(admin.getUniqueId(), new PendingInput(InputType.INTERVAL, slotKey));
                plugin.getMessageManager().send(admin, "gui-broadcast-prompt-interval",
                        Map.of("slot", slotKey));
                return;
            }
            int step = click.isRightClick() ? -30 : 30;
            if (click.isShiftClick()) step *= 10;
            bm.setIntervalSeconds(slotKey, Math.max(30, bm.getIntervalSeconds(slotKey) + step));
            Bukkit.getScheduler().runTask(plugin, () -> openSlotDetail(admin, slotKey));
            return;
        }
        if (slot == 23) {
            bm.setRandom(slotKey, !bm.isRandom(slotKey));
            sendToggle(admin, t("gui-broadcast-random"), bm.isRandom(slotKey));
            Bukkit.getScheduler().runTask(plugin, () -> openSlotDetail(admin, slotKey));
            return;
        }
        if (slot == 24) {
            bm.sendTest(slotKey);
            plugin.getMessageManager().send(admin, "gui-broadcast-test-sent");
            return;
        }
        if (slot == 31) {
            bm.deleteSlot(slotKey);
            plugin.getMessageManager().send(admin, "gui-broadcast-slot-deleted", Map.of("slot", slotKey));
            Bukkit.getScheduler().runTask(plugin, () -> openSlotList(admin));
            return;
        }
        if (slot == 40 || (click.isShiftClick() && slot >= 28 && slot <= 34)) {
            admin.closeInventory();
            awaitingInput.put(admin.getUniqueId(), new PendingInput(InputType.NEW_MESSAGE, slotKey));
            plugin.getMessageManager().send(admin, "gui-broadcast-prompt-message");
            return;
        }
        if (click.isRightClick() && slot >= 28 && slot <= 34) {
            int index = slot - 28;
            List<String> messages = bm.getMessages(slotKey);
            if (index >= 0 && index < messages.size()) {
                bm.removeMessage(slotKey, index);
                plugin.getMessageManager().send(admin, "gui-broadcast-message-removed");
                Bukkit.getScheduler().runTask(plugin, () -> openSlotDetail(admin, slotKey));
            }
        }
    }

    private ItemStack slotSummaryItem(String key) {
        var bm = plugin.getBroadcastManager();
        List<String> lore = new ArrayList<>();
        lore.add(color(bm.isSlotEnabled(key)
                ? t("gui-status-enabled") : t("gui-status-disabled")));
        lore.add(color(t("gui-broadcast-lore-interval") + bm.getIntervalSeconds(key) + "s"));
        lore.add(color(t("gui-broadcast-lore-messages") + bm.getMessages(key).size()));
        lore.add(color(t("gui-broadcast-lore-random") + (bm.isRandom(key) ? "✔" : "✘")));
        lore.add(color(t("gui-separator")));
        lore.add(color(t("gui-click-open")));
        return item(Material.CLOCK, color("&f" + key), lore);
    }

    private String slotKeyAt(List<String> keys, int slot) {
        int s = 10;
        for (String key : keys) {
            if (s == 17 || s == 26 || s == 35) s++;
            if (s == slot) return key;
            s++;
        }
        return null;
    }

    private ItemStack toggleItem(Material mat, String name, boolean enabled, String desc) {
        List<String> lore = new ArrayList<>();
        lore.add(color(enabled ? t("gui-status-enabled") : t("gui-status-disabled")));
        lore.add(color(t("gui-separator")));
        lore.add(color("&7" + desc));
        lore.add(color(t("gui-separator")));
        lore.add(color(t("gui-click-toggle")));
        return item(mat, color("&f" + name), lore);
    }

    private void fillBorder(Inventory inv) {
        ItemStack glass = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);
        for (int i = 45; i < 54; i++) {
            if (i != SLOT_BACK && i != SLOT_ADD) inv.setItem(i, glass);
        }
    }

    private void sendToggle(Player admin, String feature, boolean enabled) {
        String msgKey = enabled ? "gui-toggle-enabled" : "gui-toggle-disabled";
        admin.sendMessage(plugin.getMessageManager().getPrefix()
                + color(t(msgKey).replace("{feature}", feature)));
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

    private String stripColor(String s) {
        return s == null ? "" : s.replaceAll("§.", "").replaceAll("&.", "");
    }

    private boolean getBool(String key, boolean def) {
        if (!plugin.getConfig().contains(key)) return def;
        return plugin.getConfig().getBoolean(key, def);
    }
}
