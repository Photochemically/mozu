# 下一任务：实验室自身机关运动与间接修改保护

更新：2026-11-20。先读 PROJECT.md、PROGRESS.md、[局部设备保护](work/l13/LOCAL_DEVICE_PROTECTION.md)、[即时定义适配](work/l13/NATIVE_DEFINITION_ADAPTERS.md)、[原生路线](work/l13/NATIVE_ROOM_ROUTES.md)，再读对应源码。最新用户玩法优先。

1. 本批已完成局部保护基础：ProtectedBlocks 精确集合，原生出口/内部端点及支撑登记，放置覆盖、多方块快照、原版活塞双向移动、工具改块及部分怪物/流体 Forge 事件拦截。保护位置纳入保存格式 6，兼容 1～5；旧记录不重扫、重建，格式 6 丢失或损坏保护集合明确拒绝读入。状态快照新增 protectedBlocks。
2. 继续处理 lab/labfinal，而不是立即剥除 prevent_block_modification。原件 25 个机械组件（17 红石、2 黏性活塞、2 活塞头、2 中继器、1 黏液、1 拉杆），加下方非空气支撑共 46 个候选位置；候选尚未成为最终保护名单。需完整审查供电/黏连/移动范围，允许原两只活塞合法运动、回缩和完成移动，同时拒绝外部机械搬运。现在的通用活塞拦截没有自有机器例外，直接登记会冻结机关，因此实验室仍拒绝。
3. 核查 Create 等机械组装/搬运、绕过 Forge 事件的直接改块、桶/流体冲刷与火焰/方块更新等路径；本批 FluidPlaceBlockEvent 不是全面流体保护。补齐关键实体/非方块设备范围与原 32 个实体的放置检查。普通墙壁和普通刷怪笼继续可拆，不取消正常交互；所有怪物爆炸不破坏地牢环境仍是独立未完成要求，本批爆炸只保受保护位置。
4. 已到适合一次小批运行验证的节点，主动向用户说明首轮范围，按 LOCAL_DEVICE_PROTECTION.md 准备隔离实例与 dark_oak_exits 案例：启动/生成、保护与普通建造对照、外部活塞、一次保存重启及返回。原 chain_t 没有宝箱，不登记奖励持久化通过；该项目以后用 cherry_native。当前尚未执行。不要自动安装桥接、打开正式开关或修改生产存档。测试结束提交脱敏摘要、必要脚本/修复并核对远程；原日志与存档不上库。
5. mc-pilot 和 MC-Runtime-Test 仍仅完成只读研究，未安装或接入。可按后续测试需要评估前者的真实玩家/多人操作和后者的启动/GameTest，不把通用版本支持当成本整合包兼容性通过。
6. 六份即时定义适配保留，原生定义检查仍为 104/125，剩余 21 份：labfinal、三份指向其他模板的 classic 生成器、17 份无同路径定义。三份 classic 原文件在 classic 资源包；不能以别名替换不同建筑，也不能为凑通过数启用 blank/unused。nether_spawners 的 minecraft:mob_spawner 是有效原版方块实体名，无需迁移；eden 长 1913 格、sewer 长 1013 格，保留完整尺寸并留待负载实测。
7. WDA 39/39 仅有入口或路线静态证据，0 实机验收；未改动建筑不重复全量检查。后续还有正式五级目录、Born in Chaos/Mutant Monsters/神化战斗与地形破坏、首领最终死亡/一次性奖励、III+ 材料。L12 实施 0%，终局合成未完成。阶段性小测不替代最终完整多人、异常恢复和存档验收。

本批 device-protection-build.log 的一次完整 Gradle build 成功（4m14s），352 项测试全部通过，0 失败/错误。当前 XML 含全部 352 项，已替换上一批 330+10 的分范围记录，见 work/l13/audit/build-summary.json。原实验室结构审查见 audit/native-lab-protection-audit.json，runtimeTested=false、adapterEnabled=false。

每阶段更新交接文档，检查敏感信息并逐文件提交/推送，核对远程。辅助包本批增加说明后为 40 说明 + 1 标记，七类快捷命令不变、未安装。原日志、第三方 JAR/原 NBT、存档、缓存和辅助 ZIP 不上传。
