// Machine-room component extractor for the two heavy native rooms (.schem v2). Read-only; never executes game code.
// Also emits the canonical machine-profile resources consumed by the bridge installer and motion policy.
// Usage: node audit-machine-components.cjs [outputDir]
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {archive,readNbt}=require('./audit-lib.cjs');
const jar=path.join(__dirname,'bridge/libs/dimdoors-5.4.4.jar');
const outDir=process.argv[2]||path.join(__dirname,'audit');
const dd=archive(jar);
const rooms=[
  {id:'dungeon/jungle_riddle_fixed',entry:'resourcepacks/default/data/dimdoors/pockets/schematic/dungeon/jungle_riddle_fixed.schem'},
  {id:'lab/telecommunication',entry:'resourcepacks/default/data/dimdoors/pockets/schematic/lab/telecommunication.schem'}
];
const MACHINERY=new Set([
  'minecraft:sticky_piston','minecraft:piston','minecraft:piston_head','minecraft:moving_piston',
  'minecraft:repeater','minecraft:comparator','minecraft:redstone_torch','minecraft:redstone_wall_torch',
  'minecraft:redstone_wire','minecraft:lever','minecraft:observer','minecraft:calibrated_sculk_sensor',
  'minecraft:sculk_sensor','minecraft:lectern','minecraft:note_block','minecraft:jukebox','minecraft:spruce_door',
  'minecraft:glowstone','minecraft:target','minecraft:trapped_chest','minecraft:tripwire_hook','minecraft:daylight_detector']);
const VOLATILE=new Set(['minecraft:piston_head','minecraft:moving_piston']);
const PISTONS=new Set(['minecraft:sticky_piston','minecraft:piston']);
function parseState(text){
  const open=text.indexOf('[');
  const name=open<0?text:text.slice(0,open);
  const props={};
  if(open>=0)for(const part of text.slice(open+1,-1).split(',')){
    const eq=part.indexOf('=');props[part.slice(0,eq)]=part.slice(eq+1);
  }
  return {name,props};
}
const FACING_MAP={up:'UP',down:'DOWN',north:'NORTH',south:'SOUTH',east:'EAST',west:'WEST'};
function dump(room){
  const nbt=readNbt(dd.read(room.entry));
  const palette=[];for(const [state,idx] of Object.entries(nbt.Palette))palette[Number(idx)]=state;
  const data=nbt.BlockData,cells=nbt.Width*nbt.Height*nbt.Length;
  if(data.length<cells)throw Error('BlockData shorter than cell count');
  const byType=new Map();const stable=[],volatile=[],pistonBases=[];
  const w=nbt.Width,h=nbt.Height,l=nbt.Length;
  for(let y=0;y<h;y++)for(let z=0;z<l;z++)for(let x=0;x<w;x++){
    const text=palette[data[(y*l+z)*w+x]];
    if(!text||text==='minecraft:air'||text==='minecraft:cave_air')continue;
    const {name,props}=parseState(text);
    if(!MACHINERY.has(name))continue;
    const facing=props.facing?FACING_MAP[props.facing]||null:null;
    const rec={type:name,pos:[x,y,z],facing};
    (byType.get(name)||byType.set(name,[]).get(name)).push(rec);
    if(VOLATILE.has(name))volatile.push(rec);
    else {
      stable.push(rec);
      if(PISTONS.has(name))pistonBases.push({pos:[x,y,z],facing});
    }
  }
  const sort=(a,b)=>a.pos[0]-b.pos[0]||a.pos[1]-b.pos[1]||a.pos[2]-b.pos[2]||a.type.localeCompare(b.type);
  stable.sort(sort);volatile.sort(sort);pistonBases.sort((a,b)=>a.pos[0]-b.pos[0]||a.pos[1]-b.pos[1]||a.pos[2]-b.pos[2]);
  const entrySha=crypto.createHash('sha256').update(dd.read(room.entry)).digest('hex');
  const evidence={
    date:'2026-11-28',source:'dimdoors-5.4.4.jar',entry:room.entry,jarSha256:dd.sha256,entrySha256:entrySha,
    version:nbt.Version,size:[w,h,l],offset:nbt.Offset||[0,0,0],
    counts:Object.fromEntries([...byType.entries()].map(([t,lst])=>[t,lst.length])),
    stableCount:stable.length,volatileCount:volatile.length,pistonBaseCount:pistonBases.length,
    note:'Template-relative coordinates are 0-based (Offset recorded separately); no original book/sign text is extracted.'
  };
  fs.mkdirSync(outDir,{recursive:true});
  fs.writeFileSync(path.join(outDir,room.id.replace('/','-')+'-machine-components.json'),JSON.stringify(evidence,null,2));
  const profile={
    schema:1,id:room.id,template_sha256:entrySha,size:[w,h,l],
    component_types:[...new Set(stable.map(s=>s.type))].sort(),
    volatile_types:[...VOLATILE].sort(),
    stable,volatile,piston_bases:pistonBases,
    note:'Generated from the pinned dimdoors-5.4.4.jar by audit-machine-components.cjs. Never hand-edit; regenerate after re-review.'
  };
  const target=path.join(__dirname,'bridge/src/main/resources/data/expedition_bridge/machine_profiles',
    room.id.replace('/','_')+'.json');
  fs.mkdirSync(path.dirname(target),{recursive:true});
  fs.writeFileSync(target,JSON.stringify(profile,null,2));
  console.log(target+' stable='+stable.length+' volatile='+volatile.length+' pistons='+pistonBases.length+' sha='+entrySha.slice(0,16));
}
for(const room of rooms)dump(room);
