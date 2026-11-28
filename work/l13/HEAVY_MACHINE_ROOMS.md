# 重型机关房机械档案：jungle_riddle_fixed 与 telecommunication

2026-11-28 · 静态交付，未实机验收。设计依据：两间真实内容房原无同路径生成器（属 17 份缺口），需 labfinal 同级的机械档案与受信任运动审查后才能适配。

## 交付内容

| 层 | 内容 |
|---|---|
| 模板指纹与几何 | 从锁定 DD 5.4.4 JAR 的 `.schem`（Sponge v2）解码全部机械组件，0 基相对坐标，由 `audit-machine-components.cjs` 生成并固定在桥接资源 `data/expedition_bridge/machine_profiles/<房间>.json`（含模板 SHA-256） |
| 生成器定义 | 桥接资源 `data/dimdoors/pockets/generators/<房间>.json`：`dimdoors:schematic` + `lazy_gen_pocket`，jungle 用 `rift_data ids[0]→pocket_entrance`，telecommunication 用 `ids[1]→pocket_entrance`；DD 的 PocketLoader 走原版 ResourceManager，会合并桥接资源 |
| 生成时验证 | `MachineProfileInstaller` 扫描实例边界内全部机械类方块，稳定组件（720/385 件）按平移归一后与档案逐件比对（类型+朝向+相对位置），活塞头/移动方块等易变单元只保护不参与比对，失败即拒绝房间 |
| 保护 | 全部稳定组件、安装时存在的易变单元及其下方非空气支撑，写入 ProtectedBlocks（与裂隙/出口保护合并，仍在 4096 上限内） |
| 受信任运动 | `MachineMotionPolicy`：活塞事件只允许档案中固定的活塞底座+朝向；足迹中被保护的位置必须全部是机械组件（裂隙、路线端点、支撑与结构方块一律拦截）；通过后 `TrustedMachineMotion` 发放 64 tick 写窗口 |

## 关键决定

- **易变单元不进验证**：区块放置期间红石会立即反应，活塞头/移动方块可能已离开 `.schem` 静态状态；稳定组件的类型与朝向不受影响，足以锁定模板。
- **运动规则按房间泛化**：lab/labfinal 保持原有的"底座+头+黏液载荷"规则；两间新房的活塞会推动/破坏自己的组件（如墙上的红石火把随墙体移动脱落），因此允许触及本机组件、禁止触及一切非组件受保护位置。
- **裂隙处理**：jungle 唯一裂隙经入口数据成为到达点兼出口；telecommunication 的 relative 对保留为实例内连接，id_marker 1 成为入口，id_marker 2 转成受管出口（均按既有 NativeRiftPlan 规则）。
- **生成器注入**：不修改 DD 文件；PocketLoader 按资源优先级合并。若数据包提供同路径定义覆盖了桥接版本，`NativeGeneratorPolicy` 验证会在生成时拒绝不匹配的配置（失败安全）。

## 证据

- 组件清单与模板指纹：`audit/*-machine-components.json`（由提交的工具可重建）。
- 定义策略：`audit/native-content-policy.json` 现为 **107/125**（+2 `bridge_generator_definition`）。
- 测试：`MachineProfileTest` 6 项（指纹、平移验证、易变容忍、篡改拒绝、运动许可）、`TrustedMachineMotionTest` 5 项（窗口几何）、`NativeContentPolicyAuditTest` 更新；全量 431 项通过。

## 验收边界

- 未实机：桥接生成器是否被 PocketLoader 在整包 `/reload` 后实际加载、放置后的几何验证、谜题/通信机关可解性、活塞运动窗口的真实 Mixin 行为均待隔离验收。
- 书架/书本内容与告示牌文本永不提取、不保存、不上传。
- 定义策略通过不等于房间已纳入正式池；正式候选仍待五级池定案（见 FORMAL_TIERS.md）。
- 剩余 18 份缺口不变：8 blank、7 classic/ruins/unused 按设计不启用，3 份 classic 生成器指向别处。
