package net.andylizi.haproxydetector.bukkit;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * InjectionStrategy1 for ProtocolLib versions that supported internal netty injection API.
 * This strategy is no longer supported in ProtocolLib 5.x+ and will throw an exception.
 * Use InjectionStrategy2 instead for newer ProtocolLib versions.
 */
public class InjectionStrategy1 implements InjectionStrategy {
    private final Logger logger;

    public InjectionStrategy1(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void inject() throws ReflectiveOperationException {
        throw new UnsupportedOperationException(
            "InjectionStrategy1 is not supported in ProtocolLib 5.x+. " +
            "The required internal API classes have been removed. " +
            "Please use InjectionStrategy2 instead."
        );
    }

    @Override
    public void uninject() throws ReflectiveOperationException {
        // No-op since inject() would have failed
    }
}