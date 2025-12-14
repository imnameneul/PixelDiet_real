package com.example.pixeldiet.backup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pixeldiet.MainActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        // ✅ 진짜 로그인 여부는 FirebaseAuth 기준
        val authUid = FirebaseAuth.getInstance().currentUser?.uid

        if (authUid.isNullOrEmpty()) {
            // auth가 없으면 prefs uid도 지워서 꼬임 방지
            prefs.edit().remove("uid").apply()
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            // prefs랑 authUid 동기화(선택이지만 추천)
            val savedUid = prefs.getString("uid", null)
            if (savedUid != authUid) {
                prefs.edit().putString("uid", authUid).apply()
            }
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
