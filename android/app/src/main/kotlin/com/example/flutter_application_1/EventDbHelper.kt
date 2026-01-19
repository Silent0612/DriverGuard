package com.example.flutter_application_1

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class EventDbHelper(context: Context) : SQLiteOpenHelper(context, "driver_guard_v2.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        // Table should already be created by Flutter if the app ran once.
        // But if Native runs first (unlikely) or DB deleted, we create it.
        // Schema must match Flutter's database_helper.dart
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS fatigue_events(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER,
                event_type INTEGER,
                value REAL,
                speed REAL,
                location TEXT
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No upgrade logic for now
    }

    fun insertEvent(eventType: Int, value: Double, speed: Double) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("timestamp", System.currentTimeMillis())
            put("event_type", eventType) // 1: Fatigue/ClosedEyes, 2: Yawn, 3: Nodding/Distraction
            put("value", value)
            put("speed", speed)
            put("location", "") // Placeholder for location
        }
        db.insert("fatigue_events", null, values)
        db.close() // Close connection to be safe, or keep it open? 
        // Better to close for single operations to avoid locking issues with Flutter.
    }
}
