📢 1차 수정 사항 (구글 재로그인 시 로그인 실패 뜨는 문제 등 해결)

⭐backup/
- BackupManager.kt : syncFromFirestore() 수정
- LoginActivity.kt : backupManager.syncFromFirestore를 제거하고 SharedViewModel이 직접 syncFromFirestore() 호출하는 구조로 변경

⭐ repository/
- SyncRepository.kt 추가 : SharedViewModel.kt에 있던 동기화/백업/업로드 관련 코드들을 SyncRepository.kt 파일로 옮김
- loadBackupToday, loadDailyDetail, uploadDailyUsageToFirebase, uploadDailyGoalToFirebase, initUserProfile, insertTestDataForUid

⭐ viewmodel/
- SharedViewModel.kt 수정 : Firestore I/O 로직 제거하고 SynsRepository 호출로 사용하게 수정


---

⚠️ 유의 사항
- 앱 실행하자마자 꺼지면 Appdatabase.kt에서 version 값 올리면 되는 것 같습니다...
