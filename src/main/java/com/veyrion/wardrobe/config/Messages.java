package com.veyrion.wardrobe.config;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The texts players and admins see, from the {@code messages} section of config.yml (MiniMessage format).
 * A missing key uses the built-in default, so an old config.yml keeps working. Values that come from players
 * (names) are inserted as plain text by the sender, never parsed, so a name cannot inject formatting.
 */
public final class Messages {

    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        // mirror use
        DEFAULTS.put("mirror-not-registered", "<red>Cermin ini belum terdaftar, jadi tidak bisa dipakai. Hubungi admin.");
        DEFAULTS.put("mirror-no-access", "<red>Cermin ini milik <player>. Kamu belum diberi izin (trust) untuk memakainya.");
        DEFAULTS.put("mirror-too-far", "<red>Kamu terlalu jauh dari cermin.");
        DEFAULTS.put("mirror-access-lost", "<red>Izinmu pada cermin ini sudah dicabut.");
        DEFAULTS.put("mirror-gone", "<red>Cermin ini sudah tidak ada.");
        DEFAULTS.put("mirror-break-denied", "<red>Hanya pemilik (<player>) yang bisa membongkar cermin ini.");
        DEFAULTS.put("mirror-placed", "<green>Aether Mirror terpasang. Kamu pemiliknya. Pakai <white>/wardrobe trust \\<player><green> untuk memberi izin.");
        DEFAULTS.put("mirror-place-failed", "<red>Cermin gagal disimpan, jadi dikembalikan ke inventorimu. Beri tahu admin.");
        DEFAULTS.put("wardrobe-loading", "<red>Wardrobe-mu masih dimuat. Coba lagi sebentar.");
        // menu
        DEFAULTS.put("menu-title", "<dark_gray>Wardrobe");
        DEFAULTS.put("menu-empty", "<gray>Kamu belum punya outfit.");
        // trust
        DEFAULTS.put("no-mirror-near", "<red>Tidak ada Aether Mirror milikmu dalam jarak <range> blok. Berdirilah dekat cermin milikmu.");
        DEFAULTS.put("player-not-found", "<red>Pemain <player> tidak ditemukan. Pemain harus pernah bermain di server ini.");
        DEFAULTS.put("trust-added", "<green><player> sekarang boleh memakai cermin ini.");
        DEFAULTS.put("trust-already", "<yellow><player> sudah punya izin di cermin ini.");
        DEFAULTS.put("trust-self", "<red>Kamu tidak bisa memberi izin kepada dirimu sendiri.");
        DEFAULTS.put("trust-error", "<red>Izin gagal disimpan. Coba lagi atau beri tahu admin.");
        DEFAULTS.put("untrust-removed", "<green>Izin <player> pada cermin ini dicabut.");
        DEFAULTS.put("untrust-not-trusted", "<yellow><player> tidak punya izin di cermin ini.");
        DEFAULTS.put("trustlist-header", "<gold>Pemain dengan izin di cermin ini (<count>):");
        DEFAULTS.put("trustlist-entry", "<gray> - <white><player>");
        DEFAULTS.put("trustlist-empty", "<yellow>Belum ada pemain yang diberi izin di cermin ini.");
        // admin
        DEFAULTS.put("no-permission", "<red>Kamu tidak punya izin untuk command ini.");
        DEFAULTS.put("players-only", "Command ini hanya bisa dipakai pemain di dalam game.");
        DEFAULTS.put("usage", "<yellow>Pemakaian: <usage>");
        DEFAULTS.put("give-done", "<green>Aether Mirror diberikan kepada <player>.");
        DEFAULTS.put("give-received", "<green>Kamu menerima sebuah Aether Mirror.");
        DEFAULTS.put("give-item-missing", "<red>Item <id> tidak ada di ItemsAdder. Pasang pack-nya dan jalankan /iazip, atau periksa mirror.item-id di config.yml.");
        DEFAULTS.put("remove-none", "<red>Tidak ada Aether Mirror terdaftar dalam jarak <range> blok.");
        DEFAULTS.put("remove-done", "<green>Aether Mirror milik <player> dihapus (tanpa drop).");
        DEFAULTS.put("remove-record-only", "<yellow>Furniture-nya tidak ditemukan; hanya catatan database milik <player> yang dihapus.");
        DEFAULTS.put("reload-done", "<green>Konfigurasi dimuat ulang. (Jumlah worker skin dan batas ukuran gambar baru berlaku setelah restart.)");
        DEFAULTS.put("list-header", "<gold>Aether Mirror terpasang: <count> (halaman <page>/<pages>)");
        DEFAULTS.put("list-entry", "<gray><index>. <white><player> <gray>@ <world> <x> <y> <z> | trust: <trusted> | <state> | <id>");
        DEFAULTS.put("list-empty", "<yellow>Belum ada Aether Mirror terpasang.");
    }

    private final Map<String, String> values = new HashMap<>(DEFAULTS);

    private Messages() {}

    public static Messages defaults() {
        return new Messages();
    }

    public static Messages from(ConfigurationSection config) {
        Messages messages = new Messages();
        if (config != null) {
            for (String key : DEFAULTS.keySet()) {
                String value = config.getString("messages." + key, null);
                if (value != null && !value.isBlank()) {
                    messages.values.put(key, value);
                }
            }
        }
        return messages;
    }

    /** The MiniMessage text for a key (never null; an unknown key returns the key itself so the gap is visible). */
    public String get(String key) {
        return values.getOrDefault(key, key);
    }

    public static java.util.Set<String> keys() {
        return java.util.Set.copyOf(DEFAULTS.keySet());
    }
}
