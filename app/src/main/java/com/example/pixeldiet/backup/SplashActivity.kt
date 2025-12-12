package com.example.pixeldiet.backup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pixeldiet.MainActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uid = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            .getString("uid", null)

        if (uid.isNullOrEmpty()) {
            // UID가 없으면 로그인 화면으로
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            // UID가 있으면 바로 메인 화면으로
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
