package com.example.pixeldiet.backup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
//import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pixeldiet.MainActivity
import com.example.pixeldiet.R
import com.example.pixeldiet.data.AppDatabase
import com.example.pixeldiet.viewmodel.SharedViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var viewModel: SharedViewModel
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var backupManager: BackupManager

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(Exception::class.java)
                val idToken = account?.idToken
                val uid = account?.id // 여기서 구글 계정 ID를 UID로 사용
                if (idToken != null && uid != null) {
                    lifecycleScope.launch {
                        try {
                            backupManager.signInWithGoogle(idToken)
                            // ✅ UID SharedPreferences에 저장
                            getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString("uid", uid)
                                .apply()
                            backupManager.syncFromFirestore(viewModel)

                            Toast.makeText(this@LoginActivity, "구글 로그인 완료", Toast.LENGTH_SHORT).show()
                            goToMain()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this@LoginActivity, "구글 로그인 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@LoginActivity, "구글 로그인 실패", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val db = AppDatabase.getInstance(applicationContext)
        backupManager = BackupManager(
            userDao = db.userProfileDao(),
            trackedAppDao = db.trackedAppDao(),
            dailyUsageDao = db.dailyUsageDao(),
            groupDao = db.groupDao(),
            friendDao = db.friendDao()
        )

        val btnGuest = findViewById<Button>(R.id.btnGuest)
        val btnGoogle = findViewById<Button>(R.id.btnGoogle)

        // 구글 로그인 옵션
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // 게스트 로그인
        btnGuest.setOnClickListener {
            lifecycleScope.launch {
                try {
                    backupManager.initUser() // 익명 로그인 생성
                    // ✅ UID SharedPreferences에 저장 (게스트용)
                    getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("uid", "guest_${System.currentTimeMillis()}")
                        .apply()
                    Toast.makeText(this@LoginActivity, "게스트 로그인 완료", Toast.LENGTH_SHORT).show()
                    goToMain()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@LoginActivity, "로그인 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 구글 로그인
        btnGoogle.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}