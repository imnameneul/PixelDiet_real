package com.example.pixeldiet.friend

import android.util.Log
import com.example.pixeldiet.data.FriendDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FriendRepository(
    private val dao: FriendDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // -------------------------
    // 친구 목록
    // -------------------------
    suspend fun loadFriends() {
        val uid = auth.currentUser?.uid ?: return

        val snapshot = firestore.collection("users")
            .document(uid)
            .collection("friends")
            .get()
            .await()

        val friends = snapshot.documents.mapNotNull { doc ->
            FriendRecord(
                uid = doc.getString("uid") ?: return@mapNotNull null,
                name = doc.getString("name") ?: "친구",
                photoUrl = doc.getString("photoUrl")
            )
        }

        dao.clearFriends()
        dao.addFriends(friends)
    }

    suspend fun getAllFriends() = dao.getAllFriends()

    suspend fun addFriend(friend: FriendRecord) {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(uid)
            .collection("friends")
            .document(friend.uid)
            .set(friend)
            .await()

        dao.addFriend(friend)
    }

    suspend fun removeFriend(friendUid: String) {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(uid)
            .collection("friends")
            .document(friendUid)
            .delete()
            .await()

        dao.removeFriend(friendUid)
    }
    suspend fun addFriendForOther(userUid: String, friend: FriendRecord) {
        firestore.collection("users")
            .document(userUid)
            .collection("friends")
            .document(friend.uid)
            .set(friend)
            .await()
    }
    fun AllFriends(): Flow<List<FriendRecord>> = dao.getAllFriendsFlow()

    // -------------------------
    // 받은 친구 요청
    // -------------------------
    suspend fun loadFriendRequestsReceived() {
        val currentUid = auth.currentUser?.uid ?: return

        val snapshot = firestore.collection("users")
            .document(currentUid)
            .collection("friendRequests")
            .get()
            .await()

        val requests = snapshot.documents.map { doc ->
            FriendRequest(
                id = doc.id,
                fromUid = doc.getString("fromUid") ?: "",
                fromName = doc.getString("fromName") ?: "친구",
                toUid = currentUid,
                toName = "나",
                fromPhotoUrl = doc.getString("fromPhotoUrl")
            )
        }

        // ✅ 수정된 부분: uid 전달
        dao.clearReceivedRequests(currentUid)
        dao.addFriendRequestsReceived(requests)
    }
    suspend fun acceptFriendRequest(request: FriendRequest) {
        val currentUid = auth.currentUser?.uid ?: return

        // 1️⃣ 받은 요청 삭제
        removeFriendRequestReceived(request.id)

        // 2️⃣ 내 친구 목록에 추가
        val newFriendForMe = FriendRecord(
            uid = request.fromUid,
            name = request.fromName,
            photoUrl = request.fromPhotoUrl
        )
        addFriend(newFriendForMe)

        // 3️⃣ 보낸 사람의 친구 목록에도 추가
        val myProfile = firestore.collection("users")
            .document(currentUid)
            .collection("profile")
            .document("main")
            .get()
            .await()

        val newFriendForSender = FriendRecord(
            uid = currentUid,
            name = myProfile.getString("name") ?: "나",
            photoUrl = myProfile.getString("imageUrl")
        )
        addFriendForOther(request.fromUid, newFriendForSender)

        // 4️⃣ 보낸 요청 삭제
        removeFriendRequestSent(request.fromUid, currentUid)
    }
    suspend fun getFriendRequestsReceived() =
        dao.getFriendRequestsReceived(auth.currentUser?.uid ?: "")

    suspend fun removeFriendRequestReceived(requestId: String) {
        val currentUid = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(currentUid)
            .collection("friendRequests")
            .document(requestId)
            .delete()
            .await()

        dao.removeFriendRequestReceived(requestId, currentUid)
    }

    // -------------------------
    // 보낸 친구 요청
    // -------------------------
    suspend fun loadFriendRequestsSent() {
        val currentUid = auth.currentUser?.uid ?: return

        val snapshot = firestore.collection("users")
            .document(currentUid)
            .collection("friendRequestsSent")
            .get()
            .await()

        val requests = snapshot.documents.map { doc ->
            FriendRequest(
                id = doc.id,
                fromUid = currentUid,
                fromName = "나",
                toUid = doc.getString("toUid") ?: "",
                toName = doc.getString("toName") ?: "친구",
                status = doc.getString("status") ?: "pending"
            )
        }

        dao.clearSentRequests()
        dao.addFriendRequestsSent(requests)
    }
    suspend fun cancelFriendRequest(request: FriendRequest) {
        val currentUid = auth.currentUser?.uid ?: return

        // Firestore
        firestore.collection("users")
            .document(currentUid)
            .collection("friendRequestsSent")
            .document(request.toUid)
            .delete()
            .await()

        firestore.collection("users")
            .document(request.toUid)
            .collection("friendRequests")
            .document(currentUid)
            .delete()
            .await()

        // Room
        dao.removeFriendRequestSent(request.toUid, currentUid)
    }
    suspend fun getFriendRequestsSent() =
        dao.getFriendRequestsSent(auth.currentUser?.uid ?: "")

    suspend fun removeFriendRequestSent(senderUid: String, targetUid: String) {
        firestore.collection("users")
            .document(senderUid)
            .collection("friendRequestsSent")
            .document(targetUid)
            .delete()
            .await()
        dao.removeFriendRequestSent(senderUid, targetUid)
    }



    // -------------------------
    // 친구 요청 보내기
    // -------------------------
    suspend fun sendFriendRequest(friendCode: String) {
        val currentUser = auth.currentUser ?: return

        // 내 프로필 불러오기
        val myProfile = firestore.collection("users")
            .document(currentUser.uid)
            .collection("profile")
            .document("main")
            .get()
            .await()

        val fromName = myProfile.getString("name") ?: "나"

        // 1️⃣ 친구 코드로 상대 UID 찾기
        val usersSnapshot = firestore.collection("users")
            .get()
            .await()

        val toProfile = usersSnapshot.documents.firstOrNull { userDoc ->
            val profileDoc = userDoc.reference
                .collection("profile")
                .document("main")
                .get()
                .await()
            profileDoc.getString("friendCode") == friendCode
        }?.let { it.reference.collection("profile").document("main").get().await() }

        // 친구 코드가 없으면 종료
        if (toProfile == null) return

        val toUid = toProfile.getString("uid") ?: return
        val toName = toProfile.getString("name") ?: ""

        // Firestore 저장
        val recvData = mapOf(
            "fromUid" to currentUser.uid,
            "fromName" to fromName,
            "timestamp" to FieldValue.serverTimestamp()
        )
        val sentData = mapOf(
            "toUid" to toUid,
            "toName" to toName,          // ✅ 여기 수정
            "fromUid" to currentUser.uid,
            "fromName" to fromName,
            "timestamp" to FieldValue.serverTimestamp()
        )

        firestore.collection("users")
            .document(toUid)
            .collection("friendRequests")
            .document(currentUser.uid)
            .set(recvData)
            .await()

        firestore.collection("users")
            .document(currentUser.uid)
            .collection("friendRequestsSent")
            .document(toUid)
            .set(sentData)
            .await()

        // Room 저장
        dao.addFriendRequestSent(
            FriendRequest(
                id = UUID.randomUUID().toString(),
                fromUid = currentUser.uid,
                fromName = fromName,
                toUid = toUid,
                toName = toName,
                status = "pending"
            )
        )
    }

}
