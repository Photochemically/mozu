# 下一任务：瘟疫疯人院的地下生成与入口

更新：2026-11-19。先读 PROJECT.md、PROGRESS.md、[蚀败之楼适配](work/l13/INFESTED_TEMPLE_ENTRY.md)及[建筑覆盖表](work/l13/WDA_ENTRY_COVERAGE.md)。

1. 锁定 WDA 2.1.58 的 plague_asylum（瘟疫疯人院）目录有 33 份 NBT。原起始池 dungeons_arise:haunted/plague_asylum/plague_asylum_start，唯一首件 plague_asylum_corridor_4_crossing_pit；原定义绝对起点 0，没有 project_start_to_heightmap，size 7、最大中心距离 104、terrain_adaptation=bury、adapt_noise=false、生成阶段 strongholds。
2. 先核对完整随机走廊、竖向/地下范围、房间/原机关/刷怪和实际入口。绝对起点不能等同于名义地面；不要套用蚀败之楼降低岛面 10 格的规则。保留 bury 对原边界的影响，核实厚岩层、原接近路线及完整空间范围。
3. 根据原组件实现环境和入口，覆盖旋转、必要分支、1～4 个出口、缺件/错位/阻挡及原内容覆盖拒绝。优先静态/API 审查、对应测试和关键模块编译；不反复测试未改模块。每阶段更新交接、扫描敏感信息、提交推送并核对远程。

当前最终构建 infested-final-build.log 成功：修正原生宝箱随机种子校验后，六项蚀败测试重测通过。其余 305 项由本轮完整 311 项回归保留，源文件输入指纹未变；快照见 infested-regression-audit.json，具体范围见 build-summary.json 的 validationMode/validationRuns。最新 JUnit XML 只有定向六项，不是全量记录丢失。本批原始日志仅本地保留，仓库保存结构化结果和指纹；回执日志名不是可下载文件。后续若运行新的全量验证，应更新/移除旧的分范围说明，不能继承过期 validationMode/validationRuns。

蚀败 24 组原件样例覆盖全部 46 模板、四层外壳、12 层内部及原房间/刷怪设施；各查 1～4 个出口，路线仅到原底层外廊。原内部石英边界、完整上下层通路、原生图完整率、战斗兼容与双人重启待验收。原版 StructureTemplate 首次放置容器会改写 LootTableSeed；不要拿它与模板常量比较或为通过检查主动重置。运行中的刷怪 Delay 同样不作为不可变配置。

39 座 WDA 建筑中 36 座有原模板入口证据、3 座仅资源图审查、0 座游戏验收。剩余：plague_asylum（瘟疫疯人院）、shiraz_palace（繁花之宫）、thornborn_towers（荆棘城堡）。已有静态证据也不等于原生生成或游戏验收完成。

正式五级目录、怪物模组联动与强化、首领最终死亡/一次性奖励、III+成长材料、保护与创建旁路、L12、终局合成仍未完成。维持既定规则，不启动游戏、不自动安装桥接、不打开正式候选池/自然入口开关，不修改生产存档。
