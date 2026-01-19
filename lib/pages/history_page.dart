import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../utils/database_helper.dart';

class HistoryPage extends StatefulWidget {
  const HistoryPage({super.key});

  @override
  State<HistoryPage> createState() => _HistoryPageState();
}

class _HistoryPageState extends State<HistoryPage> {
  List<Map<String, dynamic>> _events = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadEvents();
  }

  Future<void> _loadEvents() async {
    final events = await DatabaseHelper().getEvents();
    setState(() {
      _events = events;
      _isLoading = false;
    });
  }

  Future<void> _clearHistory() async {
    await DatabaseHelper().clearEvents();
    _loadEvents();
  }

  String _formatTimestamp(int timestamp) {
    final date = DateTime.fromMillisecondsSinceEpoch(timestamp);
    return DateFormat('yyyy-MM-dd HH:mm:ss').format(date);
  }

  String _getEventLabel(int type) {
    switch (type) {
      case 1:
        return "疲劳 (闭眼)";
      case 2:
        return "哈欠";
      case 3:
        return "低头";
      default:
        return "未知";
    }
  }

  Color _getEventColor(int type) {
    switch (type) {
      case 1:
        return Colors.redAccent;
      case 2:
        return Colors.orangeAccent;
      case 3:
        return Colors.yellowAccent;
      default:
        return Colors.grey;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('历史记录'),
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_forever),
            onPressed: () {
              showDialog(
                context: context,
                builder: (ctx) => AlertDialog(
                  title: const Text("清除记录"),
                  content: const Text("确定要清空所有历史数据吗？"),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(ctx),
                      child: const Text("取消"),
                    ),
                    TextButton(
                      onPressed: () {
                        Navigator.pop(ctx);
                        _clearHistory();
                      },
                      child: const Text(
                        "确定",
                        style: TextStyle(color: Colors.red),
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _events.isEmpty
          ? const Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.history, size: 64, color: Colors.grey),
                  SizedBox(height: 16),
                  Text("暂无历史记录", style: TextStyle(color: Colors.grey)),
                ],
              ),
            )
          : Column(
              children: [
                // Chart Section
                Container(
                  height: 200,
                  padding: const EdgeInsets.all(16),
                  color: Colors.grey[100],
                  child: CustomPaint(
                    size: Size.infinite,
                    painter: HistoryChartPainter(_events),
                  ),
                ),
                const Divider(height: 1),
                // List Section
                Expanded(
                  child: ListView.builder(
                    itemCount: _events.length,
                    itemBuilder: (context, index) {
                      final event = _events[index];
                      return Card(
                        margin: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 8,
                        ),
                        child: ListTile(
                          leading: CircleAvatar(
                            backgroundColor: _getEventColor(
                              event['event_type'],
                            ),
                            child: const Icon(
                              Icons.warning_amber_rounded,
                              color: Colors.white,
                            ),
                          ),
                          title: Text(_getEventLabel(event['event_type'])),
                          subtitle: Text(
                            "时间: ${_formatTimestamp(event['timestamp'])}\n"
                            "EAR: ${(event['value'] as num).toStringAsFixed(3)} | 车速: ${(event['speed'] as num).toStringAsFixed(1)} km/h",
                          ),
                          isThreeLine: true,
                        ),
                      );
                    },
                  ),
                ),
              ],
            ),
    );
  }
}

class HistoryChartPainter extends CustomPainter {
  final List<Map<String, dynamic>> events;

  HistoryChartPainter(this.events);

  @override
  void paint(Canvas canvas, Size size) {
    if (events.isEmpty) return;

    final paint = Paint()
      ..strokeWidth = 2.0
      ..style = PaintingStyle.stroke;

    final axisPaint = Paint()
      ..color = Colors.black54
      ..strokeWidth = 1.0;

    // Draw Axis
    canvas.drawLine(
      Offset(30, size.height - 20),
      Offset(size.width, size.height - 20),
      axisPaint,
    ); // X-Axis
    canvas.drawLine(
      Offset(30, 0),
      Offset(30, size.height - 20),
      axisPaint,
    ); // Y-Axis

    // Determine Time Range (Last 24 hours or based on data)
    // Let's take the range from the first event to the last event in the list
    // List is ordered by timestamp DESC (newest first)
    final endTimestamp = events.first['timestamp'] as int;
    final startTimestamp = events.last['timestamp'] as int;

    // Avoid division by zero if only one event
    var duration = endTimestamp - startTimestamp;
    if (duration == 0) duration = 1000 * 60; // 1 minute window

    // Plot Points
    // X: Time, Y: Speed (0-120 km/h) or just Event Markers?
    // Let's plot Speed as a line if we had continuous data, but we only have events.
    // Let's plot events as dots at specific heights.
    // Height = Speed? Or fixed height?
    // Let's plot Speed on Y axis (0 - 150 km/h)

    final maxSpeed = 150.0;

    for (var event in events) {
      final timestamp = event['timestamp'] as int;
      final speed = (event['speed'] as num).toDouble();

      // Normalize X: (timestamp - start) / duration
      // But chart goes left-to-right (old to new).
      // startTimestamp is OLD (left), endTimestamp is NEW (right).
      final x =
          30 + ((timestamp - startTimestamp) / duration) * (size.width - 40);

      // Normalize Y: speed / maxSpeed
      // Y=0 is top. Y=height is bottom.
      // So y = height - 20 - (speed/maxSpeed * (height-20))
      final y = (size.height - 20) - (speed / maxSpeed) * (size.height - 40);

      // Draw Dot
      paint.color = _getColor(event['event_type']);
      paint.style = PaintingStyle.fill;
      canvas.drawCircle(Offset(x, y), 4.0, paint);
    }

    // Draw Text Labels (Min/Max Time)
    final textPainter = TextPainter(
      textDirection: ui.TextDirection.ltr,
      textAlign: TextAlign.center,
    );

    // Start Time Label
    textPainter.text = TextSpan(
      text: DateFormat(
        'HH:mm',
      ).format(DateTime.fromMillisecondsSinceEpoch(startTimestamp)),
      style: const TextStyle(color: Colors.black, fontSize: 10),
    );
    textPainter.layout();
    textPainter.paint(canvas, Offset(20, size.height - 15));

    // End Time Label
    textPainter.text = TextSpan(
      text: DateFormat(
        'HH:mm',
      ).format(DateTime.fromMillisecondsSinceEpoch(endTimestamp)),
      style: const TextStyle(color: Colors.black, fontSize: 10),
    );
    textPainter.layout();
    textPainter.paint(canvas, Offset(size.width - 40, size.height - 15));

    // Y-Axis Label (Speed)
    textPainter.text = const TextSpan(
      text: "150 km/h",
      style: TextStyle(color: Colors.black, fontSize: 10),
    );
    textPainter.layout();
    textPainter.paint(canvas, const Offset(0, 0));
  }

  Color _getColor(dynamic type) {
    switch (type) {
      case 1:
        return Colors.red;
      case 2:
        return Colors.orange;
      case 3:
        return Colors.yellow;
      default:
        return Colors.blue;
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => true;
}
