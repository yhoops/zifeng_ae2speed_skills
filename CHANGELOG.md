# 更新日志

本文件记录各版本的变更。完整的构建产物与下载见 [Releases](https://github.com/yhoops/zifeng_ae2speed_skills/releases)。

---

## 1.2.1（仅 Forge 1.20.1 构建线）

修复 [issue #1](https://github.com/yhoops/zifeng_ae2speed_skills/issues/1)：与以高优先级 `@Overwrite` 覆盖 AE2 合成服务方法的模组（实测为 crazyae2addons 2.6.2）同装时，游戏在启动阶段直接崩溃。1.21.1 主线不受该问题影响，版本号仍为 1.2.0。

### 修复：与 crazyae2addons 等模组的启动崩溃

- 崩溃原文：`InvalidInjectionException: @At("INVOKE") on CraftingService::zifeng$ownFinalOutput with priority 1000 cannot inject into CraftingService::insertIntoCpus ... merged by net.oktawia.crazyae2addons.mixins.AAECraftingServiceMixin with priority 1200`。crazyae2addons 用 `@Overwrite` 重写了整个 `insertIntoCpus`，本模组对该方法体内的调用点做 `@Redirect`，而 Mixin 不允许低优先级向已合并的方法注入。
- 根因修复：这条「兄弟 CPU 不重复结清最终产物」的去重从**调用方**的 `@Redirect` 改挂到**被调方** `CraftingCpuLogic.insert` 的 `@Inject(HEAD, cancellable)`（新文件 `mixin/ae2/AeCpuInsertMixin`）。第三方无论怎么覆盖调用方，最终仍要调用被调方，注入点因此免疫；判据与原实现完全一致，不影响其它逻辑。取舍见 [docs/adr/0003-insert-dedup-on-callee.md](docs/adr/0003-insert-dedup-on-callee.md)。

### 新增：注入失败不再阻止启动，改为游戏内提示

- 主配置的注入失败从「中止启动」降级为「停用受影响功能并继续」。原来 `required:true` 会把一次注入冲突升级成 FATAL。**代价是可能出现半应用状态**，因此配套做了下面三层防护。
- 玩家登录时会在聊天框收到三行提示：说明技能已停用、逐条列出失败原因（含冲突目标类名）、以及 `logs/latest.log` 的完整路径；同时服务端日志留一条 WARN。每个玩家每次进服只提示一次。
- 半应用防护：`FMLCommonSetupEvent` 阶段用 `isAssignableFrom` 逐个确认 5 个跨 mixin 契约接口真的挂到了目标类上，缺任何一个即整体降级——技能等级全部发布为 0，所有功能入口在触到强制转换之前就返回。同时把代码里全部 13 处跨 mixin 强制转换改为 `instanceof` 检测作为冗余保险，`NetworkHandler` 在通道未注册时短路发送，避免 tick 被打断。
- 降级时的处理方式是**全部停用**而不是「只停出问题的那一个技能」：五个技能共享同一套契约并读写同一份 NBT，只停一个会让存档结构分叉。

### 验证状态

- 通过 JDK 17 全量构建（`./gradlew build`），产物 `zifeng_ae2speed_skills-1.20.1forge-1.2.1.jar`；产物内容已校验：三个混入配置引用的 class 全部存在、`AeCpuInsertMixin` 已注册、无残留的旧注入。
- 冲突源 crazyae2addons 2.6.2 的 `@Overwrite` 方法体经 `javap` 逐条确认仍以虚调用走到 `CraftingCpuLogic.insert`，故新注入点必然命中。
- **仍未在真实存档上跑过完整回归**，首次使用请先备份存档。

---

## 1.2.0

首个同时提供 NeoForge 1.21.1 与 Forge 1.20.1 两条构建线的版本，并接上「万机之神的加护」6 级的瞬成效果。

### 新增：万机之神的加护 6 级「瞬成」

- 等级上限由 5 提升到 6，第 6 级费用 1,000,000 点（非等比序列 1,000 / 5,000 / 10,000 / 50,000 / 100,000 / 1,000,000）。技能树里 10 万以上的费用现在带千分位显示（`1,000,000`），10 万以下的既有费用一字未变。
- 6 级的效果不是更高的倍率（倍率仍是 ×1024），而是性质变化：先做 1 批验证，验证通过就把该样板剩余的全部批次瞬间记账完成。
- 验证判据取严格版——必须观察到合成任务真的从 `waitingFor` 里扣掉了该样板的产物（用 `insert` 前后的 `waitingFor` 差值判定），而不是 `pushPattern` 返回 `true`。注意不能用 `insert` 的返回值：那是下游领取方拿走的量，产物直接进 ME 存储的订单恒为 0。
- 验证期间锁住该样板，保证同一时刻只有 1 批在飞；超时 200 tick 则放弃并永久回落 5 级。
- 瞬成批次免电；验证批照常扣电。机器耗电不补偿也不追缴。
- 仅接管处理样板，且仅对 AE2 原生合成 CPU（含虚拟 CPU）生效；合成台样板仍归「合成之神的加护」，第三方 CPU 仍只享受 5 级加速。
- 「算力之神」「合成之神」的等级上限、费用与效果完全不变。

### 新增：成就「超越万机？？？」

- 首次把「万机之神的加护」点到 6 级时发放（`divine_transcendence`），图标为 `minecraft:lightning_rod`，`hidden: false`，未达成时也挂在进度界面当一个看不懂的条目。
- 达成者本人客户端会响一道原版雷声（`entity.lightning_bolt.impact` + `entity.lightning_bolt.thunder` 双段，音高与音量照抄原版落雷并拉长），其他人只在聊天栏看到公告。
- 成就按该玩家自己的技能等级判定，而技能效果是全服取最高：没点到 6 级的 OP 拿不到这条成就，这是有意为之。
- 5 级成就 `divine_machine_grace` 的门槛被钉成常量 5，老存档不会因为上限提高而失去它。

### 新增：Forge 1.20.1 支持

- 新增独立的 Forge 1.20.1 构建线（`forge-1.20.1/`），功能与 NeoForge 1.21.1 版一致，产物名 `zifeng_ae2_skills_forge_1_20_1-<版本>.jar`。
- 编译基准：技能树 1.5.5（Forge）、AE2 15.4.11、Forge 47.4.28、JDK 17。
- 网络层由 1.21.1 的 `CustomPacketPayload` + `StreamCodec` 改写为 Forge 的 `SimpleChannel` + `IMessage`。
- 存档、注入点与 1.21.1 版逐一对齐；AE2 与宿主在 1.20.1 的差异（如 `CraftingCPUCluster#writeToNBT` 少一个 `HolderLookup.Provider` 参数）已按实际签名处理。
- 第三方适配（AdvancedAE / ExtendedAE / ExtendedAE Plus / UFO）在 1.20.1 为软失败：任一适配不匹配只跳过该适配并记日志，不影响核心功能。

### 体积

- `logo.png` 由 512×512 真彩（465 KB，占旧 jar 76%）换成 256×256 / 64 色调色板 PNG，仅换图就使 jar 从 609,705 B 降到 175,387 B；接完 1.2.0 全部功能后最终约 186 KB；Forge 1.20.1 版因内置 MixinExtras，约 373 KB。

### 实现

- 新增 `divine/DivineTranscend`（取证与结算状态机）、`mixin/ae2/DivineCpuMixin`（观察 `executeCrafting` / `insert` / `tickCraftingLogic`）、`crafting/CraftingDelivery`（从 `InstantCrafting` 抽出的三级兜底投递，两处共用）。
- `PatternProviderPushMixin` 增加 `pushPattern` HEAD 的取消点，用于验证期间上锁。
- 三个不可逆决定的来由记在 [docs/adr/0001-divine-grace-level-6.md](docs/adr/0001-divine-grace-level-6.md)。
- 「神明之力」页签如何跟随宿主页签数动态定位（1.20.1 版改用运行时反射，不再匹配写死的字面量）记在 [docs/adr/0002-divine-tab-dynamic-index.md](docs/adr/0002-divine-tab-dynamic-index.md)。
- 「算力之神」5 级分裂算力会绕过 AE2 原生的 `executeCrafting`（改由 `UnlimitedDispatch` 直连 `pushPattern`），因此 6 级的取证标记在那条路径上也补了一份——否则同时点满分裂算力的玩家会看到 6 级完全不生效。

### 修复：兼容预检不再误杀可选注入

1.1.7 起，`compat/Compatibility` 的结构预检把带 `require = 0` 的可选注入也当成必检契约，于是宿主 1.5.0 上（没有 1.5.5 才引入的 `SkillTreeScreen#refreshDisplayedCategories`）客户端会直接拒绝加载附属，报「缺少混入目标 / Missing hook」。现在预检会读出同一注解的 `require`，为 0 时跳过「缺少混入目标」「缺少注入位置」两条判定，与 Mixin 自身语义对齐；`require >= 1` 的契约照旧强制校验，真正的结构漂移依然会在启动时炸出来。

### 修复：瞬成在实测中不生效

实测发现「验证批正常发出、剩余批次却从未结清」。根因是验证判据读错了返回值：`CraftingCpuLogic.insert` 返回的是下游领取方（`job.link`）拿走多少，产物直接进 ME 存储、无人领取的订单恒返回 0，而 `waitingFor` 其实已经被扣掉了——于是这类订单永远验不过，200 tick 超时后回落 5 级。

现改为比较 `insert` 前后 `waitingFor` 的差值（`delta > 0` 才算被任务接纳），与谁领走无关，且依旧严格（空壳机不会让 `waitingFor` 下降）。同时新增端到端 GameTest `divineTranscendSettlesVerifiedPattern`：真机路径放一个箱子当机器、下单 100 份，断言「只推 1 批 → 回流 1 个产物后验过 → 再 tick 一次整单结清、箱子仍只有 1 批」。

### 已知问题

以下问题在 1.2.0 中依然存在。**1.21.1 版的核心逻辑只用 GameTest 与客户端冒烟验证过，尚未在真实存档的大规模产线上跑够样本；1.20.1 版为静态比对移植，未做运行期验证。** 两边都请先备份存档，不要直接用于不可回滚的重要存档。

- **瞬成仅做了单机 GameTest 级别的验证**：1 圆石 → 1 钻石、100 批量、单一样板、真实供应器路径。「多步合成（有中间产物）」「带容器的配方」「同一样板被多个 CPU 并发使用」这几类分支只有代码层推理，缺少实测样本。
- **200 tick 超时后不再重试**：验证失败（机器是空壳、机器吞料、产物进不了合成账本）时该样板会在本任务内永久退回 5 级加速，不会再次尝试瞬成。换任务或重新下单才会重新取证。
- **两条样板产出同一物品时不瞬成**：为避免把 B 线机器做出的产物记到 A 线头上，出现歧义时两条都退回 5 级。这是刻意的保守选择，不是 Bug，但会让人觉得「有时不生效」。
- **第三方合成 CPU 不生效**：只对 AE2 原生合成 CPU（含虚拟 CPU 与本模组的适配形态）生效；AdvancedAE、UFO 等第三方 CPU 形态仍只享受 5 级 ×1024。
- **合成台样板不走瞬成**：6 级只处理加工样板；合成台样板仍归「合成之神的加护」的瞬成逻辑。
- **5 级分裂算力的交互**：`UnlimitedDispatch` 会绕过原生 `executeCrafting`，早期版本会让 6 级对点满分裂算力的玩家完全失效，现已补上取证入口，但两个技能同时满级时的组合行为样本很少。
- **成就与雷声**：`divine_transcendence`（超越万机？？？）按玩家自己的等级发放，而技能效果是全服取最高等级——没点到 6 级的人拿不到这条成就，属预期行为。

遇到异常请附上 `logs/latest.log`、机器类型、样板内容与订单规模，这些信息足以定位到上面列出的哪一类分支。

**验证状态：**

- **1.21.1**：通过 Gradle 构建、服务端 GameTest（5/5，含新增的瞬成端到端用例）、客户端冒烟（6 项断言，含费用千分位、5 级 ×1024 与 6 级「瞬成」文案）、`verify_release.py` 与 `verify_compat.py`。真实产线上的瞬成行为仍需手动实测。
- **1.20.1**：通过 Gradle 构建。全部注入点与目标签名已对照 1.20.1 的 AE2 15.4.11 与技能树 1.5.5 逐条 `javap` 核对，但**未启动游戏运行**，Mixin 能否成功应用只做了静态判定。首次使用请先在小号或测试存档里确认技能树页签、AE2 合成界面与转换机按钮三处是否正常。

构建成功不等同于所有游戏内行为均已通过验证。

---

## 1.1.7

### 新增：万机之神的加护

- 技能 id `ae_divine_machine_grace`，位于新增的第 12 个页签「神明之力」。AE 合成任务激活时，为这条产线上被样板驱动的机器追加原生工作次数。
- 实现方式是同一 tick 内重复调用机器自己的 `tickingRequest` / `BlockEntityTicker.tick`，不改动 tick 频率、进度字段或任何机器内部状态字段，因此机器自身的耗电、产出与容器返还规则全部保留。
- 倍率 ×2 / ×8 / ×16 / ×128 / ×1024，费用 1,000 / 5,000 / 10,000 / 50,000 / 100,000 点，为非等比递增。
- 目标发现只走「样板真的推给了它」这条路径：由 `PatternProviderPushMixin` 记录成功推送的样板供应器，再取其配置朝向相邻的装配机或带方块 ticker 的机器；不使用 `IGrid#getActiveMachines` 做全网格枚举，避免把技能从「这条产线」放大成「整个基地」。
- 三重闸门：有技能等级；该网格存在未暂停的活跃任务（含第三方 `ICraftingCPU#isBusy()` 兜底）；供应器属于该活跃网格。任务完成、取消或暂停后下一 tick 立即停止。
- 兼容性只依赖 AE2 公开接口（`PatternProviderLogicHost#getTargets()`、`ICraftingMachine`、`IGridTickable`、`BlockState#getTicker`），第三方机器无需逐个适配。

### 新增：神明之力页签

- 宿主把页签数写死为字面量 11，本次用 6 处 `@ModifyConstant` 精确扩到 12，并用 `Arrays.copyOf` 扩展页签标题键与强调色数组，不改写宿主长方法。
- `require` 数量与实际命中数一致，宿主升级导致失配时由 `MixinFailureHandler` 直接报错停止，不会静默错位。
- 全局页签过滤掉神明技能，避免同一技能同时出现在两个页签；页签强调色 `0xFFB388FF`，类型前缀 `[诸神]`。

### 变更：合成之神 / 算力之神迁入神明页签

- 「合成之神的加护」（`ae_assembler_infinite`）与「算力之神的加护」（`ae_cpu_infinite`）改归「神明之力」页签，tooltip 前缀改为 `[诸神]`、标题改为紫色。
- 仅归属变化：技能 id、等级表、费用（仍走普通等比费用表，不套用万机之神的非等比费用）与效果完全不变，已有存档无需迁移。

### 新增：三个满级成就

- `divine_machine_grace` / `divine_assembler_grace` / `divine_cpu_grace`，分别在有人首次把对应技能激活到 5 级时发放。
- 走原版 advancement 系统，发放状态由 `SavedData`（`zifeng_ae2_skills_divine_grace`）落盘，每个存档只发放一次，重启服务器不会重复触发；换存档后允许重新发放。
- 会话内加内存短路，避免满级时每 tick 查询存档；成就未在数据包中加载时输出 warn 日志，不再静默跳过。

### 优化：时间预算与公平性

- 万机之神的加速与「无尽装配」「分裂算力」共用同一份每 tick 时间预算：单片 2.5 ms，共享池硬顶 20 ms / tick 且不超过 tick 起点后 45 ms，预算耗尽的部分自动顺延到后续 tick，不会丢弃。
- 单台机器每 tick 的额外次数不超过 1,023，全体合计不超过 8,192，避免一台机器或一张网络独占整帧。
- 目标轮转指针只在「该台机器完整服务完」时推进，提前撞到时间或次数上限时原地保留，修复了队首机器吃满配额、队尾机器被系统性饿死的问题。
- 同一 tick 出现第二个入口时先做去重再清状态，避免让路判定失效导致同一台机器吃到两份额外 tick。

### 优化：与装配超频的互斥

- 万机之神在本 tick 实际加速过某台机器时，装配超频对该机器整段让路，而不是降级相加，单台机器每 tick 只有一份额外 tick。
- 让路判定读取「本 tick 真的执行过额外 tick」的集合，不是上一 tick 的目标快照，因此不存在一 tick 的相位差。

### 其他

- 新增模组图标与品牌素材。
- 客户端冒烟与服务端 GameTest 补充神明页签、成就与加速相关的检查用例。

---

## 1.1.6

- 修复算力之神的加护满级时，悬停 AE2 CPU 列表引发容量格式化数组越界崩溃的问题。
- 无限容量显示为 `∞`；超出 AE2 格式化范围的有限容量显示真实字节数。
- 容量格式化遇到运行时异常时，改用备用显示并提示反馈所需的日志路径；每次客户端启动最多记录一次完整异常。
- 修复位于客户端显示层，保留原有容量计算、合成任务和存档逻辑。
