// Rebuild the handoff table from pinned upstream definitions and the checked-in entry evidence.
const fs = require('node:fs');
const path = require('node:path');
const {archive} = require('./audit-lib.cjs');
const here = __dirname;
const json = p => JSON.parse(fs.readFileSync(path.join(here,p),'utf8').replace(/^\uFEFF/,''));
const jar = archive(path.join(here,'bridge/libs/DungeonsArise-1.20.x-2.1.58-release.jar'));
const catalog = json('audit/content-catalog-audit.json');
const repairs = json('audit/wda-alias-audit.json');
if (jar.sha256 !== catalog.inputs.wda.sha256 || jar.sha256 !== repairs.wdaSha256) throw Error('Pinned WDA digest mismatch');
const prefix = 'data/dungeons_arise/worldgen/structure/';
const names = jar.names.filter(p => p.startsWith(prefix) && p.endsWith('.json')).sort();
const records = new Map(catalog.structures.map(s => [s.id,s]));
const resolved = new Map(repairs.structures.map(s => [s.id,s]));
if (names.length !== 39 || records.size !== 39 || resolved.size !== 39) throw Error('WDA inventory drift');

const evidence = new Map();
function record(id, environment, entry, level, file, label, remaining) {
    if (evidence.has(id) || !records.has('dungeons_arise:'+id) || !fs.existsSync(path.join(here,file))) throw Error('Invalid evidence: '+id);
    evidence.set(id,{environment, entry, level, file, label, remaining});
}
function cases(id, environment, entry, report, description, remaining, pick = () => true) {
    const file = 'audit/'+report+'-audit.json';
    const audit = json(file);
    const rows = audit.cases.filter(pick);
    if (!rows.length || (audit.runtimeAccepted ?? audit.runtimeTested) !== false) throw Error('Unexpected evidence: '+id);
    record(id,environment,entry,'原模板',file,rows.length+' 组'+description,remaining);
}
for (const id of ['aviary','heavenly_challenger','heavenly_conqueror','heavenly_rider']) {
    cases(id,'悬空','start_deck','aerial-entry','起始平台','完整图的平台连通、坠落与额外出口',r=>r.structure===id);
}
cases('mechanical_nest','悬空','start_deck 原桥','mechanical-bridge','直接桥段（含拒绝样例）','闸门和完整桥图的实际通行');
for (const id of ['illager_corsair','illager_galley','undead_pirate_ship']) {
    cases(id,'干岸水域','boarding 栈道','ship-boarding','原主船体','随机附属件、船舱、水线和登船碰撞',r=>r.structure===id);
}
for (const id of ['foundry','mining_system']) {
    cases(id,'岩层','start_room','underground-entry','起始房间','完整矿道、原机关和分散出口',r=>r.template.startsWith('dungeons_arise:'+id+'/'));
}
cases('small_blimp','干地','start_ladder','blimp-ladder','梯链／船体','实际悬梯稳定性、铁门及完整随机图');
cases('mushroom_mines','干地','start_mine','mushroom-mine','八件主体','完整随机图拒绝率、原陷阱与岩坡碰撞');
cases('scorched_mines','岩地','start_well','scorched-well','井与大厅','熔岩释放、随机支路和竖井下坠');
cases('typhon','海床与干岸','shore_dive','typhon-dive','完整三件','气泡、供氧、上岸与埋藏宝箱');
cases('lighthouse','草地／岩地','perimeter 原台阶','lighthouse-entry','完整两件','原生实际生成、梯脚以上攀爬与塔顶灯组');
const keep = json('audit/keep-kayra-ground-evidence.json');
if (keep.runtimeAccepted !== false || keep.wdaSha256 !== jar.sha256 || keep.reachableConservativeInteriorCells <= 0) throw Error('Castle evidence mismatch');
record('keep_kayra','岩地（测试）','perimeter','原模板','audit/keep-kayra-ground-evidence.json','原主件低层路线','超高整座拼装、上下层实际通行和高度包');
cases('illager_windmill','草地（测试）','perimeter 双侧石阶','windmill-entry','塔身与田地组合','原生随机田地、实际灌溉、战斗机关和双人重启');
cases('illager_campsite','干地','perimeter 帐篷与火圈外围','illager-camp-entry','巡逻道路与营地组合','实际随机拼装、原敌人战斗、营火伤害和双人重启');
cases('jungle_tree_house','草地／岩地，随首件对齐','perimeter 原树干至木平台','tree-house-entry','两种首件与敌人分布','原生拼装、实际跳跃、上层探索、战斗和双人重启');
cases('giant_mushroom','草地／岩地','perimeter 原菌柄首段','giant-mushroom-entry','红色／双生主体与环形拼装','原生完整率、实际攀爬与登顶、战斗和双人重启');
cases('wishing_well','草地／岩地','perimeter 原坡至井沿','wishing-well-entry','井底效果与坡道组合','实机跳坡、下井效果、水流与双人重启');
cases('fishing_hut','干地／2～3 格浅水','perimeter 原码头双通道','fishing-hut-entry','岸线与刷怪件组合','游泳上岸、钓竿僵尸、水流与双人重启');
cases('bathhouse','干地／原池水','perimeter 外侧脚手架与原门廊','bathhouse-entry','基座与门厅代表布局','原生拼装与拒绝率、攀爬、池水恢复、多人重启');
cases('small_prairie_house','干地','perimeter 原门与废墟机关走道','prairie-house-entry','完好屋与废墟','实际开门、红石时序、TNT 与双人重启');
cases('mushroom_house','干地','perimeter 原云杉台阶','mushroom-house-entry','底层、菌盖与刷怪接口','实际拼装、进屋碰撞、刷怪与双人重启');
cases('merchant_campsite','草地（测试）','perimeter 街道与帐篷','merchant-camp-entry','街道与帐篷组合','原生随机营地、开门交易、战利品和双人重启');

const rows = names.map(file => {
    const id = path.posix.basename(file,'.json'), fullId = 'dungeons_arise:'+id;
    const def = JSON.parse(jar.read(file)), src = records.get(fullId), graph = resolved.get(fullId);
    if (!src || !graph || graph.missingAfter.length || graph.unsupportedAfter.length) throw Error('Unresolved graph: '+id);
    if (src.type !== def.type || src.startPool !== def.start_pool) throw Error('Structure definition mismatch: '+id);
    const height = def.start_height.absolute, heightmap = def.project_start_to_heightmap ?? null;
    let remaining;
    if (!heightmap) remaining='核对绝对起点、周边地形和原入口高差';
    else if (height > 0) remaining='核对正高度偏移后的落脚处及原接近路线';
    else if (height < -3) remaining='检查埋入地面的部分和原门道净空';
    else remaining='从原起始件核对地表、主入口与可达路线';
    const status = evidence.get(id) ?? {environment:'待定',entry:'通用岛缘待核对',level:'资源图',
        file:'audit/wda-alias-audit.json',label:'资源引用',remaining};
    return {id:fullId,generator:def.type,heightmap,startHeight:height,startPool:def.start_pool,
        biomeSelector:def.biomes,originalMissingReferences:graph.missingBefore, ...status, runtimeAccepted:false};
});
const count = s => rows.filter(r => r.level===s).length;
const summary = {structures:rows.length,originalTemplateEvidence:count('原模板'),fixtureOnly:count('开发案例'),
    resourceGraphOnly:count('资源图'),runtimeAccepted:0};
const report = {schema:1,wdaSha256:jar.sha256,summary,rows};
const md = ['# WDA 建筑环境与入口覆盖表','',
    'WDA 2.1.58 共 '+rows.length+' 座建筑。'+count('原模板')+' 座有原模板路线或入口几何证据，'+count('开发案例')+' 座有开发案例且仍待核对原路线，'+count('资源图')+' 座目前只完成资源图审查。游戏内验收为 0 座。','',
    '生成器栏的 WDA 指自定义 generic_structures，其余为原版 jigsaw。高度图一栏给出原始高度来源与偏移。绝对表示原定义没有投影高度图。样例的范围见对应报告，正式分级和权重待定。','',
    '| 建筑 | 生成器 | 高度来源与值 | 环境 | 入口实现 | 当前证据 | 下一项核对 |','|---|---|---|---|---|---|---|',
    ...rows.map(r=>'| '+[r.id.split(':')[1],r.generator.startsWith('minecraft:')?'jigsaw':'WDA',
        (r.heightmap??'绝对')+' '+r.startHeight,r.environment,r.entry,r.level+' · ['+r.label+']('+r.file+')',r.remaining].join(' | ')+' |'),
    '','机器可读表还记录起始池、群系选择器和每座建筑原有的资源缺口。[完整数据](audit/wda-entry-coverage.json)。','',
    '在仓库根目录执行下列命令可用锁定 JAR 和已提交的审查报告复核清单。','',
    '~~~text','node work/l13/audit-entry-coverage.cjs --check','~~~',''].join('\n');
const artifacts = new Map([['audit/wda-entry-coverage.json',JSON.stringify(report,null,2)+'\n'],['WDA_ENTRY_COVERAGE.md',md]]);
for (const [file,content] of artifacts) {
    const target=path.join(here,file);
    if (process.argv.includes('--check')) {
        if (fs.readFileSync(target,'utf8').replace(/\r\n/g,'\n')!==content) throw Error('Stale coverage artifact: '+file);
    } else fs.writeFileSync(target,content);
}
console.log(JSON.stringify(summary));
