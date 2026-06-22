# 打卡提醒测试矩阵

## 必跑自动测试

- `.\gradlew.bat testReleaseUnitTest`
- `.\gradlew.bat assembleRelease`

## 必查真机状态

- 安装版本：`dumpsys package com.kanon.dingpunchguard` 必须显示目标 `versionName`。
- 使用情况访问：`GET_USAGE_STATS: allow`，否则不能启用前台兜底记录。
- 后台弹出相关：`SYSTEM_ALERT_WINDOW`、HyperOS 私有后台弹出/后台启动 appop 需放行。
- 覆盖安装后需重新核对后台弹出相关 appop；HyperOS 可能把私有开关重置为 `ignore`。
- 日志：`/sdcard/Android/data/com.kanon.dingpunchguard/files/guard-events.log`。

## 上班窗口

- 锁屏未解锁：即使到点、在范围内，也只能提醒，不能尝试打开钉钉。
- 亮屏但未解锁：收到 `SCREEN_ON` 时不能打开钉钉。
- 已解锁：当前状态为 `interactive=true` 且 `keyguardLocked=false` 时，如果在范围内，应尝试打开钉钉；不能强依赖最近是否收到 `USER_PRESENT`。
- 解锁广播：收到 `USER_PRESENT` 后必须立即评估并短时高频刷新定位，但它只是触发信号，不是自动打开的必要条件。
- 旧定位：定位年龄超过 120 秒时，即使坐标看起来在范围内，也不能打开钉钉，必须先刷新定位。
- 打开失败：必须记录前台验证失败，不能记录打卡完成。
- 钉钉进入前台：只有 `latestForeground=com.alibaba.android.rimet` 后才能触发前台兜底记录。

## 下班窗口

- 未解锁：到点只能提醒，不能尝试打开钉钉。
- 已解锁：当前状态为 `interactive=true` 且 `keyguardLocked=false` 时才能尝试打开钉钉。
- 解锁广播：收到 `USER_PRESENT` 后必须立即评估，但不能把没有收到广播当作“仍未解锁”。
- 下班要求范围时：定位年龄超过 120 秒不能作为范围确认依据。
- 打开失败：短重试窗口固定约 30 秒，不能无限续命。
- 钉钉进入前台：才能按“钉钉进入前台兜底”记录下班。

## 禁止通过的情况

- 本应用自己的通知被识别成钉钉成功通知。
- `startActivity()` 发出后但钉钉未进入前台，却记录成功。
- 屏幕未交互或 `keyguardLocked=true` 时尝试打开钉钉。
