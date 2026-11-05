const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const {parsed} = require('./audit.cjs');
const out=path.resolve(__dirname,'../outputs/联动试用版-v0.1.0');
const root=path.join(out,'安装内容');
const written=[];
function write(rel,text){const p=path.join(root,rel);fs.mkdirSync(path.dirname(p),{recursive:true});fs.writeFileSync(p,text);written.push(rel.replaceAll('\\','/'));}
function json(rel,data){write(rel,JSON.stringify(data,null,2)+'\n');}
const clone=o=>JSON.parse(JSON.stringify(o));
const sg=parsed('silent-gear');
const ns='expedition';
const pistol='kubejs:expedition_pistol_casing_batch';
const rifle='kubejs:expedition_rifle_casing_batch';
write('kubejs/startup_scripts/expedition_items.js',`// Expedition integration 0.1.0. Full restart required.
StartupEvents.registry('item', event => {
  event.create('expedition_pistol_casing_batch').displayName('手枪弹壳批次').texture('minecraft:item/copper_ingot');
  event.create('expedition_rifle_casing_batch').displayName('步枪弹壳批次').texture('create:item/copper_sheet');
});
`);
const recipes={
 pistol_casings:{type:'create:compacting',ingredients:Array.from({length:5},()=>({tag:'forge:ingots/copper'})),results:[{item:pistol}]},
 rifle_casings:{type:'create:compacting',ingredients:Array.from({length:5},()=>({tag:'forge:plates/copper'})),results:[{item:rifle}]},
 load_9mm:{type:'create:deploying',ingredients:[{item:pistol},{item:'minecraft:gunpowder'}],results:[{item:'tacz:ammo',count:30,nbt:{AmmoId:'tacz:9mm'}}]},
 load_556:{type:'create:deploying',ingredients:[{item:rifle},{item:'minecraft:gunpowder'}],results:[{item:'tacz:ammo',count:18,nbt:{AmmoId:'tacz:556x45'}}]}
};
for(const[k,v]of Object.entries(recipes))json(`kubejs/data/${ns}/recipes/${k}.json`,v);
const dark=clone(sg['data/silentgear/silentgear_materials/steel.json']);
dark.crafting_items={main:{item:'born_in_chaos_v1:dark_metal_ingot'}};
dark.name={translate:'material.expedition.dark_metal'};
Object.assign(dark.stats.main,{durability:650,armor_durability:22,enchantment_value:7,harvest_speed:5,attack_speed:-0.25,armor:18,'armor/helmet':3,'armor/chestplate':7,'armor/leggings':5,'armor/boots':3});
dark.traits={main:[{name:'silentgear:malleable',level:4}],rod:[{name:'silentgear:malleable',level:4}]};
for(const layers of Object.values(dark.model))for(const layer of layers)layer.color='#554963';
const skin=clone(sg['data/silentgear/silentgear_materials/leather.json']);
skin.availability.tier=2;skin.crafting_items={main:{item:'born_in_chaos_v1:monster_skin'}};skin.name={translate:'material.expedition.monster_hide'};
skin.stats={grip:{repair_efficiency:{mul1:0.15},attack_speed:{add:0.10}},lining:{}};
skin.traits={grip:[{name:'silentgear:flexible',level:3}],lining:[{name:'silentgear:flexible',level:4}]};
for(const k of Object.keys(skin.model))if(!k.startsWith('grip/')&&!k.startsWith('lining/')&&k!=='main/fragment')delete skin.model[k];
for(const layers of Object.values(skin.model))for(const layer of layers)layer.color='#6B6552';
const fabric=clone(sg['data/silentgear/silentgear_materials/fine_silk.json']);
fabric.crafting_items={main:{item:'dimdoors:stable_fabric'}};fabric.name={translate:'material.expedition.stable_fabric'};
fabric.stats={binding:{durability:{mul1:0.05}},cord:{ranged_speed:{add:0.10}}};
fabric.traits={binding:[{name:'silentgear:flexible',level:3}],cord:[{name:'silentgear:flexible',level:2}]};
for(const layers of Object.values(fabric.model))for(const layer of layers)layer.color='#B5A4E8';
const materials={dark_metal:dark,monster_hide:skin,stable_fabric:fabric};
for(const[k,v]of Object.entries(materials))json(`kubejs/data/${ns}/silentgear_materials/${k}.json`,v);
json('kubejs/assets/expedition/lang/zh_cn.json',{'material.expedition.dark_metal':'暗金属','material.expedition.monster_hide':'怪物皮','material.expedition.stable_fabric':'稳定构造'});
json('kubejs/assets/expedition/lang/en_us.json',{'material.expedition.dark_metal':'Dark Metal','material.expedition.monster_hide':'Monster Hide','material.expedition.stable_fabric':'Stable Fabric'});
function entry(item,weight,min,max,nbt){const functions=[{function:'minecraft:set_count',count:{type:'minecraft:uniform',min,max}}];if(nbt)functions.push({function:'minecraft:set_nbt',tag:nbt});return {type:'minecraft:item',name:item,weight,functions};}
const cityEntries=[entry('minecraft:copper_ingot',12,2,5),entry('minecraft:iron_ingot',10,1,4),entry('create:zinc_ingot',8,1,3),entry('minecraft:gunpowder',8,1,3),entry('create:brass_ingot',4,1,2),entry('immersiveengineering:ingot_steel',2,1,2),entry('tacz:ammo',4,4,10,'{AmmoId:"tacz:9mm"}'),entry('tacz:ammo',2,3,6,'{AmmoId:"tacz:556x45"}')];
const railEntries=[entry('immersiveengineering:ingot_steel',7,1,3),entry('immersiveengineering:plate_steel',6,1,3),entry('create:brass_ingot',6,1,3),entry('immersiveengineering:ingot_electrum',3,1,2),entry('minecraft:gunpowder',6,2,4),entry('tacz:ammo',3,6,12,'{AmmoId:"tacz:9mm"}'),entry('tacz:ammo',2,4,8,'{AmmoId:"tacz:556x45"}')];
json(`kubejs/data/${ns}/loot_tables/city_bonus.json`,{type:'minecraft:chest',pools:[{rolls:1,conditions:[{condition:'minecraft:random_chance',chance:0.60}],entries:cityEntries}]});
json(`kubejs/data/${ns}/loot_tables/rail_bonus.json`,{type:'minecraft:chest',pools:[{rolls:1,conditions:[{condition:'minecraft:random_chance',chance:0.75}],entries:railEntries}]});
const selector=clone(parsed('lostcities')['data/lostcities/lostcities/conditions/chestloot.json']);
const routing={};
for(const choice of selector.values){
 const source=choice.value;
 if(!routing[source]){
  const slug=source.replace(':','/');const routed=`${ns}:city_wrappers/${slug}`;routing[source]=routed;
  json(`kubejs/data/${ns}/loot_tables/city_wrappers/${slug}.json`,{type:'minecraft:chest',pools:[{rolls:1,entries:[{type:'minecraft:loot_table',name:source}]},{rolls:1,entries:[{type:'minecraft:loot_table',name:source.endsWith('/raildungeonchest')?`${ns}:rail_bonus`:`${ns}:city_bonus`}]}]});
 }
 choice.value=routing[source];
}
json('kubejs/data/lostcities/lostcities/conditions/chestloot.json',selector);
const pages=[
 ['远征与工坊','联动试用版 0.1.0\n\n探索带回材料，工业加工补给，装备支持下一次远征。\n\n本版保留原枪械伤害、手工弹药配方和原有获取路线。'],
 ['9mm 工业弹药','1. 将 5 个铜锭放进工作盆。\n2. 用机械动力动力辊压机压成手枪弹壳批次。\n3. 用机械手消耗 1 个火药装填。\n\n产出：30 发 9mm。'],
 ['5.56 工业弹药','1. 将铜锭压成铜板。\n2. 工作盆中 5 个铜板压成步枪弹壳批次。\n3. 机械手消耗 1 个火药装填。\n\n产出：18 发 5.56。'],
 ['为什么建设工厂','相同铜与火药投入，工业路线比原手工配方多产 20%。\n\n自动化还有机器、动力、空间与加工时间的投入。少量用枪仍可手工制作。'],
 ['金属已经相通','机械动力黄铜、沉浸工程钢和琥珀金已有寂静装备材料定义。\n\n机械动力还能加工沉浸工程钢板、钢丝。可混搭生产线。'],
 ['暗金属装备','Born in Chaos 暗金属锭现在可用于寂静装备主材和杆件。\n\n对比钢：更耐用，但加工速度、攻速或附魔适性有取舍。原生防具用途仍保留。'],
 ['怪物皮部件','怪物皮可以制作寂静装备握柄或衬里。\n\n握柄偏向维修效率，原生装备也仍可使用这些皮。\n\n它不是通用护甲主材。'],
 ['维度纤维','维度门的世界纤维可在原生织造设备中加工为稳定构造。\n\n稳定构造新增寂静装备绑带、弓弦用途。不会自动继承维度门原装备技能。'],
 ['城市补给','城市箱子保留原奖励，并有机会增加一份补给。\n\n一般建筑偏金属、火药和少量弹药；铁路偏工业物资。并非每个箱子必出。'],
 ['城市生成','需要在创建世界时启用失落城市相关预设，或进入已配置的城市维度。\n\n本版没有强制改变世界生成。奖励规则主要用于尚未生成奖励的箱子。'],
 ['后期养成','神化的宝石、分解和重铸，以及诡厄巫法的原有路线继续保留。\n\n本版没有把枪械当作普通弓，也没有改写诅咒配方。'],
 ['补领与反馈','用 1 本书和 1 个铜锭无序合成可补领本指南。\n\n测试时关注：弹药能否装填、新材料能否制件、城市箱子是否有补给。新增物品需要完整重启。']
].map(([title,body])=>JSON.stringify({text:title+'\n\n'+body}));
const book={title:'远征与工坊',author:'联动指南',generation:0,pages};
write('kubejs/server_scripts/expedition_guide.js',`// Expedition integration 0.1.0
const expeditionGuideData = ${JSON.stringify(book,null,2)};
ServerEvents.recipes(event => {
  event.shapeless(Item.of('minecraft:written_book', expeditionGuideData), ['minecraft:book', 'minecraft:copper_ingot']).id('expedition:field_guide');
});
PlayerEvents.loggedIn(event => {
  if (!event.player.persistentData.expeditionGuideV010) {
    event.player.give(Item.of('minecraft:written_book', expeditionGuideData));
    event.player.persistentData.expeditionGuideV010 = true;
  }
});
`);
write('kubejs/client_scripts/expedition_tooltips.js',`// Expedition integration 0.1.0
ItemEvents.tooltip(event => {
  event.add('${pistol}', ['5 铜锭在工作盆压制。', '机械手消耗 1 火药 -> 30 发 9mm。']);
  event.add('${rifle}', ['5 铜板在工作盆压制。', '机械手消耗 1 火药 -> 18 发 5.56。']);
  event.add('born_in_chaos_v1:dark_metal_ingot', '远征联动：可作寂静装备主材、杆件；侧重耐用。');
  event.add('born_in_chaos_v1:monster_skin', '远征联动：可作寂静装备握柄、衬里。');
  event.add('dimdoors:stable_fabric', '远征联动：可作寂静装备绑带、弓弦。');
  event.add('create:brass_ingot', '联动提示：已有寂静装备材料定义。');
  event.add('immersiveengineering:ingot_steel', '联动提示：寂静装备材料；机械动力可压板、切钢丝。');
  event.add('immersiveengineering:ingot_electrum', '联动提示：已有寂静装备材料定义。');
});
`);
const hashes=written.sort().map(rel=>({path:rel,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(root,rel))).digest('hex')}));
fs.writeFileSync(path.join(out,'文件清单.json'),JSON.stringify({version:'0.1.0',files:hashes},null,2)+'\n');
fs.writeFileSync(path.join(__dirname,'integration-model.json'),JSON.stringify({recipes,materials,routing,selector,book,written},null,2));
console.log(JSON.stringify({files:written.length,materials:Object.keys(materials).length,recipes:Object.keys(recipes).length,cityWrappers:Object.keys(routing).length,guidePages:pages.length}));
