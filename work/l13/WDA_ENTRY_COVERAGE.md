# WDA 建筑环境与入口覆盖表

WDA 2.1.58 共 39 座建筑。18 座有原模板路线或入口几何证据，0 座有开发案例且仍待核对原路线，21 座目前只完成资源图审查。游戏内验收为 0 座。

生成器栏的 WDA 指自定义 generic_structures，其余为原版 jigsaw。高度图一栏给出原始高度来源与偏移。绝对表示原定义没有投影高度图。样例的范围见对应报告，正式分级和权重待定。

| 建筑 | 生成器 | 高度来源与值 | 环境 | 入口实现 | 当前证据 | 下一项核对 |
|---|---|---|---|---|---|---|
| abandoned_temple | jigsaw | WORLD_SURFACE_WG -2 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 从原起始件核对地表、主入口与可达路线 |
| aviary | jigsaw | 绝对 50 | 悬空 | start_deck | 原模板 · [4 组起始平台](audit/aerial-entry-audit.json) | 完整图的平台连通、坠落与额外出口 |
| bandit_towers | jigsaw | 绝对 135 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 核对绝对起点、周边地形和原入口高差 |
| bandit_village | jigsaw | WORLD_SURFACE_WG 0 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 从原起始件核对地表、主入口与可达路线 |
| bathhouse | jigsaw | WORLD_SURFACE_WG 0 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 从原起始件核对地表、主入口与可达路线 |
| ceryneian_hind | jigsaw | WORLD_SURFACE_WG -16 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 检查埋入地面的部分和原门道净空 |
| coliseum | jigsaw | WORLD_SURFACE_WG -3 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 从原起始件核对地表、主入口与可达路线 |
| fishing_hut | jigsaw | WORLD_SURFACE_WG -2 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 从原起始件核对地表、主入口与可达路线 |
| foundry | jigsaw | 绝对 -10 | 岩层 | start_room | 原模板 · [4 组起始房间](audit/underground-entry-audit.json) | 完整矿道、原机关和分散出口 |
| giant_mushroom | jigsaw | WORLD_SURFACE_WG -5 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 检查埋入地面的部分和原门道净空 |
| greenwood_pub | jigsaw | WORLD_SURFACE_WG -18 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 检查埋入地面的部分和原门道净空 |
| heavenly_challenger | jigsaw | 绝对 200 | 悬空 | start_deck | 原模板 · [8 组起始平台](audit/aerial-entry-audit.json) | 完整图的平台连通、坠落与额外出口 |
| heavenly_conqueror | jigsaw | 绝对 200 | 悬空 | start_deck | 原模板 · [16 组起始平台](audit/aerial-entry-audit.json) | 完整图的平台连通、坠落与额外出口 |
| heavenly_rider | jigsaw | 绝对 200 | 悬空 | start_deck | 原模板 · [16 组起始平台](audit/aerial-entry-audit.json) | 完整图的平台连通、坠落与额外出口 |
| illager_campsite | jigsaw | WORLD_SURFACE_WG 0 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 从原起始件核对地表、主入口与可达路线 |
| illager_corsair | jigsaw | WORLD_SURFACE_WG 0 | 干岸水域 | boarding 栈道 | 原模板 · [4 组原主船体](audit/ship-boarding-audit.json) | 随机附属件、船舱、水线和登船碰撞 |
| illager_fort | jigsaw | WORLD_SURFACE_WG -7 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 检查埋入地面的部分和原门道净空 |
| illager_galley | jigsaw | WORLD_SURFACE_WG 0 | 干岸水域 | boarding 栈道 | 原模板 · [4 组原主船体](audit/ship-boarding-audit.json) | 随机附属件、船舱、水线和登船碰撞 |
| illager_windmill | jigsaw | WORLD_SURFACE_WG -2 | 草地（测试） | perimeter 双侧石阶 | 原模板 · [16 组塔身与田地组合](audit/windmill-entry-audit.json) | 原生随机田地、实际灌溉、战斗机关和双人重启 |
| infested_temple | jigsaw | WORLD_SURFACE_WG 54 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 核对正高度偏移后的落脚处及原接近路线 |
| jungle_tree_house | jigsaw | WORLD_SURFACE_WG -5 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 检查埋入地面的部分和原门道净空 |
| keep_kayra | WDA | 绝对 54 | 岩地（测试） | perimeter | 原模板 · [原主件低层路线](audit/keep-kayra-ground-evidence.json) | 超高整座拼装、上下层实际通行和高度包 |
| lighthouse | WDA | WORLD_SURFACE_WG -1 | 草地／岩地 | perimeter 原台阶 | 原模板 · [16 组完整两件](audit/lighthouse-entry-audit.json) | 原生实际生成、梯脚以上攀爬与塔顶灯组 |
| mechanical_nest | jigsaw | 绝对 190 | 悬空 | start_deck 原桥 | 原模板 · [96 组直接桥段（含拒绝样例）](audit/mechanical-bridge-audit.json) | 闸门和完整桥图的实际通行 |
| merchant_campsite | jigsaw | WORLD_SURFACE_WG 0 | 草地（测试） | perimeter 街道与帐篷 | 原模板 · [64 组街道与帐篷组合](audit/merchant-camp-entry-audit.json) | 原生随机营地、开门交易、战利品和双人重启 |
| mining_system | jigsaw | 绝对 5 | 岩层 | start_room | 原模板 · [16 组起始房间](audit/underground-entry-audit.json) | 完整矿道、原机关和分散出口 |
| monastery | jigsaw | WORLD_SURFACE_WG 0 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 从原起始件核对地表、主入口与可达路线 |
| mushroom_house | jigsaw | WORLD_SURFACE_WG 0 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 从原起始件核对地表、主入口与可达路线 |
| mushroom_mines | jigsaw | WORLD_SURFACE_WG -8 | 干地 | start_mine | 原模板 · [16 组八件主体](audit/mushroom-mine-audit.json) | 完整随机图拒绝率、原陷阱与岩坡碰撞 |
| mushroom_village | jigsaw | WORLD_SURFACE_WG 0 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 从原起始件核对地表、主入口与可达路线 |
| plague_asylum | jigsaw | 绝对 0 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 核对绝对起点、周边地形和原入口高差 |
| scorched_mines | jigsaw | WORLD_SURFACE_WG -14 | 岩地 | start_well | 原模板 · [16 组井与大厅](audit/scorched-well-audit.json) | 熔岩释放、随机支路和竖井下坠 |
| shiraz_palace | jigsaw | WORLD_SURFACE_WG 16 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 核对正高度偏移后的落脚处及原接近路线 |
| small_blimp | jigsaw | WORLD_SURFACE_WG 100 | 干地 | start_ladder | 原模板 · [12 组梯链／船体](audit/blimp-ladder-audit.json) | 实际悬梯稳定性、铁门及完整随机图 |
| small_prairie_house | jigsaw | WORLD_SURFACE_WG 0 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 从原起始件核对地表、主入口与可达路线 |
| thornborn_towers | jigsaw | 绝对 130 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 核对绝对起点、周边地形和原入口高差 |
| typhon | jigsaw | OCEAN_FLOOR -4 | 海床与干岸 | shore_dive | 原模板 · [16 组完整三件](audit/typhon-dive-audit.json) | 气泡、供氧、上岸与埋藏宝箱 |
| undead_pirate_ship | jigsaw | WORLD_SURFACE_WG 0 | 干岸水域 | boarding 栈道 | 原模板 · [4 组原主船体](audit/ship-boarding-audit.json) | 随机附属件、船舱、水线和登船碰撞 |
| wishing_well | jigsaw | WORLD_SURFACE_WG -7 | 待定 | 通用岛缘待核对 | 资源图 · [资源引用](audit/wda-alias-audit.json) | 检查埋入地面的部分和原门道净空 |

机器可读表还记录起始池、群系选择器和每座建筑原有的资源缺口。[完整数据](audit/wda-entry-coverage.json)。

在仓库根目录执行下列命令可用锁定 JAR 和已提交的审查报告复核清单。

~~~text
node work/l13/audit-entry-coverage.cjs --check
~~~
