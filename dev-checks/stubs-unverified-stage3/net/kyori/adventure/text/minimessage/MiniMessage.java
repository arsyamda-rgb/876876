package net.kyori.adventure.text.minimessage;
public interface MiniMessage { static MiniMessage miniMessage(){return null;} net.kyori.adventure.text.Component deserialize(String s, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... r); }
