import 'dart:async';
import 'package:battery_plus/battery_plus.dart';
import 'package:flutter/material.dart';
import 'config_service.dart';

class PowerManager extends ChangeNotifier {
  final ConfigService _configService;
  final Battery _battery = Battery();
  Timer? _timer;
  int _promptCount = 0;
  DateTime? _lastPromptTime;
  
  // Max prompts allowed per session/period
  static const int MAX_PROMPTS = 3;
  // Check interval
  static const Duration CHECK_INTERVAL = Duration(minutes: 5);
  // Prompt timeout
  static const Duration PROMPT_TIMEOUT = Duration(seconds: 5);

  PowerManager(this._configService) {
    _startMonitoring();
  }

  void _startMonitoring() {
    // Initial check
    _checkPowerStatus();
    
    // Periodic check
    _timer = Timer.periodic(CHECK_INTERVAL, (_) => _checkPowerStatus());
  }

  Future<void> _checkPowerStatus() async {
    if (_promptCount >= MAX_PROMPTS) return;

    try {
      final level = await _battery.batteryLevel;
      final state = await _battery.batteryState;
      
      // Default Logic: High Performance
      // If battery < 60%, suggest Balanced
      if (level < 60 && _configService.config.detectionMode == 2) {
        _triggerPrompt(
          "电量低于60%", 
          "建议切换为平衡模式以延长续航", 
          1 // Balanced
        );
      } 
      // If Power Save Mode (simulated by very low battery or charging state check if possible)
      // Note: battery_plus doesn't directly detect "Power Save Mode" of OS easily on all devices.
      // We assume < 20% is critical/power save likely.
      else if (level < 20 && _configService.config.detectionMode != 0) {
         _triggerPrompt(
          "电量严重不足", 
          "建议切换为省电模式", 
          0 // Power Save
        );
      }
    } catch (e) {
      debugPrint("PowerManager Error: $e");
    }
  }

  void _triggerPrompt(String title, String message, int targetMode) {
    // Notify UI to show dialog
    // Since this is a service, we use a global key or a stream/callback.
    // However, simplest way in Provider is to expose a "pendingPrompt" object 
    // that the UI listens to.
    
    _pendingPrompt = PowerPrompt(
      title: title,
      message: message,
      targetMode: targetMode,
      timestamp: DateTime.now(),
    );
    _promptCount++;
    notifyListeners();
    
    // Auto-dismiss after 5 seconds if not accepted
    Timer(PROMPT_TIMEOUT, () {
      if (_pendingPrompt != null) {
        _pendingPrompt = null;
        notifyListeners();
      }
    });
  }

  PowerPrompt? _pendingPrompt;
  PowerPrompt? get pendingPrompt => _pendingPrompt;

  void acceptPrompt() {
    if (_pendingPrompt != null) {
      _configService.updateConfig(detectionMode: _pendingPrompt!.targetMode);
      _pendingPrompt = null;
      notifyListeners();
    }
  }

  void dismissPrompt() {
    _pendingPrompt = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }
}

class PowerPrompt {
  final String title;
  final String message;
  final int targetMode;
  final DateTime timestamp;

  PowerPrompt({
    required this.title,
    required this.message,
    required this.targetMode,
    required this.timestamp,
  });
}