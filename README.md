# 记不住

一个安卓待办提醒 App。纯 Java 手写 UI（除 `AndroidManifest.xml` 外没有任何布局 XML），无第三方依赖。

## 功能

- **时间提醒**：像闹钟一样按时刻提醒，重复周期为单次 / 每天
- **地点提醒**：到达腾讯地图选定的地点（含半径、时间段限制）时提醒
- **打卡**：连上或断开指定 WiFi 时打开 WeLink 打卡页（锁屏时先弹提醒页）
- **计划**：分阶段目标，按流程图形式串联
- 深色 / 浅色主题、本地 mp3 铃声、桌面组件、回收站

## 构建

```bash
cd app && sh build.sh
# 产物：app/build/app-signed.apk
```

依赖 termux 提供的 `aapt2` / `javac` / `d8` / `apksigner`，签名用 `app/build/ksig`（不纳入版本库）。

## 安装

```bash
cp app/build/app-signed.apk /sdcard/jbz.apk
# 再以 shell 身份（uid 2000）执行：
cp /sdcard/jbz.apk /data/local/tmp/ && pm install -r -d /data/local/tmp/jbz.apk
```

屏幕熄灭时安装会报 `INSTALL_FAILED_ABORTED: User rejected permissions`，先点亮解锁。

## 结构

```
app/
├── AndroidManifest.xml
├── build.sh                 # aapt2 → javac → d8 → zipalign → apksigner
├── src/com/jibuzhu/app/
│   ├── MainActivity.java    # 待办/计划列表、设置抽屉
│   ├── TodoEditActivity.java / PlanEditActivity.java / PlacePickerActivity.java
│   ├── RingActivity.java / RingUi.java / RingOverlay.java   # 提醒页
│   ├── TodoWidget.java / WidgetService.java                  # 桌面组件
│   ├── SettingsPanel.java / TrashActivity.java
│   ├── Theme.java / Ui.java / SwipeRow.java / TimeSpinner.java
│   ├── Checkin.java / TencentMap.java / Util.java
│   ├── data/                # Store（SQLite）、Todo、Plan、Stage
│   └── trigger/             # AlarmScheduler、AlarmReceiver、RingService、GeoService、WifiWatchService
└── res/
```

数据库为 `jibuzhu.db`，`Store.java` 里用 `VER` 管版本，升级走 `onUpgrade`、回退走 `onDowngrade`（保留数据）。
