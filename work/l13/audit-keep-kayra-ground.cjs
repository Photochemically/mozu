// Conservative static floor/air connectivity, not a Minecraft collision or full-route acceptance test.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {archive,readNbt}=require('./audit-lib.cjs');
const jar=process.argv[2];if(!jar)throw Error('Usage: node audit-keep-kayra-ground.cjs <WDA jar>');
const source=archive(jar),baseline=require('./audit/content-catalog-audit.json');assert.equal(source.sha256,baseline.inputs.wda.sha256);
const definition=JSON.parse(source.read('data/dungeons_arise/worldgen/structure/keep_kayra.json'));
const pool=JSON.parse(source.read('data/dungeons_arise/worldgen/template_pool/keep_kayra/keep_kayra_start.json'));
assert.equal(definition.start_height.absolute,54);assert(!definition.project_start_to_heightmap);assert(!definition.start_jigsaw_name);
assert.equal(pool.elements.length,1);assert.equal(pool.elements[0].element.location,'dungeons_arise:keep_kayra/keep_kayra_main_0');
assert.equal(pool.elements[0].element.projection,'rigid');assert.equal(pool.elements[0].element.processors,'minecraft:empty');
const template=readNbt(source.read('data/dungeons_arise/structures/keep_kayra/keep_kayra_main_0.nbt'));
assert.deepEqual(template.size,[163,250,163]);
const localFeetY=12,groundLevelDelta=1,firstPieceMinY=definition.start_height.absolute-groundLevelDelta;
const firstFreeY=firstPieceMinY+localFeetY;
const padding=8,min=-padding,max=162+padding,width=max-min+1,count=width*width;
const planes=Array.from({length:3},()=>new Uint16Array(count));
const names=template.palette.map(p=>p.Name);const air=id=>id==='minecraft:air'||id==='minecraft:cave_air';
for(const block of template.blocks){const [x,y,z]=block.pos;if(y<localFeetY-1||y>localFeetY+1)continue;
  planes[y-localFeetY+1][(z-min)*width+x-min]=block.state+1;
}
const supportNames=new Set(['minecraft:stone','minecraft:deepslate','minecraft:polished_basalt','minecraft:packed_mud',
  'minecraft:mud_bricks','minecraft:moss_block','minecraft:smooth_sandstone']);
const support=id=>supportNames.has(id)||/^minecraft:.*_(planks|wood|log|terracotta|concrete|wool)$/.test(id);
const walkable=new Uint8Array(count);
for(let i=0;i<count;i++) {
  const floor=planes[0][i],feet=planes[1][i],head=planes[2][i];
  // Omitted floor remains island stone; explicit template air removes it. Omitted feet/head are above the island.
  walkable[i]=(!floor||support(names[floor-1]))&&(!feet||air(names[feet-1]))&&(!head||air(names[head-1]))?1:0;
}
const entryX=165,entryZ=81,start=(entryZ-min)*width+entryX-min;
const previous=new Int32Array(count).fill(-1),queue=[start];assert(walkable[start]);previous[start]=start;
for(let cursor=0;cursor<queue.length;cursor++) {
  const i=queue[cursor],x=i%width,z=Math.floor(i/width);
  for(const [nx,nz] of [[x-1,z],[x+1,z],[x,z-1],[x,z+1]])if(nx>=0&&nz>=0&&nx<width&&nz<width){const j=nz*width+nx;
    if(walkable[j]&&previous[j]===-1){previous[j]=i;queue.push(j);}}
}
const interior=queue.filter(i=>{const x=i%width+min,z=Math.floor(i/width)+min;return x>=25&&x<=137&&z>=25&&z<=137;});
assert(interior.length>50,'Do not choose this ground level without a conservative original interior route');
interior.sort((a,b)=>Math.abs(a%width+min-81)+Math.abs(Math.floor(a/width)+min-81)-Math.abs(b%width+min-81)-Math.abs(Math.floor(b/width)+min-81));
const route=[];for(let i=interior[0];;i=previous[i]){route.push([i%width+min,localFeetY,Math.floor(i/width)+min]);if(i===start)break;}route.reverse();
const report={wdaSha256:source.sha256,template:'dungeons_arise:keep_kayra/keep_kayra_main_0',size:template.size,startHeight:54,
  groundLevelDelta,firstPieceMinY,localFeetY,firstFreeY,formerTestFirstFreeY:96,formerTerrainAboveLowFloor:96-firstFreeY,
  surfaceBlockY:firstFreeY-1,templateUnmodified:true,reachableConservativeInteriorCells:interior.length,exampleLocalRoute:route,
  evidence:'Pinned WDA ModifiedJigsawPlacement starts at absolute 54 minus ground delta 1; delta confirmed in mapped Minecraft bytecode',
  limitations:['Only original mandatory template plus flat island, not final random assembly','Conservative full-cube support/air whitelist, not runtime collision evaluation',
    'No stairs, jumps, doors, liquids, mobs, traps or upper-floor routing simulated','Test case environment only; production classification is not approved'],runtimeAccepted:false};
fs.writeFileSync(path.join(__dirname,'audit/keep-kayra-ground-evidence.json'),JSON.stringify(report,null,2)+'\n');
console.log(JSON.stringify({firstFreeY,firstPieceMinY,localFeetY,reachableConservativeInteriorCells:interior.length,exampleRouteLength:route.length,formerTerrainAboveLowFloor:31,runtimeAccepted:false}));
