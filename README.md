# DriverGuard - 智能疲劳驾驶检测系统

DriverGuard 是一款基于端侧 AI 技术的高性能车载安全应用，旨在通过实时监测驾驶员状态，有效预防因疲劳、分心引发的交通事故。项目采用 Flutter + Android Native 混合开发架构，集成了 Google MediaPipe 视觉框架与多模态传感器融合算法。

## 核心特性 (Key Features)

### 1. 👁️ 高精度视觉监测
*   **MediaPipe 人脸网格**：利用 468 个面部特征点，实时计算 EAR (眼睑闭合度)、PERCLOS (闭眼百分比) 及头部姿态 (Pitch/Yaw)。
*   **抗干扰算法**：
    *   **夜间增强**：自适应低光照环境，动态调整检测阈值。
    *   **眼镜优化**：独创的单眼信度优先算法，有效解决眼镜反光导致的误报问题。

### 2. 🧠 多模态融合引擎 (Fusion Engine)
*   **状态判定**：综合分析视觉数据、车辆速度 (GPS) 及心率数据（支持智能手表接入）。
*   **防误触机制**：集成加速度计 (Accelerometer) 数据，自动识别车辆颠簸状态，抑制因手机晃动产生的误报。

### 3. ⚡ 智能功耗调度
*   **自适应频率**：
    *   **高速模式 (>80km/h)**：全帧率 (30FPS) 监测，确保毫秒级响应。
    *   **堵车/停车模式 (<5km/h)**：自动降频至 2FPS，大幅降低设备发热与功耗。

### 4. 📊 全面的数据洞察
*   **实时可视化**：提供疲劳趋势图表，直观展示驾驶状态变化。
*   **历史回溯**：本地 SQLite 数据库记录所有危险事件，支持按时间线回放。

## 技术栈 (Tech Stack)

*   **Frontend**: Flutter (Dart)
*   **Native Core**: Android (Kotlin)
*   **AI Inference**: Google MediaPipe (Face Landmarker), OpenCV Mobile
*   **Database**: SQLite (sqflite)
*   **Sensors**: CameraX, GPS (Location Services), Accelerometer, Bluetooth Low Energy (BLE)

## 安装说明 (Installation)

1.  下载最新的 [Release APK](https://github.com/Silent0612/DriverGuard/releases) (推荐 `DriverGuard_Final.apk`)。
2.  安装并授予必要的权限（相机、定位、后台运行）。
3.  点击主界面的“开始检测”即可启动守护。

## 开发环境 (Development)

*   Flutter SDK: 3.10.0+
*   Android SDK: API 26+ (Recommend API 33)
*   Java: JDK 17

```bash
# Clone repository
git clone git@github.com:Silent0612/DriverGuard.git

# Install dependencies
flutter pub get

# Run on device
flutter run
```

## 许可证 (License)

MIT License
