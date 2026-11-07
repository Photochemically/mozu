# 下一任务　废弃神庙的原阶梯与内部入口

先读[当前进度](PROGRESS.md)、[丛林树屋入口](work/l13/TREE_HOUSE_ENTRY.md)和[建筑入口覆盖表](work/l13/WDA_ENTRY_COVERAGE.md)。

1. 固定 WDA JAR 的 `abandoned_temple` 有 11 个原模板。起始池为 `fortified/abandoned_temple/abandoned_temple_start`。原定义按 WORLD_SURFACE_WG 投影并偏移 −2，使用 beard_thin 地形适配，并给完整建筑范围配置流浪者生成规则。
2. 读取原入口、阶梯、拼图接口与战利品，核对完整主体和实际岛面高度。保留原遭遇、机关与上下层路线，验证四向旋转、1～4 处出口及受阻案例。
3. 按[开发约定](AGENTS.md)更新证据、构建与交付。集中实机验收继续包含树屋两种首件与上层探索、巨型蘑菇登顶、许愿井效果，以及多人与重启。
