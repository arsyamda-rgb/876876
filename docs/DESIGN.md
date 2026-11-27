# VeyrionWardrobe: design and status

## Verified (not guessed)
* SkinsRestorer 15.12.6: API read from the jar you uploaded (javap). Variants are `CLASSIC` and `SLIM`; "WIDE" in the GUI maps to `CLASSIC`.
  Flow for a URL skin: `SkinStorage.findSkinData(url, variant)` (cache) -> else `findOrCreateSkinData(url, variant)` ->
  `PlayerStorage.setSkinIdOfPlayer(uuid, identifier)` -> `getSkinApplier(Player.class).applySkin(player, property)`.
* VeyrionCharacter (this repo's updated copy): `has`, `withdraw`, `previews()` added, additive, API_VERSION still 1.
* ItemsAdder API: target is **ItemsAdder 4.0.3** (released 23 Oct 2024), `beer.devs:itemsadder-api:4.0.3` in the pom. Nothing from 4.0.17 may be used.
  The first Stage 3 draft was written from the 4.0.17-beta-12-test-8 javadoc; that was wrong for this server and has been revised:
  * `FurnitureInteractEvent#getAction()` was added in 4.0.17 (ItemsAdder issue #5375, milestone 4.0.17). REMOVED. Every interact event on the mirror
    now counts as "use"; breaking goes through `FurnitureBreakEvent`.
  * `FurniturePlacedEvent` is only confirmed from the 4.0.10 javadoc; the 4.0.17 javadoc marks `FurniturePlaceSuccessEvent` deprecated in its favour.
    For 4.0.3 the plugin uses `FurniturePlaceSuccessEvent` (exists since ItemsAdder 3.2.3c, still present in later javadocs, not Cancellable).
  * Used and still to be confirmed in the 4.0.3 jar: `CustomStack.getInstance(String)` / `isInRegistry(String)` / `#getItemStack()`,
    `CustomFurniture.byAlreadySpawned(Entity)` / `#remove(boolean)`, `getNamespacedID()` + `getBukkitEntity()` + `getPlayer()` on the three
    furniture events, `FurnitureInteractEvent`/`FurnitureBreakEvent` Cancellable, `ItemsAdderLoadDataEvent`.
  * **Not verified against the real 4.0.3 jar** (none was available to the author). Run `dev-checks/verify-itemsadder-api.sh <your ItemsAdder 4.0.3 jar>`.

## Stages
1. DONE  skeleton, dependency guard, settings, SQLite storage, OutfitManager
2. DONE* SkinsRestorerIntegration + SkinManager (async processing, cooldown, timeout, cache, apply, preview refresh)
         *the SkinsRestorer adapter still has to be checked against the jar (see "Stage 2: open verification")
3. DONE* Aether Mirror as ItemsAdder furniture (place, owner, right click, break), per-mirror trust, /wardrobe commands, plain wardrobe menu
         *compiled and rule-checked only against stand-ins; needs the in-game test list under "Stage 3: open verification"
4.       WardrobeMirrorPreview (VeyrionCharacter previews API), WardrobeGUI (ItemsAdder Custom GUI, replaces the plain menu), chat input for URL (add outfit)
5.       Paid features (only when you decide a price): MoneyIntegration on top of VeyrionCharacterAPI.withdraw

## GUI layout (placeholder textures; right-side panel like GTA)
The ItemsAdder Custom GUI is a texture drawn over a chest inventory, and that inventory is always centred on the screen.
To get a right-hand panel: use a 6-row chest, draw the panel texture only over columns 6-9 (shifted with a negative-offset
font image), keep columns 1-5 empty and transparent so the mirror and the 3D preview stay visible on the left, and put every
button in columns 6-9. Not yet tried in ItemsAdder 4.0.3: it must be tested in game before the layout is final.

```
 col: 1 2 3 4 5 | 6 7 8 9
 row1:  (clear)  | title bar            "AETHER MIRROR"
 row2:  (clear)  | My Outfits  / Add Outfit
 row3:  (clear)  | Current Skin / Settings
 row4:  (clear)  | outfit list (page)
 row5:  (clear)  | outfit list (page)
 row6:  (clear)  | < page   Close   page >
```
Black (#0b0b0f) panel, gold (#c9a227) frame and text, thin gold dividers; no extra ornaments.

## Rules this code already enforces
* An outfit belongs to the player who USES the mirror, never to the mirror owner (OutfitManager takes the user's UUID only).
* Limit, unique name (case-insensitive) and owner check are done in one SQL step each, so two requests cannot break them.
* Names: letters, digits, space, `_ - ' .`; no `<`, `>`, `§`.
* Skin URLs: https only, no credentials, no private/loopback hosts, optional host allow-list.
* Database: WAL + synchronous FULL + quick_check at start. A broken file is left untouched and the plugin disables itself.

## Stage 2: how it works
```
addOutfit(player, url, model, name)
  validate URL (text) -> name/limit pre-check (no MineSkin use for a request that cannot be saved)
  -> resolve:  cache hit?  yes: backend.lookup (SkinsRestorer's own storage, no network)
                           no / forgotten: [cooldown] [limit] image probe -> backend.create (findOrCreate, network)
                           same key already running: join that flight
  -> main thread: outfits.add (saved with skinRef) -> backend.apply (setSkinIdOfPlayer + applySkin) -> outfits.setActive
                  -> afterApply: SkinPreviews.refresh (after preview.refresh-delay-ticks)
  apply fails -> the new outfit is deleted again (nothing saved, old outfit stays active)
```
* Threads: SkinManager's state is touched on the main thread only; the worker thread gets plain values and returns one value. No locks.
* Timeout is per player; the shared flight keeps going and fills the cache, so "retry" after a TIMEOUT is usually instant. A flight that never ends is dropped after 3x the timeout (frees the slot).
* Cooldown/limit count real processing only (a cache hit or joining a running flight costs nothing). A failed processing still starts the cooldown (MineSkin is rate limited).
* Disconnect: the request ends with PLAYER_OFFLINE, nothing is applied or saved, the skin stays cached.
* Why `reapply-active-on-join` is off: SkinsRestorer remembers the player's skin by itself (`setSkinIdOfPlayer`).

## Stage 2: open verification (do this before trusting the build)
The SkinsRestorer 15.12.6 jar was NOT in the upload when Stage 2 was written, so `SkinsRestorerIntegration` could not be compiled against it.
The flow comes from the Stage 1 notes above. The names below are NOT recorded anywhere and are marked `// VERIFY` in the code:
* `net.skinsrestorer.api.property.SkinVariant` (package) with `CLASSIC` and `SLIM`
* `net.skinsrestorer.api.property.InputDataResult` (package and name) with `getIdentifier()` and `getProperty()`
* `findSkinData(String, SkinVariant)` and `findOrCreateSkinData(String, SkinVariant)` return `Optional<InputDataResult>`
* `PlayerStorage.setSkinIdOfPlayer(UUID, <type of getIdentifier()>)` and `SkinApplier.applySkin(Player, <type of getProperty()>)`
Check with (JDK needed):
```
javap -cp SkinsRestorer.jar net.skinsrestorer.api.storage.SkinStorage net.skinsrestorer.api.storage.PlayerStorage net.skinsrestorer.api.SkinsRestorer
javap -cp SkinsRestorer.jar net.skinsrestorer.api.property.SkinVariant net.skinsrestorer.api.property.InputDataResult
unzip -l SkinsRestorer.jar | grep -i "api/.*\.class" | grep -i "SkinApplier\|SkinIdentifier\|SkinProperty"
```
Also unknown: whether `applySkin` must run on the main thread (it is called there) and how many ticks until the server's profile of the player shows the new skin (`preview.refresh-delay-ticks`, default 2: if the mirror still shows the old skin, raise it).
`dev-checks/stubs-unverified-sr/` is a stand-in written from memory so the code can compile here; it proves nothing about the real jar.

## CharacterPreview (VeyrionCharacter Stage 6) as read
`previews()` returns `Optional<CharacterPreviewFactory>` (empty without ProtocolLib). `CharacterPreview.refreshSkin()` rebuilds the packet double from the subject's live profile (`WrappedGameProfile.fromPlayer`), re-shows it to the same viewers, and removes the preview itself if the subject is offline. All methods are idempotent. SkinManager never creates a preview: Stage 4's mirror creates it and calls `SkinPreviews.track(preview)`; tracking a second one for the same player removes the first.

## Stage 3: how it works
```
place   FurniturePlacedEvent (MONITOR, not cancelled), id == mirror.item-id
          -> MirrorRegistry.register(entity UUID, placer UUID, world/x/y/z)  (memory first, then SQLite)
          -> save failed: furniture removed again and the item given back (not in creative)
use     FurnitureInteractEvent (HIGH), right click only, cancelled so ItemsAdder does nothing else
          -> not registered: told so | owner or trusted on THIS mirror: WardrobeMenus.open | anyone else: refused
break   FurnitureBreakEvent (HIGH): only the owner or veyrionwardrobe.admin may; everyone else is cancelled
          FurnitureBreakEvent (MONITOR, not cancelled): MirrorRegistry.unregister -> row and trust rows deleted (ON DELETE CASCADE)
```
* Identity: the mirror id is the UUID of the furniture entity. Table `mirrors` (id, owner, world, x, y, z, created_at; `is_public` is a reserved column, always 0 and never read as a permission) and `mirror_trust (furniture_uuid, player)` with a foreign key and `ON DELETE CASCADE`. Both tables existed since Stage 1; there is no schema change, so old databases open as before.
* Trust is by player UUID and by mirror id. Only the owner changes it; nobody can trust the owner (the SQL refuses it too). Names typed in commands are looked up online first, then in the server's own name cache (`Bukkit.getOfflinePlayerIfCached`), never over the network.
* Memory is a copy of the tables, loaded at start. If the mirror tables cannot be read the plugin disables itself (an empty list would lock owners out). A save that fails undoes the change in memory.
* The menu shows the outfits of the player who uses the mirror (OutfitManager is keyed by that player's UUID only). It re-checks every 10 ticks: distance (`mirror.max-use-distance`), mirror still there, permission still there.
* Duplication: placing consumes the item (ItemsAdder), breaking drops it once (ItemsAdder `drop_when_mined`) and deletes the record in the same event; the record is keyed by the entity UUID, so one furniture can never be registered twice and a re-placed mirror is a new mirror with an empty trust list. `/wardrobe remove` drops nothing.
* Targeting for commands: the nearest mirror within `mirror.target-range` blocks (owned by the sender for trust/untrust/trustlist; any for admin remove). It does not use the line of sight: furniture hit boxes are not entities this plugin can ray trace.

## Stage 3: open verification (do this before trusting the build)
Checked here: `mvn` could not run (no Maven, no network on the build machine). The stand-in classes in `dev-checks/stubs-unverified-stage3/` are NOT evidence of compatibility. What did run: `MirrorCheck` (90 checks) and `sql_check.py` (82 checks, the real SQL on SQLite). The CI build is the first real compile against Paper and `itemsadder-api`.
Not checked, needs a server with Paper 1.21.1, ItemsAdder 4.x, SkinsRestorer 15.x and VeyrionCharacter:
1. `beer.devs:itemsadder-api:4.0.3` resolves from Maven Central (not confirmed; if not, install the API from your 4.0.3 jar, see README) and
   `dev-checks/verify-itemsadder-api.sh` prints `RESULT: every ItemsAdder member ... exists` for your real 4.0.3 jar.
2. The furniture entity keeps its UUID across a restart and across `/iareload`. If ItemsAdder ever re-creates the entity, the mirror shows "not registered" (the record stays; `/wardrobe list` shows `not loaded`). Test: place, trust, restart, use; then `/iareload`, use again.
3. Expected, NOT verified: `FurnitureInteractEvent` fires for a right click on the mirror and a left click (punch) goes to `FurnitureBreakEvent` instead. The plugin cannot filter by click type because `getAction()` was added only in 4.0.17. Test in game: left click the mirror as a non-owner and as the owner; if the wardrobe menu opens on a left click, report it.
4. `CustomFurniture.remove(false)` drops nothing and the furniture disappears (used by `/wardrobe remove` and the save-failed undo).
5. `contents/aether_mirror` (the pack in `itemsadder-pack/`) loads without errors in ItemsAdder 4.x and the model faces the player (README of the pack: rotate in Blockbench if not).
6. A mirror removed by other means (`/iaremove`, another plugin, a `/kill`) leaves its row behind. It is harmless (the UUID never comes back) and visible as `not loaded` in `/wardrobe list`; Stage 3 does not clean it up automatically.
