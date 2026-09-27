# 服务器商店（Server Shop）

> 一个面向 **NeoForge 1.21.1 / Arclight 服务端** 的完整经济系统模组：每日商店、每日收购、拍卖行、玩家摊位、
> 玩家交易与转账、邮箱、管理员发放、交易流水、同 IP 多账号管控。界面全部为模组自带 GUI，数值服务端权威。

---

## 功能一览

| 模块 | 说明 |
| --- | --- |
| **每日商店** | 卡牌翻牌查看商品；3 列 × 2 行；每件商品独立限购；普通档每日最多 3 个；可用金币**付费刷新**（默认第 1 次 200、第 2 次 500，每日 2 次，价格与次数可配） |
| **每日收购** | 每日随机最多 6 项收购需求；按物品显示收购价与剩余可收数量；**当日清单全部卖满额外奖励 1000 金币**（可配） |
| **拍卖行** | 上架拍卖、**出价即从余额扣款**、**被超价自动全额退回**（余额装不下时自动转金额邮件）、到期自动结算（买家收物品邮件、卖家收货款、按比例手续费）、无人出价原物退回 |
| **玩家摊位** | 上架 / 改价 / 下架 / 浏览购买，支持库存与售出统计；可配置上架手续费 |
| **玩家交易** | 双方放入物品与金币 → 双方同意 → 倒计时 → 双方确认后原子交换；闲置 10 分钟自动结束并按"背包 → 邮箱 → 掉落"退回 |
| **钱包** | 余额查询、玩家间转账（同 IP 拦截、冷却、余额上限饱和处理，不溢出不变负） |
| **邮箱** | 物品邮件与金额邮件；一键领取 / 清理已读；背包满自动转邮箱；默认上限 100 封、每页 45 封可翻页 |
| **管理员发放** | `/shop admin money` 发金币（全体 / 玩家名 / UUID / `@a`），`/shop admin send` 打开图形发放界面：放入物品 + 勾选玩家或全体 + 可选金额 → 发送，玩家在邮箱领取 |
| **交易流水** | 所有资产变动都记流水（买卖 / 摊位 / 拍卖 / 转账 / 交易 / 邮箱 / 管理员发放 / 刷新商店 / 全清奖励），管理员用 `/shop admin history <玩家>` 翻页查看 |
| **同 IP 多账号管控** | 一个 IP 只允许一个账号登录（管理员默认豁免），支持 `/shop admin ip list`、`/shop admin ip unbind` |

## 安装

1. 服务端：把 `server_shop_mod-1.13.0.jar` 放进 `mods/`，重启。
2. 客户端：**玩家也必须安装同一个 jar**（商店界面是客户端代码）。
3. 客户端与服务端都启动后执行 `/shop` 打开界面；管理员用 `/shop admin` 打开设置页。

> 版本要求：Minecraft 1.21.1 / NeoForge 21.1.x（已在 Arclight 1.21.1 混合服务端实测）。

## 构建

```bash
./gradlew build          # Windows: gradlew.bat build
# 产物：build/libs/server_shop_mod-1.13.0.jar
```

需要 JDK 21。

## 常用指令

```text
/shop                              打开商店
/shop mailbox | /shop stall | /shop trade open

/shop admin                        商店设置页（2 级权限）
/shop admin reload                 热重载商品池并同步所有在线玩家
/shop admin refresh <玩家>          刷新某玩家的每日商店与收购
/shop admin money <金额> [目标...]  发金币（不填目标 = 所有已知玩家，含离线）
/shop admin send                   打开发放界面（物品 + 金币，邮箱领取）
/shop admin history <玩家> [页码]   查看交易流水
/shop admin ip list | unbind <玩家|IP>
/shop wallet balance | pay <玩家> <金额> | give <玩家> <金额>
```

## 配置

配置文件：`config/server_shop_mod_1789689358-common.toml`（首次启动自动生成，注释为中文）。
常用项：

```toml
[wallet]        currencyName = "金币"      maxBalance = 999999999
[daily_shop]    dailyShopSlots = 6         dailyShopCommonLimit = 3
                dailyRefreshCosts = ["200", "500"]
[daily_buyback] buybackSlots = 6           buybackAllBonus = 1000
[mailbox]       maxMailboxSize = 100       transactionHistorySize = 50
[trade]         tradeRequestExpireSeconds = 60   tradeSessionTimeoutSeconds = 600
[anti_alt]      ipAccountLimit = 1         ipLimitExemptOps = true
```

## 文档

`docs/` 目录下是中文说明：

sed -i -e 's#docs/发放功能说明.md#docs/grant-feature.md#' \
       -e 's#docs/第二轮需求实现说明.md#docs/round2.md#' \
       -e 's#docs/第三轮需求实现说明.md#docs/round3.md#' \
       -e 's#docs/代码审查报告.md#docs/code-review.md#' README.md

## 许可

本项目使用 [MIT 许可证](LICENSE)。

> 本项目基于 NeoForge MDK 模板开发；`TEMPLATE_LICENSE.txt` 为上游模板自带说明。
> 模组基本由Deepseek开发，此mod可任意开发
