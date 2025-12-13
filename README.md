## ⚠️ 참고
- 앱 실행하자마자 앱이 꺼지면 Appdatabase.kt에서 version 값 올리면 되는 것 같습니다.

---

## 📢 1차 수정 사항 (구글 재로그인 시 로그인 실패 뜨는 문제 등 있어서 해결)

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
1. 그룹 생성 시 Firestore 쓰기들을 전부 await()로 기다리게 수정 → members/{uid} 문서가 “확실히 만들어진 다음” 다음 로직이 돌게 함.
2. 어디서든 members 문서는 update() 대신 set(merge)(업서트)로만 갱신 → 문서가 없어도 자동 생성되니까 NOT_FOUND 크래시 방지.

---

- 📢 (예정) 3차 수정 사항 : 앱 사용시간 겹치는 문제 (SharedViewModel에서 앱 사용시간으로 'Firestore(today) + UsageStats(realtime)' 기반으로 계산하는거 없애고  'UsageStats(realtime)'기반 사용시간으로 계산하게 수정(UsageRepository))
  
- 📢 (예정) 4차 수정 사항 (메인화면에서 총 사용시간 뜨게 하기, 스트릭 아이콘 추가, 프로그레스바 색깔 변경)

- 📢 (예정) 5차 수정 사항 (총 목표시간도 설정 가능하게 수정(현재는 총 목표시간을 합산으로만 계산)(firestore, room에 총 목표시간 저장 필드 추가 고려 중))

- 📢 (예정) 6차 수정 사항 (경고 알림을 보내는 기능을 15분 단위 worker 실행에서 foreground service 기반 알림으로 변경(가능하면 앱 사용 진행바도 추가 예정)(10분 마다 알림은 시연용으로 10초마다로 설정)

- 📢 (예정) 7차 수정 사항 (백업 관련: 로그인 시 n개월치 정도 복원하게 하기, 로그인 후 메인화면에 추적 앱으로 설정했던 앱 카드들 로드되게 하기)

- 📢 (예정) 8차 수정 사항 (바로바로 갱신 되지 않는 화면들 바로 로드되게 수정: 아마도 같은 viewmodel을 보고 있지 않거나 리로드 하지 않아서 생기는 문제)(ex: 마이프로필 화면에서 로그인 후 바로 친구코드 화면에 안 뜨는 문제 등)











