# 下一任务：荆棘城堡的完整建筑与入口

更新：2026-11-20。先读 PROJECT.md、PROGRESS.md、[繁花之宫适配](work/l13/SHIRAZ_PALACE_ENTRY.md)和[建筑覆盖表](work/l13/WDA_ENTRY_COVERAGE.md)。

1. 锁定 WDA 2.1.58 的 thornborn_towers（荆棘城堡）：34 份 NBT；起始池 dungeons_arise:fortified/thornborn_towers/thornborn_towers_start，唯一起始件 thornborn_towers_part_0_main。原版 jigsaw，size 7、最大中心距离 104、绝对起始高度 130、无 project_start_to_heightmap；adapt_noise=false、use_expansion_hack=false，未显式设置 terrain_adaptation（缺省 NONE）。
2. 先核查绝对根原点、完整连接图/塔身、原入口/桥梁及周边环境，保留原怪物、战利品、机关和流体。不得将绝对 130 误作岛面，不能套用繁花之宫 +16 或地下岩层策略；按原实际几何设计入口。
3. 覆盖原件旋转、必要分片和选中附件、1～4 个出口、阻挡、内容保留、搬迁及原生替换关系。关键模块编译及对应测试，避免重复未改代码的全量验证。每阶段更新交接文档、检查敏感信息、逐文件提交并推送，核对远程。

当前代码最后完整验证：2026-11-19 shiraz-final-build.log，build 成功，325 项 Java 测试（71 组）、0 失败/错误，本批新增 6 项。build-summary.json 的 validationMode/validationRuns 已更新为本次全量。原始日志本地保留；仓库以结构化回执和原模板报告接力。JAR 指纹以构建回执为准。

繁花之宫 24 组原件图保留 27 件三层主体与 61 个原遭遇，覆盖 41 份在池模板；42 份文件中的 husk_elite_0 未入原池，不启用。岛面首个空层为基座 +33（名义地面 +16），九格脚手架桥有两端支撑，最大支撑距离四格；原两格台阶保持含水状态。水与熔岩 tick 暂存，检查原内容后再释放。原宝箱随机种子和刷怪 Delay 允许原版变化；原接口 rollable 不要求顶部朝向相同，aligned 才要求。样例模拟 jigsaw 的非空最终材质，不得把全部连接方块都删成空气。

WDA 入口静态证据 38/39，剩余 thornborn_towers。0 座已实机验收，静态候选或模板覆盖不等于完整随机图已运行；繁花之宫只证明至北侧花园的接入，完整内部/上层及流体/点火仍待验收。

原生房间余项、正式五级目录、怪物模组联动/强化、首领最终死亡与一次性奖励、III+成长材料、保护/创建旁路、L12 和终局合成仍未完成。保持玩法决定，不启动游戏、不安装桥接、不打开正式候选/自然入口开关，不改生产存档。辅助包 37 份说明 + 1 份开发标记，七类命令未增加，不能安装到正式世界。
