# 下一任务：保护挂接小测与实验室可信机关运动

最新运行状态：2026-11-21 创建世界/重进与 NEW 懒入口通过；首次原生生成在读取裂隙时失败。已完成区块重取与失败通知修复，编译通过，待新入口实机复测。先读 [本轮记录](work/l13/RUNTIME_SMOKE_20261121.md)；下文旧“未安装/未启动”指历史批次。

更新：2026-11-21。先读 PROJECT.md、PROGRESS.md、[局部设备保护](work/l13/LOCAL_DEVICE_PROTECTION.md)、[即时定义适配](work/l13/NATIVE_DEFINITION_ADAPTERS.md)、[原生路线](work/l13/NATIVE_ROOM_ROUTES.md)，再读对应源码。最新用户玩法优先。

1. 在第一批精确保存/Forge 事件基础上，第二批已接入 LevelChunk 方块替换、Level 标准销毁和 FlowingFluid 冲刷前拦截；主线程只读保存坐标/内存目录，不触发其他区块加载。同种方块状态更新正常。入口放置/关闭使用 ExactBlockWrite 单次精确许可，别用宽泛线程开关替代它。新增原版黏性活塞缩回第二格移动完成位置检查。保存格式仍为 6，旧记录不重扫、重建。
2. 继续处理 lab/labfinal，而不是立即剥除 prevent_block_modification。原件 25 个机械组件（17 红石、2 黏性活塞、2 活塞头、2 中继器、1 黏液、1 拉杆），加下方非空气支撑共 46 个候选位置；候选尚未成为最终保护名单。需完整审查供电/黏连/移动范围，允许原两只活塞合法运动、回缩和完成移动，同时拒绝外部机械搬运。PistonBaseBlock.triggerEvent 在回缩 Pre 之后可对头部和第二格调用 finalTick；PistonMovingBlockEntity.tick/finalTick 会先移除方块实体再改块。不能仅在 Pre 放行，否则新底层拦截会留下不完整运动。没有给实验室建立运动白名单，仍拒绝。
3. 核查 Create 等机械组装/搬运整笔操作：只阻止其最后删块不足以防止先创建移动实体的副作用。直接改区块分区、删除/改写方块实体、桶或特殊流体的先行副作用、火焰/方块自身更新仍待审查。普通销毁/水流已有前置拦截，不代表全面模组兼容。补齐关键实体/非方块设备范围与原 32 个实体的放置检查。普通墙壁和普通刷怪笼继续可拆，不取消正常交互；所有怪物爆炸不破坏地牢环境仍是独立未完成要求，当前爆炸只保受保护位置。
4. 已到适合一次小批运行验证的节点，已再次向用户说明。按 LOCAL_DEVICE_PROTECTION.md 准备隔离实例与 dark_oak_exits/gold_reward 案例：启动/生成、保护与普通建造对照、命令替换/销毁、外部活塞、金门单人正常关闭、一次保存重启及返回。优先确认三处新增/扩展 Mixin 在生产 JAR 实际应用，入口创建/关闭没有被自己的保护锁死。原 chain_t 没有宝箱，不登记奖励持久化通过；该项目以后用 cherry_native。当前尚未执行。不要自动安装桥接、打开正式开关或修改生产存档。测试结束提交脱敏摘要、必要脚本/修复并核对远程；原日志与存档不上库。
5. mc-pilot 和 MC-Runtime-Test 仍仅完成只读研究，未安装或接入。可按后续测试需要评估前者的真实玩家/多人操作和后者的启动/GameTest，不把通用版本支持当成本整合包兼容性通过。
6. 六份即时定义适配保留，原生定义检查仍为 104/125，剩余 21 份：labfinal、三份指向其他模板的 classic 生成器、17 份无同路径定义。三份 classic 原文件在 classic 资源包；不能以别名替换不同建筑，也不能为凑通过数启用 blank/unused。nether_spawners 的 minecraft:mob_spawner 是有效原版方块实体名，无需迁移；eden 长 1913 格、sewer 长 1013 格，保留完整尺寸并留待负载实测。
7. WDA 39/39 仅有入口或路线静态证据，0 实机验收；未改动建筑不重复全量检查。后续还有正式五级目录、Born in Chaos/Mutant Monsters/神化战斗与地形破坏、首领最终死亡/一次性奖励、III+ 材料。L12 实施 0%，终局合成未完成。阶段性小测不替代最终完整多人、异常恢复和存档验收。

本批 block-replacement-build.log 的定向 test + jar/reobfJar 成功（1m56s），执行 211 项测试全部通过，0 失败/错误，其中新增 13 项。其余 154 项沿用基线 ffa342c 的完整 352 项结果，源码指纹见 audit/block-replacement-validation.json。合计 365 个不同测试，不是本轮全量运行；当前 XML 仅含 211 项，见 audit/build-summary.json。原实验室结构审查仍见 audit/native-lab-protection-audit.json，runtimeTested=false、adapterEnabled=false。JAR：784608 字节，SHA256 5C227E95EFA44DBFA8C78FF9926C18C72D181E96360505B927B23F8754FABD61。

每阶段更新交接文档，检查敏感信息并逐文件提交/推送，核对远程。辅助包本批增加说明后为 40 说明 + 1 标记，七类快捷命令不变、未安装。原日志、第三方 JAR/原 NBT、存档、缓存和辅助 ZIP 不上传。
