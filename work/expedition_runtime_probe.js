// Temporary read-only integration probe. Not part of the release package.
var expeditionProbeRan = false;
PlayerEvents.tick(event => {
  if (expeditionProbeRan) return;
  expeditionProbeRan = true;
  event.server.scheduleInTicks(60, () => {
    var RL = Java.loadClass('net.minecraft.resources.ResourceLocation');
    var MM = Java.loadClass('net.silentchaos512.gear.gear.material.MaterialManager');
    var checks = [
      ['dark_metal', 'born_in_chaos_v1:dark_metal_ingot'],
      ['monster_hide', 'born_in_chaos_v1:monster_skin'],
      ['stable_fabric', 'dimdoors:stable_fabric']
    ];
    checks.forEach(pair => {
      try {
        var mat = MM.from(Item.of(pair[1]));
        if (mat == null || String(mat.getId()) !== 'expedition:' + pair[0]) throw Error('material mismatch ' + mat);
        console.info('[EXPEDITION_CHECK] MATERIAL PASS ' + mat.getId());
      } catch (e) { console.error('[EXPEDITION_CHECK] MATERIAL FAIL ' + pair[0] + ' ' + e); }
    });
    ['pistol_casings','rifle_casings','load_9mm','load_556','field_guide'].forEach(id => {
      try {
        var result = event.server.getRecipeManager().byKey(new RL('expedition:' + id));
        if (!result.isPresent()) throw Error('recipe missing');
        var recipe = result.get();
        var stack = recipe.getResultItem(event.server.registryAccess());
        if (stack.isEmpty()) throw Error('empty output');
        if (id === 'load_9mm' && String(stack.nbt.AmmoId) !== 'tacz:9mm') throw Error('wrong 9mm data');
        if (id === 'load_556' && String(stack.nbt.AmmoId) !== 'tacz:556x45') throw Error('wrong rifle data');
        if (id === 'load_9mm' || id === 'load_556') {
          var isPistol = id === 'load_9mm';
          var casing = Item.of(isPistol ? 'kubejs:expedition_pistol_casing_batch' : 'kubejs:expedition_rifle_casing_batch');
          if (!recipe.getProcessedItem().test(casing) || !recipe.getRequiredHeldItem().test(Item.of('minecraft:gunpowder')) || recipe.shouldKeepHeldItem()) throw Error('deployer input/consumption mismatch');
          var IAmmo = Java.loadClass('com.tacz.guns.api.item.IAmmo');
          var ammoApi = IAmmo.getIAmmoOrNull(stack);
          var gun = TimelessItem.of(new RL(isPistol ? 'tacz:glock_17' : 'tacz:m4a1'));
          var wrongGun = TimelessItem.of(new RL(isPistol ? 'tacz:m4a1' : 'tacz:glock_17'));
          // TaCZ 1.1.8: isAmmoOfGun expects (gunStack, ammoStack).
          if (ammoApi == null || !ammoApi.isAmmoOfGun(gun, stack) || ammoApi.isAmmoOfGun(wrongGun, stack)) throw Error('gun ammunition matching failed');
          console.info('[EXPEDITION_CHECK] AMMO API PASS ' + id + ' correct gun accepted; wrong caliber rejected; deployer consumes powder');
        }
        console.info('[EXPEDITION_CHECK] RECIPE PASS ' + id + ' output=' + stack + (id.indexOf('load_') === 0 ? ' ammo=' + stack.nbt.AmmoId : ''));
      } catch(e) { console.error('[EXPEDITION_CHECK] RECIPE FAIL ' + id + ' ' + e); }
    });
    try {
      var r = event.server.getResourceManager().getResource(new RL('lostcities:lostcities/conditions/chestloot.json'));
      if (!r.isPresent()) throw Error('city selector missing');
      console.info('[EXPEDITION_CHECK] CITY RESOURCE ' + r.get().sourcePackId());
    } catch(e) { console.error('[EXPEDITION_CHECK] CITY FAIL ' + e); }
    try {
      var LP = Java.loadClass('net.minecraft.world.level.storage.loot.LootParams$Builder');
      var LCP = Java.loadClass('net.minecraft.world.level.storage.loot.parameters.LootContextParams');
      var LCPS = Java.loadClass('net.minecraft.world.level.storage.loot.parameters.LootContextParamSets');
      var params = new LP(event.player.serverLevel()).withParameter(LCP.ORIGIN, event.player.position()).create(LCPS.CHEST);
      var loot = event.server.getLootData().getLootTable(new RL('expedition:city_wrappers/lostcities/chests/raildungeonchest'));
      var supplies = 0;
      for (var attempt = 0; attempt < 20; attempt++) {
        var stacks = loot.getRandomItems(params);
        for (var ix = 0; ix < stacks.size(); ix++) {
          var lootItem = stacks.get(ix);
          if (String(lootItem.id).indexOf('immersiveengineering:') === 0 || String(lootItem.id) === 'create:brass_ingot' || String(lootItem.id) === 'tacz:ammo') supplies++;
        }
      }
      if (supplies === 0) throw Error('no integration supplies generated in 20 draws');
      console.info('[EXPEDITION_CHECK] LOOT PASS 20 railway rolls; supply stacks=' + supplies);
    } catch(e) { console.error('[EXPEDITION_CHECK] LOOT FAIL ' + e); }
    console.info('[EXPEDITION_CHECK] FINISHED');
  });
});
