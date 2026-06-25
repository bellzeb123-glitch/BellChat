package pl.bell.bellchat.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import pl.bell.bellchat.BellChat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Registers BellChat with BellLP group sync hub (bidirectional AFK sync). */
public final class BellLPIntegration {

    private static boolean registered;
    private static final AtomicBoolean importSuppressed = new AtomicBoolean(false);
    private static volatile Object cachedApi;
    private static volatile Method importAfkMethod;
    private static volatile Method importAfkClearedMethod;

    private BellLPIntegration() {}

    /** Called by BellLP BellChatAdapter while pushing config into BellChat. */
    public static void setImportSuppressed(boolean suppressed) {
        importSuppressed.set(suppressed);
    }

    public static void tryRegister(BellChat plugin) {
        if (registered) return;

        Plugin bellLP = Bukkit.getPluginManager().getPlugin("BellLP");
        if (bellLP == null || !bellLP.isEnabled()) return;

        try {
            ClassLoader loader = bellLP.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("pl.bell.belllp.api.BellLPAPI", true, loader);
            Class<?> handlerClass = Class.forName("pl.bell.belllp.api.GroupSyncHandler", true, loader);
            Object api = resolveApi(apiClass);
            if (api == null) return;

            cachedApi = api;
            importAfkMethod = apiClass.getMethod("importBellChatAfk",
                    String.class, int.class, boolean.class, int.class);
            importAfkClearedMethod = apiClass.getMethod("importBellChatAfkCleared", String.class);

            Object handler = Proxy.newProxyInstance(loader, new Class<?>[]{handlerClass},
                    new ChatHandler(plugin));

            apiClass.getMethod("registerSyncHandler", handlerClass).invoke(api, handler);
            registered = true;
            plugin.getLogger().info("BellLP ecosystem sync registered (bidirectional AFK).");
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("BellLP hook unavailable: " + ex.getMessage());
        }
    }

    public static void pushAfkToBellLP(String group, int autoAfkSeconds, boolean kickEnabled, int kickSeconds) {
        if (importSuppressed.get()) return;
        if (group == null || group.isBlank()) return;

        try {
            Object api = cachedApi;
            Method method = importAfkMethod;
            if (api == null || method == null) {
                tryRegister(BellChat.getInstance());
                api = cachedApi;
                method = importAfkMethod;
            }
            if (api == null || method == null) return;
            method.invoke(api, group.trim().toLowerCase(java.util.Locale.ROOT),
                    autoAfkSeconds, kickEnabled, kickSeconds);
        } catch (ReflectiveOperationException ex) {
            BellChat plugin = BellChat.getInstance();
            if (plugin != null) {
                plugin.getLogger().fine("BellLP AFK import skipped: " + ex.getMessage());
            }
        }
    }

    public static void pushAfkClearedToBellLP(String group) {
        if (importSuppressed.get()) return;
        if (group == null || group.isBlank()) return;

        try {
            Object api = cachedApi;
            Method method = importAfkClearedMethod;
            if (api == null || method == null) {
                tryRegister(BellChat.getInstance());
                api = cachedApi;
                method = importAfkClearedMethod;
            }
            if (api == null || method == null) return;
            method.invoke(api, group.trim().toLowerCase(java.util.Locale.ROOT));
        } catch (ReflectiveOperationException ex) {
            BellChat plugin = BellChat.getInstance();
            if (plugin != null) {
                plugin.getLogger().fine("BellLP AFK clear import skipped: " + ex.getMessage());
            }
        }
    }

    private static Object resolveApi(Class<?> apiClass) throws ReflectiveOperationException {
        try {
            return apiClass.getMethod("getOptional").invoke(null);
        } catch (NoSuchMethodException ignored) {
            try {
                return apiClass.getMethod("get").invoke(null);
            } catch (InvocationTargetException ex) {
                if (ex.getCause() instanceof IllegalStateException) {
                    return null;
                }
                throw ex;
            }
        }
    }

    private static final class ChatHandler implements InvocationHandler {
        private final BellChat plugin;

        private ChatHandler(BellChat plugin) {
            this.plugin = plugin;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "BellChat/BellLP@" + Integer.toHexString(System.identityHashCode(proxy));
                    default -> null;
                };
            }
            switch (method.getName()) {
                case "onGroupSynced", "onAllGroupsSynced" -> {
                    if (plugin.getAfkConfigManager() != null) {
                        plugin.getAfkConfigManager().invalidateAllCaches();
                    }
                    if (plugin.getTablistListener() != null) {
                        Bukkit.getOnlinePlayers().forEach(p ->
                                plugin.getTablistListener().updateTablist(p));
                    }
                }
                case "refreshPlayer" -> {
                    UUID uuid = (UUID) args[0];
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && plugin.getTablistListener() != null) {
                        plugin.getTablistListener().updateTablist(player);
                    }
                }
                case "onVipGranted", "onVipRevoked" -> {
                    UUID uuid = (UUID) args[0];
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && plugin.getTablistListener() != null) {
                        plugin.getTablistListener().updateTablist(player);
                    }
                }
                default -> { }
            }
            return null;
        }
    }
}
