import 'package:sqflite/sqflite.dart';
import 'package:path/path.dart';

class DatabaseHelper {
  static final DatabaseHelper _instance = DatabaseHelper._internal();
  static Database? _database;

  factory DatabaseHelper() => _instance;

  DatabaseHelper._internal();

  Future<Database> get database async {
    if (_database != null) return _database!;
    _database = await _initDatabase();
    return _database!;
  }

  Future<Database> _initDatabase() async {
    String path = join(await getDatabasesPath(), 'driver_guard_v2.db');
    print("DatabaseHelper: Initializing DB at $path");
    return await openDatabase(
      path,
      version: 1,
      onCreate: _onCreate,
      onOpen: (db) {
        print("DatabaseHelper: DB Opened");
      },
    );
  }

  Future<void> _onCreate(Database db, int version) async {
    print("DatabaseHelper: Creating tables...");
    await db.execute('''
      CREATE TABLE fatigue_events(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        timestamp INTEGER,
        event_type INTEGER,
        value REAL,
        speed REAL,
        location TEXT
      )
    ''');
    print("DatabaseHelper: Tables created.");
  }

  // Insert a new event
  Future<int> insertEvent(Map<String, dynamic> row) async {
    Database db = await database;
    return await db.insert('fatigue_events', row);
  }

  // Get all events ordered by timestamp descending
  Future<List<Map<String, dynamic>>> getEvents() async {
    Database db = await database;
    return await db.query('fatigue_events', orderBy: 'timestamp DESC');
  }

  // Get events for a specific date (start of day to end of day)
  Future<List<Map<String, dynamic>>> getEventsByDate(DateTime date) async {
    Database db = await database;

    final startOfDay = DateTime(
      date.year,
      date.month,
      date.day,
    ).millisecondsSinceEpoch;
    final endOfDay = DateTime(
      date.year,
      date.month,
      date.day,
      23,
      59,
      59,
    ).millisecondsSinceEpoch;

    return await db.query(
      'fatigue_events',
      where: 'timestamp >= ? AND timestamp <= ?',
      whereArgs: [startOfDay, endOfDay],
      orderBy: 'timestamp DESC',
    );
  }

  // Delete all events
  Future<void> clearEvents() async {
    Database db = await database;
    await db.delete('fatigue_events');
  }
}
