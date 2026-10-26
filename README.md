<p align="center">
  <img src="logo.jpg" alt="Zifeng AE2 Skills" width="360">
</p>

<h1 align="center">子枫 AE2 技能附属</h1>

<p align="center">为 AE2 自动合成提供技能成长，并将 ME 网络能量转化为技能点。</p>

<p align="center">
  <a href="https://github.com/yhoops/zifeng_ae2speed_skills/releases/latest"><img src="https://img.shields.io/github/v/release/yhoops/zifeng_ae2speed_skills?label=Release" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21.1-62B47A" alt="Minecraft 1.20.1 / 1.21.1">
  <img src="https://img.shields.io/badge/Loader-Forge%2047%20%7C%20NeoForge%2021.1-E99646" alt="Forge 47 / NeoForge 21.1">
  <img src="https://img.shields.io/badge/Java-17%20%7C%2021-478CC8" alt="Java 17 / 21">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-CC_BY--NC--SA_4.0-blue" alt="License CC BY-NC-SA 4.0"></a>
</p>

---

[子枫技能树](https://www.curseforge.com/minecraft/mc-mods/zcs) 与 [Applied Energistics 2](https://modrinth.com/mod/ae2) 的独立附属模组，把技能点投入直接转化为 AE2 自动合成的产能。

**本仓库只提供附属模组**，运行时尚需安装原作者技能树本体、AE2 及其依赖。

## 目录

- [下载与安装](#下载与安装)
- [技能](#技能)
- [AE 能量生成技能点](#ae-能量生成技能点)
- [运行环境](#运行环境)
- [常见问题](#常见问题)
- [从源码构建](#从源码构建)
- [更新日志](#更新日志)
- [许可与署名](#许可与署名)

## 下载与安装

从 [Releases](https://github.com/yhoops/zifeng_ae2speed_skills/releases/latest) 下载与游戏版本对应的 JAR，放进 `mods` 目录。**同一个 JAR 不能跨版本使用。**

| 游戏版本 | 加载器 | 下载文件 |
|---|---|---|
| 1.20.1 | Forge 47 | `zifeng_ae2_skills_forge_1_20_1-<版本>.jar` |
| 1.21.1 | NeoForge 21.1 | `zifeng_ae2_skills-<版本>.jar` |

服务端与客户端需安装相同版本。

安装步骤：

1. 下载上表对应文件。GitHub 自动生成的 Source code 压缩包用于源码构建，不是模组。
2. 安装[原作者技能树本体](https://www.curseforge.com/minecraft/mc-mods/zcs)与 [AE2](https://modrinth.com/mod/ae2)，并补齐各自依赖。
3. 将 JAR 放入 `mods` 目录。
4. 启动后可在技能树查看五项技能（其中三项位于最后一个页签「神明之力」），在技能点转换机界面查看 AE 供电开关。

升级前请退出游戏或停止服务器并备份世界，用新 JAR 替换旧附属，不要同时保留多个版本。

> **请使用原作者本体。** 已内置这五项技能的定制本体会造成重复注册，本附属检测到后会停止加载。带有虚拟 CPU 任务的世界应保持附属安装；在缺少附属的状态下打开并保存世界，可能丢失附属管理的任务数据。

## 技能

五项技能均接入本体原有的学习、升级、开关与退款流程。

| 技能 | 作用 | 上限 | 费用 |
|---|---|---|---|
| 装配超频 | 加速分子装配室处理合成样板 | 4 | 500 – 4,000 点 |
| 算力超频 | 提升合成 CPU 的有效协处理器数量 | 4 | 1,000 – 8,000 点 |
| 合成之神的加护 | CPU 直接执行合成样板，无需分子装配室 | 5 | 2,000 – 32,000 点 |
| 算力之神的加护 | 一个实体 CPU 执行多个独立任务，共享扩展存储 | 5 | 2,000 – 32,000 点 |
| 万机之神的加护 | 为产线上被样板驱动的机器追加原生工作次数 | 6 | 1,000 – 1,000,000 点 |

后三项位于「神明之力」页签，tooltip 前缀显示为 `[诸神]`，标题为紫色，满级时各解锁一个成就。

<details>
<summary><b>装配超频</b> —— 加速分子装配室</summary>

进度与能耗同步增加，单件合成的总耗能保持原有规则。

| 等级 | 速度倍率 | 本级费用 |
|---|---|---|
| 1 | ×2 | 500 点 |
| 2 | ×3 | 1,000 点 |
| 3 | ×4 | 2,000 点 |
| 4 | ×5 | 4,000 点 |

每台机器每 tick 最多完成一次合成，实际吞吐仍受供料、供电与输出空间影响。

</details>

<details>
<summary><b>算力超频</b> —— 提升并行度</summary>

```text
有效协处理器数量 =（原生协处理器数量 + 1）× 倍率 − 1
```

| 等级 | 倍率 | 本级费用 |
|---|---|---|
| 1 | ×2 | 1,000 点 |
| 2 | ×3 | 2,000 点 |
| 3 | ×4 | 4,000 点 |
| 4 | ×5 | 8,000 点 |

普通超频加成上限为 4,096，不会把原生能力已超过该值的 CPU 降级。面向 AE2 原生合成 CPU，实际加工速度仍取决于加工机器。

</details>

<details>
<summary><b>合成之神的加护</b> —— 跳过分子装配室</summary>

技能 id `ae_assembler_infinite`。合成样板由 CPU 直接执行，无需分子装配室；加工样板继续交给真实加工设备。

| 等级 | 每个 CPU 每 tick 的配额 | 本级费用 |
|---|---|---|
| 1 | 2 次 | 2,000 点 |
| 2 | 4 次 | 4,000 点 |
| 3 | 8 次 | 8,000 点 |
| 4 | 16 次 | 16,000 点 |
| 5 | 按批次完成，受服务器时间预算约束 | 32,000 点 |

按实际批次扣除材料与 CPU 派发能量，缺电时暂停，正常处理容器返还。超大订单可跨 tick 续跑，满级不保证任意规模的订单都在单 tick 内结束。

首次激活到 5 级时解锁成就 `divine_assembler_grace`，每个存档仅一次。

</details>

<details>
<summary><b>算力之神的加护</b> —— 分裂算力</summary>

技能 id `ae_cpu_infinite`。为同一个实体 CPU 提供多个拥有独立任务状态的 CPU，并共享扩展后的存储容量。

| 等级 | CPU 总数（含实体） | 共享存储 | 本级费用 |
|---|---|---|---|
| 1 | 2 个 | ×2 | 2,000 点 |
| 2 | 4 个 | ×4 | 4,000 点 |
| 3 | 8 个 | ×8 | 8,000 点 |
| 4 | 16 个 | ×16 | 16,000 点 |
| 5 | 按需增加，不设上限 | 无限 | 32,000 点 |

各 CPU 的任务可独立选用、查看与取消。满级同时解除实体与虚拟 CPU 的协处理器限制；材料、电力、加工机器速度及时间预算继续生效。

关闭或降低等级后，已有任务继续完成，超出新上限的空闲虚拟 CPU 会被回收。面向 AE2 原生合成 CPU。

首次激活到 5 级时解锁成就 `divine_cpu_grace`，每个存档仅一次。

</details>

<details>
<summary><b>万机之神的加护</b> —— 产线加速与 6 级瞬成</summary>

技能 id `ae_divine_machine_grace`。AE 合成任务激活时，为这条产线上被样板驱动的机器追加原生工作次数：同一 tick 内重复调用机器自己的 `tickingRequest` / `BlockEntityTicker.tick`，不改动 tick 频率与进度字段。

| 等级 | 倍率 | 本级费用 |
|---|---|---|
| 1 | ×2 | 1,000 点 |
| 2 | ×8 | 5,000 点 |
| 3 | ×16 | 10,000 点 |
| 4 | ×128 | 50,000 点 |
| 5 | ×1024 | 100,000 点 |
| 6 | 瞬成 | 1,000,000 点 |

费用为非等比递增，不套用 `base << n` 的等差序列。

**6 级：瞬成。** 一个样板要跑很多批时，先按 5 级速率让机器真做 **1 批**作为验证；只要这一批的预期产物真的回到了合成任务，该样板**剩余的全部批次**立刻记账完成——材料从合成 CPU 内部库存扣除，产物按 AE2 原生路径投递（合成任务 → ME 存储 → CPU 库存三级兜底），不再需要机器，也不再消耗 AE 电量。

- **验证判据是产物回流，不是「推送成功」。** 观察点是 `CraftingCpuLogic.insert`：只有合成任务真的接纳了产物才算通过。样板推送成功本身不能作为证据——空壳机器、吞材料的机器同样会让 `pushPattern` 返回 `true`。
- **验证期间该样板被锁住。** AE2 的 `executeCrafting` 在一个 tick 内会反复推同一样板，不锁就会出现多批在飞，随后按剩余批数结清会重复记账、直接超产。锁定只针对「正在取证的该样板 + 该 CPU」，验证结束立刻放开。
- **超时约 10 秒（200 tick）。** 没等到产物回流就放弃该样板、永久不再尝试，回落 5 级加速，避免空壳机器把任务反复卡住。
- **免电。** 瞬成的批次不做机器加工，因此不计也不扣 `calculatePatternPower`；验证的那 1 批照常扣电。
- **只接管处理样板，只对 AE2 原生合成 CPU 生效**（含其子类与虚拟 CPU）。第三方自有 `ICraftingCPU` 实现不产生 `ExecutingCraftingJob`，只享受 5 级加速。
- **不设批次硬上限**，结算受每 tick 2.5 ms 时间预算约束，没结完留到下一 tick 继续。

**生效条件：** 该网格上存在未暂停的活跃合成任务，且样板确实推给了机器。任务完成、取消或暂停后，下一 tick 立即停止加速。

**性能保护：** 每服务器 tick 共用一份时间预算（单片 2.5 ms）。单台机器每 tick 额外次数不超过 1,023，全体合计不超过 8,192；预算耗尽的部分顺延到后续 tick。

**与装配超频的叠加：** 不叠加。万机之神在本 tick 加速过某台机器时，装配超频对该机器整段让路，单台机器每 tick 只有一份额外 tick。

首次激活到 5 级时解锁成就 `divine_machine_grace`；首次点到 6 级再解锁成就「超越万机？？？」（`divine_transcendence`，附一道原版雷声，只放给达成者本人）。两者每个存档各一次。

</details>

### 技能生效规则

- 五项均为全局技能，按在线且开启对应技能的玩家中，所选生效等级的最高值计算。
- 学习、开启与调整等级沿用本体的全局技能权限要求，需要 OP 权限。
- 多名玩家提供相同技能时等级取最高值，倍率不叠加。
- 玩家关闭技能或登出后，服务器重新计算生效等级。

## AE 能量生成技能点

为本体的**技能点转换机**增加「使用 AE 能量」开关。开启后，转换机从相邻 ME 网络抽取 AE 能量并折算为本体接受的 FE 当量，沿用本体的转换阈值生成技能点。

使用步骤：

1. 放置本体技能点转换机，确认机器已绑定主人。
2. 让转换机任意一个面紧贴可接入 ME 网络的 AE 线缆或设备，并为网络提供电力。
3. 使用机器主人账号打开转换机界面，开启「使用 AE 能量」。
4. 按本体规则累积转换进度，达到阈值后生成技能点。需要更高吞吐时可按需使用本体的「无限制输入」开关。

| 项目 | 行为 |
|---|---|
| 默认状态 | 关闭，继续使用原有 FE 供电 |
| 接入范围 | 仅检查相邻六个面，不提供无线或远距离取电 |
| 换算规则 | 跟随 AE2 的 AE / FE 汇率；默认 `1 AE = 2 FE` |
| 每点消耗 | 使用本体配置的技能点转换阈值，不另设固定 AE 价格 |
| 输入速率 | 沿用本体输入速率和缓冲限制 |
| FE 与 AE | 两种供电方式互斥 |
| 网络暂时缺电 | 仍有可用连接时保留进度，等待供电恢复 |
| 网络断开 | 交由本体原有的断供处理逻辑执行 |
| 操作权限 | 只有机器主人可以切换，无主机器拒绝切换 |
| 可选提示 | 安装 Jade 后可显示 ME 网络连接状态提示 |

AE 供电功能与五项技能分别检查兼容性。本体转换机接口不匹配时，该功能禁用并记录原因，其余技能仍按各自的兼容检查结果加载。

## 运行环境

| 项目 | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| Minecraft | 1.20.1 | 1.21.1 |
| Java | 17 | 21 |
| 加载器 | Forge 47.4.28（限 47 系列） | NeoForge 21.1.234 起，限 21.1 系列 |
| 编译基准：技能树 | 1.5.5（Forge 版） | 1.5.0（NeoForge 版） |
| 编译基准：AE2 | 15.4.11 | 19.2.17 |
| AE2 额外依赖 | GuideME 20.1.7（AE2 硬性要求） | 由 AE2 自带 |

两版共同要求：客户端与服务端均需安装；必需模组为原作者技能树本体、AE2 及其依赖；Jade 为可选。

**关于 AdvancedAE / ExtendedAE / ExtendedAE Plus / UFO：都不是必需依赖。** 不装这四个模组可以正常启动并使用全部五项技能；装了才会额外启用对应的适配。1.20.1 版中这些适配是软失败的：若某个适配因版本不匹配而无法注入，只在日志里留下一条 `Optional AE2 skill integration contract failed` 警告并跳过该适配，核心功能不受影响。装了第三方模组却没看到相应加成时，请先查 `logs/latest.log` 里是否有这条警告。

算力超频和算力之神的加护不对第三方完全自有的合成 CPU 实现提供通用加成。第三方复用原生 CPU 的情况，以及上面四个模组的专用适配，依赖对应版本的实现与接口，不能据此推断所有设备和版本均已兼容。

万机之神的加护只依赖 AE2 公开接口（`PatternProviderLogicHost#getTargets()`、`ICraftingMachine`、`IGridTickable`、`BlockState#getTicker`），凡是能被 AE 样板正常驱动的机器都会自动生效，无需逐个适配。

启动时会检查关键接口和注入位置，发现不匹配会给出加载诊断。依赖声明允许安装其他版本，不代表这些版本全部经过验证。

> **1.20.1 版的验证范围比 1.21.1 更窄。** 所有 mixin 目标点均通过反编译比对签名确认存在，构建产物也经过内容校验，但尚未在 1.20.1 的真实客户端/服务端上完整跑过一遍。首次使用请先备份存档。

## 常见问题

| 问题 | 排查方向 |
|---|---|
| 五项技能没有出现 | 检查两端是否安装附属、依赖是否完整，以及启动日志中的兼容诊断 |
| 加工机器没有瞬间完成 | 合成之神的加护仅直接执行合成样板，加工样板仍等待真实机器输出 |
| 第三方 CPU 没有技能加成 | 第三方自有 CPU 不保证获得加成，见[运行环境](#运行环境) |
| 转换机没有 AE 开关 | 检查附属安装情况和转换机兼容诊断，确认本体版本接口可用 |
| 开启 AE 后不产生技能点 | 检查机器主人、相邻网络、供电、红石状态、输入速率与本体转换阈值 |
| 容量提示出现备用格式 | 显示层已恢复，任务逻辑不受影响；反馈时附上 `logs/latest.log` |
| 第三方模组加成没生效 | 查 `logs/latest.log` 中是否有 `Optional AE2 skill integration contract failed` |

完整的功能边界与已知限制见 [Releases](https://github.com/yhoops/zifeng_ae2speed_skills/releases) 中各版本的说明。

提交问题请到 [Issues](https://github.com/yhoops/zifeng_ae2speed_skills/issues)，说明 Minecraft、加载器、Java、技能树、附属与 AE2 的版本，列出相关第三方模组，并提供复现步骤、`logs/latest.log` 及存在时的崩溃报告。

## 从源码构建

仓库里有两条相互独立的构建线，各自是完整的 Gradle 工程：

| 版本 | 目录 | JDK | 产物 |
|---|---|---|---|
| NeoForge 1.21.1 | 仓库根目录 | 21 | `build/libs/zifeng_ae2_skills-<版本>.jar` |
| Forge 1.20.1 | `forge-1.20.1/` | 17 | `forge-1.20.1/build/libs/zifeng_ae2_skills_forge_1_20_1-<版本>.jar` |

NeoForge 1.21.1（安装 JDK 21，在仓库根目录执行）：

```powershell
.\gradlew.bat build
```

Forge 1.20.1（需要 JDK 17）：

```sh
cd forge-1.20.1
JAVA_HOME=/path/to/jdk17 ./gradlew build
```

首次构建需联网下载依赖。两个工程的脚本都会自动下载并校验编译基准 jar（技能树、AE2 等），本体、AE2 与第三方依赖不会被包装进附属产物。

### 验证命令

以下任务与脚本针对 NeoForge 1.21.1 构建线；Forge 1.20.1 目前只提供 `build`、`runClient`、`runServer`。

| 命令，Windows 示例 | 检查内容 |
|---|---|
| `.\gradlew.bat runClient` | 启动开发客户端 |
| `.\gradlew.bat runClient -PclientSmoke` | CPU 控件绘制、悬停、容量边界、异常恢复与相关界面 |
| `.\gradlew.bat runGameTestServer -Pae2Tests` | 合成、虚拟 CPU、持久化与 AE 供电等服务端检查 |
| `python tools/verify_release.py` | 翻译键、发布包内容及兼容类索引 |
| `python tools/verify_compat.py` | 技能数值、溢出饱和、降级、时间预算及适配注册 |

Python 脚本在构建后运行；`verify_compat.py` 需要设置 `JAVA_HOME` 或将 JDK 加入 `PATH`。Linux / macOS 将 Gradle 命令替换为 `./gradlew`。

可使用 `-PcompatMods=<目录>` 加载本地第三方 JAR，使用 `-PhostJar=<路径>` 或 `-Pae2Jar=<路径>` 指定运行时依赖；这些参数不改变源码中的编译基准。

## 更新日志

各版本变更记录见 [CHANGELOG.md](CHANGELOG.md)。

## 许可与署名

本项目基于原作者 **子枫（zifeng）** 的技能树项目，遵循 [CC BY-NC-SA 4.0](LICENSE)。允许注明来源的非商业修改与分发，衍生作品须沿用相同许可；具体权利与义务以许可证全文为准。

- [原作者项目：子枫技能树](https://www.curseforge.com/minecraft/mc-mods/zcs)
- [附属源码](https://github.com/yhoops/zifeng_ae2speed_skills)
- [版本发布](https://github.com/yhoops/zifeng_ae2speed_skills/releases)
