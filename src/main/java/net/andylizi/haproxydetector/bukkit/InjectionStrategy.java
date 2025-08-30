package net.andylizi.haproxydetector.bukkit;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.reflect.FuzzyReflection;
import io.netty.channel.*;
import net.andylizi.haproxydetector.HAProxyDetectorHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bukkit 注入器：面向 ProtocolLib 5.x+ 的唯一实现。
 * 负责在 Netty 管线中注入 HAProxy 检测器，并在需要时回滚。
 */
public class InjectionStrategy {
    private final Logger logger;

    private Field handlerField;
    private ChannelInboundHandler injectorInitializer;
    private ChannelInboundHandler originalHandler;

    public InjectionStrategy(Logger logger) {this.logger = logger;}

    public void inject() throws ReflectiveOperationException {
        try {
            this.uninject();
        } catch (Throwable ignored) {
        }

        Class<?> networkManagerInjectorClass = Class.forName(
                "com.comphenix.protocol.injector.netty.manager.NetworkManagerInjector");
        Class<?> injectionChannelInitializerClass = Class.forName(
                "com.comphenix.protocol.injector.netty.manager.InjectionChannelInitializer");

        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        Field injectorField = FuzzyReflection.fromObject(pm, true)
                .getFieldByType("networkManagerInjector", networkManagerInjectorClass);
        injectorField.setAccessible(true);
        Object networkManagerInjector = injectorField.get(pm);

        Field injectorInitializerField = FuzzyReflection.fromClass(networkManagerInjectorClass, true)
                .getFieldByType("pipelineInjectorHandler", injectionChannelInitializerClass);
        injectorInitializerField.setAccessible(true);
        this.injectorInitializer = (ChannelInboundHandler) injectorInitializerField.get(networkManagerInjector);

        this.handlerField = FuzzyReflection.fromClass(injectionChannelInitializerClass, true)
                .getFieldByType("handler", ChannelInboundHandler.class);
        handlerField.setAccessible(true);
        this.originalHandler = (ChannelInboundHandler) handlerField.get(injectorInitializer);

        ChannelInboundHandler myHandler = (ChannelInboundHandler) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[] { ChannelInboundHandler.class },
                (proxy, method, args) -> {
                    if ("channelActive".equals(method.getName())) {
                        ChannelHandlerContext ctx = (ChannelHandlerContext) args[0];
                        // the original method will attempt to remove itself when `channelActive` is called,
                        // so we have to put it back into the pipeline temporarily.
                        //
                        // one important thing to note is that the original handler must be added BEFORE our
                        // current handler. otherwise `channelActive` will be called on it twice.
                        //
                        // the name doesn't really matter here.
                        ctx.pipeline().remove((ChannelHandler) proxy)
                                .addFirst("protocol_lib_inbound_inject", originalHandler);

                        Object ret = method.invoke(originalHandler, args);
                        doInject(ctx.channel());
                        return ret;
                    } else {
                        return method.invoke(originalHandler, args);
                    }
                });
        handlerField.set(injectorInitializer, myHandler);
    }

    public void uninject() throws ReflectiveOperationException {
        if (this.handlerField != null && this.injectorInitializer != null && this.originalHandler != null) {
            handlerField.set(injectorInitializer, this.originalHandler);
            this.injectorInitializer = null;
            this.originalHandler = null;
        }
    }

    void doInject(Channel ch) {
        // this is similar to how ProtocolLib does it.
        if (ch.eventLoop().inEventLoop()) {
            try {
                ChannelPipeline pipeline = ch.pipeline();
                if (!ch.isOpen() || pipeline.get("haproxy-detector") != null)
                    return;

                if (pipeline.get("haproxy-decoder") != null) {
                    // remove pre-existing HAProxy decoder
                    pipeline.remove("haproxy-decoder");
                }

                ChannelHandler haproxyHandler;
                if (pipeline.get("haproxy-handler") != null) {
                    // just use pre-existing handler (Paper)
                    haproxyHandler = pipeline.remove("haproxy-handler");
                } else {
                    ChannelHandler networkManager = BukkitMain.getNetworkManager(pipeline);
                    haproxyHandler = new HAProxyMessageHandler(networkManager);
                }

                HAProxyDetectorHandler detector = new HAProxyDetectorHandler(logger, haproxyHandler);
                try {
                    pipeline.addAfter("timeout", "haproxy-detector", detector);
                } catch (NoSuchElementException e) {
                    pipeline.addFirst("haproxy-detector", detector);
                }
            } catch (Throwable t) { // 防止 Netty 吞掉异常
                if (logger != null)
                    logger.log(Level.WARNING, "注入代理检测器时发生异常", t);
                else
                    t.printStackTrace();
            }
        } else {
            ch.eventLoop().execute(() -> this.doInject(ch));
        }
    }
}
