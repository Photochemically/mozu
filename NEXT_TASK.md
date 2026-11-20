# 下一任务：原生实验室关键机关的局部保护

更新：2026-11-20。先读 PROJECT.md、PROGRESS.md、[即时定义适配](work/l13/NATIVE_DEFINITION_ADAPTERS.md)、[原生路线](work/l13/NATIVE_ROOM_ROUTES.md)、[原生空间预留](work/l13/NATIVE_ROOM_RESERVATIONS.md)，再读对应源码。最新用户玩法优先。

1. 本批完成六份原即时定义的隔离副本适配：purpur_hallway、eden、sewer、timoria、nether_farm、nether_spawners。只补原生 lazy builder，保留模板、修饰器、设置与完整范围，不修改共享原定义或生活门。125 份模板现有 104 份通过定义策略，剩余 21 份；这不是正式纳入或实机完成数。详见 audit/native-content-policy.json 和 native-definition-adapters.json。
2. 优先处理 lab/labfinal 的 prevent_block_modification。实际 DD 5.4.4 的 BREAK/PLACE 监听仍执行它，不能当作空实现放行；需检查原房关键机关、相对连接及功能设备，将保护限定到必要位置，同时考虑机械搬运/爆炸/特殊工具。保持普通建筑可破坏、普通刷怪笼可拆，不擅自剥除原机关，也不整房禁建。现有内部裂隙端点保护仅是基础，不能替代完整设备保护。
3. 继续审查三份目标不一致的生成器：classic/ruins/simple_small_t → heart_of_disorder，small_maze → hot_suspense，transfer_tunnel → spiral_hallway。源文件在 classic 资源包，不是 default 包；不能把目标不同直接视为可互换别名。17 份无同路径定义包含 8 blank、7 unused、jungle_riddle_fixed、lab/telecommunication；先厘清原用途，不为提高通过数启用空白素材。
4. 本批已纠正刷怪笼标识误判：minecraft:mob_spawner 是当前 Minecraft 的有效方块实体注册名，minecraft:spawner 是方块名。nether_spawners 九份原数据已保留，不做不必要迁移。sewer 的声明标记 2 不存在，但另一端是实际 global 裂隙；沿用现有启用前出口转换。eden 长 1913、sewer 长 1013，不能裁剪；首次缓存、空间分配和按区块生成的真实负载待测。
5. WDA 39/39 已有入口或路线静态证据，0 座实机验收。不重复未改建筑检查。随后继续正式候选与五级难度、Born in Chaos/Mutant Monsters/神化战斗与破坏限制、首领最终死亡/一次性奖励、III+ 成长材料。L12 实施 0%，终局合成未完成。
6. 保留集中实机验收：原生/WDA 完整生成、谜题与怪物、环境高度、首次负载、两人返回和真正重启；六间本批房间尚无新增自动生成快捷案例。当前不启动游戏、不安装桥接、不启用正式候选/自然入口、不改生产存档。若静态分析确实不足，先说明最小测试范围。

当前验证：native-adapters-build.log 完整执行 339 项，1 项测试资源路径错误，其余通过。修正测试路径、补齐第六间白名单及刷怪笼注册检查后，native-adapters-final-build.log 的 build 与三个定向测试组成功（1m5s），10 项通过。保留未受改动影响的 330 项结果，共 340 项不同测试通过。最终 XML 只有 10 项；输入指纹已核对，见 build-summary.json、native-adapters-regression-audit.json。后续完整运行时须替换 validationMode/validationRuns，不沿用旧分范围说明。

每阶段更新交接文档，检查敏感信息并逐文件提交/推送，核对远程。原日志、第三方 JAR/原 NBT、存档、缓存和辅助 ZIP 不上传；辅助包当前 39 说明 + 1 标记，七类命令不变、未安装。
