package net.andylizi.haproxydetector.bukkit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.comphenix.protocol.ProtocolLibrary;

import com.comphenix.protocol.utility.MinecraftReflection;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import net.andylizi.haproxydetector.MetricsId;
import net.andylizi.haproxydetector.ProxyWhitelist;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;


import static net.andylizi.haproxydetector.ReflectionUtil.sneakyThrow;

public final class BukkitMain extends JavaPlugin {
    static Logger logger;

    private InjectionStrategy injectionStrategy;

    @Override
    public void onLoad() {
        logger = getLogger();
    }

    @Override
    public void onEnable() {
        try {
            Path path = this.getDataFolder().toPath().resolve("whitelist.conf");
            ProxyWhitelist whitelist = ProxyWhitelist.loadOrDefault(path).orElse(null);
            if (whitelist == null) {
                logger.warning("!!! ==============================");
                logger.warning("!!! 代理白名单已在配置中禁用。");
                logger.warning("!!! 这非常危险，请勿在生产环境中这样做！");
                logger.warning("!!! ==============================");
            } else if (whitelist.size() == 0) {
                logger.warning("代理白名单为空。这将拒绝所有代理连接！");
            }
            ProxyWhitelist.whitelist = whitelist;
        } catch (IOException e) {
            throw new RuntimeException("加载代理白名单失败", e);
        }

        if (!ProtocolLibrary.getPlugin().isEnabled()) {
            logger.severe("缺少必要依赖 ProtocolLib，插件即将禁用");
            this.setEnabled(false);
            return;
        }
        try {
            injectionStrategy = createInjectionStrategy();
            injectionStrategy.inject();
        } catch (ReflectiveOperationException e) {
            sneakyThrow(e);
        }

        try {
            Metrics metrics = new Metrics(this, 12604);
            metrics.addCustomChart(MetricsId.createWhitelistCountChart());
            metrics.addCustomChart(new SimplePie(MetricsId.KEY_PROTOCOLLIB_VERSION,
                    () -> ProtocolLibrary.getPlugin().getDescription().getVersion()));
        } catch (Throwable t) {
            logger.log(Level.WARNING, "启动统计上报失败", t);
        }
    }

    private static InjectionStrategy createInjectionStrategy() throws ReflectiveOperationException {
        return new InjectionStrategy(logger);
    }

    @Override
    public void onDisable() {
        if (injectionStrategy != null) {
            try {
                injectionStrategy.uninject();
            } catch (Throwable ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    static ChannelHandler getNetworkManager(ChannelPipeline pipeline) {
        Class<? extends ChannelHandler> networkManagerClass = (Class<? extends ChannelHandler>) MinecraftReflection.getNetworkManagerClass();
        ChannelHandler networkManager = null;
        for (Map.Entry<String, ChannelHandler> entry : pipeline) {
            if (networkManagerClass.isAssignableFrom(entry.getValue().getClass())) {
                networkManager = entry.getValue();
                break;
            }
        }

        if (networkManager == null) {
            throw new IllegalArgumentException("NetworkManager not found in channel pipeline " + pipeline.names());
        }

        return networkManager;
    }
}
