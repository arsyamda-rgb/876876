All commands run from the project root. There is no javac on some machines; `java -m jdk.compiler/com.sun.tools.javac.Main` works the same.

WardrobeCheck (pure Java):
  bash -c 'javac -d /tmp/wchk dev-checks/stubs/org/jetbrains/annotations/Nullable.java dev-checks/stubs/org/bukkit/configuration/ConfigurationSection.java \
    src/main/java/com/veyrion/wardrobe/{model,outfit,skin}/*.java src/main/java/com/veyrion/wardrobe/integration/DependencyRules.java \
    src/main/java/com/veyrion/wardrobe/config/WardrobeSettings.java dev-checks/WardrobeCheck.java'
  (the skin/ folder now has Stage 2 classes: use the SkinCheck command below instead, it builds WardrobeCheck too)
  java -cp /tmp/wchk com.veyrion.wardrobe.WardrobeCheck        # expected: ALL CHECKS PASSED

SkinCheck (pure Java, needs VeyrionCharacter's CharacterPreview.java for the interface; takes about 15 seconds):
  bash -c 'javac -proc:none -d /tmp/sk $(find dev-checks/stubs -name "*.java") <VeyrionCharacter>/src/main/java/com/veyrion/character/api/CharacterPreview.java \
    src/main/java/com/veyrion/wardrobe/{model,outfit,config}/*.java src/main/java/com/veyrion/wardrobe/integration/DependencyRules.java \
    src/main/java/com/veyrion/wardrobe/skin/{SkinStatus,SkinException,SkinKey,ResolvedSkin,SkinBackend,PngHeader,SkinImageProbe,HttpSkinImageProbe,SkinCache,SkinCacheStore,SkinUrlValidator,SkinResult,SkinManager,SkinPreviews}.java \
    dev-checks/access/com/veyrion/wardrobe/skin/SkinCheckAccess.java dev-checks/WardrobeCheck.java dev-checks/SkinCheck.java'
  java -cp /tmp/sk com.veyrion.wardrobe.SkinCheck             # expected: ALL CHECKS PASSED
  (it starts a local HTTP server on 127.0.0.1 for the image-check tests; needs the jdk.httpserver module)

sql_check.py:
  python3 dev-checks/sql_check.py src/main/java/com/veyrion/wardrobe/storage/WardrobeStorage.java src/main/resources/schema.sql

Whole plugin against stand-ins (syntax and types only; SkinsRestorer stand-ins are UNVERIFIED, see docs/DESIGN.md):
  bash -c 'javac -proc:none -d /tmp/w $(find dev-checks/stubs dev-checks/stubs-unverified-sr -name "*.java") <VeyrionCharacter>/src/main/java/com/veyrion/character/api/*.java $(find src/main/java -name "*.java")'
For the real compile of SkinsRestorerIntegration: replace dev-checks/stubs-unverified-sr by the real SkinsRestorer 15.12.6 jar on the classpath.
Nothing here runs on Paper: SQLite JDBC, the plugin lifecycle and listeners must be tested on a server.

MirrorCheck (Stage 3, pure Java, no Bukkit):
  bash -c 'javac -proc:none -d /tmp/mc dev-checks/stubs/org/jetbrains/annotations/Nullable.java dev-checks/stubs/org/bukkit/configuration/ConfigurationSection.java \
    src/main/java/com/veyrion/wardrobe/mirror/{Mirror,MirrorStore,MirrorRegistry}.java src/main/java/com/veyrion/wardrobe/config/Messages.java dev-checks/MirrorCheck.java'
  java -cp /tmp/mc com.veyrion.wardrobe.MirrorCheck           # expected: ALL CHECKS PASSED

Whole plugin against stand-ins, Stage 3 (types and names only; Bukkit, Adventure and ItemsAdder stand-ins are in dev-checks/stubs-unverified-stage3,
written from the ItemsAdder javadoc and from memory, so they prove nothing about the real jars). The older stand-ins that the new ones replace are left out:
  bash -c 'OLD=$(find dev-checks/stubs -name "*.java" ! -name Bukkit.java ! -name Server.java ! -name Location.java ! -name Player.java ! -name BukkitScheduler.java ! -name JavaPlugin.java ! -name Plugin.java ! -name PluginManager.java ! -name EventHandler.java);
    javac -proc:none -d /tmp/w3 $(find dev-checks/stubs-unverified-stage3 dev-checks/stubs-unverified-sr -name "*.java") $OLD <VeyrionCharacter>/src/main/java/com/veyrion/character/api/*.java $(find src/main/java -name "*.java")'

ItemsAdder 4.0.3 (the real check): bash dev-checks/verify-itemsadder-api.sh <ItemsAdder-4.0.3.jar>
  Reads the real jar with javap and checks every ItemsAdder member the plugin uses. The stand-ins in stubs-unverified-stage3 are only for
  type-checking the rest of the plugin; they were adjusted so that 4.0.17-only members (FurnitureInteractEvent#getAction, FurniturePlacedEvent)
  fail to compile, but they are NOT proof that 4.0.3 has what the plugin calls.
