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
import pl.bell.bellchat.channel.Channel;
import pl.bell.bellchat.channel.ChannelType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Admin GUI for chat channels — create, edit, permissions info (BellLP / LuckPerms).
 */
public final class ChannelAdminGUI implements Listener {

    public enum View { CHANNEL_LIST, CHANNEL_DETAIL, CHANNEL_CREATE_TYPE }

    public enum InputType { NEW_CHANNEL_ID, DISPLAY_NAME, FORMAT, PERMISSION, RADIUS }

    public record PendingInput(InputType type, String channelId, ChannelType channelType) {}

    public static final class ChannelGuiHolder implements InventoryHolder {
        private Inventory inventory;
        private final View view;
        private final String channelId;

        public ChannelGuiHolder(View view, String channelId) {
            this.view = view;
            this.channelId = channelId;
        }

        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
        public View getView() { return view; }
        public String getChannelId() { return channelId; }
    }

    private static final int SLOT_BACK = 45;
    private static final int SLOT_ADD = 53;

    private final BellChat plugin;
    private final Map<UUID, PendingInput> awaitingInput = new HashMap<>();

    public ChannelAdminGUI(BellChat plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openChannelList(Player admin) {
        ChannelGuiHolder holder = new ChannelGuiHolder(View.CHANNEL_LIST, null);
        Inventory inv = Bukkit.createInventory(holder, 54, color(t("gui-title-channels")));
        holder.setInventory(inv);
        fillBorder(inv);

        inv.setItem(4, item(Material.COMPASS, color(t("gui-channels-title")),
                List.of(color(t("gui-channels-subtitle")))));

        int slot = 10;
        for (Channel ch : plugin.getChannelManager().getChannelsList()) {
            if (slot > 43) break;
            if (slot == 17 || slot == 26 || slot == 35) slot++;
            inv.setItem(slot++, channelSummaryItem(ch));
        }

        inv.setItem(SLOT_BACK, item(Material.ARROW, color(t("gui-btn-back")),
                List.of(color("&7" + t("gui-btn-back-desc")))));
        inv.setItem(SLOT_ADD, item(Material.LIME_DYE, color(t("gui-channel-add")),
                List.of(
                        color("&7" + t("gui-channel-add-desc-1")),
                        color("&7" + t("gui-channel-add-desc-2")),
                        color(t("gui-separator")),
                        color(t("gui-click-open"))
                )));

        admin.openInventory(inv);
    }

    public void openCreateType(Player admin) {
        ChannelGuiHolder holder = new ChannelGuiHolder(View.CHANNEL_CREATE_TYPE, null);
        Inventory inv = Bukkit.createInventory(holder, 54, color(t("gui-title-channel-create")));
        holder.setInventory(inv);
        fillBorder(inv);

        inv.setItem(4, item(Material.ANVIL, color(t("gui-channel-create-title")),
                List.of(color("&7" + t("gui-channel-create-subtitle")))));

        inv.setItem(20, typeButton(ChannelType.GLOBAL, Material.GRASS_BLOCK));
        inv.setItem(21, typeButton(ChannelType.LOCAL, Material.OAK_LOG));
        inv.setItem(22, typeButton(ChannelType.VIP, Material.NETHER_STAR));
        inv.setItem(23, typeButton(ChannelType.ADMIN, Material.COMMAND_BLOCK));

        inv.setItem(SLOT_BACK, item(Material.ARROW, color(t("gui-btn-back")),
                List.of(color("&7" + t("gui-channel-back-list")))));

        admin.openInventory(inv);
    }

    public void openChannelDetail(Player admin, String channelId) {
        Channel ch = plugin.getChannelManager().getChannel(channelId).orElse(null);
        if (ch == null) {
            openChannelList(admin);
            return;
        }

        ChannelGuiHolder holder = new ChannelGuiHolder(View.CHANNEL_DETAIL, ch.getId());
        Inventory inv = Bukkit.createInventory(holder, 54,
                color(t("gui-title-channel-detail").replace("{channel}", ch.getId())));
        holder.setInventory(inv);
        fillBorder(inv);

        inv.setItem(4, item(Material.NAME_TAG, color(ch.getDisplayName()),
                List.of(
                        color(t("gui-channels-type") + ch.getType().name()),
                        color(ch.isEnabled() ? t("gui-status-enabled") : t("gui-status-disabled"))
                )));

        inv.setItem(19, item(Material.OAK_SIGN, color(t("gui-channel-field-display")),
                fieldLore(stripColor(ch.getDisplayName()), t("gui-desc-channel-display"))));
        inv.setItem(20, item(Material.WRITABLE_BOOK, color(t("gui-channel-field-format")),
                fieldLore(truncate(stripColor(ch.getFormat()), 48), t("gui-desc-channel-format"))));
        inv.setItem(21, toggleItem(Material.LEVER, t("gui-channel-field-enabled"),
                ch.isEnabled(), t("gui-desc-channel-enabled")));

        if (ch.getType() == ChannelType.LOCAL) {
            inv.setItem(22, item(Material.COMPASS, color(t("gui-channel-field-radius")),
                    fieldLore(String.valueOf(ch.getLocalRadius()), t("gui-desc-channel-radius"))));
        }

        if (ch.requiresPermission() || ch.getType() == ChannelType.VIP || ch.getType() == ChannelType.ADMIN) {
            String perm = ch.getRequiredPermission() != null
                    ? ch.getRequiredPermission()
                    : plugin.getChannelManager().defaultPermissionForType(ch.getType());
            inv.setItem(23, item(Material.IRON_BARS, color(t("gui-channel-field-permission")),
                    permissionLore(perm)));
        } else {
            inv.setItem(23, item(Material.OAK_DOOR, color(t("gui-channels-open")),
                    List.of(
                            color("&7" + t("gui-desc-channel-open")),
                            color(t("gui-separator")),
                            color(t("gui-channel-perm-hint-open"))
                    )));
        }

        inv.setItem(31, item(Material.KNOWLEDGE_BOOK, color(t("gui-channel-perm-help")),
                bellLpHelpLore(ch)));

        if (!plugin.getChannelManager().isDefaultChannel(ch.getId())) {
            inv.setItem(40, item(Material.BARRIER, color(t("gui-channel-delete")),
                    List.of(
                            color("&7" + t("gui-desc-channel-delete")),
                            color(t("gui-separator")),
                            color(t("gui-click-toggle"))
                    )));
        }

        inv.setItem(SLOT_BACK, item(Material.ARROW, color(t("gui-btn-back")),
                List.of(color("&7" + t("gui-channel-back-list")))));

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
            case NEW_CHANNEL_ID -> handleNewChannelId(admin, message, pending);
            case DISPLAY_NAME -> handleDisplayName(admin, message, pending);
            case FORMAT -> handleFormat(admin, message, pending);
            case PERMISSION -> handlePermission(admin, message, pending);
            case RADIUS -> handleRadius(admin, message, pending);
        };
    }

    private boolean handleNewChannelId(Player admin, String message, PendingInput pending) {
        String id = message.trim().toLowerCase(Locale.ROOT);
        if (!plugin.getChannelManager().isValidChannelId(id)) {
            plugin.getMessageManager().send(admin, "gui-channel-invalid-id");
            awaitingInput.put(admin.getUniqueId(), pending);
            return true;
        }
        if (plugin.getChannelManager().getChannel(id).isPresent()) {
            plugin.getMessageManager().send(admin, "gui-channel-exists");
            awaitingInput.put(admin.getUniqueId(), pending);
            return true;
        }

        ChannelType type = pending.channelType();
        String display = defaultDisplayName(type, id);
        String format = defaultFormat(type);
        int radius = type == ChannelType.LOCAL ? 100 : -1;
        String perm = plugin.getChannelManager().defaultPermissionForType(type);

        if (!plugin.getChannelManager().createChannel(id, type, display, format, radius, perm)) {
            plugin.getMessageManager().send(admin, "gui-channel-create-failed");
            return true;
        }

        plugin.getMessageManager().send(admin, "gui-channel-created", Map.of("channel", id));
        Bukkit.getScheduler().runTask(plugin, () -> openChannelDetail(admin, id));
        return true;
    }

    private boolean handleDisplayName(Player admin, String message, PendingInput pending) {
        Channel ch = requireChannel(pending.channelId());
        if (ch == null) return true;
        plugin.getChannelManager().updateChannel(ch.getId(), message, ch.getFormat(),
                ch.getLocalRadius(), ch.getRequiredPermission());
        plugin.getMessageManager().send(admin, "gui-channel-saved");
        Bukkit.getScheduler().runTask(plugin, () -> openChannelDetail(admin, ch.getId()));
        return true;
    }

    private boolean handleFormat(Player admin, String message, PendingInput pending) {
        Channel ch = requireChannel(pending.channelId());
        if (ch == null) return true;
        plugin.getChannelManager().updateChannel(ch.getId(), ch.getDisplayName(), message,
                ch.getLocalRadius(), ch.getRequiredPermission());
        plugin.getMessageManager().send(admin, "gui-channel-saved");
        Bukkit.getScheduler().runTask(plugin, () -> openChannelDetail(admin, ch.getId()));
        return true;
    }

    private boolean handlePermission(Player admin, String message, PendingInput pending) {
        Channel ch = requireChannel(pending.channelId());
        if (ch == null) return true;
        String perm = message.trim();
        if (perm.equalsIgnoreCase("-") || perm.equalsIgnoreCase("none")) perm = null;
        plugin.getChannelManager().updateChannel(ch.getId(), ch.getDisplayName(), ch.getFormat(),
                ch.getLocalRadius(), perm);
        plugin.getMessageManager().send(admin, "gui-channel-saved");
        Bukkit.getScheduler().runTask(plugin, () -> openChannelDetail(admin, ch.getId()));
        return true;
    }

    private boolean handleRadius(Player admin, String message, PendingInput pending) {
        Channel ch = requireChannel(pending.channelId());
        if (ch == null) return true;
        int radius;
        try {
            radius = Integer.parseInt(message.trim());
        } catch (NumberFormatException e) {
            plugin.getMessageManager().send(admin, "afk-gui-invalid-number");
            awaitingInput.put(admin.getUniqueId(), pending);
            return true;
        }
        if (radius < 10 || radius > 500) {
            plugin.getMessageManager().send(admin, "gui-channel-invalid-radius");
            awaitingInput.put(admin.getUniqueId(), pending);
            return true;
        }
        plugin.getChannelManager().updateChannel(ch.getId(), ch.getDisplayName(), ch.getFormat(),
                radius, ch.getRequiredPermission());
        plugin.getMessageManager().send(admin, "gui-channel-saved");
        Bukkit.getScheduler().runTask(plugin, () -> openChannelDetail(admin, ch.getId()));
        return true;
    }

    private Channel requireChannel(String id) {
        return plugin.getChannelManager().getChannel(id).orElse(null);
    }

    private void reopenAfterInput(Player admin, PendingInput pending) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (pending.type()) {
                case NEW_CHANNEL_ID -> openCreateType(admin);
                default -> openChannelDetail(admin, pending.channelId());
            }
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
        if (!(event.getInventory().getHolder() instanceof ChannelGuiHolder holder)) return;

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        switch (holder.getView()) {
            case CHANNEL_LIST -> handleChannelList(admin, slot, event.getClick());
            case CHANNEL_DETAIL -> handleChannelDetail(admin, holder.getChannelId(), slot, event.getClick());
            case CHANNEL_CREATE_TYPE -> handleCreateType(admin, slot);
        }
    }

    private void handleChannelList(Player admin, int slot, ClickType click) {
        if (slot == SLOT_BACK) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getAdminGUI().openSettings(admin));
            return;
        }
        if (slot == SLOT_ADD) {
            Bukkit.getScheduler().runTask(plugin, () -> openCreateType(admin));
            return;
        }
        if (slot < 10 || slot > 43) return;

        List<Channel> list = plugin.getChannelManager().getChannelsList();
        Channel ch = channelAtSlot(list, slot);
        if (ch == null) return;

        if (click.isRightClick() && !plugin.getChannelManager().isDefaultChannel(ch.getId())) {
            boolean next = !ch.isEnabled();
            plugin.getChannelManager().setChannelEnabled(ch.getId(), next);
            sendToggle(admin, color(ch.getDisplayName()), next);
            Bukkit.getScheduler().runTask(plugin, () -> openChannelList(admin));
            return;
        }

        String target = ch.getId();
        Bukkit.getScheduler().runTask(plugin, () -> openChannelDetail(admin, target));
    }

    private void handleCreateType(Player admin, int slot) {
        if (slot == SLOT_BACK) {
            Bukkit.getScheduler().runTask(plugin, () -> openChannelList(admin));
            return;
        }
        ChannelType type = switch (slot) {
            case 20 -> ChannelType.GLOBAL;
            case 21 -> ChannelType.LOCAL;
            case 22 -> ChannelType.VIP;
            case 23 -> ChannelType.ADMIN;
            default -> null;
        };
        if (type == null) return;

        admin.closeInventory();
        awaitingInput.put(admin.getUniqueId(),
                new PendingInput(InputType.NEW_CHANNEL_ID, null, type));
        plugin.getMessageManager().send(admin, "gui-channel-prompt-id",
                Map.of("type", type.name()));
    }

    private void handleChannelDetail(Player admin, String channelId, int slot, ClickType click) {
        Channel ch = requireChannel(channelId);
        if (ch == null) return;

        if (slot == SLOT_BACK) {
            Bukkit.getScheduler().runTask(plugin, () -> openChannelList(admin));
            return;
        }
        if (slot == 21 && !plugin.getChannelManager().isDefaultChannel(channelId)) {
            boolean next = !ch.isEnabled();
            plugin.getChannelManager().setChannelEnabled(channelId, next);
            sendToggle(admin, color(ch.getDisplayName()), next);
            Bukkit.getScheduler().runTask(plugin, () -> openChannelDetail(admin, channelId));
            return;
        }
        if (slot == 40 && !plugin.getChannelManager().isDefaultChannel(channelId)) {
            plugin.getChannelManager().deleteChannel(channelId);
            plugin.getMessageManager().send(admin, "gui-channel-deleted", Map.of("channel", channelId));
            Bukkit.getScheduler().runTask(plugin, () -> openChannelList(admin));
            return;
        }

        InputType input = switch (slot) {
            case 19 -> InputType.DISPLAY_NAME;
            case 20 -> InputType.FORMAT;
            case 22 -> ch.getType() == ChannelType.LOCAL ? InputType.RADIUS : null;
            case 23 -> InputType.PERMISSION;
            default -> null;
        };
        if (input == null || !click.isShiftClick()) return;

        admin.closeInventory();
        awaitingInput.put(admin.getUniqueId(), new PendingInput(input, channelId, ch.getType()));
        String promptKey = switch (input) {
            case DISPLAY_NAME -> "gui-channel-prompt-display";
            case FORMAT -> "gui-channel-prompt-format";
            case PERMISSION -> "gui-channel-prompt-permission";
            case RADIUS -> "gui-channel-prompt-radius";
            default -> "gui-channel-prompt-display";
        };
        plugin.getMessageManager().send(admin, promptKey);
    }

    private ItemStack channelSummaryItem(Channel ch) {
        long count = Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.getChannelManager().getPlayerChannel(p).getId().equals(ch.getId()))
                .count();

        List<String> lore = new ArrayList<>();
        lore.add(color(t("gui-channels-type") + ch.getType().name()));
        lore.add(color(t("gui-channels-players") + count));
        if (ch.requiresPermission()) {
            lore.add(color(t("gui-channels-permission") + ch.getRequiredPermission()));
        } else {
            lore.add(color(t("gui-channels-open")));
        }
        lore.add(color(ch.isEnabled() ? t("gui-status-enabled") : t("gui-status-disabled")));
        lore.add(color(t("gui-separator")));
        lore.add(color(t("gui-channel-click-edit")));
        if (!plugin.getChannelManager().isDefaultChannel(ch.getId())) {
            lore.add(color(t("gui-channel-right-toggle")));
        } else {
            lore.add(color(t("gui-channels-global-locked")));
        }

        Material mat = switch (ch.getType()) {
            case GLOBAL -> Material.GRASS_BLOCK;
            case LOCAL -> Material.OAK_LOG;
            case VIP -> Material.NETHER_STAR;
            case ADMIN -> Material.COMMAND_BLOCK;
            case PARTY -> Material.BLUE_BANNER;
        };

        return item(mat, color(ch.getDisplayName()) + " §8[§7" + ch.getId() + "§8]", lore);
    }

    private ItemStack typeButton(ChannelType type, Material mat) {
        return item(mat, color("&f" + type.name()),
                List.of(
                        color("&7" + t("gui-channel-type-" + type.name().toLowerCase(Locale.ROOT))),
                        color(t("gui-separator")),
                        color(t("gui-click-open"))
                ));
    }

    private List<String> fieldLore(String value, String desc) {
        List<String> lore = new ArrayList<>();
        lore.add(color("&f" + value));
        lore.add(color(t("gui-separator")));
        lore.add(color("&7" + desc));
        lore.add(color(t("gui-separator")));
        lore.add(color(t("gui-shift-click-edit")));
        return lore;
    }

    private List<String> permissionLore(String perm) {
        List<String> lore = new ArrayList<>();
        lore.add(color(t("gui-channels-permission") + perm));
        lore.add(color(t("gui-separator")));
        lore.add(color("&7" + t("gui-desc-channel-permission")));
        lore.add(color(t("gui-separator")));
        lore.add(color(t("gui-shift-click-edit")));
        return lore;
    }

    private List<String> bellLpHelpLore(Channel ch) {
        String perm = ch.getRequiredPermission();
        if (perm == null || perm.isBlank()) {
            perm = plugin.getChannelManager().defaultPermissionForType(ch.getType());
        }
        boolean bellLp = Bukkit.getPluginManager().getPlugin("BellLP") != null;
        List<String> lore = new ArrayList<>();
        lore.add(color("&7" + t("gui-channel-perm-where-title")));
        lore.add(color(t("gui-separator")));
        if (perm != null && !perm.isBlank()) {
            lore.add(color("&7" + t("gui-channel-perm-node") + perm));
            lore.add(color(t("gui-separator")));
            if (bellLp) {
                lore.add(color("&7" + t("gui-channel-perm-belllp-1")));
                lore.add(color("&f/belllp admin &7→ " + t("gui-channel-perm-belllp-2")));
                lore.add(color("&7" + t("gui-channel-perm-belllp-3")));
            }
            lore.add(color("&7" + t("gui-channel-perm-lp-1")));
            lore.add(color("&f/lp group vip permission set " + perm + " true"));
        } else {
            lore.add(color("&7" + t("gui-desc-channel-open")));
        }
        return lore;
    }

    private Channel channelAtSlot(List<Channel> list, int slot) {
        int s = 10;
        for (Channel ch : list) {
            if (s == 17 || s == 26 || s == 35) s++;
            if (s == slot) return ch;
            s++;
        }
        return null;
    }

    private String defaultDisplayName(ChannelType type, String id) {
        return switch (type) {
            case GLOBAL -> "&aGlobal";
            case LOCAL -> "&eLocal";
            case VIP -> "&5VIP";
            case ADMIN -> "&cAdmin";
            default -> "&f" + id;
        };
    }

    private String defaultFormat(ChannelType type) {
        return switch (type) {
            case LOCAL -> "&8[&eL&8] &f{player}&f: {message}";
            case VIP -> "&8[&5VIP&8] &5{prefix}&5{player}&5:&f {message}";
            case ADMIN -> "&8[&cADM&8] &6{prefix}&6{player}&6:&f {message}";
            default -> "&f{player}&f: {message}";
        };
    }

    private ItemStack toggleItem(Material mat, String name, boolean enabled, String desc) {
        List<String> lore = new ArrayList<>();
        lore.add(color(enabled ? t("gui-status-enabled") : t("gui-status-disabled")));
        lore.add(color(t("gui-separator")));
        lore.add(color("&7" + desc));
        if (!name.equals(t("gui-channel-field-enabled"))) {
            lore.add(color(t("gui-separator")));
            lore.add(color(t("gui-click-toggle")));
        } else {
            lore.add(color(t("gui-separator")));
            lore.add(color(t("gui-click-toggle")));
        }
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

    private String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    private String stripColor(String s) {
        return s == null ? "" : s.replaceAll("§.", "").replaceAll("&.", "");
    }

    private String t(String key) {
        return plugin.getMessageManager().getRaw(key);
    }

    private String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }
}
