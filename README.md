# MyCraftDailyShop

适用于 Paper 1.12.2 的每日随机商店插件。插件版本为 `1.1.2`。

## 依赖

- Zaphkiel 2.0.24
- Vault 与一个 Vault 兼容经济插件
- PlaceholderAPI（可选；安装后解析配置中的 PAPI 占位符）

## 构建

项目使用 Java 8 字节码。Windows 环境可以执行：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-1.8'
.\gradlew.bat clean test shadowJar
```

成品位于 `build/libs/MyCraftDailyShop-1.1.2.jar`。

## 配置

首次启动生成：

```text
plugins/MyCraftDailyShop/
├─ config.yml
├─ message.yml
├─ database.db
└─ shop/
   └─ default.yml
```

`shop` 目录中每份 YAML 可以定义多个商店。商店 ID 是顶层节点名，与文件名无关；不同文件不能定义相同 ID。

商品使用 `物品库:物品ID` 格式，例如 `zaphkiel:镇好剑`。商店中的 `sell` 表示商店向玩家出售，`buy` 表示商店向玩家收购。

## 管理命令

插件短命令为 `/mcds`（MyCraftDailyShop）。完整命令 `/mycraftdailyshop` 同样可用，旧短命令 `/mds` 已移除。

```text
/mcds open <玩家> <商店>
/mcds show <玩家> <商店>
/mcds quota reset player <玩家|*> <商店|*>
/mcds quota reset server <商店|*>
/mcds refresh <商店|*> [--player <玩家>]
/mcds reload
/mcds validate
```

普通玩家没有默认可用命令。详细权限节点见 `plugin.yml`。
