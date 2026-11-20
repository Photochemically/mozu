# WDA 建筑环境与入口覆盖表

WDA 2.1.58 共 39 座建筑。38 座有原模板路线或入口几何证据，0 座有开发案例且仍待核对原路线，1 座目前只完成资源图审查。游戏内验收为 0 座。

生成器栏的 WDA 指自定义 generic_structures，其余为原版 jigsaw。高度图一栏给出原始高度来源与偏移。绝对表示原定义没有投影高度图。样例的范围见对应报告，正式分级和权重待定。

| 建筑 | 生成器 | 高度来源与值 | 环境 | 入口实现 | 当前证据 | 下一项核对 |
|---|---|---|---|---|---|---|
| abandoned_temple | jigsaw | WORLD_SURFACE_WG -2 | 草地／岩地 | perimeter 原石阶／外侧脚手架 | 原模板 · [144 组两种主体与道路灯饰](audit/temple-entry-audit.json) | 原生随机图、阶梯和脚手架、战斗与双人重启 |
| aviary | jigsaw | 绝对 50 | 悬空 | start_deck | 原模板 · [4 组起始平台](audit/aerial-entry-audit.json) | 完整图的平台连通、坠落与额外出口 |
| bandit_towers | jigsaw | 绝对 135 | 草地／岩地，按完整图最低处派生 | perimeter 支撑脚手架与原断桥平台 | 原模板 · [80 组双塔、横桥、房间与原刷怪件](audit/bandit-towers-entry-audit.json) | 原生完整率、脚手架碰撞、各层探索、刷怪和双人重启 |
| bandit_village | jigsaw | WORLD_SURFACE_WG 0 | 草地／岩地 | perimeter 原道路、台阶与外侧垫步 | 原模板 · [208 组单地块及八地块组合](audit/bandit-entry-audit.json) | 原生随机图、台阶碰撞、红沙水流、骑乘战斗与双人重启 |
| bathhouse | jigsaw | WORLD_SURFACE_WG 0 | 干地／原池水 | perimeter 外侧脚手架与原门廊 | 原模板 · [96 组基座与门厅代表布局](audit/bathhouse-entry-audit.json) | 原生拼装与拒绝率、攀爬、池水恢复、多人重启 |
| ceryneian_hind | jigsaw | WORLD_SURFACE_WG -16 | 草地／岩地，保留原埋藏高度 | perimeter 三段原尸骨旁的地面路线 | 原模板 · [16 组完整三段、原黄金与埋藏宝箱](audit/hind-entry-audit.json) | 原生生成、实际地面碰撞、发掘开箱、负载和双人重启 |
| coliseum | jigsaw | WORLD_SURFACE_WG -3 | 草地／岩地，原拱廊地板 | perimeter 四座原拱门至外圈拱廊 | 原模板 · [32 组四件主体、原骷髅与幻翼刷怪件](audit/coliseum-entry-audit.json) | 原生完整率、实际拱门与看台/场地探索、战斗和双人重启 |
| fishing_hut | jigsaw | WORLD_SURFACE_WG -2 | 干地／2～3 格浅水 | perimeter 原码头双通道 | 原模板 · [48 组岸线与刷怪件组合](audit/fishing-hut-entry-audit.json) | 游泳上岸、钓竿僵尸、水流与双人重启 |
| foundry | jigsaw | 绝对 -10 | 岩层 | start_room | 原模板 · [4 组起始房间](audit/underground-entry-audit.json) | 完整矿道、原机关和分散出口 |
| giant_mushroom | jigsaw | WORLD_SURFACE_WG -5 | 草地／岩地 | perimeter 原菌柄首段 | 原模板 · [32 组红色／双生主体与环形拼装](audit/giant-mushroom-entry-audit.json) | 原生完整率、实际攀爬与登顶、战斗和双人重启 |
| greenwood_pub | jigsaw | WORLD_SURFACE_WG -18 | 草地／岩地，入口地面升一格 | perimeter 原石阶与半砖 | 原模板 · [256 组家具与地下分支、墙材边界](audit/pub-entry-audit.json) | 原生随机图、阶梯半砖、村民刷怪与双人重启 |
| heavenly_challenger | jigsaw | 绝对 200 | 悬空 | start_deck | 原模板 · [8 组起始平台](audit/aerial-entry-audit.json) | 完整图的平台连通、坠落与额外出口 |
| heavenly_conqueror | jigsaw | 绝对 200 | 悬空 | start_deck | 原模板 · [16 组起始平台](audit/aerial-entry-audit.json) | 完整图的平台连通、坠落与额外出口 |
| heavenly_rider | jigsaw | 绝对 200 | 悬空 | start_deck | 原模板 · [16 组起始平台](audit/aerial-entry-audit.json) | 完整图的平台连通、坠落与额外出口 |
| illager_campsite | jigsaw | WORLD_SURFACE_WG 0 | 干地 | perimeter 帐篷与火圈外围 | 原模板 · [88 组巡逻道路与营地组合](audit/illager-camp-entry-audit.json) | 实际随机拼装、原敌人战斗、营火伤害和双人重启 |
| illager_corsair | jigsaw | WORLD_SURFACE_WG 0 | 干岸水域 | boarding 栈道 | 原模板 · [4 组原主船体](audit/ship-boarding-audit.json) | 随机附属件、船舱、水线和登船碰撞 |
| illager_fort | jigsaw | WORLD_SURFACE_WG -7 | 草地／岩地 | perimeter 外侧脚手架与原通道 | 原模板 · [72 组八件主体、房间与原敌人组合](audit/fort-entry-audit.json) | 原生完整率、脚手架和台阶、上下层探索、战斗与双人重启 |
| illager_galley | jigsaw | WORLD_SURFACE_WG 0 | 干岸水域 | boarding 栈道 | 原模板 · [4 组原主船体](audit/ship-boarding-audit.json) | 随机附属件、船舱、水线和登船碰撞 |
| illager_windmill | jigsaw | WORLD_SURFACE_WG -2 | 草地（测试） | perimeter 双侧石阶 | 原模板 · [16 组塔身与田地组合](audit/windmill-entry-audit.json) | 原生随机田地、实际灌溉、战斗机关和双人重启 |
| infested_temple | jigsaw | WORLD_SURFACE_WG 54 | 草地／岩地，按原基座对齐 | perimeter 原西侧台阶至底层外廊 | 原模板 · [24 组四层外壳、12 层内部及原房间/刷怪件](audit/infested-entry-audit.json) | 原生完整率、实际台阶和内部/上层通行、原战斗和双人重启 |
| jungle_tree_house | jigsaw | WORLD_SURFACE_WG -5 | 草地／岩地，随首件对齐 | perimeter 原树干至木平台 | 原模板 · [64 组两种首件与敌人分布](audit/tree-house-entry-audit.json) | 原生拼装、实际跳跃、上层探索、战斗和双人重启 |
| keep_kayra | WDA | 绝对 54 | 岩地（测试） | perimeter | 原模板 · [原主件低层路线](audit/keep-kayra-ground-evidence.json) | 超高整座拼装、上下层实际通行和高度包 |
| lighthouse | WDA | WORLD_SURFACE_WG -1 | 草地／岩地 | perimeter 原台阶 | 原模板 · [16 组完整两件](audit/lighthouse-entry-audit.json) | 原生实际生成、梯脚以上攀爬与塔顶灯组 |
| mechanical_nest | jigsaw | 绝对 190 | 悬空 | start_deck 原桥 | 原模板 · [96 组直接桥段（含拒绝样例）](audit/mechanical-bridge-audit.json) | 闸门和完整桥图的实际通行 |
| merchant_campsite | jigsaw | WORLD_SURFACE_WG 0 | 草地（测试） | perimeter 街道与帐篷 | 原模板 · [64 组街道与帐篷组合](audit/merchant-camp-entry-audit.json) | 原生随机营地、开门交易、战利品和双人重启 |
| mining_system | jigsaw | 绝对 5 | 岩层 | start_room | 原模板 · [16 组起始房间](audit/underground-entry-audit.json) | 完整矿道、原机关和分散出口 |
| monastery | jigsaw | WORLD_SURFACE_WG 0 | 草地／岩地，原底层地板 | perimeter 原通道／窗台脚手架 | 原模板 · [216 组27 种主楼、农园、翼楼与中立居民组合](audit/monastery-entry-audit.json) | 原生随机图、花坛与脚手架碰撞、水流、上层探索和双人重启 |
| mushroom_house | jigsaw | WORLD_SURFACE_WG 0 | 干地 | perimeter 原云杉台阶 | 原模板 · [160 组底层、菌盖与刷怪接口](audit/mushroom-house-entry-audit.json) | 实际拼装、进屋碰撞、刷怪与双人重启 |
| mushroom_mines | jigsaw | WORLD_SURFACE_WG -8 | 干地 | start_mine | 原模板 · [16 组八件主体](audit/mushroom-mine-audit.json) | 完整随机图拒绝率、原陷阱与岩坡碰撞 |
| mushroom_village | jigsaw | WORLD_SURFACE_WG 0 | 草地／岩地 | perimeter 原半砖坡与各房屋 | 原模板 · [192 组主体、房屋及水池](audit/village-entry-audit.json) | 原生随机图、木门半砖、水流海龟、战斗与双人重启 |
| plague_asylum | jigsaw | 绝对 0 | 厚岩层 | start_room 原地下房间 | 原模板 · [56 组地下分支、病房与原设备组合](audit/plague-entry-audit.json) | 原生完整图、跨房间/上下层通行、陷阱水流与双人重启 |
| scorched_mines | jigsaw | WORLD_SURFACE_WG -14 | 岩地 | start_well | 原模板 · [16 组井与大厅](audit/scorched-well-audit.json) | 熔岩释放、随机支路和竖井下坠 |
| shiraz_palace | jigsaw | WORLD_SURFACE_WG 16 | 草地／岩地，高台对齐 | perimeter 北侧花园支撑桥 | 原模板 · [24 组完整三层主体与原遭遇](audit/shiraz-entry-audit.json) | 原生完整图、桥/含水台阶、内部上层、流体点火与双人重启 |
| small_blimp | jigsaw | WORLD_SURFACE_WG 100 | 干地 | start_ladder | 原模板 · [12 组梯链／船体](audit/blimp-ladder-audit.json) | 实际悬梯稳定性、铁门及完整随机图 |
| small_prairie_house | jigsaw | WORLD_SURFACE_WG 0 | 干地 | perimeter 原门与废墟机关走道 | 原模板 · [8 组完好屋与废墟](audit/prairie-house-entry-audit.json) | 实际开门、红石时序、TNT 与双人重启 |
| thornborn_towers | jigsaw | 绝对 130 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 核对绝对起点、周边地形和原入口高差 |
| typhon | jigsaw | OCEAN_FLOOR -4 | 海床与干岸 | shore_dive | 原模板 · [16 组完整三件](audit/typhon-dive-audit.json) | 气泡、供氧、上岸与埋藏宝箱 |
| undead_pirate_ship | jigsaw | WORLD_SURFACE_WG 0 | 干岸水域 | boarding 栈道 | 原模板 · [4 组原主船体](audit/ship-boarding-audit.json) | 随机附属件、船舱、水线和登船碰撞 |
| wishing_well | jigsaw | WORLD_SURFACE_WG -7 | 草地／岩地 | perimeter 原坡至井沿 | 原模板 · [168 组井底效果与坡道组合](audit/wishing-well-entry-audit.json) | 实机跳坡、下井效果、水流与双人重启 |

机器可读表还记录起始池、群系选择器和每座建筑原有的资源缺口。[完整数据](audit/wda-entry-coverage.json)。

在仓库根目录执行下列命令可用锁定 JAR 和已提交的审查报告复核清单。

~~~text
node work/l13/audit-entry-coverage.cjs --check
~~~
