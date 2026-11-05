// Evidence for two further naming repairs. No templates are generated, edited, or extracted for distribution.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {archive,readNbt}=require('./audit-lib.cjs');
const jar=process.argv[2];if(!jar)throw Error('Usage: node audit-wda-repair-evidence.cjs <WDA jar>');
const source=archive(jar),baseline=require('./audit/content-catalog-audit.json');
assert.equal(source.sha256,baseline.inputs.wda.sha256,'Template metadata belongs to another jar');
const metadata=require('./audit/wda-template-metadata.json'),pools=new Map();
for(const name of source.names){const m=name.match(/^data\/dungeons_arise\/worldgen\/template_pool\/(.+)\.json$/);if(m)pools.set('dungeons_arise:'+m[1],JSON.parse(source.read(name)));}
const templatePath=id=>'data/'+id.replace(':','/structures/')+'.nbt';
const poolPath=id=>'data/'+id.replace(':','/worldgen/template_pool/')+'.json';
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
function connectors(id){
  const nbt=readNbt(source.read(templatePath(id)));
  return nbt.blocks.filter(b=>nbt.palette[b.state].Name==='minecraft:jigsaw').map(b=>({pos:b.pos,...b.nbt,
    orientation:nbt.palette[b.state].Properties.orientation}));
}
const thornMissing='dungeons_arise:thornborn_towers/thornborn_towers_hanging_bridge_2_medium_terminator';
const thornSource='dungeons_arise:thornborn_towers/thornborn_towers_hanging_bridge_3_medium_terminator';
assert(!source.names.includes(templatePath(thornMissing)));
const thornConnectors=connectors(thornSource);assert.equal(thornConnectors.length,1);
assert.equal(thornConnectors[0].name,'dungeons_arise:thornborn_towers_hanging_medium');
assert.equal(thornConnectors[0].orientation,'west_up');assert.deepEqual(metadata[thornSource].size,[1,3,3]);
const thornPools=[...pools].flatMap(([id,p])=>p.elements.filter(e=>e.element.location===thornMissing).map(e=>({pool:id,fallback:p.fallback,...e})));
assert.equal(thornPools.length,2);assert(thornPools.every(p=>p.weight===4 && p.element.projection==='rigid' && p.element.processors==='minecraft:empty'));
assert(![...pools.values()].some(p=>p.elements.some(e=>e.element.location===thornSource)),'Canonical template unexpectedly already directly used');
const thornParents=Object.entries(metadata).flatMap(([id,t])=>t.jigsaws.filter(j=>j.target===thornConnectors[0].name).map(j=>({template:id,...j})));
assert.equal(thornParents.length,7);assert(thornParents.every(j=>j.orientation.endsWith('_up') && j.pool==='dungeons_arise:fortified/thornborn_towers/thornborn_towers_hanging'));
const foundryMissing='dungeons_arise:underworld/foundry/foundry_corridor_gears';
const foundrySource='dungeons_arise:underworld/foundry/foundry_corridor_deco';
assert(!pools.has(foundryMissing));const foundryPool=pools.get(foundrySource);
const foundryParent='dungeons_arise:foundry/foundry_corridor_crossing_2';
const foundryConnectors=connectors(foundryParent),missingRefs=foundryConnectors.filter(j=>j.pool===foundryMissing);
assert.equal(missingRefs.length,2);
const adjacent=foundryConnectors.filter(j=>j.pool===foundrySource && j.target==='dungeons_arise:foundry_corridor_gear');
assert.equal(adjacent.length,4);
assert(missingRefs.every(j=>j.name==='dungeons_arise:foundry_corridor_gear' && j.target===j.name && j.orientation==='south_up'));
assert(adjacent.every(j=>j.name===missingRefs[0].name && j.orientation===missingRefs[0].orientation));
const matching=foundryPool.elements.filter(e=>e.element.location && connectors(e.element.location).some(j=>j.name===missingRefs[0].target));
assert.deepEqual(matching.map(e=>({id:e.element.location,weight:e.weight})),[
  {id:'dungeons_arise:foundry/foundry_corridor_gear_0',weight:3},{id:'dungeons_arise:foundry/foundry_corridor_gear_1',weight:3}]);
assert.equal(foundryPool.elements.length,14);assert.equal(foundryPool.elements.find(e=>e.element.element_type==='minecraft:empty_pool_element').weight,3);
const report={wdaSha256:source.sha256,assessment:'Source-grounded repair inference, not author confirmation or a recovered original missing file',runtimeAccepted:false,
  thornborn:{missing:thornMissing,source:thornSource,sourceSha256:sha(source.read(templatePath(thornSource))),size:metadata[thornSource].size,
    connectors:thornConnectors,referencingPools:thornPools,parentConnections:thornParents,horizontalRotationsCanOpposeEveryParent:true},
  foundry:{missing:foundryMissing,source:foundrySource,sourceSha256:sha(source.read(poolPath(foundrySource))),parent:foundryParent,
    missingConnections:missingRefs,adjacentOriginalConnections:adjacent,originalPool:foundryPool,matchingOriginalEntries:matching,
    policy:'Alias the WHOLE original decoration pool including empty weight 3; do not filter, invent weights, or claim the missing pool definition was recovered'},
  unresolved:['template:dungeons_arise:bandit_village/bandit_village_deco_3','template:dungeons_arise:mechanical_nest/mechanical_nest_spawner_6']};
fs.writeFileSync(path.join(__dirname,'audit/wda-additional-repair-evidence.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({thornbornReferences:thornPools.length,thornbornParentConnections:thornParents.length,foundryMissingConnections:missingRefs.length,
  foundryAdjacentOriginalConnections:adjacent.length,foundryPoolElements:foundryPool.elements.length,foundryEmptyWeight:3,runtimeAccepted:false}));
