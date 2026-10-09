See [the plugin](https://github.com/NiFeather/FeatherMorph)

![cover](./assets/cover.png)

<p align="right">
  <img src="https://github.com/NiFeather/FeatherMorphClient/actions/workflows/gradle.yml/badge.svg">
</p>

<!-- [Wiki](https://github.com/NiFeather/FeatherMorph/wiki) -->

# (WIP) FeatherMorphClient

[FeatherMorph](https://github.com/NiFeather/FeatherMorph)的客户端模组，可以提供一些增强功能。

### 功能
- [x] 在客户端显示自身伪装
- [x] 伪装选择界面
- [x] 技能快捷键
- [x] 一键切换自身可见

### 依赖关系
当前维护分支针对 Minecraft **26.2**，需要 Java **25**，至少需要下面这些依赖才能运行：
- Fabric Loader **0.19.5** 或更新版本
- [Cloth Config](https://modrinth.com/mod/cloth-config)
- [Fabric API](https://modrinth.com/mod/fabric-api) 的 26.2 版本

推荐 Cloth Config **26.2.155**、Fabric API **0.161.0+26.2**；Mod Menu **20.0.3** 为可选依赖。
本分支只构建 Fabric，NeoForge 源码保留，但尚未移植到 26.2。

配套服务端为 `../FeatherMorph` 的 26.2 版本。新配置默认启用协议兼容模式（API 17）；
已有配置若关闭了此选项，需要在模组设置中启用后重新连接服务器。
参见 [26.2 升级说明](docs/26.2-upgrade.md) 和 [集成验证](validation/26.2/README.md)。

### 构建
```bash
#!/usr/bin/env bash
git clone https://github.com/NiFeather/FeatherMorphClient
cd FeatherMorphClient

./gradlew build --no-daemon
```

生成的模组位于 `fabric/build/libs/feathermorph_client-fabric-26.2-<版本>.jar`。
带 `-sources` 的文件是源码包，运行时使用不带该后缀的 JAR。

### Credits
- [Identity Mod](https://github.com/Draylar/identity): For how I learned to make client-side disguise possible.
- [VeinMiner](https://github.com/2008Choco/VeinMiner): For the reference about how to implement *Client <-> Server* communication.
- [osu-framework](https://github.com/ppy/osu-framework): For the Drawable, Bindable and the Dependency(`@Resolved`) system, although this project has a very poor reimplementation.
