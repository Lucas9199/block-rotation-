# Block Rotation (Fabric 1.21.11, client-side only)

| You place / mine   | You see & hear        |
|--------------------|-----------------------|
| Crying Obsidian    | Block of Netherite    |
| Obsidian           | Ancient Debris        |
| Zombie Head        | Dragon Head           |
| Cobblestone        | Gilded Blackstone     |
| Deepslate Tiles    | Block of Diamond      |
| Deepslate Bricks   | Monster Spawner       |
| End Stone          | Block of Emerald      |

Textures, held/dropped items, item names (English) and break/place/mining/step sounds all change.
Only your game is affected; the server still sees the real blocks.

## Build the .jar
Needs Java 21 (Temurin/Adoptium is fine).

    Windows:      gradlew.bat build
    Mac / Linux:  ./gradlew build

The first build downloads Minecraft and Fabric, so it takes a few minutes.
Your mod ends up at `build/libs/blockrotation-1.0.0.jar`.

## Install
Put `blockrotation-1.0.0.jar` **and Fabric API** (0.141.x for 1.21.11) in your Fabric 1.21.11 mods folder.

## Change what disguises as what
* Look + item icon: `src/main/resources/assets/minecraft/blockstates/*.json` and `items/*.json`
* Name: `src/main/resources/assets/minecraft/lang/en_us.json`
* Sound: `src/main/java/com/blockrotation/BlockRotation.java`
* Fake spawner block: `FAKE_SPAWNER` in `BlockRotation.java` (also rename its blockstate/item files)

## Fake spawners
* Every fake spawner in your loaded chunks shows a small spinning mob (no limit).
* Right-click: opens the Donut-style menu (only you see it; nothing is sent to the server).
* Right-click holding a fake spawner (deepslate bricks): adds 1 to the stack. Sneak: adds the whole hand.
* Loot builds up every 10 s while the spawner is loaded (~2 stacks bones + ~2 stacks arrows per spawner after 2 min).
* Menu: click loot to take it, Sell All, Drop Page, page arrows. The mob head shows what is stored and how full the spawner is (45 stacks per spawner; it stops filling when full).
* Saved in `config/blockrotation-spawners.json`.

Client-only commands:

    /fakespawner zombie          (look at a spawner to change it, else sets default for new ones)
    /fakespawner count 448       (look at a spawner)

Loot rates: `SpawnerLoot.java`. Worth prices: `src/main/resources/blockrotation/worth.json`.
