package me.f0reach.jobs.util;

import net.kyori.adventure.text.minimessage.MiniMessage;

public final class MiniMessages {
    private static final MiniMessage INSTANCE = MiniMessage.miniMessage();

    private MiniMessages() {}

    public static MiniMessage get() {
        return INSTANCE;
    }
}
