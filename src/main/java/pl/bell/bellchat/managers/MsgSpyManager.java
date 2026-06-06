package pl.bell.bellchat.managers;

import pl.bell.bellchat.BellChat;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Handles private message spying and logging.
 *
 * Spy mode  — admins with bellchat.spy toggle it with /msgspy
 *             and see all PMs in real time in their chat.
 * File log  — every PM is appended to logs/private-messages.log
 */
public class MsgSpyManager {

    private final BellChat plugin;
    private final Set<UUID> spyingAdmins = new HashSet<>();
    private File logFile;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

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

    // ── Spy mode ──────────────────────────────────────────────

    public boolean toggleSpy(UUID adminUUID) {
        if (spyingAdmins.contains(adminUUID)) {
            spyingAdmins.remove(adminUUID);
            return false;
        } else {
            spyingAdmins.add(adminUUID);
            return true;
        }
    }

    public boolean isSpy(UUID uuid) {
        return spyingAdmins.contains(uuid);
    }

    // ── Notify + Log ──────────────────────────────────────────

    /**
     * Called after every private message is sent.
     * Notifies all online spying admins and appends to log file.
     */
    public void handle(String senderName, String receiverName, String message) {
        String spyFormat = plugin.getMessageManager().get("msg-spy-format")
                .replace("{sender}", senderName)
                .replace("{receiver}", receiverName)
                .replace("{message}", message);

        // Notify all online admins with spy mode active
        for (UUID uuid : spyingAdmins) {
            var player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(spyFormat);
            }
        }

        // Always log to file
        logToFile(senderName, receiverName, message);
    }

    private void logToFile(String sender, String receiver, String message) {
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            String line = "[" + dateFormat.format(new Date()) + "] "
                    + sender + " -> " + receiver + ": " + message;
            bw.write(line);
            bw.newLine();
        } catch (IOException e) {
            plugin.getLogger().warning("Cannot write to private-messages.log: " + e.getMessage());
        }
    }

    public void clearSpy(UUID uuid) {
        spyingAdmins.remove(uuid);
    }
}
