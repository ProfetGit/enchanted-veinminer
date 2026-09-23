scoreboard players set #problem enchanted_veinminer.data 0
execute unless score #base enchanted_veinminer.data = #load enchanted_veinminer.data run scoreboard players set #problem enchanted_veinminer.data 1
execute unless score #base_version enchanted_veinminer.data matches 10100.. run scoreboard players set #problem enchanted_veinminer.data 1
execute if score #registered enchanted_veinminer.data matches 0 run scoreboard players set #problem enchanted_veinminer.data 2
tag @a remove enchanted_veinminer.warned
function enchanted_veinminer:warn
