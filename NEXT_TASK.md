# 下一任务：ceryneian_hind 三件原结构与入口

更新：2026-11-16。先读 PROJECT.md、PROGRESS.md、[修道院适配](work/l13/MONASTERY_ENTRY.md)和[建筑覆盖表](work/l13/WDA_ENTRY_COVERAGE.md)。

1. 锁定 JAR 的 ceryneian_hind 目录有 3 份 NBT，原起始池路径为 dungeons_arise:desertic/ceryneian_hind/ceryneian_hind_start，唯一首件 ceryneian_hind_part_1。原定义 WORLD_SURFACE_WG 偏移 −16、size 7、最大中心距离 114、adapt_noise=false，没有显式 terrain_adaptation 字段。先核对完整接口、原埋入地面的部分、外壳/地形及原入口，不能照搬修道院的地面提高 3 格规则。
2. 起始池文件内的 name 写 aquatic，但资源路径为 desertic；需按实际注册/生成语义核对，不仅凭名称将其归为水域。保留原建筑、机关、战利品和原实体；检查各旋转、1～4 个出口、缺件/错位/阻挡及原内容覆盖拒绝。
3. 静态/API 审查、关键模块编译；未改模块不反复全量测试。更新原模板证据及三份交接文档，检查敏感信息、提交推送并核对远程。

当前完整 Gradle build 成功，299 项 Java 测试通过，最新回执为 build-summary.json / monastery-final-build.log。修道院 216 组原件样例覆盖全部 20 模板、27 种主楼和桥梁两种原入口高度；64 组需五格原窗台脚手架。底层及附属通道静态路线通过，原生随机图、上层探索、碰撞/水流、村民/铁傀儡和双人重启仍待集中验收。

本阶段早期失败/中止日志保留，不能把 monastery-build.log、monastery-garden-check.log 等当作最终结果。定向测试快照使用 HashMap：密集三维坐标在 Map.copyOf 的线性探测中会大量冲突，曾导致一次低效测试被主动终止；不要改回该写法。

39 座 WDA 建筑中 34 座有原模板入口证据、5 座仅资源图审查、0 座游戏验收。其余待适配项：ceryneian_hind、infested_temple、plague_asylum、shiraz_palace、thornborn_towers；已有静态证据的建筑也仍须原生完整图与游戏验收。

正式五级目录、怪物模组联动与强化、首领最终死亡/一次性奖励、III+成长材料、保护与创建旁路、L12、终局合成仍未完成。保持既定玩法，不启动游戏、不自动安装桥接、不打开正式候选池或自然入口开关。
