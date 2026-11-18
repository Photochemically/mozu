# 下一任务：蚀败之楼的完整生成图与入口

更新：2026-11-18。先读 PROJECT.md、PROGRESS.md、[尸骨遗迹适配](work/l13/HIND_ENTRY.md)及[建筑覆盖表](work/l13/WDA_ENTRY_COVERAGE.md)。

1. 锁定 WDA 2.1.58 的 infested_temple（原中文名“蚀败之楼”）目录有 46 份 NBT。起始池 dungeons_arise:haunted/infested_temple/infested_temple_start，唯一首件 infested_temple_layout_part_1_middle；原定义 WORLD_SURFACE_WG 偏移 +54、size 7、最大中心距离 104、adapt_noise=false，没有显式 terrain_adaptation 字段。
2. 先核对根布局、完整连接图、向下/向上组件、外壳/房间/原刷怪设施和原入口。较高的起点不能直接作为玩家落脚高度；不要照搬尸骨的名义地面和埋藏宝箱方案。保留完整建筑、原机关/战利品/遭遇，不截断高层或地下部分来通过空间检查。
3. 依照实际原组件设计环境与入口，验证各旋转、必要随机分支、1～4 个出口及缺件/错位/阻挡/内容覆盖拒绝。优先静态/API 审查、相应测试和关键模块编译，不反复运行未改模块的完整测试。
4. 更新原模板证据、PROJECT/PROGRESS/NEXT_TASK 及相关设计，检查敏感信息后提交推送，核对远程。

当前完整 Gradle build 成功（hind-build.log，3m33s），305 项 Java 测试、0 失败/错误，回执为 build-summary.json。尸骨 16 组原件样例覆盖三个原模板、四向旋转、两种干地与规划高度，各核对 1～4 个出口和 4523 条原方块记录；保留原埋深、骨轴、黄金及地下宝箱。原生随机生成、实际地面碰撞、挖掘开箱、负载和双人重启未验收。

尸骨池文件内部 aquatic 名称是残留字段：原 StructureTemplatePool codec 读取 fallback/elements，实际注册资源路径及群系为沙漠；StructureSettings 的 terrain_adaptation 默认 NONE。API 提取可从锁定 Forge JAR 重建，原始提取文本不上传。密集方块快照继续使用 HashMap，避免 Map.copyOf 的线性探测冲突问题。

39 座 WDA 建筑中 35 座有原模板入口证据、4 座仅资源图审查、0 座游戏验收。剩余适配：infested_temple、plague_asylum、shiraz_palace、thornborn_towers。已有静态证据的建筑也仍须原生完整图和实机验收。

正式五级目录、怪物模组联动与强化、首领最终死亡/一次性奖励、III+成长材料、保护与创建旁路、L12、终局合成仍未完成。维持既定规则，不启动游戏、不自动安装桥接、不打开正式候选池/自然入口开关，不修改生产存档。
