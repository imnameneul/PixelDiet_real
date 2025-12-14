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

## 📢 3차 수정 사항 : 앱 사용시간 겹치는 문제
> SharedViewModel에서 앱 사용시간으로 'Firestore(today) + UsageStats(realtime)' 기반으로 계산하는거 없애고  'UsageStats(realtime)'기반 사용시간으로 계산하게 수정(UsageRepository)

⭐SyncRepository.kt 수정

⭐SharedViewModel.kt 수정

⭐UsageCheckWorkger.kt 수정

- 주요 수정: SharedViewModel.kt 에 있던 firebase에 오늘 사용시간을 가져와 앱 사용시간에 합산하던 계산 삭제하고 UsageRepositry.kt(수정X)에 있던 계산 로직 그대로 가져다 씀.
- 백그라운드로 넘어갈때 firebase에 업로드하는건 동일, 대신 오늘 사용시간은 안 가져옴(함수는 남아있음)

## 📢 4차 수정 사항
1. 메인화면에서 총 사용시간 뜨게 하기
2. 스트릭 아이콘 추가
3. 프로그레스바 색깔 변경
4. 사용시간 순으로 앱 카드 정렬(옵션: 사용시간 순, 목표 달성 순)

---
- 📢 (예정) 5차 수정 사항 (총 목표시간도 설정 가능하게 수정(현재는 총 목표시간을 합산으로만 계산)(firestore, room에 총 목표시간 저장 필드 추가 고려 중))

- 📢 (예정) 6차 수정 사항 (경고 알림을 보내는 기능을 15분 단위 worker 실행에서 foreground service 기반 알림으로 변경(가능하면 앱 사용 진행바도 추가 예정)(10분 마다 알림은 시연용으로 10초마다로 설정)

- 📢 (예정) 7차 수정 사항 (백업 관련: 로그인 시 n개월치 정도 복원하게 하기, 로그인 후 메인화면에 추적 앱으로 설정했던 앱 카드들 로드되게 하기)

- 📢 (예정) 8차 수정 사항 (바로바로 갱신 되지 않는 화면들 바로 로드되게 수정: 아마도 같은 viewmodel을 보고 있지 않거나 리로드 하지 않아서 생기는 문제)(ex: 마이프로필 화면에서 로그인 후 바로 친구코드 화면에 안 뜨는 문제 등), 앱 추가하면 거품 뷰에 바로 뜨게 하기 등

- 캘린더 화면에서 검색하는 토글 부분이 room에 저장된 것만 적용되는 것 같아서 복원 후 firebase가 가져온 앱들도 검색할 수 있게 바꾸기

---

- 친구창 닉네임 뜨게 하기, 프로그레스바 뜨게 하기

- 프로필 및 닉네임 및 아이디 등 설정

- 앱 카드 개별 삭제 기능
- 알림창, 리포트 기능
- 앱 시작할때마다 sync 불러와서 로딩 긴거 해결
- 다른 앱에서 가져왔을때 폰에 없는 앱: com.instagram.android로 뜨는 문제(폰에 없는 앱은 아이콘에 ?로 뜨게 하기 등)
- viewmdoel 초기화 버전에서 건우님 loginActivity 버전으로 바꿔서도 확인해보기

- 거품 뷰 vs 원형 그래프 비교해서 선택
- 백그라운드로 넘어갈때 말고 목표 앱 선택할때마다 firebase 업로드하게 바꾸기?(근데 지금은 불필요함)
- 내 room에 저장되어 있던건 앱 카드들은 삭제 안됨(다른 room에서 불러왓을시 목표시간 같은게 혼합될 수 있을 듯)

- 동기화 범위 줄이기: 동기화할때마다 전체 기록을 다 동기화 받는 듯 -> 로그인 할때만 전체 기록을 동기화 받고, 24시간 마다 복원할때는 최신 1일 값만 복원받기 등

- 친구한테 streak 공유, 혹은 메인 화면 전체 공유 등 (설정 화면에 친구에게 공유 버튼 추가)




