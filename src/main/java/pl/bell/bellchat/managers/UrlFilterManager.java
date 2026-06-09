package pl.bell.bellchat.managers;

import pl.bell.bellchat.BellChat;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * UrlFilterManager — blokuje lub zamienia linki w chacie.
 *
 * Konfiguracja w config.yml sekcja url-filter:
 *   url-filter:
 *     enabled: true
 *     mode: block        # block = blokuje wiadomość | replace = zamienia URL na [link]
 *     replacement: "[link]"
 *     whitelist:
 *       - "twojserwer.pl"
 *       - "discord.gg/twojlink"
 *
 * Uprawnienia:
 *   bellchat.url.bypass — omija filtr (default: op)
 */
public class UrlFilterManager {

    // Regex łapie typowe URL: http(s)://, www., oraz domeny bez protokołu (np. serwer.pl/cos)
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b(https?://|www\\.)[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]" +
            "|\\b[a-zA-Z0-9][-a-zA-Z0-9]*\\.[a-zA-Z]{2,}(/\\S*)?\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final BellChat plugin;
    private final Logger log;

    private boolean enabled    = false;
    private String mode        = "block";   // "block" lub "replace"
    private String replacement = "&8[&clink&8]";
    private List<String> whitelist = new ArrayList<>();

    public UrlFilterManager(BellChat plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
        load();
    }

    // ── Load ──────────────────────────────────────────────────

    public void load() {
        var cfg = plugin.getConfig();
        this.enabled     = cfg.getBoolean("url-filter.enabled", false);
        this.mode        = cfg.getString("url-filter.mode", "block");
        this.replacement = cfg.getString("url-filter.replacement", "&8[&clink&8]");
        this.whitelist   = cfg.getStringList("url-filter.whitelist");

        if (enabled) {
            log.info("[UrlFilter] Aktywny — tryb: " + mode
                    + ", whitelist: " + whitelist.size() + " wpisów.");
        }
    }

    public void reload() { load(); }

    // ── Przetwarzanie ─────────────────────────────────────────

    /**
     * Wynik filtrowania URL.
     * blocked=true → wiadomość powinna być odrzucona (tryb block).
     * message → przetworzona wiadomość (tryb replace lub oryginał).
     */
    public record Result(boolean blocked, String message) {}

    /**
     * Przetwarza wiadomość przez filtr URL.
     * Jeśli disabled lub gracz ma bypass → zwraca oryginał, blocked=false.
     */
    public Result process(String message) {
        if (!enabled) return new Result(false, message);

        var matcher = URL_PATTERN.matcher(message);
        if (!matcher.find()) return new Result(false, message);

        // Sprawdź czy URL jest na whiteliście
        // Reset matchera
        matcher.reset();
        while (matcher.find()) {
            String url = matcher.group().toLowerCase();
            if (isWhitelisted(url)) continue;

            // Znaleziono URL nie na whiteliście
            if ("block".equalsIgnoreCase(mode)) {
                return new Result(true, message);
            }
        }

        // Tryb replace — zamień wszystkie nie-whitelistowane URL-e
        if ("replace".equalsIgnoreCase(mode)) {
            String processed = replaceUrls(message);
            return new Result(false, processed);
        }

        return new Result(false, message);
    }

    private boolean isWhitelisted(String url) {
        for (String entry : whitelist) {
            if (url.contains(entry.toLowerCase())) return true;
        }
        return false;
    }

    private String replaceUrls(String message) {
        var matcher = URL_PATTERN.matcher(message);
        var sb = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group().toLowerCase();
            if (isWhitelisted(url)) {
                matcher.appendReplacement(sb, matcher.group()); // zachowaj
            } else {
                String rep = replacement.replace("&", "§");
                matcher.appendReplacement(sb, rep);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public boolean isEnabled() { return enabled; }
}
