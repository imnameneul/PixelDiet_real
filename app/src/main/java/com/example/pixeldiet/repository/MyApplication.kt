package com.example.pixeldiet.repository

import android.app.Application

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UsageRepository.init(this) // 여기서 db 초기화
    }
}