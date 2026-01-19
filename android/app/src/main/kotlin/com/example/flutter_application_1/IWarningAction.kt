package com.example.flutter_application_1

interface IWarningAction {
    fun trigger(level: Int) // 1: Mild, 2: Severe
    fun cancel()
}