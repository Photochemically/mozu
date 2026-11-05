// Full installed-resource metadata audit. No Minecraft launch, world edits or runnable catalog produced.
const fs=require('node:fs'),path=require('node:path');
const {archive,readNbt,identifier,reachable}=require('./audit-lib.cjs');
const wdaPath=process.argv[2];if(!wdaPath)throw Error('Usage: node audit-content.cjs <exact WDA jar>');
const wda=archive(wdaPath),dd=archive(path.join(__dirname,'bridge/libs/dimdoors-5.4.4.jar'));
const json=(jar,name)=>JSON.parse(jar.read(name).toString('utf8'));
const object=count=>Object.fromEntries([...count.entries()].sort(([a],[b])=>a.localeCompare(b)));
const add=(map,id,n=1)=>{if(id)map.set(id,(map.get(id)||0)+n);};
const union=values=>[...new Set(values)].sort();
function templateMetadata(nbt) {
  const variants=nbt.palettes||[nbt.palette||[]],palette=variants[0]||[];
  const blocks=new Map(),spawnTypes=new Set(),entityTypes=new Map(),loot=new Set(),pools=new Set();
  const containers=[],spawners=[],jigsaws=[];let commands=0;
  for(const b of nbt.blocks||[]) {
    const name=palette[b.state]?.Name;add(blocks,name);
    const tag=b.nbt;if(!tag)continue;
    if(tag.LootTable)loot.add(tag.LootTable);
    if(/(?:chest|barrel|shulker_box)$/.test(name||''))containers.push({pos:b.pos,loot:tag.LootTable||null,preFilledItems:tag.Items?.length||0});
    if(name==='minecraft:jigsaw') {
      if(tag.pool)pools.add(identifier(tag.pool));
      jigsaws.push({pos:b.pos,pool:tag.pool||null,name:tag.name||null,target:tag.target||null,orientation:palette[b.state]?.Properties?.orientation});
    }
    if(name==='minecraft:spawner') {
      const types=new Set();if(tag.SpawnData?.entity?.id)types.add(tag.SpawnData.entity.id);
      if(tag.SpawnData?.id)types.add(tag.SpawnData.id);
      for(const p of tag.SpawnPotentials||[]){const id=p.data?.entity?.id||p.Entity?.id;if(id)types.add(id);}
      for(const id of types)spawnTypes.add(id);
      spawners.push({pos:b.pos,types:[...types].sort(),spawnCount:tag.SpawnCount??null,maxNearby:tag.MaxNearbyEntities??null,requiredPlayerRange:tag.RequiredPlayerRange??null});
    }
    if(/command_block/.test(name||''))commands++;
  }
  function entity(nbt){if(!nbt)return;add(entityTypes,nbt.id);for(const p of nbt.Passengers||[])entity(p);}
  for(const e of nbt.entities||[])entity(e.nbt);
  const allPalette=union(variants.flatMap(v=>v.map(p=>p.Name)));
  return {size:nbt.size,blockEntries:nbt.blocks?.length||0,paletteVariants:variants.length,blocks:object(blocks),
    containers,lootTables:[...loot].sort(),spawners,spawnerTypes:[...spawnTypes].sort(),entityTypes:object(entityTypes),
    jigsawPools:[...pools].sort(),jigsaws,commandBlocks:commands,
    hazardPalette:allPalette.filter(id=>/lava|tnt|dispenser|tripwire|pressure_plate|magma|cactus|fire|wither_rose|powder_snow/.test(id)),
    waterPalette:allPalette.filter(id=>/water|kelp|seagrass/.test(id)),
    alternatePaletteIds:variants.length>1?allPalette:[]};
}
const templates=new Map(),pools=new Map(),structures=[];
for(const name of wda.names.filter(n=>/^data\/[^/]+\/structures\/.*\.nbt$/.test(n)).sort()) {
  const match=name.match(/^data\/([^/]+)\/structures\/(.*)\.nbt$/);templates.set(match[1]+':'+match[2],templateMetadata(readNbt(wda.read(name))));
}
for(const name of wda.names.filter(n=>/^data\/[^/]+\/worldgen\/template_pool\/.*\.json$/.test(n))) {
  const match=name.match(/^data\/([^/]+)\/worldgen\/template_pool\/(.*)\.json$/);pools.set(match[1]+':'+match[2],json(wda,name));
}
for(const name of wda.names.filter(n=>/^data\/dungeons_arise\/worldgen\/structure\/.*\.json$/.test(n)).sort()) {
  const def=json(wda,name),id='dungeons_arise:'+name.split('/').pop().replace(/\.json$/,'');
  const graph=reachable(def.start_pool,pools,templates),pieces=graph.templates.map(id=>templates.get(id)).filter(Boolean);
  const max=[0,0,0];for(const piece of pieces)for(let a=0;a<3;a++)max[a]=Math.max(max[a],piece.size[a]);
  const flags=[];
  if(!def.project_start_to_heightmap)flags.push('fixed_height_requires_environment_alignment');
  if(def.start_pool.includes('/aquatic/') || def.project_start_to_heightmap==='OCEAN_FLOOR')flags.push('aquatic_environment_review');
  if(def.start_pool.includes('/underworld/') || /mines|mining_system/.test(id))flags.push('underground_rock_envelope_review');
  if(/blimp|heavenly|mechanical_nest|aviary/.test(id))flags.push('airborne_route_review');
  if(def.type==='dungeons_arise:generic_structures')flags.push('custom_generator_terrain_height_below_150_check');
  if(max[1]>224)flags.push('some_reachable_templates_exceed_224_height');
  if(pieces.some(p=>p.paletteVariants>1))flags.push('alternate_palettes');
  if(graph.missing.length)flags.push('unresolved_resource_references');
  if(graph.unsupported.length)flags.push('unsupported_pool_elements');
  structures.push({id,type:def.type,startPool:def.start_pool,jigsawDepth:def.size,startHeight:def.start_height,
    projectedHeightmap:def.project_start_to_heightmap||null,maxDistance:def.max_distance_from_center,
    naturalSpawnOverrides:def.spawn_overrides||{},graph,uniqueReachableTemplateCount:pieces.length,maxSingleTemplateDimensions:max,
    reachableTemplateTotals:{containers:pieces.reduce((n,p)=>n+p.containers.length,0),spawners:pieces.reduce((n,p)=>n+p.spawners.length,0),commandBlocks:pieces.reduce((n,p)=>n+p.commandBlocks,0)},
    spawnerTypes:union(pieces.flatMap(p=>p.spawnerTypes)),embeddedEntityTypes:union(pieces.flatMap(p=>Object.keys(p.entityTypes))),
    lootTables:union(pieces.flatMap(p=>p.lootTables)),hazardPalette:union(pieces.flatMap(p=>p.hazardPalette)),flags,
    difficultyTier:null,environment:null,exits:null,reviewStatus:'unassigned_requires_review'});
}
const native=[];
for(const name of dd.names.filter(n=>/^resourcepacks\/[^/]+\/data\/dimdoors\/pockets\/schematic\/.*\.schem$/.test(n)).sort()) {
  const match=name.match(/^resourcepacks\/([^/]+)\/data\/dimdoors\/pockets\/schematic\/(.*)\.schem$/),nbt=readNbt(dd.read(name));
  const generatorPath=`resourcepacks/${match[1]}/data/dimdoors/pockets/generators/${match[2]}.json`;
  const generator=dd.names.includes(generatorPath)?json(dd,generatorPath):null;
  const palette=Object.keys(nbt.Palette||nbt.Blocks?.Palette||{}),entities=nbt.BlockEntities||nbt.Blocks?.BlockEntities||[];
  const rifts=entities.filter(e=>/rift/.test(e.Id||e.id||'')),containers=entities.filter(e=>/chest|barrel|shulker_box/.test(e.Id||e.id||''));
  native.push({id:'dimdoors:'+match[2],resourcePack:match[1],size:[nbt.Width,nbt.Height,nbt.Length],
    directSchematicGenerator:generator?.type==='dimdoors:schematic' && generator.id===match[2],generator:generator?{type:generator.type,id:generator.id,setupLoot:generator.setup_loot,builder:generator.builder,modifierTypes:(generator.modifiers||[]).map(m=>m.type)}:null,
    originalRifts:rifts.map(e=>({pos:e.Pos,type:e.data?.destination?.type||null})),containers:containers.length,
    lootTables:union(containers.map(e=>e.LootTable).filter(Boolean)),hazardPalette:palette.filter(id=>/spawner|lava|tnt|dispenser|tripwire|pressure_plate|magma|cactus|fire/.test(id)),
    dedicatedGoldRoom:match[2]==='dungeon/upwards_t_treasure',difficultyTier:null,reviewStatus:'unassigned_requires_review'});
}
const report={schema:1,inputs:{wda:{file:path.basename(wdaPath),sha256:wda.sha256},dimdoors:{file:'dimdoors-5.4.4.jar',sha256:dd.sha256}},
  limitations:['Reachability is a conservative union, ignoring joint matching, rotation, depth limits and randomness.',
    'Template totals are NOT counts in a generated building; a template can be omitted or used multiple times.',
    'Max single-template dimensions are NOT complete building bounds; height fit requires native seeded planning.',
    'Counts use the first palette; alternate palettes are flagged. No difficulty or reward policy is inferred as final.',
    'Native builtin resource-pack availability must be checked against actual server resources.'],
  summary:{wdaStructures:structures.length,wdaTemplates:templates.size,wdaPools:pools.size,nativeTemplates:native.length,
    structuresWithMissing:structures.filter(s=>s.graph.missing.length).length,structuresWithUnsupported:structures.filter(s=>s.graph.unsupported.length).length,
    nativeDirectGenerators:native.filter(n=>n.directSchematicGenerator).length},structures,native};
fs.writeFileSync(path.join(__dirname,'audit/content-catalog-audit.json'),JSON.stringify(report,null,2));
fs.writeFileSync(path.join(__dirname,'audit/wda-template-metadata.json'),JSON.stringify(Object.fromEntries(templates),null,2));
const lines=['# L13 建筑资源静态目录','','由 audit-content.cjs 读取指定版本JAR生成；未启动游戏、未生成建筑，所有等级／环境分配均未定案。',
  '',`WDA：${structures.length}个结构，${templates.size}个模板，${pools.size}个拼图池；DD：${native.length}个口袋模板。`,
  '','**下表尺寸是可达模板的各轴最大值，不是完整建筑尺寸。箱子／刷怪笼是模板库合计，不是单次生成数量。**',
  '','| WDA结构 | 可达模板数 | 最大单片各轴 | 模板库箱子／刷怪笼 | 原生刷怪种类 | 适配检查标记 |','|---|---:|---|---|---|---|'];
for(const s of structures)lines.push(`| ${s.id} | ${s.uniqueReachableTemplateCount} | ${s.maxSingleTemplateDimensions.join('×')} | ${s.reachableTemplateTotals.containers}／${s.reachableTemplateTotals.spawners} | ${s.spawnerTypes.map(t=>t.replace('minecraft:','')).join(', ')||'未发现刷怪笼实体ID'} | ${s.flags.join(', ')||'仍需完整规划与路线审查'} |`);
lines.push('','## 原生房间资源','','classic资源包是否启用需运行时核对；只列实际资源，不自动加入候选池。','',
  '| 房间 | 资源包 | 尺寸 | 原始裂隙数 | 对应单房间生成器 | 原生危险方块线索 |','|---|---|---|---:|---|---|');
for(const n of native)lines.push(`| ${n.id}${n.dedicatedGoldRoom?'（金门专用）':''} | ${n.resourcePack} | ${n.size.join('×')} | ${n.originalRifts.length} | ${n.directSchematicGenerator?'有':'需进一步核对'} | ${n.hazardPalette.join(', ')||'未在调色板发现所检查的危险类型'} |`);
lines.push('','## 限制','','连接图是保守并集，尚未考虑接口匹配、旋转、深度和随机取舍。没有刷怪笼不代表安全：还有结构刷怪覆盖、内嵌实体、陷阱和后续怪物联动。完整高度与环境适配必须通过原生生成器规划及最终游戏内验收。');
fs.writeFileSync(path.join(__dirname,'../../outputs/L13建筑资源静态目录.md'),lines.join('\n')+'\n');
console.log(JSON.stringify(report.summary));
