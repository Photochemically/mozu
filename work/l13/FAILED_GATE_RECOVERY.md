# 失败门恢复：静态设计与验证条件

2026-11-28 恢复审查：管理操作限权限 4；仅已确认分配前三次失败可管理员重试；分配不明继续隔离。重新指派前核对并保存旧原生口袋，WDA 清单按实例和口袋编号分别保存。14 项定向测试通过，含中断且无目的地的回归；编译与打包成功，JAR 指纹 E1500FE5A31E0B1FFF65032D7ECA49ADBE2890EBDB89C65ED502C6B30A3B2C65。新产物未安装，恢复命令与旧空间保留仍待实机。

2026-11-28 · 静态交付，未实机验收。设计依据为 NEXT_TASK.md 交接要求与既有隔离原则：生成失败保留预留及快照并隔离，不自动换房间、重放建筑或删除区块。

## 分类：分配前失败与分配后失败

| 情形 | 判定 | 处理 |
|---|---|---|
| 分配前失败 | 实例仍是 NEW/PREPARING 且 `destination == null`，且调用点能证明尚未进入分配（WDA：`allocationCommitted` 标志置位前的准备/规划阶段；原生：校验在 `preparationStarted` 前执行） | **限次自动重试**：`retryableFailure` 计 1 次尝试，未达 `MAX_GENERATION_ATTEMPTS`（3）时回到 NEW，保留锁定内容、种子与已有旅程结果；达到上限隔离为 FAILED |
| 分配状态不明 | 崩溃/重启时 PREPARING/RESERVED/GENERATING；原生 `prepareAndPlacePocket` 期间任何异常；WDA 分配器调用开始后任何异常 | **一律隔离**（FAILED），保留预留与现场，不自动重放 |
| 部分施工 | RESERVED/GENERATING 阶段的运行时失败（tick、停服、终态安装） | **一律隔离**，由管理员处理 |

确定性模板错误会在每次重试中重复失败，自动耗尽重试次数后隔离；管理员须先修原因，再决定 retry 或 reassign。

## 状态与字段

- `PortalRecord.generationAttempts`：已消耗的生成尝试次数，随存档 schema 6 持久化；旧档无 `Attempts` 字段按 0 读入，负值拒绝。
- `MAX_GENERATION_ATTEMPTS = 3`：分配前失败的自动重试上限。
- 方法：`retryableFailure(reason)`、`beginRetryAttempt()`（每次新尝试清除上次诊断）、`adminRetry()`、`adminReassign()`；`recoverInterruptedGeneration()` 保持原样（重启中断一律隔离）。
- 已分配或部分施工的实例**不能**调用 `retryableFailure` 或 `adminRetry`，只能 `adminReassign`。

## 管理员恢复

`/expedition admin recover <uuid>`：只读报告当前阶段、尝试计数、失败原因与建议动作。

`/expedition admin recover <uuid> retry`：仅限已记录三次安全分配前失败且无目的地的 FAILED 实例（权限 4）。重置计数与原因，回到 NEW；玩家下次进门重新尝试，沿用锁定内容、种子与旅程记录。

`/expedition admin recover <uuid> reassign`：仅限已分配/部分施工失败。执行前核对入口区块已加载且入口方块实体仍是本实例的受管入口（`EntranceRiftBlockEntity` + `ManagedEntranceTarget.portalId`），否则拒绝。通过后丢弃生成工件（destination/exits/internalLinks/protectedBlocks），保留选择、种子、玩家访问与首领奖励；**旧空间不删除不重写**，下次进入由分配器建立新的独立预留。

## 验证条件（新增 13 项测试，全量 425 项通过）

- 分配前失败不改变锁定内容与种子；重试不重抽内容/种子。
- 连续 `MAX_GENERATION_ATTEMPTS` 次失败后隔离，原因保留。
- 已分配实例拒绝自动重试，只能隔离。
- 重启中断（PREPARING/RESERVED/GENERATING）一律隔离，预留保留。
- `adminRetry` 只对分配前失败有效；`adminReassign` 清空生成工件但**保留 defeatedBosses、visit 记录、种子与入口**（不重刷奖励）；reassign 后可用新的 pocket 重新生成。
- NBT 往返保留尝试计数与失败原因；旧档缺省 0；负值拒读。

## 边界与未验收项

- 本批只覆盖探索门（EXPLORATION/UNSTABLE）内容生成失败；金门奖励房与入口放置失败维持原有隔离路径。
- WDA 分配器（DD registry 写入）本身无回滚：崩溃窗口内的部分写入可能留下孤儿 pocket 引用；重试/重指派不会覆盖或删除旧区块，但孤儿清理仍待后续专项。
- retry/reassign 的实机行为、入口区块未加载时的恢复流程、命令在多人/重启后的持久性仍未运行验收。
- 尝试计数上限为代码常量，未做配置化；正式参数化留待用户决定。
