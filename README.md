# MyCraftDailyShop

适用于 Paper 1.12.2 的每日随机商店插件。
插件版本为 `1.1.5`。

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

成品位于 `build/libs/MyCraftDailyShop-1.1.5.jar`。

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

刷新类型支持 `timely`、`daily`、`weekly:1,3,5` 和 `monthly:1,15`。`timely` 的 `time` 是 `H:mm:ss` 格式的刷新间隔，例如 `2:00:00` 表示每两小时刷新一次；周期以配置时区中的 `1970-01-01 00:00:00` 为固定起点，不会因服务器重启而重新计时。

## 可用占位符

```
%mcds_next_refresh_time_<商店ID>%  # 显示指定商店的下次刷新时间（输出格式由 config.yml 中 placeholder.next-refresh-time-format 控制）
%mcds_next_refresh_remaining_<商店ID>%  # 显示剩余时间
%mcds_next_refresh_timestamp_<商店ID>%  # 获取下次刷新时间的 Unix 秒级时间戳
```

## 管理命令

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

## 致谢

#### AI辅助
ChatGPT-5.6 Sol
Codex
#### 我的手艺测试组成员
bilibiliHMP
Hermois
licha
qingye
Xtlylg
