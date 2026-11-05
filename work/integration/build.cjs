const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const defaults = require('./config.json');

function assemble(config = defaults) {
  if (!/^\d+\.\d+\.\d+$/.test(config.version)) throw Error('Invalid package version');
  const modules = [];
  for (const [id, enabled] of Object.entries(config.modules)) {
    if (!/^[a-z_]+$/.test(id) || typeof enabled !== 'boolean') throw Error(`Invalid module flag: ${id}`);
    if (!enabled) continue;
    const mod = JSON.parse(fs.readFileSync(path.join(__dirname,'modules',id+'.json'),'utf8'));
    if (mod.id !== id) throw Error(`Module name mismatch: ${id}`);
    modules.push(mod);
  }
  for (const mod of modules) for (const dep of mod.dependsOn) {
    if (!modules.some(m => m.id === dep)) throw Error(`${mod.id} requires enabled module ${dep}`);
  }
  const files = new Map();
  function write(rel, content, owners) {
    if (!rel.startsWith('kubejs/') || rel.includes('..') || rel.includes('\\')) throw Error(`Unsafe output path: ${rel}`);
    if (files.has(rel)) throw Error(`Two modules write the same path: ${rel}`);
    files.set(rel,{content:Buffer.isBuffer(content) || typeof content === 'string' ? content : JSON.stringify(content,null,2)+'\n',modules:owners});
  }
  const items = new Map(), tooltips = new Map(), languages = {}, languageOwners = {};
  const recipes = {}, materials = {};
  for (const mod of modules) {
    for (const entry of mod.items || []) {
      if (!/^expedition_[a-z0-9_]+$/.test(entry.id) || items.has(entry.id)) throw Error(`Invalid or duplicate item: ${entry.id}`);
      items.set(entry.id,{...entry,module:mod.id});
    }
    for (const [id, data] of Object.entries(mod.recipes || {})) {
      write(`kubejs/data/expedition/recipes/${id}.json`,data,[mod.id]); recipes[id]=data;
    }
    for (const [id, data] of Object.entries(mod.materials || {})) {
      write(`kubejs/data/expedition/silentgear_materials/${id}.json`,data,[mod.id]); materials[id]=data;
    }
    for (const [rel, data] of Object.entries(mod.resources || {})) write(rel,data,[mod.id]);
    for (const [rel, source] of Object.entries(mod.binaryResources || {})) {
      if (!/^assets\/[a-z0-9_\/-]+\.png$/.test(source) || source.includes('..')) throw Error(`Unsafe texture source: ${source}`);
      const bytes = fs.readFileSync(path.join(__dirname,source));
      if (!bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) throw Error(`Invalid PNG: ${source}`);
      if (!/^kubejs\/assets\/[a-z0-9_]+\/textures\/item\/[a-z0-9_]+\.png$/.test(rel)) throw Error(`Invalid texture target: ${rel}`);
      write(rel,bytes,[mod.id]);
    }
    for (const [id, lines] of Object.entries(mod.tooltips || {})) {
      if (!tooltips.has(id)) tooltips.set(id,{lines:[],modules:[]});
      tooltips.get(id).lines.push(...lines); tooltips.get(id).modules.push(mod.id);
    }
    for (const [locale, texts] of Object.entries(mod.lang || {})) {
      languages[locale] ||= {}; languageOwners[locale] ||= [];
      for (const [key, value] of Object.entries(texts)) {
        if (Object.hasOwn(languages[locale], key)) throw Error(`Duplicate translation: ${key}`);
        languages[locale][key] = value;
      }
      languageOwners[locale].push(mod.id);
    }
  }
  function checkCustomReferences(value) {
    if (typeof value === 'string' && /^kubejs:[a-z0-9_]+$/.test(value) && !items.has(value.slice(7))) throw Error(`Missing custom item definition: ${value}`);
    if (value && typeof value === 'object') for (const [key, child] of Object.entries(value)) { checkCustomReferences(key); checkCustomReferences(child); }
  }
  for (const mod of modules) checkCustomReferences(mod);
  const header = `// Expedition integration ${config.version}. Generated from work/integration/modules.\n`;
  if (items.size) write('kubejs/startup_scripts/expedition_items.js', header+
    "StartupEvents.registry('item', event => {\n"+
    [...items.values()].map(i=>`  event.create(${JSON.stringify(i.id)}).displayName(${JSON.stringify(i.name)}).texture(${JSON.stringify(i.texture)});`).join('\n')+'\n});\n',
    [...new Set([...items.values()].map(i=>i.module))]);
  if (tooltips.size) write('kubejs/client_scripts/expedition_tooltips.js',header+
    'ItemEvents.tooltip(event => {\n'+[...tooltips].map(([id,v])=>`  event.add(${JSON.stringify(id)}, ${JSON.stringify(v.lines)});`).join('\n')+'\n});\n',
    [...new Set([...tooltips.values()].flatMap(v=>v.modules))]);
  for (const [locale, texts] of Object.entries(languages)) write(`kubejs/assets/expedition/lang/${locale}.json`,texts,languageOwners[locale]);
  const guideModule = modules.find(m => m.guide);
  let book = null;
  if (guideModule) {
    const pages = [guideModule.guide.intro.replace('{version}',config.version),...modules.flatMap(m=>m.guidePages || []),guideModule.guide.closing];
    book = {title:guideModule.guide.title,author:guideModule.guide.author,generation:0,pages:pages.map(text=>JSON.stringify({text}))};
    const guideVersion = config.guideVersion || config.version;
    if (!/^\d+\.\d+\.\d+$/.test(guideVersion)) throw Error('Invalid guide version');
    const flag = 'expeditionGuideV'+guideVersion.replaceAll('.','');
    write('kubejs/server_scripts/expedition_guide.js', header+
      `const expeditionGuideData = ${JSON.stringify(book,null,2)};\n`+
      "ServerEvents.recipes(event => {\n  event.shapeless(Item.of('minecraft:written_book', expeditionGuideData), ['minecraft:book', 'minecraft:copper_ingot']).id('expedition:field_guide');\n});\n"+
      `PlayerEvents.loggedIn(event => {\n  if (!event.player.persistentData.${flag}) {\n    event.player.give(Item.of('minecraft:written_book', expeditionGuideData));\n    event.player.persistentData.${flag} = true;\n  }\n});\n`,
      modules.filter(m => m.guide || m.guidePages?.length).map(m=>m.id));
  }
  return {files,modules,items,recipes,materials,book};
}

function build(config = defaults) {
  const built = assemble(config);
  const output = path.resolve(__dirname,`../../outputs/联动试用版-v${config.version}`);
  const root = path.join(output,'安装内容');
  // Refuse stale payloads instead of silently shipping files from disabled modules.
  if (fs.existsSync(root)) {
    const walk = dir => fs.readdirSync(dir,{withFileTypes:true}).flatMap(e => e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);
    for (const file of walk(root)) {
      const rel = path.relative(root,file).replaceAll('\\','/');
      if (!built.files.has(rel)) throw Error(`Stale payload file; use a fresh version/output: ${rel}`);
    }
  }
  const files = [];
  for (const [rel, entry] of [...built.files].sort(([a],[b])=>a.localeCompare(b))) {
    const target = path.join(root,rel);
    fs.mkdirSync(path.dirname(target),{recursive:true}); fs.writeFileSync(target,entry.content);
    files.push({path:rel,sha256:crypto.createHash('sha256').update(entry.content).digest('hex'),modules:entry.modules});
  }
  fs.writeFileSync(path.join(output,'文件清单.json'),JSON.stringify({version:config.version,files},null,2)+'\n');
  fs.writeFileSync(path.join(output,'模块清单.json'),JSON.stringify({version:config.version,switches:'build-time only; changing source config requires rebuilding and upgrading the complete package',modules:built.modules.map(({id,title,requires,dependsOn})=>({id,title,requires:['kubejs',...requires],dependsOn}))},null,2)+'\n');
  const result = {version:config.version,files:files.length,modules:built.modules.length,recipeJsons:Object.keys(built.recipes).length,materials:Object.keys(built.materials).length,customItems:built.items.size,guidePages:built.book?.pages.length || 0};
  console.log(JSON.stringify(result));
  return {...built,output,root};
}
module.exports = {assemble,build};
if (require.main === module) build(process.argv[2] ? JSON.parse(fs.readFileSync(path.resolve(process.argv[2]),'utf8')) : defaults);
