import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AppConfig {
  bool enableVoice;
  double earThreshold; // 0.15 - 0.30
  double durationThreshold; // 1.0s - 3.0s
  int detectionMode; // 0: PowerSaving, 1: Balanced, 2: HighPerf
  String languageCode; // 'zh', 'en'
  bool showFaceMesh;

  AppConfig({
    this.enableVoice = true,
    this.earThreshold = 0.20,
    this.durationThreshold = 1.5,
    this.detectionMode = 1,
    this.languageCode = 'zh',
    this.showFaceMesh = false,
  });

  Map<String, dynamic> toJson() => {
    'enableVoice': enableVoice,
    'earThreshold': earThreshold,
    'durationThreshold': durationThreshold,
    'detectionMode': detectionMode,
    'showFaceMesh': showFaceMesh,
  };
}

class ConfigService extends ChangeNotifier {
  late SharedPreferences _prefs;
  AppConfig _config = AppConfig();
  static const platform = MethodChannel('com.example.fatigue/detection');

  AppConfig get config => _config;

  Future<void> init() async {
    _prefs = await SharedPreferences.getInstance();
    _loadConfig();
    // Initialize Native side with loaded config
    syncToNative();
  }

  void _loadConfig() {
    _config = AppConfig(
      enableVoice: _prefs.getBool('enableVoice') ?? true,
      earThreshold: _prefs.getDouble('earThreshold') ?? 0.15,
      durationThreshold: _prefs.getDouble('durationThreshold') ?? 1.5,
      detectionMode: _prefs.getInt('detectionMode') ?? 1,
      languageCode: _prefs.getString('languageCode') ?? 'zh',
      showFaceMesh: _prefs.getBool('showFaceMesh') ?? false,
    );
    notifyListeners();
  }

  Future<void> updateConfig({
    bool? enableVoice,
    double? earThreshold,
    double? durationThreshold,
    int? detectionMode,
    String? languageCode,
    bool? showFaceMesh,
  }) async {
    bool needsSync = false;

    if (enableVoice != null) {
      _config.enableVoice = enableVoice;
      await _prefs.setBool('enableVoice', enableVoice);
      needsSync = true;
    }
    if (earThreshold != null) {
      _config.earThreshold = earThreshold;
      await _prefs.setDouble('earThreshold', earThreshold);
      needsSync = true;
    }
    if (durationThreshold != null) {
      _config.durationThreshold = durationThreshold;
      await _prefs.setDouble('durationThreshold', durationThreshold);
      needsSync = true;
    }
    if (detectionMode != null) {
      _config.detectionMode = detectionMode;
      await _prefs.setInt('detectionMode', detectionMode);
      needsSync = true;
    }
    if (languageCode != null) {
      _config.languageCode = languageCode;
      await _prefs.setString('languageCode', languageCode);
      // Language change might not need Native sync unless Native has TTS text
    }
    if (showFaceMesh != null) {
      _config.showFaceMesh = showFaceMesh;
      await _prefs.setBool('showFaceMesh', showFaceMesh);
      needsSync = true;
    }

    if (needsSync) {
      syncToNative();
    }
    notifyListeners();
  }

  Future<void> syncToNative() async {
    try {
      await platform.invokeMethod('updateConfig', _config.toJson());
    } on PlatformException catch (e) {
      debugPrint("Failed to sync config to native: ${e.message}");
    }
  }
}
