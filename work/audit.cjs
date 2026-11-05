const fs = require('fs');
const path = require('path');
const dir = path.join(__dirname, 'mod-audit');
const raw = {};
for (const f of fs.readdirSync(dir).filter(f => f.endsWith('.json') && f !== 'manifest.json')) {
  raw[f] = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8').replace(/^\uFEFF/, ''));
}
const mod = prefix => raw[Object.keys(raw).find(k => k.startsWith(prefix))];
const parsed = prefix => Object.fromEntries(Object.entries(mod(prefix)).filter(([k]) => k.endsWith('.json')).flatMap(([k,v]) => {try{return [[k,JSON.parse(v)]]}catch{return []}}));
const find = (prefix, re) => Object.entries(parsed(prefix)).filter(([k,v]) => re.test(k));
const emit = (label,v) => console.log(label + '\n' + JSON.stringify(v,null,2));
const mode = process.argv[2];
if (mode === 'overview') {
  for (const p of ['silent-gear','create-','Immersive','Enigmatic','born_','Mutant','dimdoors','Apotheosis']) {
    const data=parsed(p);
    emit(p+' matches',Object.keys(data).filter(k=>/silentgear_materials\/(brass|steel|electrum|constantan)|recipes\/.*(gunpowder|brass|electrum|constantan|steel|dark_metal|darkmetal|spiritual|etherium|astral|heart|skull)|loot_tables\/entities\/|loot_tables\/dungeon/.test(k)).slice(0,100));
    if(['Enigmatic','Mutant','dimdoors'].includes(p)) emit(p+' names',Object.entries(data).filter(([k])=>k.endsWith('/lang/en_us.json')).flatMap(([k,v])=>Object.entries(v).filter(([k])=>/^(item|block)\./.test(k))));
  }
}
if (mode === 'materials') {
  emit('Existing SG metals',find('silent-gear',/silentgear_materials\/(brass|steel|electrum|constantan|lead|copper|iron|bone|silver)\.json$/).map(([k,v])=>({path:k,ingredient:v.crafting_items,stats:v.stats.main,traits:v.traits})));
  for (const p of ['create-','Immersive']) emit(p+' recipe links',Object.entries(parsed(p)).filter(([k,v])=>k.includes('/recipes/') && /silentgear|born_in_chaos|enigmaticlegacy|tacz/.test(JSON.stringify(v))).map(([k])=>k));
  emit('Existing SG integration',Object.entries(parsed('silent-gear')).filter(([k,v])=>/create:|immersiveengineering:|born_in_chaos|enigmaticlegacy:|tacz:/.test(JSON.stringify(v))).map(([k])=>k));
}
if (mode === 'recipes') {
  for (const [p,re] of [['create-',/recipes\/.*(gunpowder|brass_ingot|precision_mechanism|cinder_flour|compat\/immersiveengineering)/],['Immersive',/recipes\/.*(gunpowder|alloysmelter\/|metalpress\/.*plate|blastfurnace\/steel)/],['Enigmatic',/recipes\/(etherium|astral|.*ingot|.*nugget|.*dust|.*scroll|.*ring).*json$/],['born_',/recipes\/.*(dark_metal|darkmetal|spiritual|skull)/],['Mutant',/loot_tables\/entities/]]) emit(p,find(p,re).slice(0,35));
}
if (mode === 'loot') {
  for (const [p,re] of [['lostcities',/(loot_tables|conditions)/],['dimdoors',/loot_tables\/dungeon|recipes\/.*(fabric|thread|door)/],['Apotheosis',/recipes\/(reforging|salvaging)|loot_modifiers|affix_loot_entries/],['Enigmatic',/loot_tables|loot_modifiers/]]) emit(p,find(p,re).slice(0,20));
}
module.exports={raw,mod,parsed,find,emit};
