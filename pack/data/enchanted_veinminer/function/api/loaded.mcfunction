scoreboard objectives add enchanted_veinminer.data dummy
execute store result score #base enchanted_veinminer.data run time query gametime
execute store result score #base_version enchanted_veinminer.data run data get storage veinminer:meta version_id
data modify storage veinminer:meta requires append value {text:"Your pickaxe needs the ",color:"gray",extra:[{text:"Veinminer",color:"light_purple",hover_event:{action:"show_text",value:"From the Enchanted Veinminer add-on. Find it in the enchanting table, from librarians, or in loot."}},{text:" enchantment. "}]}
