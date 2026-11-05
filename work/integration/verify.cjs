// Static inspection only. Does not load Minecraft, Rhino, Forge or a world.
const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const assert = require('node:assert/strict'), vm = require('node:vm');
const {assemble} = require('./build.cjs');
const config = require('./config.json'), references = require('./references.json');
const {parsed, raw} = require('../audit.cjs');
const built = assemble(config);
const output = path.resolve(__dirname,`../../outputs/联动试用版-v${config.version}`);
const root = path.join(output,'安装内容');
const manifest = JSON.parse(fs.readFileSync(path.join(output,'文件清单.json')));
const checks = [];
function test(name, fn) { fn(); checks.push(name); }
const hash = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
const walk = dir => fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);
test('源码与发布文件一致；JSON、JavaScript 语法、路径和文件指纹通过', () => {
  assert.equal(manifest.files.length,built.files.size);
  assert.equal(walk(root).length,built.files.size);
  for (const entry of manifest.files) {
    const target = path.resolve(root,entry.path);
    assert(target.startsWith(root+path.sep));
    const bytes = fs.readFileSync(target);
    assert.deepEqual(bytes,Buffer.from(built.files.get(entry.path).content));
    assert.equal(hash(bytes),entry.sha256);
    if (target.endsWith('.json')) JSON.parse(bytes.toString('utf8'));
    if (target.endsWith('.js')) new vm.Script(bytes.toString('utf8'),{filename:target});
    if (target.endsWith('.png')) {
      assert.equal(bytes.readUInt32BE(16),16); assert.equal(bytes.readUInt32BE(20),16);
      assert(bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])));
    }
    assert(entry.modules.every(id=>built.modules.some(m=>m.id===id)));
  }
  for (const file of walk(__dirname).filter(f=>f.endsWith('.json')||f.endsWith('.cjs'))) {
    const s=fs.readFileSync(file,'utf8'); if(file.endsWith('.json'))JSON.parse(s);else new vm.Script(s,{filename:file});
  }
});
test('模块迁移保留第一阶段所有配方、材料和城市规则；覆盖范围仍限定在联动文件', () => {
  const previous = JSON.parse(fs.readFileSync(path.resolve(__dirname,'../../outputs/联动试用版-v0.1.0/文件清单.json')));
  for (const entry of previous.files) {
    assert(built.files.has(entry.path));
    if (entry.path.includes('/data/')) assert.equal(hash(built.files.get(entry.path).content),entry.sha256,entry.path);
  }
  for (const rel of built.files.keys()) {
    if (rel.includes('/data/') && !rel.startsWith('kubejs/data/expedition/')) assert.equal(rel,'kubejs/data/lostcities/lostcities/conditions/chestloot.json');
    assert(!rel.includes('runtime_probe'));
  }
});
const sourceData = Object.values(raw).flatMap(mod=>Object.entries(mod).filter(([p])=>p.endsWith('.json')).flatMap(([p,s])=>{try{return [[p,JSON.parse(s)]]}catch{return []}}));
const indexes = fs.readdirSync(path.join(__dirname,'../mod-audit')).filter(f=>f.endsWith('.index.txt')).flatMap(f=>fs.readFileSync(path.join(__dirname,'../mod-audit',f),'utf8').split(/\r?\n/));
const knownItems = new Set(indexes.flatMap(p=>{const m=p.match(/^assets\/([^/]+)\/models\/item\/(.+)\.json$/);return m?[m[1]+':'+m[2]]:[]}));
// A few items have custom model loaders; language keys are additional local resource evidence.
for(const[p,v]of sourceData.filter(([p])=>/\/lang\/en_us\.json$/.test(p))) for(const key of Object.keys(v)) {
  const m=key.match(/^(?:item|block)\.([^.]+)\.(.+)$/);if(m)knownItems.add(m[1]+':'+m[2]);
}
for(const id of ['copper_ingot','iron_ingot','gunpowder','string','paper','flint','book','written_book'])knownItems.add('minecraft:'+id);
for(const id of built.items.keys())knownItems.add('kubejs:'+id);
function allItemRefs(value,result = new Set()) {
  if(!value || typeof value!=='object')return result;
  for(const[key,child]of Object.entries(value)) {
    if(key==='item'&&typeof child==='string')result.add(child);
    else allItemRefs(child,result);
  }
  return result;
}
test('新增配方和材料的物品引用在本地模组资源中有依据；自定义物品均已定义',()=>{
  for(const id of allItemRefs({recipes:built.recipes,materials:built.materials}))assert(knownItems.has(id),id);
  for (const item of built.items.values()) {
    const match=item.texture.match(/^expedition:item\/([a-z0-9_]+)$/);
    if(match)assert(built.files.has(`kubejs/assets/expedition/textures/item/${match[1]}.png`),item.texture);
  }
  for(const m of built.modules) for(const id of Object.keys(m.tooltips||{}))assert(knownItems.has(id),id);
  const knownTags = new Set(sourceData.flatMap(([p])=>{const m=p.match(/^data\/([^/]+)\/tags\/items\/(.+)\.json$/);return m?[m[1]+':'+m[2]]:[]}));
  function tags(v){if(v&&typeof v==='object')for(const[k,x]of Object.entries(v)){if(k==='tag')assert(knownTags.has(x),x);else tags(x);}}
  // The cache contains mod JARs, not Forge's base copper tags. Unchanged v0.1
  // recipes are covered by the migration hash check; inspect new recipes here.
  for (const mod of built.modules.filter(m=>['resource_processing','astral_fiber','apotheosis_support'].includes(m.id))) tags(mod.recipes);
});
test('新增配方沿用原生格式；数量、加热条件、模具与织造参数有效',()=>{
  const stage2=built.modules.filter(m=>['resource_processing','astral_fiber','apotheosis_support'].includes(m.id));
  for(const mod of stage2)for(const[id,r]of Object.entries(mod.recipes)){
    const outputs=r.results || [r.result];
    assert(outputs.every(v=>v?.item && Number.isInteger(v.count??1)&&(v.count??1)>0&&(v.count??1)<=64),id);
    if(r.type.startsWith('create:')){
      assert(Array.isArray(r.ingredients)&&r.ingredients.length>0&&r.ingredients.length<=64,id);
      assert(r.results.length<=4);
      if(r.type==='create:mixing')assert.equal(r.heatRequirement,'heated');
      if(r.type==='create:pressing')assert.equal(r.ingredients.length,1);
    }else if(r.type==='immersiveengineering:arc_furnace'){
      assert(r.input.item&&r.energy>0&&r.time>0);assert.deepEqual(r.additives,[]);
    }else if(r.type==='immersiveengineering:metal_press'){
      assert(r.input.item&&r.energy>0);assert.equal(r.mold,'immersiveengineering:mold_plate');
    }else if(r.type==='dimdoors:shaped_tesselating'){
      assert(r.weavingtime>0);assert(r.pattern.length>0&&r.pattern.length<=3);
      const width=r.pattern[0].length;assert(width>0&&width<=3&&r.pattern.every(s=>s.length===width));
      for(const char of r.pattern.join(''))assert(char===' '||r.key[char]);
    }else assert.fail(`Unexpected type ${r.type}`);
  }
});
function inputs(r){
  if(r.pattern)return r.pattern.join('').split('').filter(x=>x!==' ').map(x=>r.key[x]);
  return r.ingredients || [r.input || r.ingredient];
}
function outputs(r){return r.results || [typeof r.result==='string'?{item:r.result}:r.result];}
function quantities(ingredients){const result={};for(const i of ingredients)result[i.item]=(result[i.item]||0)+(i.count||1);return result;}
test('暗金属新增加工与原有碎片、金属堆、粒、锭、块转换均守恒',()=>{
  const mass=Object.fromEntries(Object.entries({pieceofdarkmetal:1,pileof_dark_metal:9,dark_metal_nugget:1,dark_metal_ingot:9,dark_metal_block:81}).map(([id,n])=>['born_in_chaos_v1:'+id,n]));
  let checked=0;
  for(const r of [...Object.values(references).filter(r=>r.type?.startsWith('minecraft:')),...Object.values(built.modules.find(m=>m.id==='resource_processing').recipes)]){
    const a=inputs(r),b=outputs(r);
    if(!a.every(i=>i?.item in mass)||!b.every(i=>i?.item in mass))continue;
    const sum=values=>values.reduce((s,i)=>s+mass[i.item]*(i.count||1),0);
    assert.equal(sum(a),sum(b));checked++;
  }
  assert.equal(checked,10);
});
test('两种星纤维加工消耗一致；神化增产保留核心材料成本与冒险模块条件',()=>{
  const fiber=built.modules.find(m=>m.id==='astral_fiber').recipes;
  assert.deepEqual(quantities(inputs(fiber.astral_fiber_weaving)),quantities(inputs(fiber.astral_fiber_mixing)));
  assert.deepEqual(outputs(fiber.astral_fiber_weaving),outputs(fiber.astral_fiber_mixing));
  const apo=built.modules.find(m=>m.id==='apotheosis_support').recipes;
  const condition=references['data/apotheosis/recipes/sigil_of_rebirth.json'].conditions;
  for(const r of Object.values(apo))assert.deepEqual(r.conditions,condition);
  for(const name of ['pressing','metal_press']){
    const r=apo['sigil_blank_'+name];assert.deepEqual(quantities(inputs(r)),{'apotheosis:gem_fused_slate':1});
    assert.deepEqual(outputs(r),[{item:'kubejs:expedition_sigil_blank'}]);
  }
  for(const name of ['rebirth','unnaming']){
    const original=references[`data/apotheosis/recipes/sigil_of_${name}.json`];
    const newer=apo[`sigil_${name}_mixing`];
    const cost=quantities(inputs(newer));cost['apotheosis:gem_fused_slate']=cost['kubejs:expedition_sigil_blank'];delete cost['kubejs:expedition_sigil_blank'];
    assert.deepEqual(cost,quantities(inputs(original)));
    assert.equal(newer.results[0].count,original.result.count+1);
  }
  const newOutputs=Object.values(apo).flatMap(outputs).map(i=>i.item);
  assert(!newOutputs.includes('apotheosis:gem_dust')&&!newOutputs.includes('apotheosis:gem_fused_slate'));
});
test('新材料限定部件种类；特性与属性来自已有材料系统；输入没有现成材料冲突',()=>{
  const existing=sourceData.filter(([p])=>p.includes('/silentgear_materials/')).map(([,v])=>v);
  const traits=new Set(existing.flatMap(m=>Object.values(m.traits||{}).flat().map(t=>t.name)));
  const stats=new Set(existing.flatMap(m=>Object.values(m.stats||{}).flatMap(s=>Object.keys(s))));
  const textures=new Set(existing.flatMap(m=>Object.values(m.model||{}).flat().map(t=>t.texture)));
  for(const name of ['astral_fiber','mutant_bone']){
    const m=built.materials[name];
    for(const key of Object.values(m.stats).flatMap(s=>Object.keys(s)))assert(stats.has(key),key);
    for(const trait of Object.values(m.traits).flat())assert(traits.has(trait.name),trait.name);
    for(const layer of Object.values(m.model).flat())assert(textures.has(layer.texture),layer.texture);
    for(const id of allItemRefs(m.crafting_items))assert(!existing.some(e=>allItemRefs(e.crafting_items).has(id)),id);
  }
  assert.deepEqual(Object.keys(built.materials.astral_fiber.stats).sort(),['binding','cord']);
  assert.deepEqual(Object.keys(built.materials.mutant_bone.stats),['rod']);
  assert.equal(built.materials.mutant_bone.crafting_items.main,undefined);
  assert.deepEqual(Object.keys(built.materials.mutant_bone.crafting_items.subs),['rod']);
});
test('逐个关闭模块后仍可组装且没有缺失自定义物品；指南只收录启用模块页面',()=>{
  for(const id of Object.keys(config.modules)){
    const c={...config,modules:{...config.modules,[id]:false}};
    const variant=assemble(c);assert(!variant.modules.some(m=>m.id===id));
    for(const ref of allItemRefs({recipes:variant.recipes,materials:variant.materials}))if(ref.startsWith('kubejs:'))assert(variant.items.has(ref.slice(7)),ref);
    if(id==='field_guide')assert.equal(variant.book,null);
    else assert.equal(variant.book.pages.length,2+variant.modules.reduce((n,m)=>n+(m.guidePages||[]).length,0));
  }
});
test('指南页格式、长度与更新标记有效；发布包不含运行探针',()=>{
  assert(built.book.pages.length<100);
  built.book.pages.forEach(p=>assert(JSON.parse(p).text.length<180));
  assert(built.files.get('kubejs/server_scripts/expedition_guide.js').content.includes('expeditionGuideV'+(config.guideVersion||config.version).replaceAll('.','')));
  assert(!manifest.files.some(f=>f.path.includes('probe')));
  const probe=fs.readFileSync(path.resolve(__dirname,'../expedition_runtime_probe.js'),'utf8');
  new vm.Script(probe);assert(probe.includes('isAmmoOfGun(gun, stack)'));assert(!probe.includes('isAmmoOfGun(stack, gun)'));
});
const result={version:config.version,status:'static_checks_passed',checks,counts:{files:manifest.files.length,modules:built.modules.length,recipeJsons:Object.keys(built.recipes).length,materials:Object.keys(built.materials).length,guidePages:built.book.pages.length},limitation:`Only static checks. No Minecraft launch/reload, machine processing, combat, world generation or save roundtrip performed for v${config.version}.`};
fs.writeFileSync(path.join(output,'静态检查结果.json'),JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result,null,2));
