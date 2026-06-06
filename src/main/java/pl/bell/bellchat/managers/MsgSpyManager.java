package pl.bell.bellchat.managers;

import pl.bell.bellchat.BellChat;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Private message spy — always ON by default for admins with bellchat.spy.
 * Can be toggled off per-admin with /msgspy.
 */
public class MsgSpyManager {

    private final BellChat plugin;
    private File logFile;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Admins who explicitly DISABLED spy — everyone else with bellchat.spy sees PMs
    private final Set<UUID> spyDisabled = new HashSet<>();

    public MsgSpyManager(BellChat plugin) {
        this.plugin = plugin;
        initLogFile();
    }

    private void initLogFile() {
        File logsDir = new File(plugin.getDataFolder(), "logs");
        logsDir.mkdirs();
        logFile = new File(logsDir, "private-messages.log");
        try {
            if (!logFile.exists()) logFile.createNewFile();
        } catch (IOException e) {
            plugin.getLogger().severe("Cannot create private-messages.log: " + e.getMessage());
        }
    }

    /**
     * Toggle spy for a specific admin.
     * Returns true if spy is now ENABLED, false if DISABLED.
     */
    public boolean toggle(UUID adminUUID) {
        if (spyDisabled.contains(adminUUID)) {
            spyDisabled.remove(adminUUID);
            return true; // now enabled
        } else {
            spyDisabled.add(adminUUID);
            return false; // now disabled
        }
    }

    public boolean isSpy(UUID uuid) {
        return !spyDisabled.contains(uuid);
    }

    /**
     * Called after every private message.
     * Sends to all online admins with bellchat.spy who haven't disabled it.
     */
    public void handle(String senderName, String receiverName, String message) {
        String spyFormat = plugin.getMessageManager().get("msg-spy-format")
                .replace("{sender}", senderName)
                .replace("{receiver}", receiverName)
                .replace("{message}", message);

        for (var player : plugin.getServer().getOnlinePlayers()) {
            if (!player.hasPermission("bellchat.spy")) continue;
            if (spyDisabled.contains(player.getUniqueId())) continue;
            if (player.getName().equals(senderName)) continue;
            if (player.getName().equals(receiverName)) continue;
            player.sendMessage(spyFormat);
        }

        logToFile(senderName, receiverName, message);
    }

    private void logToFile(String sender, String receiver, String message) {
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write("[" + dateFormat.format(new Date()) + "] "
                    + sender + " -> " + receiver + ": " + message);
            bw.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot write to private-messages.log: " + e.getMessage());
        }
    }
}
