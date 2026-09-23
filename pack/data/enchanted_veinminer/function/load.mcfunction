data modify storage enchanted_veinminer:meta version set value "1.0.0"
scoreboard objectives add enchanted_veinminer.data dummy
scoreboard objectives add enchanted_veinminer.left minecraft.custom:minecraft.leave_game
scoreboard players set #registered enchanted_veinminer.data 0
function #enchanted_veinminer:registered
execute store result score #load enchanted_veinminer.data run time query gametime
schedule function enchanted_veinminer:check 1t replace
