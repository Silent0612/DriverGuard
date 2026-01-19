# Flutter + MediaPipe + OpenCV Mobile 防疲劳驾驶APP 技术框架文档

## 一、 核心开发背景与约束

### 1.1 项目目标
构建一款支持**全天候、低功耗、高隐私**的手机端防疲劳驾驶APP。核心在于解决传统视觉检测的高功耗问题，并通过多传感器融合提高检测准确率，支持在应用退至后台时仍能持续监测。

### 1.3 核心难点与应对策略 (关键)
*   **Android后台摄像头限制**: Android 9 (API 28) 及以上系统严禁后台 Service 访问摄像头。
    *   **策略**: 引入 **悬浮窗 (Floating Window)** 或 **画中画 (PiP)** 模式。当 APP 退后台时，自动开启 1x1 像素或预览小窗，保持 `Top-Visible` 状态以绕过系统限制，确保视觉算法持续运行。
*   **多版本适配**: 适配 Android 14 的前台服务类型 (`camera`, `location`) 强制声明。

---

## 二、 整体架构分层设计

采用 **Flutter UI + Native Service + Plugin Architecture** 的架构，增强扩展性。

```mermaid
graph TD
    subgraph "Flutter UI Layer"
        UI[主检测页]
        PiP[画中画/悬浮窗控制器]
    end

    subgraph "Native Core Layer (Android)"
        Service[Foreground Service (业务宿主)]
        Lifecycle[生命周期与保活管理器]
        
        subgraph "Abstract Interfaces (扩展层)"
            ISensor[ISensorSource 接口]
            IAlgo[IDetectionAlgo 接口]
            IAction[IWarningAction 接口]
        end
        
        subgraph "Implementations (实现层)"
            Cam[CameraXImpl] --> IAlgo
            GPS[GPSImpl] --> ISensor
            Acc[AccelerometerImpl] --> ISensor
            
            MP[MediaPipeEngine] --> IAlgo
            Fusion[FusionStrategy]
            
            TTS[TTSAction] --> IAction
        end
    end

    UI <--> MethodChannel
    MethodChannel <--> Service
    IAlgo --> Fusion
    ISensor --> Fusion
    Fusion --> IAction
```

### 层级职责与扩展设计：
1.  **接口抽象层 (Interfaces)**: 
    *   定义 `ISensorSource`：未来接入**智能手表心率**或**OBD车辆数据**时，只需实现此接口，无需修改核心逻辑。
    *   定义 `IWarningAction`：未来接入**车载蓝牙报警**或**发送云端短信**时，只需新增实现类。
2.  **生命周期管理器**: 专门处理 Android 各版本的保活策略（如 Android 14 需要特定的 `ServiceType` 声明）。

---

## 三、 Flutter端页面结构与功能

由于无登录页，APP 启动即进入主功能区。

### 3.1 页面清单

| 页面名称 | 英文类名 | 核心功能 | 关键组件 |
| :--- | :--- | :--- | :--- |
| **主检测页** | `DetectionPage` | 摄像头预览、实时状态显示、启停控制 | `Texture`(预览), `CustomPaint`(人脸网格绘制), `FAB`(启停), `StatusCard`(疲劳值) |
| **设置页** | `SettingsPage` | 灵敏度调节、报警方式、隐私设置 | `Slider`(阈值), `Switch`(震动/语音), `PermissionWidget` |
| **统计页** | `HistoryPage` | 查看本地历史记录 (可选) | `ListView`, `Charts` (基于本地SQLite) |

### 3.2 详细设计：主检测页 (`DetectionPage`)
*   **布局**: `Stack` 布局。底层为摄像头预览，上层覆盖仪表盘数据。
*   **摄像头预览**: 使用 `Texture` 控件显示 Native 传递的纹理 ID (如果需要预览)，或者直接使用 NativeView。推荐 `Texture` 方案以获得更好的 Flutter 组件层级控制。
*   **交互**:
    *   **模式切换**: 顶部 Tab 切换 "省电模式" / "高性能模式"。
    *   **实时反馈**: 屏幕边缘呼吸灯效果（绿->黄->红），根据 `fatigue_level` 变化。

### 3.3 详细设计：设置页 (`SettingsPage`)
此页面对用户开放核心算法参数与个性化设置，支持即时生效。

*   **功能模块**:
    1.  **基础设置**:
        *   **多语言切换**: 支持 "简体中文" / "English" (预留)。使用 `flutter_localizations` 实现。
        *   **报警方式**: 震动开关、语音播报开关、系统通知音量。
    2.  **检测灵敏度 (高级参数)**:
        *   **EAR 闭眼阈值**: 滑块调节 (0.15 - 0.30, 默认 0.20)。数值越低越难触发。
        *   **闭眼时长触发**: 滑块调节 (1.0s - 3.0s)。
        *   **打哈欠灵敏度**: MAR 阈值调节。
    3.  **调试信息**: 开关 "显示实时特征点"，用于在预览画面上绘制 468 个 Face Mesh 点（仅高性能模式可用）。

---

## 四、 核心功能模块详细设计

### 4.1 摄像头采集模块 (Native -> Flutter)
*   **职责**: 在 Native 层通过 `CameraX` 获取数据流，一路送算法分析，一路（可选）渲染到 SurfaceTexture 供 Flutter 预览。
*   **后台适配**: 当 APP 切后台时，停止向 Flutter 发送预览数据，但**保持 CameraX 分析用例 (ImageAnalysis) 运行**。

### 4.2 跨平台通信模块 (MethodChannel)
定义 Channel 名称: `com.example.fatigue/detection`

#### 方法定义 (Flutter -> Native)
| 方法名 | 参数 (Map) | 作用 |
| :--- | :--- | :--- |
| `startDetection` | `{ "mode": 1, "camera": "front" }` | 启动前台服务和摄像头 |
| `stopDetection` | `{}` | 停止服务，释放资源 |
| `updateConfig` | `{ "earThreshold": 0.2, "enableAudio": true }` | 运行时更新配置 |
| `getHistory` | `{ "date": "2023-10-27" }` | 获取本地历史数据 |

#### 事件定义 (Native -> Flutter, EventChannel)
定义 EventChannel 名称: `com.example.fatigue/status`
*   **返回格式**: JSON String
    ```json
    {
      "timestamp": 1698300000,
      "faceDetected": true,
      "ear": 0.25,        // Eye Aspect Ratio
      "mar": 0.10,        // Mouth Aspect Ratio
      "fatigueLevel": 1,  // 0:正常, 1:轻微, 2:严重
      "fps": 15
    }
    ```

### 4.3 原生视觉检测模块 (Android)
*   **输入**: `ImageProxy` (YUV_420_888)。
*   **预处理**:
    1.  **YUV转RGB**: 使用 OpenCV `cvtColor`。
    2.  **CLAHE (对比度受限自适应直方图均衡化)**: 增强夜间/逆光下的人脸特征，这是**OpenCV Mobile** 的核心作用。
*   **MediaPipe推理**:
    *   输入: 处理后的 Bitmap/Mat。
    *   输出: 468个关键点坐标。
*   **特征计算**:
    *   **EAR (眼睛纵横比)**: `(|p2-p6| + |p3-p5|) / (2 * |p1-p4|)`
    *   **MAR (嘴巴纵横比)**: 类似公式计算张嘴程度。

### 4.4 传感器与多模态融合模块 (Native)
*   **GPS**: 获取 `speed` (m/s)。当 `speed < 5km/h` 持续 5分钟，自动切换至**低功耗模式**（暂停视觉检测或降低帧率）。
*   **加速度计**: 监测 Z 轴剧烈抖动（颠簸路段）。
*   **融合算法 (Fusion Engine)**:
    *   **逻辑**: 
        ```kotlin
        // 伪代码
        val isFatigue = if (speed > 60) {
            // 高速模式：仅依赖视觉，阈值收紧
            ear < 0.20 && duration > 1.5s
        } else {
            // 低速/城市：结合头部姿态
            (ear < 0.20 && duration > 2.0s) || (headNodCount > 3)
        }
        ```

### 4.5 功耗调度模块 (Adaptive Scheduler)
这是本框架的核心创新点。

| 模式 | 触发条件 | 帧率 (FPS) | 检测频率 | 算法精度 |
| :--- | :--- | :--- | :--- | :--- |
| **高性能** | 速度 > 80km/h 或 判定为疲劳前兆 | 30 | 实时 | Full Mesh |
| **平衡** | 20km/h < 速度 < 80km/h | 15 | 每隔1帧检测 | 仅眼部关键点 |
| **低功耗** | 速度 < 20km/h 或 静止 | 1-5 | 5秒一次 | 仅检测人脸存在 |

---

## 五、 数据流转完整流程

### 5.1 前台检测模式
1.  **CameraX** 产生数据帧 (30fps)。
2.  **Native**: OpenCV 进行直方图均衡化。
3.  **Native**: MediaPipe 提取关键点。
4.  **Native**: FusionEngine 计算疲劳值。
5.  **Native**: 通过 `EventChannel` 将 `{ear, mar, status}` 发送给 Flutter。
6.  **Flutter**: `CustomPaint` 根据关键点绘制面具，更新仪表盘数值。

### 5.2 后台检测模式 (核心差异)
1.  **Flutter UI**: `Paused` / `Detached` 状态，停止渲染。
2.  **Native**: Foreground Service 持续运行，显示通知栏 "正在守护您的驾驶安全"。
3.  **Native**: CameraX 继续产出数据，但**断开**与 Flutter Texture 的连接（节省 GPU）。
4.  **Native**: FusionEngine 继续计算。
5.  **触发预警**: 若检测到疲劳，Native 直接调用 `TextToSpeech` (语音播报: "请注意休息")。**无需唤醒 Flutter UI**。
6.  **存储**: 将本次事件写入 SQLite。

---

## 六、 接口定义规范 (Interface Definition Language - IDL)

### 6.1 Native 提供的能力 (Kotlin Interface)

```kotlin
interface IDriverGuardService {
    // 核心控制
    fun startMonitoring(config: MonitorConfig)
    fun stopMonitoring()
    
    // 动态调整
    fun updateSensitivity(level: Int) // 1-10
    
    // 获取瞬时状态（供UI轮询或调试）
    fun getCurrentStatus(): DriverStatus
}

data class MonitorConfig(
    val useFrontCamera: Boolean = true,
    val maxFrameRate: Int = 30,
    val enableGps: Boolean = true
)
```

### 6.2 数据库模型 (SQLite)

Table: `fatigue_events`

| 字段名 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | INTEGER | PK |
| `timestamp` | LONG | 事件发生时间戳 |
| `event_type` | INT | 1:闭眼, 2:哈欠, 3:低头 |
| `value` | REAL | 触发时的 EAR/MAR 值 |
| `speed` | REAL | 触发时车速 |
| `location` | TEXT | 经纬度 (加密存储) |

---

## 七、 差异化创新点与实现细节

### 7.1 隐私优先的 "无图" 检测
*   **设计**: APP 内部**绝不**保存原始图像帧。MediaPipe 提取完 468 个坐标点后，原始 Bitmap 立即在内存中销毁 (`bitmap.recycle()`)。
*   **存储**: 数据库仅存储坐标点特征或 EAR 数值，无法还原人脸图像。

### 7.2 动态 ROI (感兴趣区域) 裁剪
*   **优化**: 为了在低端机上跑满 30fps。
*   **逻辑**: 第一帧全图检测人脸。后续帧根据上一帧的人脸矩形框 `Rect`，向外 20% 扩充作为 ROI 区域进行裁剪，只对该区域做推理。大幅降低计算量。

### 7.3 光照鲁棒性 (OpenCV Mobile)
*   **场景**: 进出隧道、夜间行车。
*   **实现**: 在送入 MediaPipe 前，对图像 Y 通道进行 `CLAHE` 处理。这比单纯的提高亮度更能保留闭眼细节。

---

## 八、 Android版本适配与扩展规划

### 8.1 Android 版本兼容性矩阵
| Android 版本 | API Level | 关键限制 | 适配方案 |
| :--- | :--- | :--- | :--- |
| **Android 9 (Pie)** | 28 | 禁止后台 Service 访问相机 | 引入**悬浮窗 (Floating Window)** 权限，APP 退后台时自动开启 1x1 像素预览悬浮窗。 |
| **Android 10 (Q)** | 29 | 限制后台启动 Activity | 使用 `PendingIntent` 发送全屏通知 (Full-screen Intent) 进行预警。 |
| **Android 10 (Q)** | 29 | **分区存储 & 后台定位** | 1. 必须在 Service 中声明 `foregroundServiceType="location"` 以在后台获取 GPS。<br>2. 仅使用应用私有目录 (`Context.getExternalFilesDir`) 存储数据以规避分区存储限制。 |
| **Android 12 (S)** | 31 | 更加严格的通知权限 | 动态申请 `POST_NOTIFICATIONS` 权限。 |
| **Android 14 (U)** | 34 | 前台服务类型强制声明 | Manifest 中必须声明 `<service android:foregroundServiceType="camera|location|microphone" />`。 |

### 8.2 预留扩展接口 (Extension Points)

为了支持未来接入智能手表或车载硬件，原生层预留以下接口：

#### ISensorSource (通用传感器接口)
```kotlin
interface ISensorSource {
    fun getDataType(): SensorType // HEART_RATE, OBD_SPEED, etc.
    fun startSample(callback: (Data) -> Unit)
    fun stopSample()
}
```

#### IWarningAction (通用预警动作)
```kotlin
interface IWarningAction {
    fun trigger(level: FatigueLevel)
    fun cancel()
}
// 实现示例: BluetoothWarningAction (发送指令给车机)
```
