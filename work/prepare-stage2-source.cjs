// One-time migration of the v0.1 definitions. Future edits belong in integration/modules.
const fs = require('node:fs');
const path = require('node:path');
const {parsed} = require('./audit.cjs');
const base = JSON.parse(fs.readFileSync(path.join(__dirname, 'integration-model.json')));
const dir = path.join(__dirname, 'integration');
const clone = value => JSON.parse(JSON.stringify(value));
function save(rel, value) {
  const file = path.join(dir, rel);
  if (fs.existsSync(file)) throw Error(`Refusing to overwrite maintained source: ${file}`);
  fs.mkdirSync(path.dirname(file), {recursive:true});
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n');
}
const modules = [];
function moduleFile(id, title, mods, data) {
  modules.push(id);
  save(`modules/${id}.json`, {id, title, requires:mods, dependsOn:[], ...data});
}
const oldPages = base.book.pages.map(page => JSON.parse(page).text);
const page = (title, body) => `${title}\n\n${body}`;
moduleFile('ammunition', '工业弹药', ['create','tacz'], {
  items:[
    {id:'expedition_pistol_casing_batch',name:'手枪弹壳批次',texture:'minecraft:item/copper_ingot'},
    {id:'expedition_rifle_casing_batch',name:'步枪弹壳批次',texture:'create:item/copper_sheet'}
  ],
  recipes:base.recipes,
  tooltips:{
    'kubejs:expedition_pistol_casing_batch':['5 铜锭在工作盆压制。','机械手消耗 1 火药 → 30 发 9mm。'],
    'kubejs:expedition_rifle_casing_batch':['5 铜板在工作盆压制。','机械手消耗 1 火药 → 18 发 5.56。']
  },
  guidePages:[oldPages[1],oldPages[2],oldPages[3]]
});
moduleFile('monster_materials', '暗金属与怪物皮装备', ['born_in_chaos_v1','silentgear'], {
  materials:{dark_metal:base.materials.dark_metal,monster_hide:base.materials.monster_hide},
  lang:{zh_cn:{'material.expedition.dark_metal':'暗金属','material.expedition.monster_hide':'怪物皮'},en_us:{'material.expedition.dark_metal':'Dark Metal','material.expedition.monster_hide':'Monster Hide'}},
  tooltips:{'born_in_chaos_v1:dark_metal_ingot':['远征联动：可作寂静装备主材、杆件；侧重耐用。'],'born_in_chaos_v1:monster_skin':['远征联动：可作寂静装备握柄、衬里。']},
  guidePages:[oldPages[5],oldPages[6]]
});
moduleFile('dimension_materials', '稳定构造装备', ['dimdoors','silentgear'], {
  materials:{stable_fabric:base.materials.stable_fabric},
  lang:{zh_cn:{'material.expedition.stable_fabric':'稳定构造'},en_us:{'material.expedition.stable_fabric':'Stable Fabric'}},
  tooltips:{'dimdoors:stable_fabric':['远征联动：可作寂静装备绑带、弓弦。']},
  guidePages:[oldPages[7]]
});
const cityResources = {};
for (const rel of base.written.filter(rel => rel.includes('/loot_tables/') || rel.includes('/lostcities/conditions/'))) {
  cityResources[rel] = JSON.parse(fs.readFileSync(path.join(__dirname,'../outputs/联动试用版-v0.1.0/安装内容',rel)));
}
moduleFile('city_supplies', '城市与铁路补给', ['lostcities','create','immersiveengineering','tacz'], {
  resources:cityResources, guidePages:[oldPages[8],oldPages[9]]
});
const item = id => ({item:id});
const repeat = (id, count) => Array.from({length:count}, () => item(id));
moduleFile('resource_processing', '暗金属工业加工', ['born_in_chaos_v1','create','immersiveengineering'], {
  recipes:{
    dark_metal_pile_compacting:{type:'create:compacting',ingredients:repeat('born_in_chaos_v1:pieceofdarkmetal',9),results:[item('born_in_chaos_v1:pileof_dark_metal')]},
    dark_metal_arc_smelting:{type:'immersiveengineering:arc_furnace',additives:[],energy:51200,input:item('born_in_chaos_v1:pileof_dark_metal'),results:[item('born_in_chaos_v1:dark_metal_ingot')],time:100}
  },
  tooltips:{'born_in_chaos_v1:pieceofdarkmetal':['远征联动：9 个碎片可在工作盆压成暗金属堆。'],'born_in_chaos_v1:pileof_dark_metal':['远征联动：电弧炉冶炼为 1 个暗金属锭。']},
  guidePages:[page('战利品进入工厂','9 个暗金属碎片在工作盆压制为 1 堆；沉浸工程电弧炉可将 1 堆炼成 1 锭。\n\n金属总量与原配方相同，适合接入自动加工线。暗金属仍需探索战斗获取。')]
});
const sg = parsed('silent-gear');
const fiber = clone(sg['data/silentgear/silentgear_materials/fine_silk.json']);
fiber.availability.tier = 3;
fiber.crafting_items = {main:item('kubejs:expedition_astral_fiber')};
fiber.name = {translate:'material.expedition.astral_fiber'};
fiber.stats = {binding:{durability:{mul1:0.10},magic_armor:{add:1}},cord:{ranged_speed:{add:0.10},ranged_damage:{mul1:0.04}}};
fiber.traits = {binding:[{name:'silentgear:flexible',level:4}],cord:[{name:'silentgear:flexible',level:2}]};
for (const layers of Object.values(fiber.model)) for (const layer of layers) layer.color = '#ADA6FF';
moduleFile('astral_fiber', '维度与星尘纤维', ['dimdoors','enigmaticlegacy','silentgear','create'], {
  items:[{id:'expedition_astral_fiber',name:'星纤维',texture:'minecraft:item/string'}],
  recipes:{
    astral_fiber_weaving:{type:'dimdoors:shaped_tesselating',pattern:['SAS'],key:{S:item('dimdoors:stable_fabric'),A:item('enigmaticlegacy:astral_dust')},result:{item:'kubejs:expedition_astral_fiber',count:2},weavingtime:400},
    astral_fiber_mixing:{type:'create:mixing',heatRequirement:'heated',ingredients:[...repeat('dimdoors:stable_fabric',2),item('enigmaticlegacy:astral_dust')],results:[{item:'kubejs:expedition_astral_fiber',count:2}]}
  },
  materials:{astral_fiber:fiber},
  lang:{zh_cn:{'material.expedition.astral_fiber':'星纤维'},en_us:{'material.expedition.astral_fiber':'Astral Fiber'}},
  tooltips:{'kubejs:expedition_astral_fiber':['2 稳定构造 + 1 星尘 → 2 星纤维。','维度织造或加热搅拌；用于寂静装备绑带、弓弦。'],'enigmaticlegacy:astral_dust':['远征联动：可与稳定构造制成星纤维。']},
  guidePages:[page('两次远征的交汇','2 个稳定构造与 1 个星尘制成 2 个星纤维。\n\n可在维度门织造设备中按“稳定构造—星尘—稳定构造”排成一行，或在机械动力工作盆中加热搅拌。'),page('星纤维的取舍','星纤维用于寂静装备绑带、弓弦。\n\n绑带偏耐久和少量魔法护甲；弓弦兼顾速度与少量伤害。细丝仍有自己的幸运、护甲或伤害优势。\n\n星尘仍通过原路线获取。')]
});
const condition = [{type:'apotheosis:module',module:'adventure'}];
const blank = 'kubejs:expedition_sigil_blank';
moduleFile('apotheosis_support', '神化辅助耗材', ['apotheosis','create','immersiveengineering'], {
  items:[{id:'expedition_sigil_blank',name:'符石坯片',texture:'minecraft:item/paper'}],
  recipes:{
    sigil_blank_pressing:{type:'create:pressing',conditions:condition,ingredients:[item('apotheosis:gem_fused_slate')],results:[item(blank)]},
    sigil_blank_metal_press:{type:'immersiveengineering:metal_press',conditions:condition,energy:2400,input:item('apotheosis:gem_fused_slate'),mold:'immersiveengineering:mold_plate',result:item(blank)},
    sigil_rebirth_mixing:{type:'create:mixing',conditions:condition,heatRequirement:'heated',ingredients:[...repeat(blank,6),...repeat('apotheosis:gem_dust',3)],results:[{item:'apotheosis:sigil_of_rebirth',count:7}]},
    sigil_unnaming_mixing:{type:'create:mixing',conditions:condition,heatRequirement:'heated',ingredients:[...repeat(blank,6),...repeat('minecraft:flint',3)],results:[{item:'apotheosis:sigil_of_unnaming',count:7}]}
  },
  tooltips:{'kubejs:expedition_sigil_blank':['1 宝石融合板岩经机械动力压制或金属冲压制成。','6 坯片 + 3 宝石粉/燧石，加热搅拌 → 7 新生/除名符石。'],'apotheosis:gem_fused_slate':['远征联动：可压成符石坯片，接入工业制作。']},
  guidePages:[page('符石的工业准备','1 个宝石融合板岩可由动力辊压机或装有板模具的金属冲压机制成 1 个符石坯片。\n\n板岩仍使用原来的宝石粉配方。压制不返还粉末，也不生产核心宝石。'),page('辅助耗材量产','工作盆加热搅拌：\n6 坯片 + 3 宝石粉 → 7 新生符石；\n6 坯片 + 3 燧石 → 7 除名符石。\n\n原手工配方同量材料产出 6 个。工业小幅增产，分解、重铸和宝石路线照常。')]
});
const bone = clone(sg['data/silentgear/silentgear_materials/bone.json']);
bone.availability.tier = 3;
bone.crafting_items = {subs:{rod:item('mutantmonsters:mutant_skeleton_limb')}};
bone.name = {translate:'material.expedition.mutant_bone'};
bone.stats = {rod:{durability:{mul1:0.20},melee_damage:{add:1},attack_speed:{add:-0.10},ranged_damage:{add:0.5}}};
bone.traits = {rod:[{name:'silentgear:sturdy',level:1},{name:'silentgear:flexible',level:2}]};
bone.model = Object.fromEntries(Object.entries(bone.model).filter(([key]) => key.startsWith('rod/')));
moduleFile('mutant_parts', '突变肢骨杆件', ['mutantmonsters','silentgear'], {
  materials:{mutant_bone:bone},
  lang:{zh_cn:{'material.expedition.mutant_bone':'突变肢骨'},en_us:{'material.expedition.mutant_bone':'Mutant Bone'}},
  tooltips:{'mutantmonsters:mutant_skeleton_limb':['远征联动：可作寂静装备杆件，偏耐久与伤害，略降攻速。','仍可用于原生突变骷髅装备。']},
  guidePages:[page('强敌带回的杆件','骷髅突变体肢骨可制作寂静装备杆件。\n\n侧重耐久和伤害，代价是少量攻速。它只作杆件，仍可制作原生突变装备。\n\n击败敌人后的特殊掉落流程保持原样。')]
});
moduleFile('field_guide', '指南与已有金属联动提示', ['create','immersiveengineering','silentgear'], {
  guide:{title:'远征与工坊',author:'联动指南',intro:page('远征与工坊','联动试用版 0.2.0\n\n探索带回材料，工业加工补给，装备支持下一次远征。\n\n本版增加暗金属加工、星纤维、工业符石和突变肢骨用途。'),closing:page('补领指南','用 1 本书和 1 个铜锭无序合成可补领本版指南。\n\n更新后首次进入世界会获赠新版。旧书内容不会自动变化。\n\n新增物品需要完整重启才能加载。')},
  tooltips:{'create:brass_ingot':['联动提示：已有寂静装备材料定义。'],'immersiveengineering:ingot_steel':['联动提示：寂静装备材料；机械动力可压板、切钢丝。'],'immersiveengineering:ingot_electrum':['联动提示：已有寂静装备材料定义。']},
  guidePages:[oldPages[4]]
});
save('config.json', {version:'0.2.0',modules:Object.fromEntries(modules.map(id => [id,true]))});
const references = {};
for (const [prefix, regex] of [
  ['born_',/\/recipes\/.*(darkmetal|dark_metal)/],
  ['silent-gear',/\/silentgear_materials\/(steel|leather|fine_silk|bone|blaze_rod|crimson_steel)\.json$/],
  ['Apotheosis',/\/recipes\/(sigil_of_rebirth|sigil_of_unnaming|gem_fused_slate)\.json$/],
  ['Immersive',/\/recipes\/(arcfurnace\/dust_iron|metalpress\/plate_steel)\.json$/],
  ['dimdoors',/\/recipes\/tesselating\/stable_fabric\.json$/],
  ['create-',/\/recipes\/mixing\/brass_ingot\.json$/],
  ['Mutant',/\/loot_tables\/entities\/mutant_skeleton\/limb\.json$/]
]) for (const [name, json] of Object.entries(parsed(prefix)).filter(([name]) => regex.test(name))) references[name] = json;
save('references.json', references);
save('reference-versions.json', JSON.parse(fs.readFileSync(path.join(__dirname,'mod-audit/manifest.json'),'utf8').replace(/^\uFEFF/,'')));
console.log(`Prepared ${modules.length} source modules. Build with integration/build.cjs.`);
