import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:permission_handler/permission_handler.dart';
import '../services/config_service.dart';
import '../services/power_manager.dart';
import '../utils/database_helper.dart';
import 'settings_page.dart';
import 'history_page.dart';

class DetectionPage extends StatefulWidget {
  const DetectionPage({super.key});

  @override
  State<DetectionPage> createState() => _DetectionPageState();
}

class _DetectionPageState extends State<DetectionPage> {
  static const platform = MethodChannel('com.example.fatigue/detection');
  static const eventChannel = EventChannel('com.example.fatigue/status');

  String _status = "点击下方按钮开启守护";
  double _currentEar = 0.0;
  double _currentPerclos = 0.0;
  int _currentFps = 0;
  double _currentSpeed = 0.0;
  List<Offset> _landmarks = [];
  bool _isFatigue = false;
  int? _textureId;
  StreamSubscription? _subscription;
  bool _isRunning = false;

  @override
  void initState() {
    super.initState();
    // Auto-start check if needed (optional)
  }

  @override
  void dispose() {
    _subscription?.cancel();
    super.dispose();
  }

  Future<void> _startService() async {
    Map<Permission, PermissionStatus> statuses = await [
      Permission.camera,
      Permission.notification,
      Permission.location,
      Permission.systemAlertWindow, // Request Overlay permission
    ].request();

    if (statuses[Permission.camera]!.isGranted &&
        statuses[Permission.notification]!.isGranted) {
      if (statuses[Permission.systemAlertWindow] != PermissionStatus.granted) {
        // Optional: Show dialog explaining why we need overlay
        // For now, proceed but warn or just rely on foreground service (which might fail on Android 14 without overlay)
      }
      try {
        // 1. Create Preview Texture
        final int? textureId = await platform.invokeMethod('createPreview');

        // 2. Start Service
        await platform.invokeMethod('startDetection');

        // 3. Listen to Event Stream
        _subscription?.cancel();
        _subscription = eventChannel.receiveBroadcastStream().listen((event) {
          final Map<dynamic, dynamic> data = event;
          if (mounted) {
            final landmarksData =
                (data['landmarks'] as List<dynamic>?)?.cast<double>() ?? [];
            List<Offset> points = [];
            for (int i = 0; i < landmarksData.length; i += 2) {
              if (i + 1 < landmarksData.length) {
                points.add(Offset(landmarksData[i], landmarksData[i + 1]));
              }
            }

            final bool newFatigueState = data['fatigue'] as bool;
            // Record event on rising edge
            if (newFatigueState && !_isFatigue) {
              print("DetectionPage: Fatigue Detected! Recording event...");
              _recordFatigueEvent(data);
            }

            setState(() {
              _currentEar = (data['ear'] as num).toDouble();
              _currentPerclos = (data['perclos'] as num).toDouble();
              _currentFps = (data['fps'] as num).toInt();
              _isFatigue = data['fatigue'] as bool;
              _landmarks = points;
            });
          }
        });

        setState(() {
          _textureId = textureId;
          _status = "监测中";
          _isRunning = true;
        });
      } on PlatformException catch (e) {
        setState(() => _status = "错误: ${e.message}");
      }
    } else {
      setState(() => _status = "缺少必要权限");
    }
  }

  Future<void> _stopService() async {
    try {
      await platform.invokeMethod('stopDetection');
      _subscription?.cancel();
      setState(() {
        _status = "监测已停止";
        _isRunning = false;
        // Keep textureId for a moment or clear it? Clearing it hides the view.
        // _textureId = null;
      });
    } on PlatformException catch (e) {
      setState(() => _status = "错误: ${e.message}");
    }
  }

  Future<void> _recordFatigueEvent(Map<dynamic, dynamic> data) async {
    await DatabaseHelper().insertEvent({
      'timestamp': DateTime.now().millisecondsSinceEpoch,
      'event_type': 1, // 1: Fatigue (Closed Eyes)
      'value': (data['ear'] as num).toDouble(),
      'speed': (data['speed'] as num?)?.toDouble() ?? 0.0,
      'location':
          '', // Location can be retrieved from Location package if needed
    });
  }

  @override
  Widget build(BuildContext context) {
    final configService = Provider.of<ConfigService>(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('驾驶守护'),
        actions: [
          IconButton(
            icon: const Icon(Icons.history),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const HistoryPage()),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (context) => const SettingsPage()),
            ),
          ),
        ],
      ),
      body: Stack(
        children: [
          // Layer 1: Camera Preview (Full Screen or Large Area)
          Positioned.fill(
            child: Container(
              color: Colors.black,
              child: _textureId != null && _isRunning
                  ? Stack(
                      fit: StackFit.expand,
                      children: [
                        Texture(textureId: _textureId!),
                        IgnorePointer(
                          child: SizedBox.expand(
                            child: CustomPaint(
                              painter: FaceMeshPainter(
                                _landmarks,
                                configService.config.showFaceMesh,
                              ),
                            ),
                          ),
                        ),
                      ],
                    )
                  : const Center(
                      child: Icon(
                        Icons.videocam_off,
                        color: Colors.white54,
                        size: 64,
                      ),
                    ),
            ),
          ),

          // Layer 2: Top Status Bar (Mode Switch)
          Positioned(
            top: 20,
            left: 20,
            right: 20,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [_buildModeBadge(configService), _buildStatusBadge()],
            ),
          ),

          // Layer 3: Bottom Dashboard
          Positioned(
            bottom: 30,
            left: 20,
            right: 20,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (_isRunning) _buildDashboardCard(),
                const SizedBox(height: 20),
                _buildControlButtons(),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildModeBadge(ConfigService configService) {
    final modes = ['省电', '平衡', '高性能'];
    final currentMode = configService.config.detectionMode;

    // Determine active mode display (Override if speed > 80)
    String displayMode = modes[currentMode];
    Color badgeColor = Colors.black54;

    if (_currentSpeed > 80) {
      displayMode = "高速(高性能)";
      badgeColor = Colors.redAccent.withOpacity(0.8);
    } else if (_currentSpeed < 20 && currentMode != 2 && _currentSpeed > 0) {
      // Only show auto-power-save if actually moving slowly, not stationary (0.0) which might be just no GPS
      // Actually, stationary is also power save.
      displayMode = "自动省电";
      badgeColor = Colors.green.withOpacity(0.8);
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: badgeColor,
        borderRadius: BorderRadius.circular(20),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<int>(
          value: currentMode,
          dropdownColor: Colors.grey[850],
          style: const TextStyle(
            color: Colors.white,
            fontWeight: FontWeight.bold,
          ),
          icon: const Icon(Icons.arrow_drop_down, color: Colors.white),
          items: [0, 1, 2].map((mode) {
            return DropdownMenuItem(
              value: mode,
              child: Text("${modes[mode]}模式"),
            );
          }).toList(),
          onChanged: (val) {
            if (val != null) {
              configService.updateConfig(detectionMode: val);
            }
          },
        ),
      ),
    );
  }

  Widget _buildStatusBadge() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: _isFatigue
            ? Colors.red.withOpacity(0.8)
            : Colors.green.withOpacity(0.8),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        _isFatigue ? "⚠️ 疲劳预警" : "✅ 状态良好",
        style: const TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }

  Widget _buildDashboardCard() {
    return Card(
      color: Colors.black54,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            _buildMetric("EAR", _currentEar.toStringAsFixed(3)),
            _buildMetric(
              "PERCLOS",
              "${(_currentPerclos * 100).toStringAsFixed(1)}%",
            ),
            _buildMetric("FPS", "$_currentFps"),
            _buildMetric("车速", "${_currentSpeed.toStringAsFixed(0)} km/h"),
          ],
        ),
      ),
    );
  }

  Widget _buildMetric(String label, String value) {
    return Column(
      children: [
        Text(
          value,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 24,
            fontWeight: FontWeight.bold,
          ),
        ),
        Text(
          label,
          style: const TextStyle(color: Colors.white70, fontSize: 12),
        ),
      ],
    );
  }

  Widget _buildControlButtons() {
    return SizedBox(
      width: double.infinity,
      height: 50,
      child: ElevatedButton(
        onPressed: _isRunning ? _stopService : _startService,
        style: ElevatedButton.styleFrom(
          backgroundColor: _isRunning ? Colors.redAccent : Colors.blueAccent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(25),
          ),
        ),
        child: Text(
          _isRunning ? "停止监测" : "开始驾驶守护",
          style: const TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: Colors.white,
          ),
        ),
      ),
    );
  }
}

class FaceMeshPainter extends CustomPainter {
  final List<Offset> landmarks;
  final bool showGrid;

  FaceMeshPainter(this.landmarks, this.showGrid);

  @override
  void paint(Canvas canvas, Size size) {
    if (!showGrid || landmarks.isEmpty) return;

    // Debug: Draw a red border to verify Painter is working
    /*
    final borderPaint = Paint()
      ..color = Colors.red
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.0;
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height), borderPaint);
    */

    final paint = Paint()
      ..color = Colors.green
      ..strokeWidth = 2.0
      ..style = PaintingStyle.fill;

    // Scale logic for "BoxFit.cover" equivalent
    // Assumption: Camera image is 4:3 (e.g. 480x640), Screen is taller (e.g. 20:9)
    // To "cover" the screen with a 4:3 image, we scale the image up until width matches screen width (if screen is wider)
    // or height matches screen height (if screen is taller).
    // Actually, 4:3 is 0.75. Screen is 9/20 = 0.45. Screen is thinner.
    // So to cover, we match the Height, and crop the Width? No, we match the Height, and the Width of image will be larger than screen width.
    // So we need to center the 4:3 image on the screen.

    // Hardcoded Aspect Ratio of Camera (3:4 = 0.75) because we set 480x640 in Native
    // But wait, Native might rotate it?
    // If native rotates 270, the image sent to MP is 480x640 (Portrait).
    // The normalized coordinates are 0-1 relative to 480x640.

    // We need to map 0-1 to a virtual rectangle that covers the screen.
    double screenAspect = size.width / size.height;
    double imageAspect = 480 / 640; // 0.75

    double scaleX = 1.0;
    double scaleY = 1.0;
    double offsetX = 0.0;
    double offsetY = 0.0;

    if (screenAspect < imageAspect) {
      // Screen is thinner than image. Fit Height, crop Width.
      // Image height = Screen height
      // Image width = Screen height * imageAspect
      double virtualImageWidth = size.height * imageAspect;
      scaleY = size.height;
      scaleX = virtualImageWidth;
      offsetX = (size.width - virtualImageWidth) / 2;
    } else {
      // Screen is wider than image. Fit Width, crop Height.
      double virtualImageHeight = size.width / imageAspect;
      scaleX = size.width;
      scaleY = virtualImageHeight;
      offsetY = (size.height - virtualImageHeight) / 2;
    }

    for (var point in landmarks) {
      // Mirror X for Front Camera
      double x = 1.0 - point.dx;
      double y = point.dy;

      // Apply Cover Scaling
      double drawX = x * scaleX + offsetX;
      double drawY = y * scaleY + offsetY;

      canvas.drawCircle(Offset(drawX, drawY), 2.0, paint);
    }
  }

  @override
  bool shouldRepaint(covariant FaceMeshPainter oldDelegate) {
    return oldDelegate.landmarks != landmarks ||
        oldDelegate.showGrid != showGrid;
  }
}
