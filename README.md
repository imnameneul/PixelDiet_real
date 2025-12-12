> 📢 ChatGPT에서 복붙한 설명인데 참고용으로 올립니다... 이대로 안 하셔도 됩니다.

# 1. Repository 역할 개요

UsageRepository는 현재 앱에서 “사용시간/일별 사용기록/알림 설정” 을 관리하는 데이터 레이어의 허브다.

## Repository의 책임

### 1. 오늘의 앱 사용시간 수집

- UsageStatsManager + UsageEvents를 이용하여 오늘 0시~현재까지 각 패키지별 사용시간(분)을 계산

### 2. 과거 N일(현재는 30일) 일별 사용기록 관리

- DailyUsage(date, appUsages: Map<String, Int>) 리스트로 보관

### 3. 목표시간(goalTime) & 스트릭(streak) 계산

- currentGoals: Map<String, Int>를 기준으로 streak 맵 계산

### 4. 알림 설정(NotificationSettings) 관리

- NotificationPrefs(SharedPreferences 래퍼)에 저장/로드

### 5. UI/ViewModel에 전달할 도메인 모델 제공

- LiveData<List<AppUsage>>

- LiveData<List<DailyUsage>>

- LiveData<NotificationSettings>

---

# 2. 현재 UsageRepository 인터페이스 정리

코드는 object UsageRepository 형태로 구현되어 있고, 외부에 공개되는 프로퍼티/함수는 다음과 같다.

## 2.1 공개 프로퍼티 (LiveData)
```
val appUsageList: LiveData<List<AppUsage>>
val dailyUsageList: LiveData<List<DailyUsage>>
val notificationSettings: LiveData<NotificationSettings>
```
appUsageList

- 타입: LiveData<List<AppUsage>>

- 내용:

  - 오늘 날짜 기준, 화면에 표시할 개별 앱 데이터 목록

  - 각 원소:

    - packageName: String

    - appLabel: String

    - icon: Drawable?

    - currentUsage: Int (오늘 사용시간, 분 단위)

    - goalTime: Int (분 단위 목표시간)

    - streak: Int (양수=연속 성공일, 음수=연속 실패일)

- DB 도입 후 요구사항:

  - 오늘 사용시간은 여전히 UsageStats 기반으로 계산하되,

  - goalTime, streak 계산 시 DB에 저장된 목표/히스토리를 활용할 수 있음. (히스토리는 4번 항목 참고)

  - ViewModel/UI에서 observe 방식은 그대로 유지.

dailyUsageList

- 타입: LiveData<List<DailyUsage>>

- 내용:

  - 과거 일별 전체 앱 사용 통계

  - 각 DailyUsage:

    - date: String ("yyyy-MM-dd")

    - appUsages: Map<String, Int> (key: packageName, value: usageMinutes)

- 현재:

    - UsageStatsManager.queryUsageStats(INTERVAL_DAILY, last30days, now) 결과를 파싱해서 메모리상에 구성.

    - 오늘 날짜에 대해서는 preciseUsageMap(정밀 계산 결과)을 덮어써서 사용.

- DB 도입 후 요구사항:

  - Room/Firestore에 일별 사용량을 영속 저장하고,

  - dailyUsageList는 DB에서 불러온 결과를 도메인 모델로 변환하여 제공.

  - 조회 범위(최근 30일, n개월 등)는 향후 확장 가능해야 함.
---
notificationSettings

- 타입: LiveData<NotificationSettings>

- 내용:

  - 개별 앱/전체 사용에 대해 50/70/100% 알림 여부

  - 100% 초과시 반복 알림 간격 등

- 현재:

  - NotificationPrefs(SharedPreferences 래퍼)에 저장/로드하고,

  - 초기화 시 loadNotificationSettings() 값을 LiveData에 반영.

- DB 도입 후 요구사항:

  - 알림 설정은 SharedPreferences 그대로 써도 OK (DB에 억지로 넣을 필요는 없음).

  - 혹은 Room/Firestore로 이관할 경우, 인터페이스는 그대로 유지:

    - notificationSettings 는 항상 최신 설정을 방출해야 함.
---
## 2.2 공개 메서드
① fun updateNotificationSettings(settings: NotificationSettings, context: Context)

- 역할:

  - 알림 설정 변경 시 호출

  - 현재 구현:

    - NotificationPrefs.saveNotificationSettings() 로 SharedPreferences에 저장

    - _notificationSettings.postValue(settings) 로 LiveData 업데이트

- DB 도입 후 요구사항:

- 알림 설정을 어디에 저장하든(SharedPrefs/Room/Firestore),
이 함수는 “설정 저장 + LiveData 최신화”라는 의미를 유지.

---

② fun updateGoalTimes(goals: Map<String, Int>)

- 파라미터:

  - goals: key = packageName, value = 목표시간(분)

- 현재 역할:

  - currentGoals 맵을 갱신

  - 기존 _appUsageList에 대해 goalTime 값을 재매핑하여 postValue()

- 한계:

  - 메모리 상에만 존재 → 앱 재시작 시 유실

  - 과거 목표 히스토리 없음 → 과거 캘린더/그래프가 “현재 목표 기준”으로만 재해석됨

- DB 도입 후 요구사항:

  - 이 함수는 **“현재 시점의 목표 설정 요청”**으로 취급

  - Repository 내부에서:

    - Room/Firestore에 목표 히스토리/현재 목표를 기록

    - 필요 시, “effectiveDate = 오늘” 같은 형태로 GoalHistory 테이블에 insert

- _appUsageList의 goalTime 필드도 DB 기준으로 재계산해서 LiveData 업데이트.

---

③ suspend fun loadRealData(context: Context)

⚠️ 현재 구조에서 가장 중요한 로직.

- 역할 (현재):

1. 오늘 0시~현재까지의 정밀 사용시간 계산

  - calculatePreciseUsage(context, startTime, endTime)

2. 최근 30일 UsageStats로 dailyUsageMap 생성

3. 오늘 데이터(todayUsageMap)를 dailyUsageMap에 덮어씀

4. DailyUsage 리스트로 변환 후 _dailyUsageList.postValue(...)

5. calculateStreaks(newDailyList, currentGoals) 로 어제까지 streak 계산

6. 추적 앱 목록(tracked_packages SharedPreferences)을 읽어와서

  - 오늘 사용 앱

  - 목표가 설정된 앱

  - 스트릭이 있는 앱
→ 이들을 모두 합쳐 newAppUsageList 구성 후 _appUsageList.postValue(...)
<br>
- DB 도입 후 추천 방향:

함수 이름/시그니처는 그대로 두되, 내부 로직을 다음처럼 역할 분리:

1. 로우 데이터 수집

  - 오늘 UsageEvents / UsageStats → 오늘 사용량

2. DB 갱신

  - Room/Firestore의 DailyUsage 테이블/컬렉션을 업데이트

3. 도메인 모델 구성

  - DB에서 DailyUsage를 읽어서 streak 계산

  - 목표 히스토리/추적앱 히스토리를 고려해 AppUsage 리스트 구성

4. LiveData 업데이트

  - _dailyUsageList, _appUsageList 에 최종 리스트 반영

이 함수는 여전히 “오늘 기준 데이터 새로 로드/동기화”를 의미해야 함.

---

## 2.3 내부 보조 함수 (참고용)

DB/사용량 로직에 중요한 의미가 있어서, DB 담당이 구현을 이해할 때 참고하면 좋음.

calculateStreaks(dailyList: List<DailyUsage>, goals: Map<String, Int>): Map<String, Int>

- 역할:

  - “어제까지의” 스트릭(연속 성공/실패 일수)을 앱별로 계산

- 주요 로직:

  - 오늘(todayStr)을 제외한 날짜들(pastDays)을 **내림차순(가장 최근 → 과거)**으로 순회

  - 각 앱별로:

    - 목표(goal)가 0이면 streak=0

    - 첫 날 기준으로 성공/실패 여부 결정(wasSuccess)

    - 연속해서 성공/실패가 이어지는 날 수 카운트

    - 최종적으로 성공이면 +n, 실패이면 -n

DB 도입 후:

- 과거 목표 시간이 “히스토리 기반”이 되면,
이 함수는 “각 날짜에서 유효한 goal”을 DB에서 가져와야 함.

- streak 계산 자체는 Repository/Domain 레벨에 남겨두는 게 좋다.

---
calculatePreciseUsage(context, startTime, endTime): Map<String, Int>

- 역할:

  - UsageEvents를 순회하면서:

    - MOVE_TO_FOREGROUND / ACTIVITY_RESUMED 시점 기억

    - MOVE_TO_BACKGROUND / ACTIVITY_PAUSED 시점에 duration 누적

    - SCREEN_NON_INTERACTIVE(화면 꺼짐) 시 모든 app 정산

  - 마지막까지 foreground인 앱은 endTime 기준으로 duration 정산

  - 최종 결과: Map<packageName, minutes>

- DB 도입과는 직접 관계없지만,
“오늘 사용시간이 디지털 웰빙과 최대한 비슷하게 나오게 하는 핵심 로직”이라
DB 담당도 전체 동작 이해용으로 한 번 읽어두면 좋음.

---

# 3. Room / Firestore 담당자에게 강조하고 싶은 점

## 1. UI / ViewModel 인터페이스 유지

- UsageRepository.appUsageList, dailyUsageList, notificationSettings 는 그대로 남기고,

- 가능하면 함수 이름도 유지 (loadRealData, updateGoalTimes, updateNotificationSettings).

## 2. 도메인 모델 vs DB 모델 분리

- AppUsage, DailyUsage는 UI/도메인용 모델로 유지

- Room/Firestore 쪽에는 별도의 Entity/DTO를 둔 후
Repository에서 변환해서 LiveData로 노출

## 3. 과거 통계/캘린더/스트릭을 위한 히스토리 구조

- 추가로 설계해야 할 테이블/컬렉션 (요구사항 수준):

- GoalHistory

  - effectiveDate, packageName?, goalMinutes

- TrackingHistory

  - effectiveDate, trackedPackages (혹은 N:M 구조)

- 캘린더/그래프/스트릭 계산 시:

  - 각 날짜에 대해 “그 날짜에 유효한 goal + trackedApps” 를 계산할 수 있어야 함.

## 4. Worker와의 연동

- UsageCheckWorker는 현재 Repository/SharedPreferences에 의존해서
사용시간/목표/알림 상태를 결정

- 나중에 DB에서 “오늘까지의 사용/목표 히스토리”를 읽어야 할 수도 있으므로,
Worker에서 사용할 수 있는 Repository API도 같이 고려.

---

# 4. 히스토리(목표시간 / 추적 앱) 도입 배경 & 요구사항
## 4.1 왜 히스토리가 필요한가?

현재 코드에서는:

- 목표시간(goalTime)
  - AppUsage.goalTime은 항상 “현재 설정된 목표시간”을 기준으로 계산됨

  - 캘린더/그래프/스트릭 계산 시에도 현재 goalTime을 사용

- 추적 앱 목록(trackedPackages)

  - 과거에 어떤 앱을 추적하고 있었는지에 대한 기록은 없음

  - 통계 조회 시 “지금 추적 중인 앱” 기준으로 과거 사용량을 재해석

이 구조의 문제점:

1. 과거 목표시간이 보존되지 않음

  - 예: 6/1~6/10은 목표 60분, 6/11부터 120분으로 올렸다고 해도
→ 캘린더/그래프/스트릭 계산은 “항상 현재 목표(120분)”로 과거까지 재평가됨

- 즉, “그 당시 기준으로 목표를 얼마나 잘 지켰는지”를 다시 볼 수 없음

2. 과거 추적 앱 상태를 재현할 수 없음

- 예: 6/1~6/15는 인스타+유튜브를 추적하다가
6/16부터는 인스타만 추적하도록 바꿨을 때,
→ 6/5 기준 통계를 보고 싶어도,
실제로 그때 어떤 앱들을 “추적 대상으로 보고 있었는지” 복원할 수 없음

- 과거 통계 화면이 “지금 추적앱 기준”으로만 계산됨

3. 결과적으로,

- 캘린더 색깔(SUCCESS/WARNING/FAIL)

- 월별 그래프

- 스트릭(🔥/💀)
이 모든 통계가 **“과거 당시 상태”가 아니라 “현재 설정을 이용해 재계산된 값”**이 된다.

> → 따라서, **“과거 상태를 그대로 재현하는 통계/캘린더”**를 만들기 위해서는
목표시간 / 추적앱 설정의 히스토리를 날짜 기준으로 저장할 수 있는 구조가 필요하다.

## 4.2 필요한 히스토리의 종류

DB 레이어에서 새로 도입해야 하는 히스토리는 크게 두 가지다.

### (1) 목표시간 히스토리 (GoalHistory)

목적:

- “특정 날짜에, 특정 앱의 목표시간이 얼마였는지”를 복원하기 위함

- 전체 목표시간(선택된 앱들의 총 목표)과, 개별 앱 목표시간 모두 포함 가능

필요 필드 (요구사항 수준):

- effectiveDate: String

  - "YYYY-MM-DD" 형식

  - 이 날짜부터 이후로 목표가 적용된다는 의미

- packageName: String?

  - null이면 “전체 목표시간”을 의미

  - null이 아니면 특정 앱(packageName)의 목표

- goalMinutes: Int

  - 분 단위 목표시간

예시 시나리오:

- 2025-06-01: 전체 목표 180분

- 2025-06-05: 인스타그램 목표 60분

- 2025-06-10: 인스타그램 목표 120분으로 상향

이 경우:

- 6/01~6/09: 인스타 목표 60분으로 평가

- 6/10 이후: 인스타 목표 120분으로 평가

- 캘린더/그래프/스트릭 계산 시,
각 날짜별로 해당 날짜보다 과거의 GoalHistory 중 가장 마지막 기록을 찾아서 사용해야 함.

---
### (2) 추적 앱 히스토리 (TrackingHistory)

목적:

- “특정 날짜에, 사용자가 어떤 앱들을 ‘추적 대상으로 설정’했는지”를 복원하기 위함

필요 필드 (요구사항 수준):

- effectiveDate: String

  - "YYYY-MM-DD" 형식

  - 이 날짜부터 이후로 해당 설정이 적용

- trackedPackages: List<String>

  - 또는 N:M 구조로 쪼개서 설계해도 무방 (예: 별도 relation 테이블)

예시 시나리오:

- 2025-06-01: 추적앱 = [네이버웹툰, 인스타그램]

- 2025-06-15: 추적앱 = [인스타그램]

이 경우:

- 6/01~6/14: 통계/캘린더/그래프/스트릭은 네이버웹툰+인스타그램 기준

- 6/15 이후: 인스타그램 기준

캘린더/그래프 계산 시에는 각 날짜에 대해:

- 해당 날짜보다 과거의 TrackingHistory 중 가장 마지막 기록을 찾아서
**그 날의 “유효한 tracked 앱 목록”**으로 사용.

---

## 4.3 히스토리 구조를 사용한 조회 요구사항

DB에 히스토리를 저장해두면, Repository/Domain 레벨에서는 아래와 같은 조회를 지원할 수 있어야 한다.

### (A) 캘린더 하루 색칠 (SUCCESS/WARNING/FAIL)

입력:

- targetDate: String (YYYY-MM-DD)

- selectedFilter: String?

  - null이면 “전체 보기”

  - 아니면 특정 앱(packageName)

필요 조회(Repository 내부):

#### 1. 해당 날짜의 사용 시간

- DailyUsage에서:

  - 전체 보기 → trackedApps 기준으로 일별 합산

  - 앱별 보기 → 해당 packageName 사용시간

#### 2. 해당 날짜의 목표시간

- 전체 보기:

  - GoalHistory에서 packageName = null (전체) 기준으로
effectiveDate <= targetDate 인 기록 중 가장 최신 기록

  - 앱별 보기:

    - GoalHistory에서 해당 packageName 기준으로
마찬가지로 가장 최신 기록

#### 3. (전체 보기일 경우) 해당 날짜의 추적앱 목록

- TrackingHistory에서 effectiveDate <= targetDate 중 가장 최신 기록

- 이 리스트에 포함된 앱만 합산하여 사용시간 계산

위 데이터를 기반으로:

- usage / goal 비율에 따라 DayStatus 결정

  - usage > goal → FAIL

  - usage > goal * 0.7 → WARNING

  - 나머지 → SUCCESS
 
---

### (B) 월별 그래프 (이번 달 사용시간 + 목표선)

입력:

- year, month

- selectedFilter: String?

필요 조회:

- 해당 월의 모든 날짜에 대해:

1. DailyUsage에서 하루 사용량

2. 그 날짜에 유효한 목표시간 (GoalHistory)

3. (전체 보기일 경우) 그 날짜에 유효한 추적앱 목록 (TrackingHistory)

그래프:

- X축: 일(dayOfMonth)

- Y축: 사용시간(분)

- 목표선: 해당 월에 대해 “기준이 되는 목표”

  - 현재 구현에서는 “현재 목표”를 쓰고 있으나,
향후에는 “해당 월 평균 목표” 등으로 확장 가능

---

### (C) 스트릭(streak) 계산

입력:

- dailyUsageList (과거 N일)

- 히스토리:

  - GoalHistory

  - TrackingHistory (필요시)

현재 calculateStreaks() 함수는:

- 각 날짜에서 goal을 현재값으로 평가하고 있음

- 히스토리 도입 후에는:

  - 각 날짜마다 “그 날짜의 goal”을 GoalHistory에서 찾아서 계산해야 함

  - 필요하다면 “그 날짜에 추적 대상이었는지 여부”도 TrackingHistory 기준으로 판단 가능

> ⚠ streak 계산 자체는 여전히 Repository/Domain 레벨에서 수행되며,
DB 담당자는 “해당 날짜의 goal / trackedApps를 가져올 수 있는 DAO/쿼리”를 제공해주면 된다.
---
## 4.4 히스토리는 현재 코드에 없는 부분이며, DB 쪽에서 도입만 해두면 됨

- 현재 코드 상태
  - 목표시간 히스토리 / 추적앱 히스토리 테이블은 존재하지 않음

  - 모든 통계는 “현재 설정값” 기준으로 과거를 재계산하는 구조

- DB 담당자의 역할

  - 위에서 설명한 GoalHistory, TrackingHistory 개념을
Room Entity / DAO / Firestore 컬렉션 형태로 도입해두기

  - “effectiveDate 기준으로, 해당 날짜에 유효한 기록을 가져올 수 있는 쿼리”를 지원

- 앱 로직(ViewModel/Repository)은 이후에 살을 붙일 예정

- 히스토리를 언제 insert할지:

  - 목표를 변경할 때?

  - 자정에 하루를 마감할 때?

- 히스토리를 어떻게 조합해서 캘린더/그래프/스트릭에 반영할지: Repository/ViewModel 담당자가 구현
