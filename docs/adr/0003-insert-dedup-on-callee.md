# ADR 0003：把「兄弟 CPU 去重」从调用方 @Redirect 改挂到被调方 HEAD

- 状态：已采纳
- 日期：2026-10-29
- 版本：1.2.1（Forge 1.20.1 构建线）
- 相关代码：`mixin/ae2/AeCpuInsertMixin.java`、`mixin/ae2/AeCraftingServiceMixin.java`、`compat/MixinFailureHandler.java`、`compat/Compatibility.java`、`compat/DegradedNotice.java`、`divine/DivineTranscend.java`
- 触发问题：GitHub issue #1「关于该模组在无限计划（改）整合包中出现的问题」

## 背景

玩家在整合包里加入本模组后游戏直接起不来，日志里唯一的相关错误是：

```
InvalidInjectionException: @At("INVOKE") on appeng/me/service/CraftingService::zifeng$ownFinalOutput
with priority 1000 cannot inject into
appeng/me/service/CraftingService::insertIntoCpus(AEKey;JActionable;)J
merged by net.oktawia.crazyae2addons.mixins.AAECraftingServiceMixin with priority 1200
```

crazyae2addons 2.6.2 用 `@Overwrite`（priority 1200）重写了 `CraftingService.insertIntoCpus` 的整段方法体；
本模组在这个方法体里对 `CraftingCpuLogic.insert` 的调用做 `@Redirect`（默认 priority 1000）。
Mixin 的 `Injector.findTargetNodes` 规定：目标方法已被他人合并、且注入方优先级更低时直接抛
`InvalidInjectionException`；本模组主配置是 `required:true`，于是这个异常被升级成 FATAL 崩溃。

## 决定一：把去重挪到被调方 `CraftingCpuLogic.insert` 的 HEAD

`@Redirect` 的目标是「调用方方法体里的某个 invoke 指令」，因此调用方一旦被 `@Overwrite`，
注入点根本不存在。改成对被调方 `CraftingCpuLogic.insert` 做 `@Inject(HEAD, cancellable)` 后：

- 任何第三方对 `insertIntoCpus` 的 `@Overwrite` 都改不掉这个注入点——它的字节码里仍然是对
  `craftingLogic.insert(what, left, mode)` 的虚调用（已用 javap 确认 crazyae 的覆盖体第 92 行）；
- `CraftingCpuLogic.insert` 在 AE2 15.4.11 中没有任何 `@Overwrite`，不存在 merged 优先级检查；
- 语义等价：原 `@Redirect` 只在 `CraftingDelivery.finalDepth > 0`（最终产物落进 ME 存储后回流
  其他 CPU）这一小段窗口内生效，新注入点用同一条判据 `CraftingDelivery.isDeliveringFinalOutput()`
  + `key.matches(getFinalJobOutput())`，因此被拦下的仍是同一批调用。

**代价：** 这条判据现在对所有 `CraftingCpuLogic.insert` 调用生效，包括 `CraftingCPUCluster.insert`
这条路径。查证后确认这是**预期行为**：原生 `insertIntoCpus` 本来就对同一批物品做同样的去重，
只是原来挂在更窄的调用点上。

## 决定二：主配置注入失败从 ERROR 降级为 WARN

`MixinFailureHandler` 对 `zifeng_ae2_skills.mixins.json` 原本返回 `ErrorAction.ERROR`，语义是
「接不上就别启动」。但 `MixinProcessor.handleMixinError` 的规则是：`config.isRequired()` 决定
默认动作（`required:true` → ERROR），随后**错误处理器返回的动作会覆盖它**。因此返回 WARN 即可
让游戏继续进入。

**理由：** 对玩家而言「装了两个模组，游戏起不来，不知道谁的错」的成本远高于「技能暂时不生效」。
而且这类冲突的面很窄——通常只有一两个注入点被抢，其余功能完全可用。

**代价：** 会出现「部分 mixin 已应用、部分没应用」的半应用状态。这带出决定三。

## 决定三：能力检测 + 全局停用，而不是「能跑多少跑多少」

半应用状态下最危险的不是功能缺失，而是 `ClassCastException`：功能代码大量使用
`(CpuExtension) cluster`、`(CpuLogicAccess) logic`、`(JobAccess) job` 这类跨 mixin 强制转换，
一旦对应接口没挂上，第一次走到就抛异常，并且往往发生在服务器 tick 里，比原来的启动崩溃更难查。

因此：

1. `Addon` 在构造期无条件把 `Compatibility.checkApplied()` 挂到 `FMLCommonSetupEvent`（那一刻所有目标类都已加载完、mixin 已应用）；它用 `contract.isAssignableFrom(target)` 逐个确认 5 个跨 mixin 接口真的挂上了，缺任何一个即 `markDegraded(...)`。
2. `Ae2Effects.update` 在 `degraded()` 或 `!enabled()` 时把全部技能等级发布成 0——所有功能入口的前置条件都是「等级 > 0」，于是这些路径在触到强制转换之前就返回了。
3. 所有强制转换点仍然改成 `instanceof` 检测（共 13 处 + 新增的 `cluster()`/`access()` 辅助方法）。这一层是**冗余保险**：即使第 1、2 步的判断因为某种原因失效，也只会「功能不生效」而不会崩。

**为什么不做「只停用受影响的那一个技能」：** 五个技能共享同一套跨 mixin 契约（同一个
`CraftingCpuLogic`、同一个 `CraftingCPUCluster`），且读写同一份 NBT。只停一个会让存档结构分叉，
下次正常加载时读到半截数据。

## 决定四：进游戏后在聊天框告知玩家

降级后玩家的可见症状只是「技能点了没用」，极易被误判成模组没装或自己点错。因此玩家登录时
`DegradedNotice` 会发三条消息：一句中文说明（`translatableWithFallback`，客户端有译文则用译文）、
逐条原因（取自 Mixin 异常消息首行，最多 6 条）、以及 `logs/latest.log` 的绝对路径。

**理由：** issue #1 的报告方式就是「附一个 debug.log」，而这需要玩家先知道有这个东西。把路径直接
贴进聊天框，是把「排查成本」从「让玩家找日志」降到「让玩家复制一行」。

**已知取舍：** 提示是英文原文的目标类名（如 `appeng.me.service.CraftingService`），不翻译——
翻译后的类名对排查没有任何价值。

## 遗留问题

- 1.20.1 版仍**未在真实存档上跑过完整回归**。本次修复的验证范围是：JDK 17 全量构建通过、
  产物内容校验（混入配置指向的 class 全部存在、新混入已注册）、以及针对 crazyae 字节码的静态
  对照。真实环境下的行为需要玩家侧验证。
- 1.21.1 主线的 `AeCraftingServiceMixin` 仍是原来的 `@Redirect`（该构建线与 crazyae 的 1.20.1
  版本无关），本次未同步改动。若将来 1.21.1 也出现同类冲突，可直接照本文搬迁。
