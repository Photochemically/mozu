# 原生即时房间的桥接适配与剩余缺口

2026-11-20。针对 DD 5.4.4，完成六份定义的代码适配；没有自动纳入正式地牢池，也没有实际生成或运行验收。

后续进度：已增加[局部设备保护的记录和事件拦截](LOCAL_DEVICE_PROTECTION.md)，保存格式现为 6。下文六间适配当批的生成/路线规则继续保留；labfinal 仍拒绝，未因基础保护代码而增加定义通过数。

## 实现方式

`NativeDefinitionAdapter` 只接收桥接正在创建的精确同标识房间。原定义未提供 builder 时，仅对白名单中的六间，在独立副本中补上原生 `dimdoors:lazy_gen_pocket`；随后仍执行严格的 `NativeGeneratorPolicy`。已有 lazy 定义也复制后验证。显式其他 builder、错误目标、未审查附加组件不会被覆盖或删除。

不是另写模板放置器：原 `SchematicGenerator` 本来就同时实现即时及按区块放置。后者仍调用原模板放置、原战利品设置和暂存裂隙修饰器。保留原模板标识、修饰器、相对机关、天空/战利品设置和放置方式。原始资源、PocketLoader 中缓存的生成定义及玩家生活空间流程不修改。序列化前已被上游忽略的旧字段，不宣称因此获得了效果。

预留完整空间、外壳范围修正、生成前检查点、逐区块施工、原裂隙捕获及出口接管沿用现有模块；存档 schema 仍为 5。重复进入不会重跑适配或生成。

| 原房间 | 原尺寸（X×Y×Z） | 关键内容与边界 |
| --- | --- | --- |
| example/purpur_hallway | 9×5×5 | 八个裂隙、三对原内部连接及一层粉色织物外壳；公共空间入口目标在启用前转为主世界出口 |
| myth/eden | 1913×34×35 | 保留完整长条模板、两处裂隙、容器及建筑设备；不裁去空白/远端区域 |
| myth/sewer | 12×21×1013 | 保留完整模板和机关设备；入口标记 1，另一端为原生 global 目标 |
| myth/timoria | 31×31×31 | 保留三处裂隙、容器、漏斗及设备 |
| nether/nether_farm | 31×31×31 | 保留两处裂隙、原宝箱及营火 |
| nether/nether_spawners | 31×31×31 | 保留两处裂隙、原宝箱和九份刷怪笼数据；不改其生成内容、计时或范围 |

`sewer` 的定义还声明标记 2，但原件中没有该标记。上游 RiftDataModifier/RiftManager 只遍历已有标记，因此这条声明不应用，不补造裂隙。真实 global 裂隙由 NativeRiftInstaller 在启用前改为受管出口；检查时不解析它的原目标、不加载旧主世界坐标，也不创建下一口袋。

`eden` 和 `sewer` 的范围明显偏大。虽然继续按区块施工，DD 首次构建模板缓存、空间分配和每区块处理仍可能造成高开销，不能以分批施工推断实际负载合格。正式启用前须测首次峰值、耗时、全部区块完成及重进/重启。六份被审查的原即时模板均无独立 Entities 列表内容；这不是其他原生房间实体处理已经验收的结论。

## 仍拒绝的 21 份

125 份模板现有 **104 份定义策略通过**（原 98 + 本批 6），剩余：

- 17 份没有同路径生成器：8 份 blank、7 份 classic/ruins/unused、dungeon/jungle_riddle_fixed、lab/telecommunication。仍是素材/定义缺口，不擅自启用 blank/unused。
- 3 份生成器指向另一真实模板：simple_small_t → heart_of_disorder，small_maze → hot_suspense，transfer_tunnel → spiral_hallway（均位于 classic/ruins）。不把不同房间当作别名替换。
- `lab/labfinal`：原 `prevent_block_modification` 确实由 DimensionalDoors 的 BREAK/PLACE 事件检查执行，并非无效附加组件。必须先确定关键机关的局部保护及间接修改路径，不能整房间禁建，也不能无条件剥除保护。

审查纠正：`nether_spawners` 中 `minecraft:mob_spawner` 不是兼容缺口。核对当前 Minecraft BlockEntityType 的实际注册代码后，确认它正是有效方块实体标识，与方块标识 `minecraft:spawner` 不同。保留全部九份数据，无需迁移或改名；已加入注册契约检查，不能再依据名称把它误判为过期数据。

## 证据与验收边界

原 JAR 中定义/模板检查见 [定义报告](audit/native-content-policy.json)、[六份即时原件元数据](audit/native-definition-adapters.json)；后者记录尺寸、源文件指纹、设备种类、裂隙目标与缺失标记，不保存原模板内容。

新增五项定义/资源测试及三项锁定版本字节码契约测试，覆盖副本隔离、字段保留、拒绝边界、全部六份即时模板、真实生成调用顺序、实验室保护执行点及 Minecraft 刷怪笼注册。构建结果以 [当前回执](audit/build-summary.json) 为准。原始字节码与构建日志只在本地保存。

集中实机验收仍需覆盖：六间原房完整生成、裂隙注册、purpur 内部谜题与外壳、sewer 两端出口、原设备/刷怪笼与战利品、两实例隔离、两人返回/重启、超长模板负载及自建空间例外。当前七类快捷命令没有新增这六间的自动生成案例。定义策略通过不等于房间可达、机关可解、战斗合理或真实性能合格。
