# ADR 0002：「神明之力」页签索引在运行时跟随宿主页签数

- 状态：已采纳
- 日期：2026-10-26
- 版本：1.2.0（Forge 1.20.1 线）
- 相关代码：`forge-1.20.1/src/main/java/org/zifeng/ae2skills/SkillDefinitions.java:24-54`、`forge-1.20.1/src/main/java/org/zifeng/ae2skills/mixin/host/SkillTreeCategoryMixin.java`

## 背景

子枫技能树本体把页签数量写成一个字面量常量 `SkillTreeScreen.CATEGORY_COUNT`，并在自己的代码里用 `for (i = 0; i < CATEGORY_COUNT; i++)` 收集页签、分配数组、查找索引。本附属要追加第 N+1 个「神明之力」页签，就必须让这个 N 与本体保持一致。

难点在于**这个 N 不是固件常量**：本体 1.21.1 是 11，1.20.1 是 12（多一个「枪械」`col_gun` 页签）。写死任何一个值，都会在另一条版本线上错位；更要紧的是本体自己还在加页签——它每加一个，写死的 N 就错一次。

## 决定一：不再用 `@ModifyConstant` 改写宿主的字面量

旧做法是把字面量 `11`（1.21.1）或 `12`（1.20.1）通过 `@Constant(intValue = ...)` 替换成「页签数 + 1」，在 7 处 `@ModifyConstant` 注入点上逐一对齐 `require` 数量。这个方案有两个致命问题：

1. **匹配值本身是写死的。** 本体一旦新增页签，`intValue = 12` 就匹配不上任意一处，而 `require` 是硬校验——整个 Mixin 应用失败，游戏直接起不来。这是一个「本体更新即崩服」的耦合。
2. **`CATEGORY_COUNT` 在字节码里被完全内联。** 实测：

```
$ javap -p -c -cp <host-1.20.1-1.5.5.jar> org.zifeng.skilltree.client.screen.SkillTreeScreen \
    | grep -c 'SkillTreeScreen.CATEGORY_COUNT'
0
```

全局 **0 处 `getstatic`**——编译器把每个引用点都折成了 `bipush 12`。所以不存在「改一处常量、处处生效」的捷径，必须逐个注入点去数，且每处都要单独维护 `require`。

## 决定二：页签数在运行时从宿主反射读取

`SkillDefinitions.hostCategoryCount()` 用反射读宿主的 `CATEGORY_COUNT`：

```java
Class<?> screen = Class.forName("org.zifeng.skilltree.client.screen.SkillTreeScreen");
Field field = screen.getDeclaredField("CATEGORY_COUNT");
field.setAccessible(true);
return field.getInt(null);   // static 字段，实例传 null
```

能这么读的前提是**该字段带 `ConstantValue` 属性**，核对方式：

```
$ javap -p -constants -cp <host.jar> org.zifeng.skilltree.client.screen.SkillTreeScreen
private static final int CATEGORY_COUNT = 12;    ← 有 = 12 说明 ConstantValue 存在
```

于是三个派生量全部变成函数，不再有常量：

| 方法 | 含义 | 返回值 |
|---|---|---|
| `hostCategoryCount()` | 宿主页签数 | 反射读到的 N |
| `divineCategoryIndex()` | 「神明之力」的索引 | `N`（紧跟宿主最后一个之后） |
| `categoryCount()` | 总页签数 | `N + 1` |

本体将来加页签，N 自动变化，附属的页签自动后移，**不需要改一行代码**。

### 为什么用反射，而不是直接写 `SkillTreeScreen.CATEGORY_COUNT`

`SkillDefinitions` 同时被服务端逻辑使用。直接写类字面量会把 `org.zifeng.skilltree.client.screen.SkillTreeScreen` 变成编译期依赖，解析这个类时它自己的静态初始化链会被拉起——**专用服务器没有客户端屏幕类，会在加载期就抛 `NoClassDefFoundError`**。用 `Class.forName` 字符串形式，服务端只要不调用这个方法就不会碰客户端类。

这也是为什么 `resolveHostCategoryCount()` 的 `catch` 捕获的是 `Throwable` 而不是 `ReflectiveOperationException`：类加载失败抛的是 `NoClassDefFoundError`（`Error`，不是 `Exception`）。服务端调用时应当静默退到兜底值，而不是让整条调用链炸掉。

## 决定三：四处补齐，全部 `require = 1`

既然不再改宿主的字面量，就得在宿主自己的循环**跑完之后**把我们那一项补进去。`SkillTreeCategoryMixin` 在四处动手：

### 1. `<clinit>` TAIL —— 两张静态表

`CATEGORY_TITLE_KEYS`（页签标题键）与 `CATEGORY_COLORS`（强调色）都是 `static final`，长度由宿主按字面量创建。用 `Arrays.copyOf` 扩到 `N + 1` 后填入 `col_divine` 与 `0xFFB388FF`。

必须同时标 `@Final @Mutable` 才能重新赋值——只标 `@Shadow` 是只读的。

### 2. `<init>` TAIL —— 三个实例数组

`catButtonX`、`catButtonW`、`catTitles` 是**实例**字段，在宿主构造器里按字面量分配，长度只够宿主自己的页签。不扩的话，`rebuildCategoryRow` 按 `displayedCategories` 里的索引写这些数组时会**数组越界**。

这里用的是「长度不足才扩」的写法（`if (catButtonX.length < total)`），而不是无条件 `copyOf`：宿主若某天自己把数组分配得比 `CATEGORY_COUNT` 更长，我们不该把它缩短。

### 3. `refreshDisplayedCategories` TAIL —— 可见页签列表

宿主用 `for (i < CATEGORY_COUNT)` 收集可见页签后，我们把索引 `N` 补进 `displayedCategories`。

**这个方法是 1.20.1 独有的，1.21.1 不存在**（1.21.1 的宿主直接在渲染路径上算）。所以同一个 Mixin 在两套工程里注入点不同。

判断「我们的页签是否该显示」用的是宿主自己的 `isSkillVisible(String)`，而不是自己写一套判定——语义与它筛宿主页签时完全一致，宿主今后改可见性规则我们自动跟随。

### 4. `categoryIndexOf` RETURN —— 索引查找

宿主按 `for (i < CATEGORY_COUNT)` 查找技能归属页签，找不到返回 `-1`。我们的页签索引是 N，宿主永远找不到，所以在 RETURN 时把 `-1` 换成 `N`。

用 `cir.getReturnValue() >= 0` 先判断，**已经找到就绝不覆盖**——否则宿主自己页签上的技能会被我们抢过来。

### 另有一处 `categorySkills` HEAD —— 成员表与去重

`categorySkills(int)` 返回某个页签的技能列表。这里做两件事：

- 索引等于 `divineCategoryIndex()` 时，返回 `DIVINE_TAB_SKILLS`；
- 索引等于 **6（「寰宇法则」）** 时，返回「从 `Skills.GLOBAL_SKILLS` 里滤掉神明技能」的结果，避免同一个技能同时出现在两个页签。

第 6 这个数字是宿主「全局」页签的固定索引，与页签总数无关，所以**它仍然可以写死**——这是本方案唯一保留的字面量，且它的含义不是「第几个页签」而是「全局页签是第几个」，本体新增页签时这个索引也不会变。

## 降级策略

- **四处注入全部 `require = 1`。** 宿主结构一变就整体失败并明确报错，不会出现「能进游戏但页签错位」的静默错误。错位比崩溃更难排查——玩家会看到技能莫名其妙地出现在别的页签里。
- **反射失败时退到 `HOST_CATEGORY_COUNT_FALLBACK = 12`。** 只有在本体结构彻底变化（字段被改名或移除）时才会走到这条路。此时页签大概率也注入不上，兜底值只是让报错发生在更明确的地方，而不是 `NullPointerException`。
- **缓存用 `volatile Integer`，null 表示未解析。** 反射只在第一次调用时做一次；页签数在进程生命周期内不会变。

## 与 1.21.1 线的差异

| | 1.21.1 | 1.20.1 |
|---|---|---|
| 宿主 `CATEGORY_COUNT` | 11 | 12 |
| 附属页签索引 | 11 | 12 |
| 实现方式 | `SkillDefinitions.HOST_CATEGORY_COUNT = 11` 常量 + `@ModifyConstant` | 反射读 + 四处 TAIL/RETURN 补齐 |
| 宿主是否有 `refreshDisplayedCategories` | 否 | 是 |

1.21.1 线目前仍是写死常量 + `@ModifyConstant`（7 处），且其中 **4 处**因为宿主版本差异被设成 `require = 0`：

| 注入点 | require | 原因 |
|---|---|---|
| `<init>` | 3 | 两版本都保留字面量 11 |
| `refreshDisplayedCategories` | 0 | **1.5.0 没有这个方法** |
| `rebuildCategoryRow` | 0 | 1.5.5 已改为遍历 `displayedCategories`，无字面量 |
| `renderCategoryRow` | 0 | 同上 |
| `mouseClickedInner` | 0 | 同上 |
| `categoryIndexOf` | 1 | 两版本都保留字面量 |
| `searchResults` | 1 | 两版本都保留字面量 |

**这一串 `require=0` 本身就是「靠匹配字面量定位」的代价**：宿主换个写法改写同一个逻辑，注入点就静默失效，只能靠降 `require` 来兼容多个宿主版本——而 `require = 0` 的注入点失效时没有任何日志。

它只在宿主 1.5.0 / 1.5.5 上验证过，**若本体在 1.21.1 上新增页签，这条线会直接起不来**（`intValue = 11` 匹配不上）。同样的重构可以照搬到根工程：把 `HOST_CATEGORY_COUNT` 换成 `hostCategoryCount()`，把 7 处 `@ModifyConstant` 换成对应的 TAIL 注入。注意 1.21.1 宿主**没有** `refreshDisplayedCategories`，那一处要换成它自己收集可见页签的方法。

## 排查：页签没出现或位置不对时

按顺序查这四件事：

1. **`zifeng-ae2-contract-classes.txt` 是否存在。** 它在产物 jar 里，由 `generateContractIndex` 任务生成。缺了它，`Compatibility` 的预检会把 `PROBLEMS` 塞满，`enabled()` 变 false，**所有 mixin 静默不应用**——表现是游戏正常启动、只多一行 ERROR 日志、附属功能全灭。
2. **`SkillTreeCategoryMixin` 是否被应用。** 看日志里有没有该 mixin 的注入失败记录。四处都是 `require = 1`，失败必然有日志。
3. **`hostCategoryCount()` 实际读到的值。** 宿主升版后如果页签数变了但我们的索引没跟着变，说明反射读失败了（走了兜底 12）。核对 `javap -constants` 看字段是否仍带 `ConstantValue`。
4. **页签顺序与强调色。** 标题键 `col_divine` 需要翻译条目 `ui.zifeng_s_custom_skill_tree.col_divine`；强调色跟在 `CATEGORY_COLORS` 数组末尾，若显示为黑色说明 `Arrays.copyOf` 后没填值。

## 一句话总结

**宿主把页签数写死、且内联到 0 处可改的 `getstatic`，所以不要试图改它的常量——改为在它每个循环跑完之后补一条，并让「补在第几位」在运行时从宿主反射求得。**
