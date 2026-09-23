schedule clear enchanted_veinminer:check
schedule clear enchanted_veinminer:warn
scoreboard objectives remove enchanted_veinminer.data
scoreboard objectives remove enchanted_veinminer.left
tag @a remove enchanted_veinminer.warned
data remove storage enchanted_veinminer:meta version
tellraw @s ["",{text:"✦ Enchanted Veinminer data removed. ",color:"light_purple"},{text:"Now delete EnchantedVeinminer-1.0.0.zip from the datapacks folder and reopen the world or restart the server. Veinminer then works without the enchantment again.",color:"gray"}]
