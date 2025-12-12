package com.example.pixeldiet.backup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
//import android.util.Log
//import androidx.lifecycle.ViewModelProvider
//import androidx.lifecycle.viewmodel.compose.viewModel

import com.example.pixeldiet.MainActivity
//import com.example.pixeldiet.data.AppDatabase
//import com.example.pixeldiet.data.DatabaseProvider
//import com.example.pixeldiet.repository.UsageRepository
import com.example.pixeldiet.viewmodel.SharedViewModel
//import kotlinx.coroutines.flow.filter
//import kotlinx.coroutines.flow.first


class LauncherActivity : AppCompatActivity() {

    private lateinit var viewModel: SharedViewModel
    private lateinit var backupManager: BackupManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 앱 시작 시 순차 처리
        lifecycleScope.launch {
            handleUserAndBackup(viewModel)     // viewModel 전달
            goToMain()
        }
    }

    private suspend fun handleUserAndBackup(viewModel: SharedViewModel) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            backupManager.initUser()
        }

        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {

            goToMain()
        } else {
            goToLogin()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
