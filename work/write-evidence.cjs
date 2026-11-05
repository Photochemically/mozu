const fs = require('fs');
const path = require('path');
const {find} = require('./audit.cjs');
const dir=path.join(__dirname,'mod-audit');
const manifest=JSON.parse(fs.readFileSync(path.join(dir,'manifest.json'),'utf8').replace(/^\uFEFF/,''));
const selections=[
 ['create-',/^data\/create\/recipes\/(pressing\/compat\/immersiveengineering\/plate_steel|cutting\/compat\/immersiveengineering\/wire_steel|filling\/gunpowder|mixing\/brass_ingot)\.json$/],
 ['create-',/^data\/forge\/tags\/items\/(ingots|plates)\/brass\.json$/],
 ['Immersive',/^data\/forge\/tags\/items\/(ingots|plates)\/(steel|electrum)\.json$/],
 ['Immersive',/^data\/immersiveengineering\/recipes\/(crafting\/gunpowder_from_dusts|alloysmelter\/electrum|blastfurnace\/steel)\.json$/],
 ['silent-gear',/^data\/silentgear\/silentgear_materials\/(brass|steel|electrum|fine_silk)\.json$/],
 ['tacz',/\/data\/tacz\/recipes\/ammo\/(9mm|556x45|762x39|12g)\.json$/],
 ['born_',/^data\/born_in_chaos_v1\/recipes\/(dark_metalngot_k|dark_metal_nugget_craft|dark_metal_nugget_craft_2|dark_metal_nugget_craft_4|pileof_dark_metal_d|armor_plate_from_dark_metal_k|spiritual_dust_k)\.json$/],
 ['born_',/^data\/born_in_chaos_v1\/loot_tables\/entities\/bonescaller\.json$/],
 ['lostcities',/\/loot_tables\/chests\/|\/conditions\/chestloot\.json$/],
 ['dimdoors',/\/loot_tables\/dungeon_chest\.json$|\/recipes\/tesselating\/(stable_fabric|rift_pearl)\.json$/],
 ['Apotheosis',/^data\/apotheosis\/(recipes\/(reforging\/(common|rare|mythic)|salvaging\/common_material)|loot_modifiers\/(affix_conversion|affix_hook|affix_loot|gems))\.json$/],
 ['Enigmatic',/^data\/enigmaticlegacy\/recipes\/(twisted_heart|etherium_ingot|ender_rod)\.json$/],
 ['Mutant',/^data\/mutantmonsters\/loot_tables\/entities\/mutant_skeleton(\/limb)?\.json$/],
];
let out='# 模组联动核查证据\n\n日期：2026-11-01。来源为用户提供的 C:/mods/。这是静态核查记录，未完成游戏加载或玩法测试。\n\n';
out+='## 1. 文件清单与指纹\n\n| JAR | SHA-256 |\n|---|---|\n';
for(const x of manifest)out+=`| ${x.file} | \`${x.sha256}\` |\n`;
out+='\n## 2. 选定资源证据\n\n下列 JSON 均从上述 JAR 读取。为了便于核对，部分材料仅展示与本方案相关的字段，未将其描述为完整文件。掉落表权重不是直接的最终掉落概率。\n\n';
let count=0;
for(const[p,re]of selections){
 const rows=find(p,re);if(!rows.length)throw new Error('Missing evidence '+p+' '+re);
 const jar=manifest.find(x=>x.file.startsWith(p)).file;
 for(const[k,v]of rows){
  let data=v;
  if(k.includes('/silentgear_materials/'))data={type:v.type,availability:v.availability,crafting_items:v.crafting_items,stats:v.stats,traits:v.traits};
  out+=`### E${String(++count).padStart(2,'0')} · ${jar}\n\n内部路径：\`${k}\`\n\n\`\`\`json\n${JSON.stringify(data,null,2)}\n\`\`\`\n\n`;
 }
}
out+='## 3. 对应版本代码核查\n\n以下是对本地 JAR 使用 javap 读取的相关位置；行号属于本次检查文本，不是上游 Java 源码行号。只据此判断接口与处理流程存在，不据此宣称跨模组效果已实测。\n\n';
const checks=[
 ['tacz-kubejs.txt',/String gun_smith_table_crafting|String TimelessItem|Method.*setAmmoId|public void registerRecipeSchemas/,'TaCZ 内置配方与弹药构造接口'],
 ['tacz-common-events.txt',/String entityHurtByGunPre|String entityHurtByGunPost|String entityKillByGun|public default void setBaseAmount|public default void setHeadshotMultiplier/,'TaCZ 枪械伤害事件及可修改参数'],
 ['tacz-bullet.txt',/Field armorIgnore:F|private void tacAttackEntity|Method.*EntityHurtByGunEvent\$Pre.getBaseAmount/,'TaCZ 子弹独立伤害流程：不能视作普通近战属性计算'],
 ['apotheosis-categories.txt',/String bow$|String crossbow$|String sword$|instanceof.*BowItem|instanceof.*CrossbowItem|ItemStack.canPerformAction/,'Apotheosis 分类包含物品类型与工具动作检查；需要逐类测试枪械和模组装备'],
 ['incontrol-lostcities.txt',/public static boolean is(City|Street|Building|MultiBuilding)\(/,'In Control 对 Lost Cities 区域的判断接口'],
 ['enigmatic-cursed-recipe.txt',/instanceof.*ITaintable|InterfaceMethod.*ITaintable.isTainted/,'Enigmatic Legacy 诅咒配方对原料状态的检查']
];
for(const[f,re,label]of checks){
 const lines=fs.readFileSync(path.join(dir,f),'utf8').split(/\r?\n/);
 const hits=lines.flatMap((s,i)=>re.test(s)?[`${i+1}: ${s.trim()}`]:[]);
 if(!hits.length)throw new Error('Missing bytecode check '+f);
 out+=`### ${label}\n\n检查文本：\`${f}\`\n\n\`\`\`text\n${hits.slice(0,18).join('\n')}\n\`\`\`\n\n`;
}
out+='## 4. 证据的边界\n\n- 配方/标签定义存在，不等于启动实例中的最终配方未被配置或脚本覆盖。\n- 未取得完整实例、Forge 运行版本、外置枪包、现有配置与存档；未启动游戏。\n- 未证明 TaCZ 与 Apotheosis/饰品的全部战斗效果兼容；未穷尽所有事件监听器。\n- Silent Gear 材料文件中的参数不等于最终装备面板。\n- 特殊怪物可能通过死亡动画、实体碎片或其他代码生成奖励，普通空掉落表不代表没有奖励。\n- 本文中的原始文件是研究依据，不是应直接覆盖进实例的发布包。\n';
fs.writeFileSync(path.join(__dirname,'../outputs/模组联动核查证据.md'),out);
console.log(JSON.stringify({resourceEvidence:count,bytecodeChecks:checks.length,jars:manifest.length,characters:out.length}));
