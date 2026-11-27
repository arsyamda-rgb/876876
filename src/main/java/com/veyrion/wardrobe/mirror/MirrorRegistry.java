package com.veyrion.wardrobe.mirror;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Every placed mirror and its trust list, in memory (a copy of the database, loaded once at start) and saved on
 * every change. Main thread only; the store runs on its own thread and answers back on the main thread.
 * <p>
 * Changes are applied to memory first and saved right after, so a click a moment later already sees them; if the
 * save fails the change is taken back (trust) or reported to the caller (registration), so memory never says
 * something the database does not.
 * <p>
 * Rules decided here (and only here): only the owner changes a trust list, nobody trusts themselves, trust belongs
 * to one mirror id and is looked up by that id only.
 */
public final class MirrorRegistry {

    public enum TrustStatus { ADDED, ALREADY_TRUSTED, SELF, NOT_OWNER, NOT_FOUND, ERROR }

    public enum UntrustStatus { REMOVED, NOT_TRUSTED, NOT_OWNER, NOT_FOUND, ERROR }

    private final Logger logger;
    private final MirrorStore store;
    private final Executor main;
    private final Map<UUID, Mirror> mirrors = new HashMap<>();

    public MirrorRegistry(Logger logger, MirrorStore store, Executor main) {
        this.logger = logger;
        this.store = store;
        this.main = main;
    }

    /**
     * Reads every saved mirror. Blocks up to {@code wait}; call it once while the plugin enables.
     * @throws IllegalStateException if the database cannot be read: starting with an empty registry would lock every
     *         owner out of their mirrors, so the caller must stop instead
     */
    public void load(Duration wait) {
        try {
            for (Mirror mirror : store.loadMirrors().get(wait.toMillis(), TimeUnit.MILLISECONDS)) {
                mirrors.put(mirror.id(), mirror);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while loading the mirrors", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load the saved mirrors: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------------------------------ reading

    public Optional<Mirror> find(UUID mirrorId) {
        return Optional.ofNullable(mirrors.get(mirrorId));
    }

    public int size() {
        return mirrors.size();
    }

    /** Every mirror, oldest first. A copy. */
    public List<Mirror> all() {
        List<Mirror> list = new ArrayList<>(mirrors.values());
        list.sort(Comparator.comparingLong(Mirror::createdAt).thenComparing(m -> m.id().toString()));
        return list;
    }

    /** The mirror closest to {@code from} within {@code range} blocks, in the same world, that passes the filter. */
    public Optional<Mirror> nearest(Mirror.Pos from, double range, Predicate<Mirror> filter) {
        double limit = range * range;
        Mirror best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mirror mirror : mirrors.values()) {
            if (!mirror.pos().sameWorld(from) || !filter.test(mirror)) {
                continue;
            }
            double distance = mirror.pos().distanceSquared(from);
            if (distance <= limit && (distance < bestDistance
                    || (distance == bestDistance && mirror.id().compareTo(best.id()) < 0))) {
                best = mirror;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    // ------------------------------------------------------------------------------------------ mirrors

    /** A new mirror was placed. The future says whether it was saved; if not, it is not registered either. */
    public CompletableFuture<Boolean> register(UUID mirrorId, UUID owner, Mirror.Pos pos, long now) {
        if (mirrors.containsKey(mirrorId)) {
            return CompletableFuture.completedFuture(false); // the same furniture is never registered twice
        }
        Mirror mirror = new Mirror(mirrorId, owner, pos, false, now, java.util.Set.of());
        mirrors.put(mirrorId, mirror);
        return store.insert(mirror).handleAsync((ok, error) -> {
            if (error != null) {
                mirrors.remove(mirrorId, mirror);
                logger.log(Level.SEVERE, "Could not save the mirror " + mirrorId, error);
                return false;
            }
            return true;
        }, main);
    }

    /** The furniture is gone (broken or removed). Safe to call twice. @return true if a mirror was registered */
    public CompletableFuture<Boolean> unregister(UUID mirrorId) {
        if (mirrors.remove(mirrorId) == null) {
            return CompletableFuture.completedFuture(false);
        }
        return store.delete(mirrorId).handleAsync((deleted, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Could not delete the mirror " + mirrorId + " from the database "
                        + "(its row stays; delete it by hand if it comes back after a restart)", error);
            }
            return true;
        }, main);
    }

    // ------------------------------------------------------------------------------------------ trust

    public CompletableFuture<TrustStatus> trust(UUID mirrorId, UUID actor, UUID target) {
        Mirror mirror = mirrors.get(mirrorId);
        if (mirror == null) {
            return CompletableFuture.completedFuture(TrustStatus.NOT_FOUND);
        }
        if (!mirror.isOwner(actor)) {
            return CompletableFuture.completedFuture(TrustStatus.NOT_OWNER);
        }
        if (mirror.isOwner(target)) {
            return CompletableFuture.completedFuture(TrustStatus.SELF);
        }
        if (mirror.isTrusted(target)) {
            return CompletableFuture.completedFuture(TrustStatus.ALREADY_TRUSTED);
        }
        mirrors.put(mirrorId, mirror.withTrusted(target));
        return store.addTrust(mirrorId, target).handleAsync((added, error) -> {
            if (error != null || !added) {
                mirrors.computeIfPresent(mirrorId, (id, current) -> current.withoutTrusted(target));
                if (error != null) {
                    logger.log(Level.SEVERE, "Could not save the trust of " + target + " on mirror " + mirrorId, error);
                    return TrustStatus.ERROR;
                }
                return TrustStatus.NOT_FOUND;
            }
            return TrustStatus.ADDED;
        }, main);
    }

    public CompletableFuture<UntrustStatus> untrust(UUID mirrorId, UUID actor, UUID target) {
        Mirror mirror = mirrors.get(mirrorId);
        if (mirror == null) {
            return CompletableFuture.completedFuture(UntrustStatus.NOT_FOUND);
        }
        if (!mirror.isOwner(actor)) {
            return CompletableFuture.completedFuture(UntrustStatus.NOT_OWNER);
        }
        if (!mirror.isTrusted(target)) {
            return CompletableFuture.completedFuture(UntrustStatus.NOT_TRUSTED);
        }
        mirrors.put(mirrorId, mirror.withoutTrusted(target));
        return store.removeTrust(mirrorId, target).handleAsync((removed, error) -> {
            if (error != null) {
                mirrors.computeIfPresent(mirrorId, (id, current) -> current.withTrusted(target));
                logger.log(Level.SEVERE, "Could not save the removal of " + target + " from mirror " + mirrorId, error);
                return UntrustStatus.ERROR;
            }
            return UntrustStatus.REMOVED;
        }, main);
    }
}
