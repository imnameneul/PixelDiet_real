package com.example.pixeldiet.friend.group

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pixeldiet.data.TrackedAppDao

class GroupViewModelFactory(
    private val repository: GroupRepository,
    private val usageDao: TrackedAppDao,
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupViewModel::class.java)) {
            return GroupViewModel(repository, usageDao, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
