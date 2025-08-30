# HAProxyDetector

[![](https://img.shields.io/github/downloads/andylizi/haproxy-detector/total?style=for-the-badge)](https://github.com/andylizi/haproxy-detector/releases) [![](https://img.shields.io/github/license/andylizi/haproxy-detector?style=for-the-badge)](https://github.com/andylizi/haproxy-detector/blob/master/LICENSE) [![](https://img.shields.io/bstats/servers/12604?label=Spigot%20Servers&style=for-the-badge)](https://bstats.org/plugin/bukkit/HAProxyDetector/12604) [![](https://img.shields.io/bstats/servers/12605?label=BC%20Servers&style=for-the-badge)](https://bstats.org/plugin/bungeecord/HAProxyDetector/12605) [![](https://img.shields.io/bstats/servers/14442?label=Velocity%20Servers&style=for-the-badge)](https://bstats.org/plugin/velocity/HAProxyDetector/14442)

这是一个适用于 [BungeeCord](https://github.com/SpigotMC/BungeeCord/)、[Spigot](https://www.spigotmc.org/wiki/spigot/) 与 [Velocity](https://velocitypowered.com/) 的插件，允许在同一端口同时接受直连与通过 HAProxy 转发的代理连接。关于 [HAProxy](https://www.haproxy.org/) 的用途与说明，可参阅 [此处的文档](https://github.com/MinelinkNetwork/BungeeProxy/blob/master/README.md)。

## 安全警告

同时允许直连与代理连接存在显著的安全风险：恶意玩家可以自行搭建 HAProxy 并伪造 PROXY 协议头，从而让服务器误以为其来源于虚假 IP。

为降低风险，本插件实现了“代理白名单”。**默认仅允许来自 `localhost` 的代理连接**（直连不受影响）。你可以在插件数据目录中的 `whitelist.conf` 文件里，添加受信任的 HAProxy 实例的 IP/域名或网段。

<details>
    <summary>白名单格式详情</summary>

```
# 允许的代理 IP 列表
#
# 空白名单将拒绝所有代理。
# 每一行必须是有效的 IP 地址、域名或 CIDR。
# 域名仅在启动时解析一次。
# 单个域名可解析出多个 A/AAAA 记录，均会被允许。
# 域名不支持附带 CIDR 前缀。

127.0.0.0/8
::1/128
```

如需禁用白名单（极不建议），请将下列整行内容置于第一行：

```
YesIReallyWantToDisableWhitelistItsExtremelyDangerousButIKnowWhatIAmDoing!!!
```

</details>

## 各平台注意事项

#### BungeeCord

- 需要在 BungeeCord 的 `config.yml` 中开启 `proxy_protocol`（不要与 `paper.yml` 中的同名选项混淆）。
- 较老版本的 BungeeCord 理论上可与 [BungeeProxy](https://github.com/MinelinkNetwork/BungeeProxy) 并行使用，但尚未充分测试，欢迎反馈。

#### Spigot 及其分支

- 需要安装并启用 [ProtocolLib](https://github.com/dmulloy2/ProtocolLib)。
- 本插件以 ProtocolLib v4.8.0 为基线开发；若出现错误，请优先尝试该版本。关于 ProtocolLib 5.0 的实验性支持，参见 [issue #3](https://github.com/andylizi/haproxy-detector/issues/3)。

#### Paper

- 新版本 Paper 自带 HAProxy 支持（仅代理连接）。与本插件不兼容，请在 `paper.yml` 关闭 `proxy-protocol`。

#### Velocity

- 需要在 Velocity 配置中启用 `haproxy-protocol`。
- 仅支持 3.0 及以上版本。

## Java 版本提示

#### Java ≥ 9

若遇到 `NoClassDefFoundError: sun.misc.Unsafe`、`InaccessibleObjectException` 等问题，请在 JVM 启动参数中添加：

```
--add-opens java.base/java.lang.invoke=ALL-UNNAMED
```

#### Java ≥ 18

若出现 `IllegalAccessException: static final field has no write access`，请将插件升级到 v3.0.2 及以上版本。

如暂时无法升级，可临时添加以下 JVM 参数（未来的 Java 版本可能会移除该参数）：

```
-Djdk.reflect.useDirectMethodHandle=false
```

## 统计信息（bStats）

本插件使用 [bStats](https://bStats.org) 统计匿名使用数据（如服务器数量、玩家总数等）。你可以随时在 `plugins/bStats/` 目录下修改配置以选择退出。

## 许可证与致谢

- 原作者：Andy Li（GitHub：[@andylizi](https://github.com/andylizi)）
- 源仓库：[`andylizi/haproxy-detector`](https://github.com/andylizi/haproxy-detector)
- 许可证：GNU Lesser General Public License v3 或更高版本。详见本仓库中的 `LICENSE` 文件。

