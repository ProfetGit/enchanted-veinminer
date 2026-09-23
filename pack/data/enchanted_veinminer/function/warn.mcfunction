execute if score #problem enchanted_veinminer.data matches 0 run return fail
tag @a[scores={enchanted_veinminer.left=1..}] remove enchanted_veinminer.warned
scoreboard players reset @a enchanted_veinminer.left
execute if score #problem enchanted_veinminer.data matches 1 run tellraw @a[tag=!enchanted_veinminer.warned] ["",{text:"✦ Enchanted Veinminer ",color:"light_purple"},{text:"needs the Veinminer data pack, version 1.1.0 or newer. Install or update it, then run /reload. Until then, the Veinminer enchantment does nothing. ",color:"gray"},{text:"[Get Veinminer]",color:"aqua",hover_event:{action:"show_text",value:"modrinth.com/datapack/vanilla-veinminer"},click_event:{action:"open_url",url:"https://modrinth.com/datapack/vanilla-veinminer"}}]
execute if score #problem enchanted_veinminer.data matches 2 run tellraw @a[tag=!enchanted_veinminer.warned] ["",{text:"✦ Enchanted Veinminer ",color:"light_purple"},{text:"was added without a restart, so the Veinminer enchantment doesn't exist yet. Reopen the world or restart the server. Until then, veinmining works without the enchantment.",color:"gray"}]
tag @a add enchanted_veinminer.warned
schedule function enchanted_veinminer:warn 2s replace
