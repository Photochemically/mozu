# 下一任务：修道院的完整结构与原入口

更新：2026-11-15。先读 PROJECT.md、PROGRESS.md、[斗兽场适配](work/l13/COLISEUM_ENTRY.md)和[建筑覆盖表](work/l13/WDA_ENTRY_COVERAGE.md)。

1. 锁定 JAR 的 monastery 目录有 20 份 NBT；起始池 dungeons_arise:fortified/monastery/monastery_start，首件 monastery_start。原定义 WORLD_SURFACE_WG 偏移 0、size 7、最大中心距离 114、beard_thin、adapt_noise=true。先核对原完整连接图、主楼/附属、处理器与敌人附件、原入口与地面高差；不照搬斗兽场的地面降低 2 格规则。
2. 保留原建筑、原敌人/刷怪设施、机关与战利品；核对旋转/随机分支/1～4 个出口，加入缺件、错位、阻挡和内容覆盖拒绝案例。保留 beard_thin 的原生成器范围，不仅检查模板外壳。
3. 静态/API 检查、关键模块 Gradle build；更新原模板证据及三份交接文档，检查敏感信息、提交推送并核对远程。

当前全套 292 项 Java 测试通过，最新回执为 build-summary.json。斗兽场有 32 组完整原件样例、六份模板证据；静态路线只到原外圈拱廊。原生生成、看台/场地完整探索、实际碰撞/战斗、多人与重启仍待集中验收。39 座 WDA 建筑中 33 座有原模板入口证据、6 座仅资源图审查、0 座游戏验收。

正式五级目录、怪物模组联动与强化、首领最终死亡/一次性奖励、III+成长材料、保护与创建旁路、L12、终局合成仍未完成。继续保留既定玩法；不启动游戏、不自动安装桥接、不打开正式候选池或自然入口开关。
