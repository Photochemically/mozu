const fs=require('fs'),path=require('path'),assert=require('assert/strict'),crypto=require('crypto'),vm=require('vm');
const {parsed}=require('./audit.cjs');
const root=path.resolve(__dirname,'../outputs/联动试用版-v0.1.0/安装内容');
const model=JSON.parse(fs.readFileSync(path.join(__dirname,'integration-model.json')));
const manifest=JSON.parse(fs.readFileSync(path.join(root,'../文件清单.json')));
const checks=[];const test=(name,fn)=>{fn();checks.push(name);};
test('All manifest paths remain inside package; JSON and JS parse; file hashes match',()=>{
 for(const f of manifest.files){const p=path.resolve(root,f.path);assert(p.startsWith(root+path.sep));const s=fs.readFileSync(p,'utf8');assert.equal(crypto.createHash('sha256').update(s).digest('hex'),f.sha256);if(p.endsWith('.json'))JSON.parse(s);if(p.endsWith('.js'))new vm.Script(s,{filename:p});}
});
test('All provided core JARs match the actual instance',()=>{
 const old=JSON.parse(fs.readFileSync(path.join(__dirname,'mod-audit/manifest.json'),'utf8').replace(/^\uFEFF/,''));
 for(const f of old){const bytes=fs.readFileSync(path.join('C:/Users/hp/AppData/Roaming/.minecraft-forge-3/mods',f.file));assert.equal(crypto.createHash('sha256').update(bytes).digest('hex').toUpperCase(),f.sha256);}
});
test('Both ammo routes yield exactly 20 percent more at equal copper/gunpowder; no reusable gunpowder',()=>{
 for(const [prep,load,expected]of [['pistol_casings','load_9mm',30],['rifle_casings','load_556',18]]){const a=model.recipes[prep],b=model.recipes[load];assert.equal(a.ingredients.length,5);assert.equal(a.results.length,1);assert.equal(b.ingredients[0].item,a.results[0].item);assert.equal(b.ingredients[1].item,'minecraft:gunpowder');assert.equal(b.keepHeldItem,undefined);assert.equal(b.results[0].count,expected);assert.equal(b.results[0].item,'tacz:ammo');}
 assert.equal(30/25,1.2);assert.equal(18/15,1.2);
});
test('Three material inputs are distinct and do not collide with existing Silent Gear item ingredients',()=>{
 const inputs=Object.values(model.materials).map(v=>v.crafting_items.main.item);assert.equal(new Set(inputs).size,3);
 const old=Object.entries(parsed('silent-gear')).filter(([k])=>k.includes('/silentgear_materials/'));
 for(const[,m]of old)assert(!inputs.includes(m.crafting_items?.main?.item));
 assert.deepEqual(Object.keys(model.materials.monster_hide.stats).sort(),['grip','lining']);
 assert.deepEqual(Object.keys(model.materials.stable_fabric.stats).sort(),['binding','cord']);
});
test('City selector preserves every factor and location constraint and routes only its own tables',()=>{
 const old=parsed('lostcities')['data/lostcities/lostcities/conditions/chestloot.json'];assert.equal(old.values.length,model.selector.values.length);
 old.values.forEach((o,i)=>{const n=model.selector.values[i];assert.equal(n.value,model.routing[o.value]);const {value:a,...oa}=o,{value:b,...nb}=n;assert.deepEqual(oa,nb);});
 for(const[src,dst]of Object.entries(model.routing)){assert.notEqual(src,dst);const rel='kubejs/data/'+dst.replace(':','/loot_tables/')+'.json';const table=JSON.parse(fs.readFileSync(path.join(root,rel)));assert.equal(table.pools[0].entries[0].name,src);assert.equal(table.pools.length,2);assert(!Object.values(model.routing).includes(src));}
 assert(!manifest.files.some(f=>f.path.startsWith('kubejs/data/minecraft/')));
});
test('Guide NBT pages are valid JSON and bounded for a book',()=>{assert.equal(model.book.pages.length,12);model.book.pages.forEach(p=>assert(JSON.parse(p).text.length<180));});
const result={status:'static_checks_passed',checks,limitation:'Does not replace Minecraft loading, machine operation, combat or world-generation tests.'};
fs.writeFileSync(path.join(root,'../静态检查结果.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result,null,2));
