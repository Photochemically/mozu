// Stage with the complete L13 bridge release. Never restrict ordinary wooden/metal doors.
ServerEvents.recipes(function (event) {
  if (!Platform.isLoaded('expedition_bridge')) return
  // Includes recipes added by other mods that produce native dimensional door wrappers.
  event.remove({ output: /^dimdoors:item_ag_dim_.+$/ })
  event.shaped('dimdoors:item_ag_dim_minecraft_iron_door', ['I I', 'IPI', 'I I'], {
    I: 'minecraft:iron_block', P: 'minecraft:ender_pearl'
  }).id('expedition_bridge:iron_dimensional_door')
  event.shaped('dimdoors:item_ag_dim_dimdoors_quartz_door', ['QQQ', 'QPQ', 'QQQ'], {
    Q: 'minecraft:quartz_block', P: 'minecraft:ender_pearl'
  }).id('expedition_bridge:quartz_dimensional_door')
})
