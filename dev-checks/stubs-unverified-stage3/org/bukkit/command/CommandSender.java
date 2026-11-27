package org.bukkit.command;
public interface CommandSender { boolean hasPermission(String p); void sendMessage(net.kyori.adventure.text.Component c); }
