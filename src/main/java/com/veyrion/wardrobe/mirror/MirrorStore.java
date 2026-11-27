package com.veyrion.wardrobe.mirror;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Where mirrors and their trust lists are saved. Every method may run on another thread and completes its future
 * there; {@link MirrorRegistry} moves back to the main thread.
 */
public interface MirrorStore {

    /** Every saved mirror with its trust list. */
    CompletableFuture<List<Mirror>> loadMirrors();

    /** Saves a new mirror (its trust list starts empty). Fails if the id exists. */
    CompletableFuture<Void> insert(Mirror mirror);

    /** @return false if there was no such mirror. Its trust rows go with it. */
    CompletableFuture<Boolean> delete(UUID mirrorId);

    /**
     * @return true if the row was added; false if the mirror does not exist, the player is its owner, or the player
     *         was trusted already (all decided in one SQL step)
     */
    CompletableFuture<Boolean> addTrust(UUID mirrorId, UUID player);

    /** @return false if the player was not trusted on that mirror */
    CompletableFuture<Boolean> removeTrust(UUID mirrorId, UUID player);
}
