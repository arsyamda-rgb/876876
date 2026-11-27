#!/usr/bin/env bash
# Checks that every ItemsAdder API member used by VeyrionWardrobe exists in YOUR ItemsAdder 4.0.3 jar.
# This is the only valid proof of compatibility: it reads the real jar with javap, no stand-ins involved.
#
# Usage:  dev-checks/verify-itemsadder-api.sh path/to/ItemsAdder.jar
#   (the plugin jar from plugins/ contains the dev.lone.itemsadder.api classes; the jar must be version 4.0.3)
# Needs a JDK (javap). Exit code 0 = all members found, 1 = something missing, 2 = usage/tool problem.
set -u
JAR="${1:-}"
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then echo "usage: $0 <ItemsAdder-4.0.3.jar>" >&2; exit 2; fi
command -v javap >/dev/null 2>&1 || { echo "javap not found: install a JDK 21" >&2; exit 2; }
if command -v unzip >/dev/null 2>&1; then
  unzip -p "$JAR" plugin.yml 2>/dev/null | grep -E '^version:' | sed 's/^/jar plugin.yml: /'
fi
A=dev.lone.itemsadder.api
fail=0
decl() { javap -public -cp "$JAR" "$1" 2>/dev/null; }
need_class() {
  if decl "$1" | grep -q "class $1\|interface $1"; then echo "ok      class $1"; else echo "MISSING class $1"; fail=1; fi
}
need_member() { # class, grep pattern (fixed string), description
  if decl "$1" | grep -qF -- "$2"; then echo "ok      $1  $3"; else echo "MISSING $1  $3"; fail=1; fi
}
forbid_member() { # members that must NOT be used (4.0.17-only); informational: shows if the jar has them
  if decl "$1" | grep -qF -- "$2"; then echo "info    $1 has $3 (not used by the plugin)"; fi
}

for c in $A.CustomStack $A.CustomFurniture $A.Events.FurnitureBreakEvent $A.Events.FurnitureInteractEvent \
         $A.Events.FurniturePlaceSuccessEvent $A.Events.ItemsAdderLoadDataEvent; do need_class "$c"; done

need_member $A.CustomStack "static boolean isInRegistry(java.lang.String)"           "isInRegistry(String)"
need_member $A.CustomStack "static $A.CustomStack getInstance(java.lang.String)"     "getInstance(String)"
need_member $A.CustomStack "org.bukkit.inventory.ItemStack getItemStack()"           "getItemStack()"
need_member $A.CustomFurniture "static $A.CustomFurniture byAlreadySpawned(org.bukkit.entity.Entity)" "byAlreadySpawned(Entity)"
need_member $A.CustomFurniture "void remove(boolean)"                                "remove(boolean)"

# In ItemsAdder 4.0.3 these three events extend FurnitureEvent, which itself extends PlayerEvent.
need_class $A.Events.FurnitureEvent
need_member $A.Events.FurnitureEvent "extends org.bukkit.event.player.PlayerEvent" "extends PlayerEvent (inherits getPlayer())"
for e in FurniturePlaceSuccessEvent FurnitureInteractEvent FurnitureBreakEvent; do
  need_member $A.Events.$e "java.lang.String getNamespacedID()" "getNamespacedID()"
  need_member $A.Events.$e "org.bukkit.entity.Entity getBukkitEntity()" "getBukkitEntity()"
  need_member $A.Events.$e "extends $A.Events.FurnitureEvent" "extends FurnitureEvent"
done
need_member $A.Events.FurnitureInteractEvent "implements org.bukkit.event.Cancellable" "Cancellable"
need_member $A.Events.FurnitureBreakEvent    "implements org.bukkit.event.Cancellable" "Cancellable"

forbid_member $A.Events.FurnitureInteractEvent "getAction()" "getAction() (added in 4.0.17)"
if decl $A.Events.FurniturePlacedEvent | grep -q class; then echo "info    FurniturePlacedEvent exists in this jar (not used by the plugin)"; fi

echo
if [ $fail -eq 0 ]; then echo "RESULT: every ItemsAdder member used by VeyrionWardrobe exists in this jar."; else echo "RESULT: FAILED, see MISSING lines above."; fi
exit $fail
