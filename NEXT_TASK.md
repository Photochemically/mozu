# 下一任务：繁花之宫的完整建筑与入口

更新：2026-11-19。先读 PROJECT.md、PROGRESS.md、[瘟疫疯人院适配](work/l13/PLAGUE_ASYLUM_ENTRY.md)和[建筑覆盖表](work/l13/WDA_ENTRY_COVERAGE.md)。

1. 锁定 WDA 2.1.58 的 shiraz_palace（繁花之宫）：42 份 NBT；起始池 dungeons_arise:desertic/shiraz_palace/shiraz_palace_start，唯一起始件 shiraz_palace_part_13。原版 jigsaw，size 7、最大中心距离 114、WORLD_SURFACE_WG +16、adapt_noise=false、use_expansion_hack=false，未显式设置 terrain_adaptation（缺省 NONE）。
2. 先核查完整主体、原连接图、实际入口/楼层、地基与环境高度，原怪物/战利品/流体等。+16 是原投影偏移，不能直接当岛面或入口高度；不要套用瘟疫疯人院的地下岩层策略。按原实际内容实现环境和入口，保留完整建筑。
3. 覆盖原件旋转、必要分片和选中附件、1～4 个出口、阻挡、内容保留、搬迁及真实原生替换关系。关键模块编译及对应测试，避免重复未改代码的全量验证。每阶段更新交接文档、检查敏感信息、逐文件提交并推送，核对远程。

当前代码最后完整验证：plague-final-build.log，build 成功，319 项 Java 测试、0 失败/错误；本批新增 8 项。build-summary.json 的 validationMode/validationRuns 已改为本次全量结果，不再沿用蚀败批次的 305+6 说明。原始日志本地保留；仓库以结构化回执和原模板报告接力。

瘟疫疯人院 56 组原件图覆盖全部 33 模板、四旋转和 1～4 个出口；首件绝对 Y=-1，保留 bury 外扩 12 格与完整岩层。原 corridor_1_cells 已含默认病房，占位宝箱可被合法随机普通病房替换；七种普通病房必须完整写入 504 格，检查最终选中内容，不能把原生覆盖当成意外删箱，也不能放开任意覆盖。原宝箱随机种子和刷怪 Delay 继续允许原版变化。

WDA 入口静态证据 37/39，剩余 shiraz_palace（繁花之宫）、thornborn_towers（荆棘城堡）。0 座已实机验收，静态候选或模板覆盖不等于完整随机图已运行。

原生房间余项、正式五级目录、怪物模组联动/强化、首领最终死亡与一次性奖励、III+成长材料、保护/创建旁路、L12 和终局合成仍未完成。保持玩法决定，不启动游戏、不安装桥接、不打开正式候选/自然入口开关，不改生产存档。辅助包仅说明与开发标记，不能安装到正式世界。
