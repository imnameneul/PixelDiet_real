## 📢 1차 수정 사항 (구글 재로그인 시 로그인 실패 뜨는 문제 등 해결)

⭐backup/
- BackupManager.kt : syncFromFirestore() 수정
- LoginActivity.kt : backupManager.syncFromFirestore를 제거하고 SharedViewModel이 직접 syncFromFirestore() 호출하는 구조로 변경

⭐ repository/
- SyncRepository.kt 추가 : SharedViewModel.kt에 있던 동기화/백업/업로드 관련 코드들을 SyncRepository.kt 파일로 옮김
- loadBackupToday, loadDailyDetail, uploadDailyUsageToFirebase, uploadDailyGoalToFirebase, initUserProfile, insertTestDataForUid

⭐ viewmodel/
- SharedViewModel.kt 수정 : Firestore I/O 로직 제거하고 SynsRepository 호출로 사용하게 수정


## 📢 2차 수정 사항 (1차 수정 후에 NOT_FOUND 크래시 발생해서 수정함)

⭐group/
- GroupRepository.kt
- GroupViewModel.kt
=> 그룹 생성 시 Firestore 쓰기들을 전부 await()로 기다리게 수정 → members/{uid} 문서가 “확실히 만들어진 다음” 다음 로직이 돌게 함. 
=> 어디서든 members 문서는 update() 대신 set(merge)(업서트)로만 갱신 → 문서가 없어도 자동 생성되니까 NOT_FOUND 크래시 방지.

---

## ⚠️ 유의 사항
- 앱 실행하자마자 꺼지면 Appdatabase.kt에서 version 값 올리면 되는 것 같습니다...
