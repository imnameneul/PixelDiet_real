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

## 📢 4.5차 수정 사항
1. 앱 실행마다 sync함수로 firestore에 있는 값을 복원하고 있어서 로딩이 긺 -> 계정이 바뀌었을때, 복원 기록이 없을때, 복원한지 너무 오래되었을때만 복원하게 수정

## 📢 5차 수정 사항
1. 총 목표시간 없애고 사용시간만 측정하게 함 (총 사용시간 필드 추가 귀찮, 중요하지 않은거 같아서 삭제)

## 📢 6차 수정 사항
- 경고 알림을 보내는 기능을 15분 단위 worker 실행에서 foreground service 기반 알림으로 변경
- 가능하면 앱 사용 진행바도 추가 예정

---
- 📢 (예정) 7차 수정 사항 (백업 관련: 로그인 시 n개월치 정도 복원하게 하기)
- 가짜 데이터 넣어보고 캘린더 제대로 작동되는지 확인
- goalhistory 기반으로 바 그래프 수정

- 📢 (예정) 8차 수정 사항 (바로바로 갱신 되지 않는 화면들 바로 로드되게 수정: 아마도 같은 viewmodel을 보고 있지 않거나 리로드 하지 않아서 생기는 문제)(ex: 마이프로필 화면에서 로그인 후 바로 친구코드 화면에 안 뜨는 문제 등), 앱 추가하면 거품 뷰에 바로 적용, 로그인하고 나면 메인 화면에 바로 불러오기 등, 앱 삭제하기 화면에서 이전에 있던 앱들도 바로 로드 안됨
=> 다른 viewmodel을 바라보고 잇어서 메인화면에서 바로 갱신 안되던거는 해결됨. 이 문제는 딱 초기화면에서 잡히는 문제인듯?? 처음에 firestore에서 복원하고 나서 모든 ui에 적용되지 않아 생기는 문제 같기도

- 캘린더 화면에서 검색하는 토글 부분이 room에 저장된 것만 적용되는 것 같아서 복원 후 firebase가 가져온 앱들도 검색할 수 있게 바꾸기

---
- 앱 선택화면에서 카테고리별로 검색이 가능하게 하고싶어 전에 네가 앱별로 카테고리를 가지고 있다고 해서 그걸로 검색하면 될 것 같은데 그 기능을 추가해줄 수 있어?
- 앱 선택화면에서 정확히 일치해야만 앱 검색이 가능한데 "youtube" 앱이면 유튜브라고 쳤을때도 검색결과가 뜨게끔 만들 수 있을까? 그런 기능들은 firebase에 있을까?

- 친구창 닉네임 뜨게 하기, 프로그레스바 뜨게 하기
- 프로필 및 닉네임 및 아이디 등 설정


---

- 알림창, 리포트 기능
- 다른 앱에서 가져왔을때 폰에 없는 앱: com.instagram.android로 뜨는 문제(폰에 없는 앱은 아이콘에 ?로 뜨게 하기 등)

- 친구한테 streak 공유, 혹은 메인 화면 전체 공유 등 (설정 화면에 친구에게 오늘 통계 보여주기 등 추가)


후순위:
- 거품 뷰 vs 원형 그래프 비교해서 선택
- 백그라운드로 넘어갈때 말고 목표 앱 선택할때마다 firebase 업로드하게 바꾸기?
- 앱 카드 정렬에 목표시간 순도 추가
- 동기화 범위 줄이기: 동기화할때마다 전체 기록을 다 동기화 받는 듯 -> 로그인 할때만 전체 기록을 동기화 받고, 24시간 마다 복원할때는 최신 1일 값만 복원받기 등
- 알림 설정 화면에서 개별 앱만 알림 설정 가능하게 수정
- 지금 리마타에서 측정하는 주기가 어떻게 되는지, 최소 몇 분 단위로 알림을 보낼 수 있는 지 알아보고 시현용으로 수정할 수 있으면 수정(10초마다 알림 등)

