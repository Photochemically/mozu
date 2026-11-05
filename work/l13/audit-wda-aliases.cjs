// Offline graph verification and evidence for the repairs/substitutes declared by the Java implementation.
const fs=require('node:fs'),path=require('node:path');
const crypto=require('node:crypto'),assert=require('node:assert/strict');
const {archive,readNbt,reachable}=require('./audit-lib.cjs');
const jar=process.argv[2];if(!jar)throw Error('Usage: node audit-wda-aliases.cjs <WDA jar>');
const source=archive(jar),baseline=require('./audit/content-catalog-audit.json');
if(source.sha256!==baseline.inputs.wda.sha256)throw Error('Rebuild template metadata for this WDA version first');
const templates=new Map(Object.entries(require('./audit/wda-template-metadata.json'))),pools=new Map();
for(const name of source.names){const match=name.match(/^data\/([^/]+)\/worldgen\/template_pool\/(.+)\.json$/);if(match)pools.set(match[1]+':'+match[2],JSON.parse(source.read(name)));}
const java=fs.readFileSync(path.join(__dirname,'bridge/src/main/java/dev/expedition/bridge/compat/WdaAliasData.java'),'utf8');
const aliases=[...java.matchAll(/new Alias\("([^"]+)",\s*"([^"]+)",(true|false)\)/g)].map(m=>({missing:m[1],source:m[2],pool:m[3]==='true'}));
if(aliases.length!==4)throw Error('Alias definition format/count changed; review audit');
const substitutions=[...java.matchAll(/new Substitute\("([^"]+)",\s*"([^"]+)",\s*"([a-f0-9]{64})",\s*"([^"]+)",\s*"([a-f0-9]{64})"\)/g)]
  .map(m=>({missing:m[1],source:m[2],sourceSha256:m[3],pool:m[4],poolSha256:m[5]}));
if(substitutions.length!==2)throw Error('Substitute definition format/count changed; review audit');
const id=p=>'dungeons_arise:'+p.replace(/^(worldgen\/template_pool|structures)\//,'').replace(/\.(json|nbt)$/,'');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const connectors=n=>n.blocks.filter(b=>n.palette[b.state].Name==='minecraft:jigsaw')
  .map(b=>({pos:b.pos,orientation:n.palette[b.state].Properties.orientation,...b.nbt}));
for(const alias of aliases){
  if(source.names.includes('data/dungeons_arise/'+alias.missing))throw Error('Exact original resource now exists; review audit');
  if(!source.names.includes('data/dungeons_arise/'+alias.source))throw Error('Missing canonical source');
  if(alias.pool){const original=pools.get(id(alias.source));pools.set(id(alias.missing),{...original,name:id(alias.missing)});}
  else templates.set(id(alias.missing),templates.get(id(alias.source)));
}
const evidence=[];
for(const substitute of substitutions) {
  assert(!source.names.includes('data/dungeons_arise/'+substitute.missing),'Original now exists; re-audit');
  const bytes=source.read('data/dungeons_arise/'+substitute.source),poolBytes=source.read('data/dungeons_arise/'+substitute.pool);
  assert.equal(hash(bytes),substitute.sourceSha256);assert.equal(hash(poolBytes),substitute.poolSha256);
  const pool=JSON.parse(poolBytes),nbt=readNbt(bytes),jigsaws=connectors(nbt);
  const missing=pool.elements.filter(e=>e.element.location===id(substitute.missing));
  const borrowed=pool.elements.filter(e=>e.element.location===id(substitute.source));
  assert.equal(missing.length,1);assert.equal(borrowed.length,1);
  assert.deepEqual({...missing[0].element,location:id(substitute.source)},borrowed[0].element);
  assert.equal(jigsaws.length,1);assert.equal(nbt.entities.length,0);
  const bandit=substitute.missing.includes('bandit_village');
  assert.deepEqual(nbt.size,bandit?[7,10,7]:[1,2,1]);
  assert.deepEqual(jigsaws[0].pos,bandit?[0,0,3]:[0,1,0]);
  assert.equal(jigsaws[0].orientation,bandit?'west_up':'down_east');
  const parents=[];
  for(const file of source.names.filter(n=>n.startsWith('data/dungeons_arise/structures/')&&n.endsWith('.nbt'))) {
    for(const joint of connectors(readNbt(source.read(file)))) {
      if(joint.pool!==id(substitute.pool)||joint.target!==jigsaws[0].name)continue;
      const [front,top]=joint.orientation.split('_');
      const compatible=bandit?['north','east','south','west'].includes(front)&&(joint.joint==='rollable'||top==='up')
        :front==='up'&&(joint.joint==='rollable'||['north','east','south','west'].includes(top));
      assert(compatible,'Unreviewed parent orientation: '+file);
      parents.push({file,...joint});
    }
  }
  assert(parents.length>0,'No matching upstream attachment');
  const spawners=nbt.blocks.filter(b=>nbt.palette[b.state].Name==='minecraft:spawner').map(b=>b.nbt);
  const containers=nbt.blocks.filter(b=>/^(minecraft:chest|minecraft:trapped_chest|minecraft:barrel)$/.test(nbt.palette[b.state].Name));
  assert.equal(containers.length,0);assert.equal(spawners.length,bandit?0:1);
  if(!bandit) {
    assert.equal(spawners[0].SpawnData.entity.id,'minecraft:skeleton');
    assert.equal(spawners[0].SpawnData.entity.Health,40);
    assert.equal(spawners[0].SpawnData.entity.HandItems[0].id,'minecraft:diamond_sword');
    assert.equal(spawners[0].SpawnData.entity.Passengers,undefined);
  }
  templates.set(id(substitute.missing),templates.get(id(substitute.source)));
  evidence.push({...substitute,exactOriginalRecovered:false,compatibilitySubstitutionImplemented:true,size:nbt.size,
    originalMissingWeight:missing[0].weight,sourceOriginalWeight:borrowed[0].weight,
    effectiveSourceWeight:missing[0].weight+borrowed[0].weight,totalPoolWeight:pool.elements.reduce((n,e)=>n+e.weight,0),
    owningPoolUnchanged:true,element:missing[0].element,connectors:jigsaws,matchingParents:parents,containers:0,spawners});
}
const structures=baseline.structures.map(s=>{
  const graph=reachable(s.startPool,pools,templates);
  return {id:s.id,missingBefore:s.graph.missing,missingAfter:graph.missing,unsupportedAfter:graph.unsupported};
});
const report={wdaSha256:source.sha256,aliases,substitutions,source:'installed_jar_plus_virtual_repair_and_substitute_overlay',runtimeAccepted:false,
  missingBefore:[...new Set(structures.flatMap(s=>s.missingBefore))].sort(),missingAfter:[...new Set(structures.flatMap(s=>s.missingAfter))].sort(),
  structures};
if(report.missingBefore.length!==6 || report.missingAfter.length!==0)throw Error('Unexpected resource graph delta; inspect before accepting');
assert(structures.every(s=>s.unsupportedAfter.length===0));
fs.writeFileSync(path.join(__dirname,'audit/wda-alias-audit.json'),JSON.stringify(report,null,2)+'\n');
fs.writeFileSync(path.join(__dirname,'audit/wda-substitution-evidence.json'),JSON.stringify({wdaSha256:source.sha256,
  userAuthorized:true,runtimeAccepted:false,limitations:'Compatible existing parts, not recovered originals or full building placement evidence',cases:evidence},null,2)+'\n');
console.log(JSON.stringify({aliases:aliases.length,substitutions:substitutions.length,structures:structures.length,
  missingBefore:report.missingBefore.length,missingAfter:report.missingAfter.length,
  parentConnections:evidence.map(e=>({missing:e.missing,count:e.matchingParents.length})),runtimeAccepted:false}));
