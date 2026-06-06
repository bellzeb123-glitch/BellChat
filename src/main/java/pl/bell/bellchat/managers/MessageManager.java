package pl.bell.bellchat.managers;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.bell.bellchat.BellChat;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MessageManager {

    private final BellChat plugin;
    private FileConfiguration messages;
    private String prefix;

    public MessageManager(BellChat plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String lang = plugin.getConfig().getString("language", "en").toLowerCase();
        messages = loadLanguage(lang);
        prefix   = color(messages.getString("prefix", "&8[&6BellChat&8] "));
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(prefix + get(key));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String msg = get(key);
        for (var e : placeholders.entrySet()) msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        sender.sendMessage(prefix + msg);
    }

    public String get(String key) {
        String val = messages.getString(key);
        if (val == null) return color("&cMissing message: " + key);
        return color(val);
    }

    public String getPrefix() { return prefix; }

    public String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private FileConfiguration loadLanguage(String lang) {
        String fileName = "messages_" + lang + ".yml";
        File external = new File(plugin.getDataFolder(), fileName);
        if (external.exists()) return YamlConfiguration.loadConfiguration(external);

        InputStream stream = plugin.getResource(fileName);
        if (stream != null) {
            plugin.saveResource(fileName, false);
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        plugin.getLogger().warning("Language '" + lang + "' not found. Falling back to English.");
        InputStream en = plugin.getResource("messages_en.yml");
        if (en != null) {
            plugin.saveResource("messages_en.yml", false);
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(en, StandardCharsets.UTF_8));
        }
        return new YamlConfiguration();
    }
}
