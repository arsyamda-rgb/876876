package com.veyrion.wardrobe;

import com.veyrion.wardrobe.config.Messages;
import com.veyrion.wardrobe.mirror.Mirror;
import com.veyrion.wardrobe.mirror.MirrorRegistry;
import com.veyrion.wardrobe.mirror.MirrorRegistry.TrustStatus;
import com.veyrion.wardrobe.mirror.MirrorRegistry.UntrustStatus;
import com.veyrion.wardrobe.mirror.MirrorStore;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Stage 3 rules without Bukkit: MirrorRegistry against an in-memory store. Run: see dev-checks/README.txt. */
public final class MirrorCheck {

    static int checks, failures;

    static void eq(String what, Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual)) {
            failures++;
            System.out.println("FAIL " + what + ": expected " + expected + " but was " + actual);
        }
    }

    /** What the database does, in memory, with switches to make it fail. Mirrors the SQL rules (see sql_check.py). */
    static final class FakeStore implements MirrorStore {
        final Map<UUID, Mirror> rows = new HashMap<>();
        final Map<UUID, Set<UUID>> trust = new HashMap<>();
        boolean failInsert, failTrust, failLoad;

        public CompletableFuture<List<Mirror>> loadMirrors() {
            if (failLoad) {
                return CompletableFuture.failedFuture(new RuntimeException("disk"));
            }
            return CompletableFuture.completedFuture(rows.values().stream()
                    .map(m -> new Mirror(m.id(), m.owner(), m.pos(), m.isPublic(), m.createdAt(), trust.getOrDefault(m.id(), Set.of()))).toList());
        }

        public CompletableFuture<Void> insert(Mirror m) {
            if (failInsert || rows.containsKey(m.id())) {
                return CompletableFuture.failedFuture(new RuntimeException("insert"));
            }
            rows.put(m.id(), m);
            return CompletableFuture.completedFuture(null);
        }

        public CompletableFuture<Boolean> delete(UUID id) {
            trust.remove(id);
            return CompletableFuture.completedFuture(rows.remove(id) != null);
        }

        public CompletableFuture<Boolean> addTrust(UUID id, UUID player) {
            if (failTrust) {
                return CompletableFuture.failedFuture(new RuntimeException("trust"));
            }
            Mirror m = rows.get(id);
            if (m == null || m.owner().equals(player)) {
                return CompletableFuture.completedFuture(false);
            }
            return CompletableFuture.completedFuture(trust.computeIfAbsent(id, k -> new HashSet<>()).add(player));
        }

        public CompletableFuture<Boolean> removeTrust(UUID id, UUID player) {
            return CompletableFuture.completedFuture(trust.getOrDefault(id, new HashSet<>()).remove(player));
        }
    }

    static UUID id(int n) {
        return new UUID(0, n);
    }

    static Mirror.Pos pos(String world, double x, double y, double z) {
        return new Mirror.Pos(world, x, y, z);
    }

    static MirrorRegistry registry(FakeStore store) {
        Logger quiet = Logger.getAnonymousLogger();
        quiet.setLevel(Level.OFF);
        MirrorRegistry r = new MirrorRegistry(quiet, store, Runnable::run);
        r.load(Duration.ofSeconds(1));
        return r;
    }

    public static void main(String[] args) {
        UUID alice = id(1), bob = id(2), carol = id(3);
        UUID m1 = id(101), m2 = id(102), m3 = id(103);

        FakeStore store = new FakeStore();
        MirrorRegistry r = registry(store);

        // registration
        eq("register ok", true, r.register(m1, alice, pos("world", 0, 64, 0), 10).join());
        eq("register again refused (same furniture)", false, r.register(m1, bob, pos("world", 0, 64, 0), 11).join());
        eq("owner kept after duplicate attempt", alice, r.find(m1).get().owner());
        eq("saved", true, store.rows.containsKey(m1));
        eq("public flag off", false, r.find(m1).get().isPublic());
        r.register(m2, bob, pos("world", 100, 64, 100), 20).join();
        r.register(m3, alice, pos("world", 3, 64, 0), 30).join();

        // use rights
        eq("owner can use", true, r.find(m1).get().canUse(alice));
        eq("stranger cannot use", false, r.find(m1).get().canUse(bob));

        // trust rules
        eq("trust by owner", TrustStatus.ADDED, r.trust(m1, alice, bob).join());
        eq("trusted can use", true, r.find(m1).get().canUse(bob));
        eq("trust is saved", Set.of(bob), store.trust.get(m1));
        eq("trust per mirror: not on the owner's other mirror", false, r.find(m3).get().canUse(bob));
        eq("trust per mirror: not on someone else's", false, r.find(m2).get().isTrusted(alice));
        eq("trust twice", TrustStatus.ALREADY_TRUSTED, r.trust(m1, alice, bob).join());
        eq("trusted cannot add trust", TrustStatus.NOT_OWNER, r.trust(m1, bob, carol).join());
        eq("stranger cannot add trust", TrustStatus.NOT_OWNER, r.trust(m1, carol, carol).join());
        eq("nobody trusted by a stranger", false, r.find(m1).get().isTrusted(carol));
        eq("owner cannot trust self", TrustStatus.SELF, r.trust(m1, alice, alice).join());
        eq("owner is never in the list", false, r.find(m1).get().trusted().contains(alice));
        eq("unknown mirror", TrustStatus.NOT_FOUND, r.trust(id(999), alice, bob).join());
        eq("trusted cannot untrust", UntrustStatus.NOT_OWNER, r.untrust(m1, bob, bob).join());
        eq("untrust not trusted", UntrustStatus.NOT_TRUSTED, r.untrust(m1, alice, carol).join());
        eq("untrust unknown mirror", UntrustStatus.NOT_FOUND, r.untrust(id(999), alice, bob).join());
        eq("untrust", UntrustStatus.REMOVED, r.untrust(m1, alice, bob).join());
        eq("untrusted cannot use again", false, r.find(m1).get().canUse(bob));
        eq("untrust saved", true, store.trust.get(m1).isEmpty());

        // trust on one mirror, then the second mirror of the same owner stays clean (and the reverse)
        r.trust(m3, alice, carol).join();
        eq("trust m3 only", true, r.find(m3).get().canUse(carol) && !r.find(m1).get().canUse(carol));

        // save failures never leave memory ahead of the database
        store.failTrust = true;
        eq("trust save fails", TrustStatus.ERROR, r.trust(m1, alice, bob).join());
        eq("failed trust rolled back", false, r.find(m1).get().isTrusted(bob));
        store.failTrust = false;
        r.trust(m1, alice, bob).join();
        store.failTrust = true;
        eq("untrust save is not blocked by failTrust", UntrustStatus.REMOVED, r.untrust(m1, alice, bob).join());
        store.failTrust = false;
        store.failInsert = true;
        eq("register save fails", false, r.register(id(104), alice, pos("world", 9, 9, 9), 40).join());
        eq("failed register not kept", true, r.find(id(104)).isEmpty());
        store.failInsert = false;

        // finding the nearest
        r.untrust(m3, alice, carol).join();
        eq("nearest own within 5", m1, r.nearest(pos("world", 1, 64, 0), 5, m -> m.isOwner(alice)).get().id());
        eq("nearest picks the closer one", m3, r.nearest(pos("world", 3, 64, 1), 5, m -> m.isOwner(alice)).get().id());
        eq("nobody else's mirror for the owner filter", true, r.nearest(pos("world", 100, 64, 100), 5, m -> m.isOwner(alice)).isEmpty());
        eq("any owner (admin remove)", m2, r.nearest(pos("world", 100, 64, 101), 5, m -> true).get().id());
        eq("out of range", true, r.nearest(pos("world", 10, 64, 0), 5, m -> m.isOwner(alice)).isEmpty());
        eq("range is inclusive", true, r.nearest(pos("world", 5, 64, 0), 5, m -> m.id().equals(m1)).isPresent());
        eq("other world ignored", true, r.nearest(pos("nether", 0, 64, 0), 5, m -> true).isEmpty());

        // list order
        eq("all is oldest first", List.of(m1, m2, m3), r.all().stream().map(Mirror::id).toList());

        // restart: a new registry on the same store sees everything, trust included
        r.trust(m1, alice, bob).join();
        MirrorRegistry again = registry(store);
        eq("restart: all mirrors", 3, again.size());
        eq("restart: owner", alice, again.find(m1).get().owner());
        eq("restart: position", pos("world", 0, 64, 0), again.find(m1).get().pos());
        eq("restart: trust", true, again.find(m1).get().isTrusted(bob));
        eq("restart: trust did not leak", false, again.find(m3).get().isTrusted(bob));

        // breaking
        eq("unregister", true, r.unregister(m1).join());
        eq("unregister twice", false, r.unregister(m1).join());
        eq("record gone", false, store.rows.containsKey(m1));
        eq("its trust gone", false, store.trust.containsKey(m1));
        eq("other mirror untouched", true, r.find(m3).isPresent());
        eq("trust after break", TrustStatus.NOT_FOUND, r.trust(m1, alice, carol).join());
        eq("the same id can be registered again as a new mirror", true, r.register(m1, carol, pos("world", 0, 64, 0), 50).join());
        eq("new owner, empty trust", true, r.find(m1).get().isOwner(carol) && r.find(m1).get().trusted().isEmpty());

        // a mirror list that cannot be read stops the plugin (never an empty registry)
        FakeStore broken = new FakeStore();
        broken.failLoad = true;
        boolean threw = false;
        try {
            registry(broken);
        } catch (IllegalStateException e) {
            threw = true;
        }
        eq("load failure throws", true, threw);

        // Mirror record
        Mirror m = new Mirror(m1, alice, pos("w", 0, 0, 0), false, 1, Set.of(bob));
        eq("trusted set is immutable", true, throwsOnAdd(m.trusted()));

        // messages
        Messages defaults = Messages.defaults();
        for (String key : Messages.keys()) {
            eq("message " + key + " is not empty", true, !defaults.get(key).isBlank());
        }
        eq("unknown key shows itself", "nope", defaults.get("nope"));

        System.out.println(checks + " checks, " + failures + " failures");
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : "SOME CHECKS FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    static boolean throwsOnAdd(Set<UUID> set) {
        try {
            set.add(UUID.randomUUID());
            return false;
        } catch (UnsupportedOperationException e) {
            return true;
        }
    }
}
