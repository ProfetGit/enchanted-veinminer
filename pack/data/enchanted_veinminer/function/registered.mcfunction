# This file fails to load while the enchantment isn't registered yet (pack added with /reload instead of a restart).
execute if items entity @s weapon.mainhand *[minecraft:enchantments~[{enchantments:"enchanted_veinminer:veinminer"}]] run return 1
scoreboard players set #registered enchanted_veinminer.data 1
