# 子枫 AE2 技能附属

Zifeng AE2 Skills 为原作者「子枫技能树」提供四个 Applied Energistics 2 技能，当前版本为 **1.1.6**。
这是独立附属模组，需要同时安装技能树本体与 AE2。

| 技能 | 效果 |
|---|---|
| 装配超频 | 提升分子装配室合成速度 |
| 算力超频 | 提升合成 CPU 的并行处理能力 |
| 无尽装配 | 加速执行合成样板，保留材料与电力消耗 |
| 分裂算力 | 提供独立合成任务与共享容量，满级支持无限容量与按需增加 CPU |

算力超频与分裂算力面向 AE2 原生合成 CPU；第三方自有 CPU 不保证获得技能加成。
1.1.6 修复满级无限容量在 CPU 悬停提示中引发的崩溃，并为容量格式化异常提供备用显示和友好提示。

## 运行环境

- Minecraft 1.21.1、Java 21、NeoForge 21.1.234 或更新的 21.1 系列版本。
- [原作者技能树本体](https://www.curseforge.com/minecraft/mc-mods/zcs/files/9048417)及 AE2 自身依赖。
- 编译基准为技能树 1.5.0、AE2 19.2.17。
- 客户端和服务器均需安装附属。更新时退出游戏或停止服务器后替换旧附属 JAR。

## 构建

首次构建会下载并校验原作者本体，作为编译依赖；本体和 AE2 类不会打包进附属。

```powershell
.\gradlew.bat build
```

Linux / macOS：

```sh
./gradlew build
```

产物位于 `build/libs/`。开发客户端可通过 `runClient` 启动。

## 测试

```powershell
.\gradlew.bat runClient -PclientSmoke
.\gradlew.bat runGameTestServer -Pae2Tests
python tools/verify_release.py
python tools/verify_compat.py
```

`verify_compat.py` 需要安装 JDK 21 并设置 `JAVA_HOME`，或将 JDK 命令加入 `PATH`。
客户端测试覆盖 CPU 列表绘制、容量边界、无限容量、格式化失败恢复及相关界面功能。
完整服务端测试中，大型多步骤订单的原生 tick 完成断言存在已知失败；当前不保证该测试套件全部通过。
可通过 `-PcompatMods=<目录>` 加载本地第三方依赖进行兼容测试，相关 JAR 不随源码提供。

## 许可与署名

本项目基于原作者 **子枫（zifeng）** 的技能树项目，遵循 [CC BY-NC-SA 4.0](LICENSE)：
允许注明来源的非商业修改与分发，衍生作品须使用相同许可。

- 原作者项目：https://www.curseforge.com/minecraft/mc-mods/zcs
- 附属源码：https://github.com/yhoops/zifeng_ae2speed_skills
