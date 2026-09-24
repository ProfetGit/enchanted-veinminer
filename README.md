![Enchanted Veinminer](https://raw.githubusercontent.com/ProfetGit/enchanted-veinminer/main/docs/banner.gif)

**Veinmining becomes an enchantment.**

Enchanted Veinminer is an add-on for **[Veinminer](https://modrinth.com/datapack/vanilla-veinminer)**, the vanilla data pack for **Minecraft Java 26.2 and 26.3**. It adds a new pickaxe enchantment, **Veinminer**. Only a pickaxe that has it breaks the whole vein. Without it, a pickaxe mines one block at a time, as in vanilla.

It needs **Veinminer 1.1.0 or newer**. Install both.

## Why this add-on?

**Earn it, don't just have it.** Veinmining turns from a free rule into a reward. Early on you mine block by block. Later you find the enchantment and put it on your best pickaxe.

**A real vanilla enchantment.** Veinminer is a proper enchantment, not a renamed book or a lore line. The enchanting table offers it, librarians sell it, it turns up in chest loot and fishing, and anvils combine it like any other enchantment. The tooltip shows it by name.

**Plays fair with everything else.** It works alongside Fortune, Silk Touch, Efficiency, Unbreaking and Mending. Veinminer's own rules and settings still apply on top: sneaking, tool tier, durability, and which ores count.

**One zip, server-side only.** Like Veinminer, it needs no mods, no resource pack, no client install and no experimental features.

**Tested, not hoped.** Every release is checked by 54 automated tests on real 26.2 and 26.3 servers, running together with Veinminer: a simulated player mines with and without the enchantment, and the tests check the anvil, the enchanting table, `/enchant`, the loot and trade tags, and what happens when Veinminer is missing or out of date.

## How to use

1. Get the **Veinminer** enchantment on a pickaxe.
2. Sneak.
3. Mine an ore.

Veinminer's join hint and settings menu tell players that the enchantment is needed.

## The enchantment

| | |
|---|---|
| Name | Veinminer |
| Max level | I |
| Goes on | Any pickaxe: wood, stone, copper, iron, gold, diamond, netherite |
| Rarity | Rare, the same as Fortune |
| Enchanting table | Yes, at the same levels as Fortune I |
| Librarians | Yes, as an enchanted book |
| Loot and fishing | Yes, like other non-treasure enchantments |
| Enchanted pickaxe trades | Toolsmiths' and wandering traders' enchanted pickaxes can come with it |
| Combines with | Every other pickaxe enchantment, including Fortune and Silk Touch |

It can't go on shovels, axes, swords or other tools, and the anvil refuses those combinations.

## Commands

| Command | What it does |
|---|---|
| `/enchant @s enchanted_veinminer:veinminer` | Enchants the pickaxe in your hand |
| `/give @s enchanted_book[stored_enchantments={"enchanted_veinminer:veinminer":1}]` | Gives the enchanted book |
| `/function enchanted_veinminer:uninstall` | Removes the add-on's scoreboards and data |

All of Veinminer's commands and settings work as before. See its page.

## Installation

You need both zips: **Veinminer 1.1.0+** and **Enchanted Veinminer**.

**Singleplayer**
- New world: under **More → Data Packs**, drag both `.zip` files into the window.
- Existing world: put both `.zip` files in the world's `datapacks/` folder, then **close and reopen the world**.

**Server:** put both `.zip` files in `world/datapacks/` and **restart the server**.

Don't unzip the files.

**`/reload` is not enough for this add-on.** The game registers enchantments only when a world loads. If you add the pack with `/reload`, it tells you in chat to reopen the world, and until you do, veinmining works without the enchantment. Veinminer itself keeps working the whole time.

If Veinminer is missing or older than 1.1.0, the add-on says so in chat with a download link. Until you fix that, the enchantment does nothing.

**As a mod:** both packs also come as mods for Fabric, Quilt, NeoForge and Forge. Put `EnchantedVeinminer-1.0.0-fabric.jar` (Fabric or Quilt, needs Fabric API) or `EnchantedVeinminer-1.0.0-forge.jar` (Forge or NeoForge) in the `mods` folder, together with Veinminer as a mod or as a data pack. A mod loads before the world does, so the enchantment is always registered and the restart note above doesn't apply. Use either the mod or the zip, not both.

**As a server plugin:** both packs also come as plugins for Paper, Purpur, Spigot and Bukkit. Put `EnchantedVeinminer-1.0.0-plugin.jar` in the `plugins` folder, together with Veinminer as a plugin or as a data pack, and restart the server. On Paper and Purpur the enchantment works right away. On Spigot and Bukkit the plugin copies the pack into `world/datapacks/` when the server stops, so restart once more. Use either the plugin or the zip, not both.

## Compatibility

- One zip supports Minecraft Java **26.2 and 26.3**.
- Needs **Veinminer 1.1.0 or newer**. With Veinminer 1.0.0 the enchantment does nothing, and every pickaxe veinmines as before.
- Everything lives in the `enchanted_veinminer` namespace. It adds to the vanilla `#minecraft:non_treasure` and `#minecraft:tooltip_order` enchantment tags and the `#minecraft:load` function tag, and to Veinminer's add-on hooks. It doesn't replace any files.

## Good to know

- **Removing the add-on removes the enchantment from every item.** The next time the world loads without the pack, the game strips Veinminer from all pickaxes and books, because the enchantment no longer exists. Pickaxes keep their other enchantments, and Veinminer books become blank enchanted books. Adding the pack back later doesn't restore them.
- Adding or removing the pack always needs the world reopened or the server restarted.
- The enchantment's name isn't translated, so it shows as "Veinminer" in every language. Resource packs can translate the key `enchantment.enchanted_veinminer.veinminer`.

## Uninstall

1. Run `/function enchanted_veinminer:uninstall`.
2. Remove `EnchantedVeinminer-1.0.0.zip` from the `datapacks` folder.
3. Reopen the world or restart the server. Veinminer then works without the enchantment again, and the enchantment is removed from all items (see above).

Installed as a mod? Run step 1, then remove the jar from the `mods` folder and restart the game or server.

Installed as a plugin? Run step 1, then remove the jar from the `plugins` folder. On Spigot and Bukkit, also delete `world/datapacks/enchanted_veinminer.zip`. Restart the server.

## Support

Enchanted Veinminer is free. If it saves you some time, a coffee helps fund the next update.

[![Support me on Ko-fi](https://raw.githubusercontent.com/ProfetGit/assets/main/kofi-banner.gif)](https://ko-fi.com/profetgit)

## License

© 2026 Profet. All rights reserved.

- **You can** use Enchanted Veinminer on any server, including monetized ones, and include the unmodified zip in any modpack that credits Profet and links here. You can also feature it in videos and modify it for your own world or server.
- **Please don't** re-upload Enchanted Veinminer or a modified version of it elsewhere, sell it, or present it as your own.

The full terms are in the `LICENSE` file inside the zip. For anything else, just ask.
