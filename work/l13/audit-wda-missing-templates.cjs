// Describe exact missing references and existing neighbors; never synthesize or install a replacement.
const fs=require('node:fs'),path=require('node:path');
const {archive,readNbt}=require('./audit-lib.cjs');
const source=archive(process.argv[2]);
if(source.sha256!=='f8f1254dbe7ed7c1d5403f20361a36f0d0587c82b71313e3f427c7f1ce7c8a90')throw Error('Unexpected WDA source version');
const targets=['bandit_village/bandit_village_deco_3','mechanical_nest/mechanical_nest_spawner_6'];
const cases=targets.map(target=>{
  const id='dungeons_arise:'+target,file='data/dungeons_arise/structures/'+target+'.nbt';
  if(source.names.includes(file))throw Error('Previously missing template is now present; re-audit before changing compatibility behavior');
  const references=[];
  for(const poolFile of source.names.filter(n=>n.includes('/worldgen/template_pool/')&&n.endsWith('.json'))) {
    const pool=JSON.parse(source.read(poolFile));
    for(const item of pool.elements||[])if(item.element.location===id)references.push({poolFile,weight:item.weight,
      totalWeight:pool.elements.reduce((sum,e)=>sum+e.weight,0),entryCount:pool.elements.length,fallback:pool.fallback,element:item.element});
  }
  const prefix='data/dungeons_arise/structures/'+target.replace(/\d+$/,'');
  const neighbors=source.names.filter(n=>n.startsWith(prefix)&&n.endsWith('.nbt')).sort().map(name=>{
    const nbt=readNbt(source.read(name)),blocks=nbt.blocks||[],palette=nbt.palette||[];
    const spawners=blocks.filter(b=>palette[b.state]?.Name==='minecraft:spawner').map(b=>({pos:b.pos,
      spawn:b.nbt?.SpawnData?.entity?.id||null,potentials:(b.nbt?.SpawnPotentials||[]).map(p=>({weight:p.weight,id:p.data?.entity?.id||p.Entity?.id||null}))}));
    return {file:name,size:nbt.size,spawners,containers:blocks.filter(b=>/^(minecraft:chest|minecraft:barrel|minecraft:trapped_chest)$/.test(palette[b.state]?.Name||'')).length,
      lootTables:[...new Set(blocks.map(b=>b.nbt?.LootTable).filter(Boolean))],
      connectors:blocks.filter(b=>palette[b.state]?.Name==='minecraft:jigsaw').map(b=>({pos:b.pos,name:b.nbt?.name,target:b.nbt?.target,pool:b.nbt?.pool}))};
  });
  return {missing:id,file,references,neighbors,exactOriginalRecovered:false};
});
const result={sourceSha256:source.sha256,scope:'Raw WDA 2.1.58 archive only; bridge substitutions reported separately in wda-substitution-evidence.json',cases};
fs.writeFileSync(path.join(__dirname,'audit/wda-missing-template-details.json'),JSON.stringify(result,null,2));
console.log(JSON.stringify(cases.map(c=>({missing:c.missing,references:c.references.map(r=>({pool:r.poolFile,weight:r.weight,totalWeight:r.totalWeight})),
  neighbors:c.neighbors.map(n=>({name:path.basename(n.file),size:n.size,spawners:n.spawners.length,entities:[...new Set(n.spawners.flatMap(s=>[s.spawn,...s.potentials.map(p=>p.id)]).filter(Boolean))],containers:n.containers}))})),null,2));
