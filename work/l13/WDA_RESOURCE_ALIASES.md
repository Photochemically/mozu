# WDA资源别名修复

2026-11-04。代码已编译，静态内容／资源图检查通过；桥接未安装，尚未验证Forge注册表加载及实际生成。

## 四个有原资源依据的修复

| 缺失资源 | 复用的原资源 | 核查依据 |
|---|---|---|
| mechanical_nest/mechanical_nest_decoration 池 | mechanical_nest/mechanical_nest_decorations 池 | towers_1的连接目标为deco_0_library，原池具有匹配接口；沿用全部88个加权条目、处理器及fallback |
| mechanical_nest/mechanical_nest_bridge_terminator_6_ 模板 | 同路径去掉末尾下划线的模板 | 原模板10×15×13，接口名roofed_bridge_0；7个原模板连接点经crossing池的terminators回退可匹配，方向亦有对应 |
| thornborn_towers/hanging_bridge_2_medium_terminator 模板 | 完整同前缀的hanging_bridge_3_medium_terminator | 唯一同名中型收尾，1×3×3，原接口hanging_medium／west_up；7个上游接口可经水平旋转匹配。原两个池中各自权重4均保持 |
| underworld/foundry/foundry_corridor_gears 池 | 同目录foundry_corridor_deco池 | crossing_2中2个失效引用与相邻4个有效装饰池引用使用相同连接名、目标和方向；原池已有两种匹配齿轮，每种权重3 |

证据：audit/wda-alias-evidence.json及audit/wda-additional-repair-evidence.json。这里是根据固定版本资源和连接关系作出的修复判断，不冒充上游作者明确确认，也不宣称找回了缺失文件的原始内容。

铸造厂复用**整份原装饰池**：14个条目、原fallback、处理器、投影和全部权重不变，包括权重3的空白选项。连接名匹配的直接部件为gear_0／gear_1；不擅自筛成“齿轮必出”或重设概率。缺失专用池的历史定义未找到，因此这是一项有相邻资源依据的兼容适配，仍须实机检查装饰是否正确接合。

## 实现方式

通过Forge的AddPackFindersEvent注册服务端虚拟数据包 `expedition_wda_resource_aliases`，默认位于低优先级。它从实际安装的WDA模组读取原资源，桥接JAR不复制发布原建筑NBT。

缺失池仅改自身name到被引用的名称，所有原条目、权重、处理器、投影和fallback保持不变；模板别名返回原始压缩NBT字节，不重新生成或修改建筑。确切原路径若已存在则不提供对应别名；更高优先级数据包可提供显式修复。若预期的原资源丢失或池格式异常，明确报错，不使用空资源。

原6项缺失引用防护继续保留，运行时以实际注册表／资源管理器中是否补齐为准。在前4项修复基础上，新增用户授权的2项兼容替代：盗匪村deco_3复用deco_0、机械巢穴spawner_6复用spawner_0。两个源模板及各自原池校验固定SHA-256；不覆盖原池，不修改NBT。它们是兼容选择而非找回原件，具体内容与权重影响见[缺失模板处理](WDA_TEMPLATE_GAPS.md)。

## 当前检查结果与运行待办

离线叠加4项修复及2项替代后，39座建筑的保守可达图中缺失引用由原6处降为0处。替代来源原本就在对应池中，接口检查通过；盗匪村0号装饰和机械巢穴0号近战刷怪件更常见。没有把整座建筑标为适配完成，完整生成、空间／路线、实际注册表加载仍待集中验收。

2026-11-04核查了[作者当前Forge仓库](https://github.com/Aureljz/WhenDungeonsArise-Forge-main/tree/5d173247111ba44ef34100258465deff3665bce4)、这4个缺失路径在当前默认分支的提交历史，以及作者[Fabric 1.20.1](https://github.com/Aureljz/WhenDungeonsArise--Fabric/tree/1.20.1)和[旧Forge](https://github.com/Aureljz/WhenDungeonsArise--Forge)仓库当前树，未找到这两个确切文件。该结论限于已检查的仓库／分支，不代表穷尽所有发行包或作者未公开资源。记录见audit/wda-upstream-refs-20261104.json、wda-missing-upstream-history.json、wda-related-resource-search.json。

复查命令：`node work/l13/audit-wda-repair-evidence.cjs <WDA JAR路径>`生成前期适配依据；`node work/l13/audit-wda-aliases.cjs <WDA JAR路径>`核对JAR及替代源指纹、读取Java中的4项别名与2项替代定义并审查39座资源图，输出audit/wda-alias-audit.json及wda-substitution-evidence.json。保守资源图不是随机拼装、关节可达性或内容完整性的实机证明。

同日先前核查[Modrinth官方发行列表](https://modrinth.com/mod/when-dungeons-arise/versions)中全部三个Forge／1.20.1兼容发行包：2.1.58、2.1.57、2.1.56。逐包验证官方SHA-512后只读扫描，两个确切模板均缺失。旧包只下载到开发目录，未安装或执行。audit/wda-release-resource-search.json记录URL、哈希和缺失路径；该范围不等同于穷尽其他平台／游戏版本的所有发行物。本轮在用户授权后采用上述兼容替代，原件调查不再阻塞这两座建筑。

集中验收应检查 `/datapack list enabled` 中的虚拟包、加载日志和实际资源解析，再验证建筑生成；目录与自然门开关仍保持关闭，等待测试配置准备。
