package pl.bell.bellchat.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import pl.bell.bellchat.BellChat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

/** Registers BellChat with BellLP group sync hub. */
public final class BellLPIntegration {

    private static boolean registered;

    private BellLPIntegration() {}

    public static void tryRegister(BellChat plugin) {
        if (registered) return;

        Plugin bellLP = Bukkit.getPluginManager().getPlugin("BellLP");
        if (bellLP == null || !bellLP.isEnabled()) return;

        try {
            ClassLoader loader = bellLP.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("pl.bell.belllp.api.BellLPAPI", true, loader);
            Class<?> handlerClass = Class.forName("pl.bell.belllp.api.GroupSyncHandler", true, loader);
            Object api = apiClass.getMethod("get").invoke(null);

            Object handler = Proxy.newProxyInstance(loader, new Class<?>[]{handlerClass},
                    new ChatHandler(plugin));

            apiClass.getMethod("registerSyncHandler", handlerClass).invoke(api, handler);
            registered = true;
            plugin.getLogger().info("BellLP ecosystem sync registered.");
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("BellLP hook unavailable: " + ex.getMessage());
        }
    }

    private static final class ChatHandler implements InvocationHandler {
        private final BellChat plugin;

        private ChatHandler(BellChat plugin) {
            this.plugin = plugin;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "onGroupSynced", "onAllGroupsSynced" -> plugin.reload();
                case "refreshPlayer" -> {
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
