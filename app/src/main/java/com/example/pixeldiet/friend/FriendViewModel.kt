package com.example.pixeldiet.ui.friend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixeldiet.friend.FriendRecord
import com.example.pixeldiet.friend.FriendRepository
import com.example.pixeldiet.friend.FriendRequest
import com.example.pixeldiet.friend.group.GroupRecord
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class FriendViewModel(val repository: FriendRepository) : ViewModel() {

    private val _friendList = MutableStateFlow<List<FriendRecord>>(emptyList())
    val friendList: StateFlow<List<FriendRecord>> = _friendList

    private val _friendRequestsReceived = MutableStateFlow<List<FriendRequest>>(emptyList())
    val friendRequestsReceived: StateFlow<List<FriendRequest>> = _friendRequestsReceived

    private val _friendRequestsSent = MutableStateFlow<List<FriendRequest>>(emptyList())
    val friendRequestsSent: StateFlow<List<FriendRequest>> = _friendRequestsSent

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog
    private val _selectedGroup = MutableStateFlow<GroupRecord?>(null)
    val selectedGroup: StateFlow<GroupRecord?> = _selectedGroup
    private val _friends = MutableStateFlow<List<FriendRecord>>(emptyList())
    val friends: StateFlow<List<FriendRecord>> = _friends
    init { refreshAll() }

    fun refreshAll() = viewModelScope.launch {
        repository.loadFriends()
        _friendList.value = repository.getAllFriends()

        repository.loadFriendRequestsReceived()
        _friendRequestsReceived.value = repository.getFriendRequestsReceived()

        repository.loadFriendRequestsSent()
        _friendRequestsSent.value = repository.getFriendRequestsSent()
    }

    fun sendFriendRequest(toUid: String) = viewModelScope.launch {
        repository.sendFriendRequest(toUid)
        refreshAll()
    }
    fun selectGroup(group: GroupRecord) {
        _selectedGroup.value = group
    }

    fun removeFriend(uid: String) = viewModelScope.launch {
        repository.removeFriend(uid)
        refreshAll()
    }

    fun removeFriendRequestReceived(requestId: String) = viewModelScope.launch {
        repository.removeFriendRequestReceived(requestId)
        refreshAll()
    }

    fun cancelFriendRequest(request: FriendRequest) = viewModelScope.launch {
        repository.cancelFriendRequest(request)
        refreshAll()
    }

    fun acceptFriendRequest(request: FriendRequest) = viewModelScope.launch {
        repository.acceptFriendRequest(request)
        refreshAll()
    }

    fun openAddDialog() { _showAddDialog.value = true }
    fun closeAddDialog() { _showAddDialog.value = false }

    fun loadFriends() {
        viewModelScope.launch {
            repository.AllFriends().collect { list ->
                _friends.value = list
            }
        }
    }
}
