# 下一任务：原生维度门房间的定义兼容缺口

更新：2026-11-20。先读 PROJECT.md、PROGRESS.md、[原生路线](work/l13/NATIVE_ROOM_ROUTES.md)、[原生空间预留](work/l13/NATIVE_ROOM_RESERVATIONS.md)和 audit/native-content-policy.json。

1. WDA 39/39 已有入口或路线的静态证据，0 座实机验收。不要再把这个数字当作完整随机图、全楼层路线、性能或正式分级已完成。荆棘城堡本批见 [说明](work/l13/THORNBORN_TOWERS_ENTRY.md)与 audit/thornborn-entry-audit.json。
2. 先审查 DD 5.4.4 的 125 份原模板中尚未通过定义策略的 27 份：17 份无同路径生成器、3 份指向其他模板、6 份非当前 lazy 类型、1 份整房间禁止修改。名单与原因以实际 JAR/报告为准，不把 blank/unused 原件直接启用为探索房间，也不改写原数据凑通过数。
3. 对照 NativeGeneratorPolicy、NativeRoomProvider、NativeRoomBuild、NativeGenerationGuard、NativePocketAllocator 及锁定源码/API，区分可安全适配的现有生成路径、原始资源别名或确实没有生成定义的素材。先形成具体处理记录，再逐个实现必要适配。保留完整房间、原机关/内部有效连接、主世界出口、玩家正常建造和生活空间例外。
4. 优先核对 lab/labfinal 的 prevent_block_modification：不能只放行整房间保护而违背玩家自由建造，也不能删除关键机关保护；如需替代，明确限定保护位置和行为。无需重复询问已定玩法，遇到确实不能兼容才说明限制。
5. 随后继续正式候选与五级难度、Born in Chaos/Mutant Monsters/神化的战斗与破坏限制、首领最终死亡与一次性奖励、III+成长材料。L12 与终局合成仍未完成；原生策略通过也不能自动加入正式池。
6. 保留集中实机验收：先用隔离开发世界进行原生随机完整图、环境高度、首次负载和两人返回/重启检查。当前不启动游戏、不安装桥接、不启用正式候选/自然入口、不改生产存档；缺口的静态工作继续推进。

当前验证：首次完整 thornborn-build.log 执行 332 项，一项手工分支重叠失败，其余 331 项通过。修正该样例后，最终 thornborn-final-build.log 的 build test --tests '*ThornbornAuditTest' 成功（43s），7 项通过。保留未改代码的 325 项全量证据，输入指纹已核对；共 332 项不同测试通过。最终本地 XML 仅有 7 项。见 build-summary.json、thornborn-regression-audit.json；后续如果完整运行，须更新 validationMode/validationRuns，不沿用旧分范围说明。

荆棘城堡 128 组原件图覆盖 34 份资源，32～44 件/例，31～46 个原内容点；原 medium_terminator 2→3 别名沿用。原绝对根 Y=129，背景首个空层取最低组件 Y；两类原桥使用实体支撑列，保留原半砖、倒置台阶和中央花盆。允许原生终止下延塔段/可选分支，不删已选内容、不补造无限塔身。手工样例必须遵守原结构碰撞约束，房间/刷怪附件仅在原父结构中嵌入。

每阶段更新交接文档，检查敏感信息并逐文件提交/推送，核对远程。原日志、第三方 JAR/原 NBT、存档、缓存和辅助 ZIP 不上传；辅助包目前 38 说明 + 1 标记，未安装。
