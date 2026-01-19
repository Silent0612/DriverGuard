import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../services/config_service.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  static const platform = MethodChannel('com.example.fatigue/detection');
  bool _isWatchConnected = false;

  Future<void> _connectWatch() async {
    try {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('正在搜索附近设备...')));

      // Simulate scanning delay
      await Future.delayed(const Duration(seconds: 1));

      final bool success = await platform.invokeMethod('connectWatch');

      if (mounted) {
        setState(() {
          _isWatchConnected = success;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(success ? '智能手表已连接 (模拟)' : '连接失败')),
        );
      }
    } on PlatformException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('连接错误: ${e.message}')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('设置')),
      body: Consumer<ConfigService>(
        builder: (context, configService, child) {
          final config = configService.config;
          return ListView(
            children: [
              const _SectionHeader(title: '基础设置'),
              ListTile(
                title: const Text('语言 / Language'),
                subtitle: Text(
                  config.languageCode == 'zh' ? '简体中文' : 'English',
                ),
                trailing: const Icon(Icons.arrow_forward_ios, size: 16),
                onTap: () {
                  // 简单的语言切换逻辑
                  final newLang = config.languageCode == 'zh' ? 'en' : 'zh';
                  configService.updateConfig(languageCode: newLang);
                },
              ),
              SwitchListTile(
                title: const Text('语音播报'),
                value: config.enableVoice,
                onChanged: (val) =>
                    configService.updateConfig(enableVoice: val),
              ),

              const Divider(),
              const _SectionHeader(title: '算法灵敏度'),
              ListTile(
                title: const Text('EAR 闭眼阈值'),
                subtitle: Text(
                  '当前值: ${config.earThreshold.toStringAsFixed(2)} (越低越难触发)',
                ),
              ),
              Slider(
                value: config.earThreshold,
                min: 0.10,
                max: 0.30,
                divisions: 20,
                label: config.earThreshold.toStringAsFixed(2),
                onChanged: (val) =>
                    configService.updateConfig(earThreshold: val),
              ),

              ListTile(
                title: const Text('闭眼时长触发 (秒)'),
                subtitle: Text(
                  '当前值: ${config.durationThreshold.toStringAsFixed(1)}s',
                ),
              ),
              Slider(
                value: config.durationThreshold,
                min: 1.0,
                max: 3.0,
                divisions: 20,
                label: "${config.durationThreshold.toStringAsFixed(1)}s",
                onChanged: (val) =>
                    configService.updateConfig(durationThreshold: val),
              ),

              const Divider(),
              const _SectionHeader(title: '扩展设备'),
              ListTile(
                leading: Icon(
                  Icons.watch,
                  color: _isWatchConnected ? Colors.green : null,
                ),
                title: const Text('连接智能手表'),
                subtitle: Text(
                  _isWatchConnected ? '已连接: Mock Watch' : '暂未连接设备',
                ),
                trailing: ElevatedButton(
                  onPressed: _isWatchConnected ? null : _connectWatch,
                  child: Text(_isWatchConnected ? '已连接' : '连接'),
                ),
              ),

              const Divider(),
              const _SectionHeader(title: '高级调试'),
              SwitchListTile(
                title: const Text('显示 Face Mesh 网格'),
                subtitle: const Text('在预览画面绘制人脸网格'),
                value: config.showFaceMesh,
                onChanged: (val) =>
                    configService.updateConfig(showFaceMesh: val),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader({required this.title});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Text(
        title,
        style: TextStyle(
          color: Theme.of(context).primaryColor,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
}
