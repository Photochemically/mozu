# L13 统一探索目录：开发格式

2026-11-06。格式已编译，尚无已批准并启用的建筑分级／权重配置，未做游戏加载验收。当前随 JAR 提供的唯一目录资源是 `enabled=false` 的设置；不是可玩地牢池。

## 资源位置

- 全局设置：`data/expedition_bridge/expedition_dungeons/settings.json`。
- 候选：`data/<namespace>/expedition_dungeons/<path>.json`，候选标识为 `<namespace>:<path>`。同标识的数据包覆盖遵循 Minecraft 资源优先级。
- 此目录与主世界自然入口密度／门类型权重分开；这里只管理一扇已存在探索门的内容选择。自然生成器与开关已单独实现，见 [自然入口说明](NATURAL_ENTRANCES.md)。两者当前均关闭。

## 设置字段

| 字段 | 类型与约束 | 含义 |
|---|---|---|
| schema | 整数1 | 目录格式版本，不是存档版本 |
| enabled | 布尔值 | 当前为 false；开启前必须补齐所有等级候选和明确的不稳定等级权重 |
| unstable_tier_weights | 五个0～1000000的整数，合计大于0 | 依次对应I～V，相对权重；关闭时可省略，没有内置玩法默认值 |

## 每项候选字段

| 字段 | 类型与约束 | 含义 |
|---|---|---|
| schema | 整数1 | 候选格式版本 |
| provider | `wda` 或 `native` | 保留独立提供者，统一参与同级加权抽取 |
| target | 资源标识 | WDA为 `dungeons_arise:<结构名>`；原生为 `dimdoors:<生成器路径>` |
| tiers | 非空、无重复的1～5整数数组 | 可出现的等级；暂不提供擅自定案的分配表 |
| weight | 1～1000000整数 | 在每个兼容等级内的相对权重，不是百分比 |
| exits | WDA为1～4，原生为0 | WDA的perimeter模式按东、西、北、南选择岛缘出口，start_room／start_deck模式从起始房间和其他原房间选择，start_ladder使用梯脚主入口及其他原船体出口，start_mine使用已验证步行路线的地面入口及其他原组件出口，start_well使用原熔岩井沿外的地面入口及其他原地下房间出口，shore_dive按要求使用1～4处原建筑外干岸出口；原生0表示自动审查原裂隙：保留有效内部连接、改接外部出口，不是无出口 |
| environment | WDA必填，原生不允许 | 见下表；原生保留自带空间 |

| environment字段 | 约束 | 含义 |
|---|---|---|
| first_free_y | -64与320之间的整数，不含端点 | 规划地表上方第一层；真正口袋仍须通过完整高度检查，此范围不保证可生成 |
| padding | 4～64整数 | 建筑外平坦支撑范围，给出口留位置 |
| rim | 1～64整数 | 外缘渐薄范围 |
| foundation_depth | 4～128整数 | 基础厚度；地下建筑仍须核对原模板完整范围 |
| surface | `grass`、`rock`、`water` 或 `void` | 草地／岩地／有实体底部与干岸的水域；void保留悬空原建筑，不生成地形 |
| water_depth | `water`必填2～64整数；其他表层省略或0 | 水面第一层为first_free_y，海床第一层为first_free_y-water_depth；随选择快照保存，没有正式默认水深 |
| boarding | 可选布尔值，默认false；仅water可为true | 完整原建筑放置后补建干岸至原船体的栈道；无合适路线时保留并隔离实例，不拆墙或换建筑 |
| entry_mode | perimeter（默认）、start_room（要求rock）、start_deck（必须与void成对）、start_ladder（仅小飞艇、grass/rock干地）、start_mine（仅蘑菇矿井、grass/rock干地）、start_well（仅灼热矿井、rock干地）、shore_dive（仅typhon、water且boarding=false） | 依次使用岛缘、地下原通道、空中原平台、原梯脚、已验证步行路线的地面入口、原熔岩井沿外入口、海床建筑干岸潜水入口。机械巢穴专用原闸门外桥面；其他原组件选择额外出口。找不到全部要求位置时拒绝开放 |

WDA出口支持岛屿边缘、地下原房间和空中原平台选址，不能当作所有大型建筑内部路线已完成。原生候选必须能解析到同标识的单 schematic 生成器与模板；不会把不支持的生成器默默替换成其他房间。新增建筑模组需要新增受检查的提供者，不能只伪造命名空间绕过接口约束。

水域先建底部／侧壁再放水，入口与出口位于干岸；施工期间暂存本实例水流更新，原结构完整放置后按预算释放。原WDA的水面／海床高度查询保持区别。具体边界、海洋建筑原定义及未完成的登船路线见[水域环境模块](WATER_ENVIRONMENTS.md)，不能只切换water就认定建筑已适配。旧干燥候选／保存快照省略water_depth时保持原行为。

登船模块的 `boarding: true` 随首次选择固定；先完成原建筑与栈道，再恢复水流、安装出口并激活。旧快照缺少boarding时保持false。栈道普通可拆改，重进不重铺。搜索边界、正常一格跳跃和原船体静态检查见[登船路线](SHIP_BOARDING.md)，未自动确定正式船只候选或等级。

## 选择、重载与失败处理

地下入口的 `entry_mode: "start_room"` 随首次选择保存，先完成原建筑，再从原水平通道寻找安全平台。首次与再次进入使用同一起始房间入口；出口数量不足时保留并隔离实例。实际模板证据、岩层限制及未适配矿井见[地下入口](UNDERGROUND_ENTRIES.md)。

先保存每名玩家本次进入的边狱结果。分流时不选内容、不分配地牢；正常分支第一次选择会固定实际等级、候选、目标、权重与环境／出口快照。资源重载只改变尚未选择的入口，不能重置已选或已生成实例。

同实例共享一份选择，各玩家保留自己的操作与锚点。生成失败保留预留及快照并隔离，不自动换房间、重放建筑或删除区块。切换出口重试不能重抽未完成的离开结果。已生成实例不依赖当前候选仍存在才允许返回。

资源加载先校验整份新目录，再替换旧快照；非法条目使本次目录加载失败。尚未验证与整包 `/reload` 事件顺序的兼容性。开启目录不代表自然入口、战斗、奖励与保护系统都已完成，不能单独作为发布开关。

空中 `void`／`start_deck` 的细则见[模块说明](AERIAL_ENVIRONMENTS.md)。仍保留旧环境字段格式，但first_free_y、padding、rim、foundation_depth在此模式下不生成地基；原建筑完整范围与16格隔离余量不变。机械巢穴使用[专用桥侧入口](MECHANICAL_BRIDGE_ENTRY.md)，找不到合格原桥平台仍拒绝开放；小飞艇使用[专用梯脚入口](SMALL_BLIMP_ENTRY.md)，原规划的first_free_y仍供原生成器使用，施工地面第一空层改由实际最低梯子推导；不改变原组件高度关系，不自动加入可用池。旧快照保留已有到达点，新规划拒绝为小飞艇使用其他模式。

蘑菇矿井的 `start_mine` 规则见[稀疏空间与地表入口](MUSHROOM_MINE_ENTRY.md)。原规划first_free_y仍供生成器使用，施工地面第一空层由完整主体最低Y推导，地基不进入任何原组件。新规划不允许用perimeter/start_room代替，旧快照仍可读取并保留既有位置。完整八件、原接口、真实路线和出口数量都必须通过后才开放。

灼热矿井的 `start_well` 规则见[原熔岩井沿入口](SCORCHED_WELL_ENTRY.md)。保留原规划和全部组件坐标，施工地面第一空层取原井底加13；核对完整原井／四件大厅、原熔岩、井沿路线与竖井。施工暂存本实例水与熔岩tick，完工后按预算恢复；不删除危险或保证安全下井。新规划不允许用其他模式代替，旧快照可读；保存格式仍为5。

海床建筑 typhon 使用 shore_dive。原件相对海床的偏移固定，水深须覆盖双格潜水路线（4～64）。环境从候选显式读取，正式值待定。主门和附加门都位于经过检查的干岸，施工后核对原危险和水道。详见[海床与干岸潜水入口](TYPHON_DIVE_ENTRY.md)。

灯塔 lighthouse 使用 grass 或 rock、perimeter 和关闭的 boarding。firstFreeY 传给原生规划器。新实例施工采用实际塔底（规划首个空层减 2）推导地面以保留原台阶，出口和保存方式沿用岛缘规则。详见[灯塔入口](LIGHTHOUSE_ENTRY.md)。

掠夺者风车 illager_windmill 沿用干地 perimeter，关闭 boarding。草地开发案例地表为 96，原塔底为 93。沿用原生刚性塔身与 terrain_matching 农田，施工后检查所有指定出口到原石阶的旱路。详见[风车入口](WINDMILL_ENTRY.md)。

商人营地 merchant_campsite 沿用干地 perimeter 和关闭的 boarding。草地案例为规划首个空层 96、地基深度 16。生成后检查所有指定出口、每顶原帐篷以及中央地图箱。见[商人营地入口](MERCHANT_CAMP_ENTRY.md)。

掠夺者营地 illager_campsite 使用干地 perimeter，关闭 boarding。桥接沿实际地面搜索所有指定出口、原帐篷内部和火圈外的一处落脚点，保留围住地图箱的四处信号营火及原遭遇点。见[掠夺者营地](ILLAGER_CAMP_ENTRY.md)。

草原小屋和蘑菇小屋使用干地 perimeter。桥接按原件拼装结果核对两种草原门道、废墟机关或蘑菇屋云杉台阶，再检查岛缘出口到屋外的路线。[小屋入口与配置范围](GROUND_HOUSE_ENTRIES.md)。

浴场 bathhouse 使用干地 perimeter。桥接在完整模板外从原岛面搭设垂直脚手架，按所选中层从西侧或东侧进门，保留原池水。已占用且会覆盖战利品箱的屋顶接口在规划时拒绝。见[浴场入口](BATHHOUSE_ENTRY.md)。

渔屋 fishing_hut 使用 perimeter，支持干地或 water 水深 2、3。水域 boarding=true 在岸与可达原码头之间补一块橡木板；关闭时保留水道。更深水会使原云杉柱脚悬空。[渔屋入口与水线](FISHING_HUT_ENTRY.md)。

许愿井采用 grass 或 rock 的 perimeter，boarding 关闭。WDA 默认起始池要求完整效果件；显式重新指定其独立无效果池时才接受无云主体。[许愿井的起始池与井沿](WISHING_WELL_ENTRY.md)。

巨型蘑菇 giant_mushroom 采用 grass 或 rock 的 perimeter，boarding 关闭。红色要求四件主体和一个原骷髅附件，双生要求八件主体和两个附件。地面出口连接首段原菌柄。[巨型蘑菇入口](GIANT_MUSHROOM_ENTRY.md)。
