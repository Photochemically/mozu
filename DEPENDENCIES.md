# 依赖与接力环境还原

仓库不分发第三方模组 JAR、下载源码、JDK、Gradle 缓存或 Minecraft 数据。需要 Java 17、Node.js（本项目脚本仅用内置模块，无 npm 安装）、PowerShell，以及相同版本的合法模组副本。构建使用已提交的 Gradle 8.8 wrapper；第一次构建需要联网获取 Gradle/Forge/Maven 依赖，有缓存后才能使用 `--offline`。

## 最小桥接开发环境

三份本地 JAR 名称、目标名称及 SHA-256 锁定在 [bridge-dependencies.json](scripts/bridge-dependencies.json)。从现有实例的 `mods` 目录还原即可；以下命令在仓库根目录执行，替换示例路径：

```powershell
.\scripts\prepare-local-deps.ps1 -ModsPath 'D:\MinecraftInstance\mods' -ValidateOnly
.\scripts\prepare-local-deps.ps1 -ModsPath 'D:\MinecraftInstance\mods'
$env:JAVA_HOME = 'D:\Tools\jdk-17'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) 'work\l13\gradle-cache'
Push-Location work\l13\bridge
.\gradlew.bat --no-daemon build
Pop-Location
```

脚本先校验全部源文件/已有目标指纹，再复制缺少的依赖，不覆盖不同版本，不改游戏实例。Java 测试直接读取这些锁定 JAR 和已提交审查夹具，因此不能随便升级依赖后沿用旧结论。生产运行还需要 DD/KubeJS 的完整传递依赖，以上三份只表示桥接编译/单元测试输入，不是完整服务器 mods 清单。

WDA 版本资料见 `work/l13/audit/wda-release-metadata-20261104.json`，原作者项目为 https://www.curseforge.com/minecraft/mc-mods/when-dungeons-arise 。其他模组依据 `work/mod-audit/manifest.json` 和 `work/l13/audit/target-versions.json` 匹配具体文件及指纹，从作者发布渠道或用户现有实例取得。不要将缓存或 JAR 加入 Git。

## KubeJS 构建和静态检查

`node work/integration/build.cjs` 可独立从已提交源文件生成 0.2.1 安装内容（只写仓库 outputs，不自动安装）。完整 `verify.cjs` 还依赖原模组资源提取缓存；需先提供清单中的完整模组集。两个提取脚本只读取 JAR，不运行模组：

```powershell
.\work\inspect-mods.ps1 -ModsPath 'D:\MinecraftInstance\mods'
.\work\l13\inspect-targets.ps1 -ModsPath 'D:\MinecraftInstance\mods'
node work/integration/build.cjs
node work/integration/verify.cjs
```

提取内容写入被忽略的 `work/mod-audit/` 和 `work/l13/audit/` 原始资源缓存；版本清单也会按实际输入刷新，检查差异，不能把不匹配版本误当锁定版本。第一阶段 `outputs/联动试用版-v0.1.0/文件清单.json` 保留用于迁移回归检查；各版本发布文件清单/静态结果是历史证据，安装内容可重建。静态检查不会证明游戏运行正确。

## 继续源码/API 审查

- DD 已核查源码提交：`DimensionalDevelopment/DimDoors@22eb5a58b242966b76340ee95e5dd75eeed32412`；证据 `audit/dimdoors-544-source-commit.json`。
- WDA 已核查源码提交：`Aureljz/WhenDungeonsArise-Forge-main@5d173247111ba44ef34100258465deff3665bce4`；证据 `audit/wda-source-commit.json`。源码分支与发布 JAR 资源不一定完全一致，运行目标以锁定 JAR 为准。
- `work/l13/audit-lib.cjs` 提供只读 ZIP/NBT 解析，可用 `node --test work/l13/audit-lib.test.cjs` 验证。
- `node work/l13/audit-content.cjs work/l13/bridge/libs/DungeonsArise-1.20.x-2.1.58-release.jar` 生成内容统计；`audit-schematics.cjs` 审查锁定 DD 模板；其他审查脚本见文件头参数。
- 原始模组 JSON、字节码/反编译全文和下载源码未提交；统计、兼容性结论、针对性几何/接口证据及测试必需夹具已保留。历史文档可能提到本机 `upstream/` 路径，需要时按以上提交重新获取，不应重新上传全文。

本机历史实例为 `C:\Users\hp\AppData\Roaming\.minecraft-forge-3`，早期审查目录为 `C:\mods`。它们是历史来源而非其他机器的默认安装目标。不要运行旧安装/迁移脚本来还原开发环境；不要把 `test-packs/tall-exploration` 或开发世界标记装进正式世界。

## 推送前检查

查看 Git 状态与待提交差异，确认只有当前阶段源码、配置、贴图、文档、必要证据；检查秘密信息、未跟踪依赖与备份排除规则。提交后普通推送并比对远程分支 SHA。系统凭据管理器负责认证，文档/脚本/远程 URL 不保存令牌。其他电脑需自行授权该仓库；本机授权不会随克隆转移。
