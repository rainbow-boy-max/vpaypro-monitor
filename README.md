# V支付监控端（vpaypro Monitor）

> V支付 多商户版的 Android 收款监听端。监听微信 / 支付宝到账通知，把金额实时回调给 V支付 服务端。

---

## 开源协议与署名（Attribution）

本项目是**二次开发（Derivative Work）**，遵循上游协议，特此致谢：

| 角色 | 项目 | 协议 | 说明 |
|---|---|---|---|
| **基线项目** | [shinian-a/Vmq-App](https://github.com/shinian-a/Vmq-App) | **Apache-2.0** | 保活体系与通知解析规则的主要来源 |
| **原始项目** | [szvone/vmqApk](https://github.com/szvone/vmqApk) | **MIT** | V免签监控端原始实现 |
| **定制方** | 秒囤兔（rainbow-boy-max） | Apache-2.0 | 多商户适配、https 支持、健康上报 |

- 本项目整体以 **Apache License 2.0** 发布（见 [LICENSE](LICENSE)）。
- 源自 `shinian-a/Vmq-App`（Apache-2.0）的代码部分，保留其版权与协议声明（见 [NOTICE](NOTICE)）。
- 源自 `szvone/vmqApk`（MIT）的代码部分，保留原作者署名（见 [LICENSE-MIT](LICENSE-MIT)）。

---

## 相对基线的改动

### 1. 多商户适配（V支付 专属）
- 包名 `com.vpaypro.monitor`，与 V免签监控端**可同机共存**
- 配置串支持完整 URL（`https://域名/通道密钥`）

### 2. HTTPS 支持（关键）
基线硬编码 `http://`。V支付 商户域名通常只开放 443，明文请求会直接失败。
现已改为：

```java
AppConstants.baseUrl(host)
// 配置带 http:// 或 https:// → 原样使用
// 否则默认补 https://
```

### 3. 监听健康上报（配合平台总控台）
每次心跳额外上报（**不参与签名**，旧协议兼容）：

| 参数 | 含义 |
|---|---|
| `device_id` | 设备指纹（ANDROID_ID 的 SHA-256 前 16 位） |
| `device_name` | 厂商 + 型号 |
| `app_version` / `android_ver` | 版本信息 |
| `listener_ok` | 监听服务是否已连接（**区分"进程活着"与"监听正常"**） |
| `notif_access` | 通知使用权是否开启 |
| `battery_opt` | 是否已加入电池白名单 |
| `boot_ts` | 进程启动时间（服务端据此识别重启） |
| `last_notify_ts` | 最近一次收到任意通知的时间 |

> 这是"商户说没到账"排障的关键：进程活着但 `listener_ok=0` 时，条幅一条都收不到。

### 4. 保活体系（继承基线）
前台服务 / 双进程守护 / JobScheduler / 账号同步 / 1 像素 Activity / 开机自启。

### 5. 解析规则（继承基线）
微信 6 个标题 + 企业微信；支付宝普通收款 / 店员通分离；金额关键词定位 + 正则 + 范围校验。

---

## 使用方法

1. 在 V支付 商户控制台绑定域名，生成**配置二维码**（`域名/通道密钥`）
2. 安装本 APP → 扫码配置
3. 开启**通知使用权** → **应用权限** → **一键保活**（电池白名单 / 自启动）
4. 回到控制台确认通道「在线」
5. 用唯一金额（如 `0.13`）真实收款测试

---

## 构建

GitHub Actions（JDK 17 + AGP 8.12 + Gradle 8.13），产物 `app-release.apk`。

签名密钥通过 Secrets 注入：`ANDROID_KEYSTORE_B64` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`，保证多次构建**签名一致**，升级无需卸载。

---

## 免责声明

仅供学习与技术研究。请遵守当地法律法规及微信 / 支付宝服务协议，使用风险自负。

**License: Apache-2.0**
