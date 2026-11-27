package com.veyrion.wardrobe.mirror;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/** MiniMessage helper. Placeholder values are inserted as plain text: a player name can never add formatting. */
final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private Text() {}

    /** @param pairs key, value, key, value ... (a value replaces {@code <key>} in the text) */
    static Component render(String miniMessage, String... pairs) {
        TagResolver[] resolvers = new TagResolver[pairs.length / 2];
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            resolvers[i / 2] = Placeholder.unparsed(pairs[i], pairs[i + 1]);
        }
        return MINI.deserialize(miniMessage, resolvers);
    }

    static void send(CommandSender to, String miniMessage, String... pairs) {
        to.sendMessage(render(miniMessage, pairs));
    }
}
