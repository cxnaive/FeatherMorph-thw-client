# 26.2 实际客户端验证

此验证使用两个真实 Minecraft 26.2 / Fabric 客户端和临时 Lophine 服务端，
测试 `fabric/build/libs` 中的生产 JAR。客户端类路径不含项目编译输出，
也不直接提供协议库，协议类由生产模组的嵌套 JAR 加载。

需要 Java 25、Python 3、可用的 `DISPLAY`（桌面或 Xvfb）、OpenGL 驱动，以及
`../FeatherMorph` 已构建的 26.2 插件。默认服务端种子目录为
`../thw_servers/26.2/test-server`，其中应有 `server.jar`、`cache`、`libraries`、
`versions` 和 `plugins/packetevents-spigot-2.14.0.jar`。

```sh
DISPLAY=:197 python3 validation/26.2/validate.py
```

`FM_SERVER_SEED`、`FM_TEST_PORT`、`FM_JAVA` 可覆盖相应路径、端口和 Java 程序。
`FM_SCREEN_VALIDATION_ONLY=true` 可只重跑握手、界面、相机和自身可见检查。
默认选择空闲的回环端口，创建临时平坦世界，客户端使用离线测试账号。
测试不会重启现有服务器或复制现有世界；测试目录使用本地测试环境的 EULA 接受设置。

## 覆盖范围

- 生产 JAR 的游戏/Java 版本、资源包格式、嵌套协议库、测试类隔离。
- 两个客户端启动、五种注册表查询及默认 API 17 的实际 V3 握手。
- cow、pig、chicken、cat、wolf、sheep、mooshroom、nautilus、evoker、slime、
  magma cube、sulfur cube、mannequin、copper golem、horse、ender dragon 和玩家形态。
- 每次实际 `/morph`、自身同步器、实体类型、玩家渲染重定向、默认服务端后端的他人实体、`/unmorph`。
- 控制台专用测试插件在临时服务器切换至客户端后端，再验证牛、史莱姆、岩浆怪、
  硫磺立方怪及玩家形态的他人同步器与渲染；生产服务端不安装这个测试插件。
- 史莱姆和岩浆怪尺寸 3；硫磺立方怪的基本形态。
- 形态选择、快速选择、表情、生存模式背包、创造模式背包、Cloth Config 和关闭界面，
  对各界面实际类型进行断言，避免创造模式自动切换背包造成误判。
- 第一人称、第三人称、自身可见切换；牛、史莱姆、硫磺立方怪及界面截图。
- 一个客户端安装 EMF、ETF、EntityCulling，另一个使用基础依赖。
- 两个客户端和测试服务端正常退出。

输出路径在启动时打印。`result.json` 保存结果及产物 SHA-256，`server.log` 和两个
客户端目录的 `client.log`、`events.jsonl`、`screenshots` 保留证据。失败时也会清理
本次启动的进程，保留失败目录；测试类仅存在于单独的 `FeatherMorphClientUpgradeProbe.jar`。
服务端测试插件为临时目录内的 `BackendProbe.jar`，仅允许控制台切换测试后端。
测试检查日志中的错误和未处理异常；已知离线认证与缺少旁白库的环境错误单独记录。

不覆盖全部技能及能力、完整自定义 EMF 资源包、Vulkan、跨区域定位连接或 NeoForge。
无在线令牌时 Mojang/Realms 请求可能返回 401，缺少可选 Vulkan/旁白库也可能产生
环境错误；渲染使用软件 OpenGL。OpenAL 无音频设备时的声音系统错误会单独记录，
声音播放不在此环境中验证。实际运行记录应注明这些情况。

若游戏和正常退出已经通过，可用 `python3 validation/26.2/validate.py --audit-run <证据目录>`
仅重查保留日志。该模式要求两个客户端所有事件均通过、已正常退出且生产 JAR 的
SHA-256 未改变，并保留首次结果文件；不会把游戏失败改成通过。

本地运行结果见 [2026-10-10 记录](runs/2026-10-10-lophine.md)。
