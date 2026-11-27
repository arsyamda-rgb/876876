# VeyrionWardrobe (Stage 3)

Build order:
1. In the VeyrionCharacter project: `mvn install` (it has has/withdraw/previews).
2. Here: `mvn package`. The jar is `target/VeyrionWardrobe-0.3.0.jar`. Put it in `plugins/` next to VeyrionCharacter, ItemsAdder 4.x and SkinsRestorer 15.x.
3. Install the mirror pack: copy `itemsadder-pack/contents/aether_mirror` into `plugins/ItemsAdder/contents/`, then run `/iazip`.

If one of the three plugins is missing or the wrong version, VeyrionWardrobe disables itself and prints why (see `DependencyRules`). ItemsAdder is a hard dependency by design: the mirror is furniture.
If the furniture id is not known to ItemsAdder (pack not installed, wrong `mirror.item-id`), the log says so once ItemsAdder has loaded, and `/wardrobe give` answers with the same reason.

## ItemsAdder 4.0.3 API

ItemsAdder 4.0.3 is not available from the configured Maven repositories. The GitHub Actions workflow installs the local API-only jar before compiling this plugin.

1. Put the API-only jar at `libs/itemsadder-api-4.0.3.jar` in this repository.
2. The workflow installs it locally using coordinates `beer.devs:itemsadder-api:4.0.3`, then runs `mvn package`.
3. Do not put the full ItemsAdder plugin jar in this repository. The API-only jar should contain the `dev/lone/itemsadder/api/` classes needed for compilation.

The API-only jar was extracted from an ItemsAdder 4.0.3 server jar. Check ItemsAdder's license/redistribution terms before committing it to this public repository. If redistribution is not permitted, keep the API jar in a private repository or use a private build process instead.

## Using the Aether Mirror
1. An admin runs `/wardrobe give <player>`. The player places the item on the floor like any furniture (1 block wide, 2 high). The player who places it is the **owner**.
2. Right click the mirror: the owner and players trusted on that mirror open the wardrobe menu. They always see **their own** outfits, never the owner's.
3. Only the owner (or an admin) can break the mirror. Breaking it drops the item and deletes its record and its trust list.

## Commands and permissions
| Command | Who | What |
|---|---|---|
| `/wardrobe trust <player>` | everyone, for their own mirror | lets the player use the nearest mirror you own (within `mirror.target-range`) |
| `/wardrobe untrust <player>` | same | takes that permission away |
| `/wardrobe trustlist` | same | lists who may use that mirror |
| `/wardrobe give <player>` | `veyrionwardrobe.admin` | gives an Aether Mirror item (player must be online) |
| `/wardrobe remove` | `veyrionwardrobe.admin` | removes the nearest mirror (anyone's), no drop |
| `/wardrobe reload` | `veyrionwardrobe.admin` | reloads config.yml |
| `/wardrobe list [page]` | `veyrionwardrobe.admin` | all mirrors: owner, place, number of trusted players, `loaded` / `not loaded`, id |

Trust rules: it belongs to one mirror (the owner's other mirrors are not affected), is saved by UUID, survives restarts, cannot be given to yourself, and a trusted player can use the mirror but cannot change its trust list or break it. A player who is not trusted cannot use someone else's mirror. `veyrionwardrobe.admin` defaults to op; it does not let an admin use other people's mirrors, only manage and break them.

## Config (config.yml)
* `mirror.item-id` (default `aether_mirror:aether_mirror`): the ItemsAdder id of the furniture. Change it only if you renamed the pack's namespace.
* `mirror.target-range` (default 5.0): how close you must stand for trust/untrust/trustlist/remove.
* `mirror.max-use-distance` (default 6.0): the menu closes when you walk further away.
* `messages.<key>`: every text can be replaced (MiniMessage). Defaults are Indonesian; keys are in `config/Messages.java`.
* Everything from Stage 1 and 2 is unchanged. An old config.yml keeps working (new keys use defaults).

## What is not here yet (Stage 4)
The textured ItemsAdder GUI, the 3D preview in front of the mirror and adding outfits from the GUI. The mirror opens a plain chest menu that lists your outfits and wears the one you click. `SkinManager.addOutfit` exists but nothing calls it from the game yet.

## Stage 2 (unchanged)
* `skin/SkinManager`: add outfit from a URL (`addOutfit`), wear a saved outfit (`wear`), re-apply the active one (`reapplyActive`).
* `skin/SkinsRestorerIntegration`: the only class that touches the SkinsRestorer API. **Read the VERIFY notes in docs/DESIGN.md before the first `mvn package`.**
* `skin/SkinCache` + table `skin_cache`, `skin/HttpSkinImageProbe`, `skin/SkinPreviews`; config keys `skins.*`, `preview.refresh-delay-ticks`.

## Checks (no Paper needed; commands in dev-checks/README.txt)
* `WardrobeCheck` 100, `SkinCheck` 249, `MirrorCheck` 90 (Stage 3 rules: ownership, trust per mirror, save failures, restart, nearest), `sql_check.py` 82 (the real SQL on SQLite, including trust, cascade and restart).
* NOT proven here: the plugin on Paper, the real ItemsAdder API, `SkinsRestorerIntegration` against the real jar, the SQLite JDBC driver. See "Stage 3: open verification" in docs/DESIGN.md.
