# PhilMission 진행 상황

마지막 갱신: 2026-09-29 저녁 (Claude가 Codex 작업을 이어받음. 에뮬레이터 검증 1차 진행 후 중단)

## 현재 상태: 개발용 debug APK 빌드 가능 (현장 배포 불가)
- `app/build/outputs/apk/debug/app-debug.apk` 생성 확인 (`assembleDebug` 성공).
- Kotlin 단위 테스트(`LogicTest`) 통과, Python 콘텐츠 테스트 5개 통과.
- 에뮬레이터 1차 검증 완료(아래 '에뮬레이터 검증'). 실기기 검증은 아직 없음.
- lint: 환경 문제로 완료 확인 못 함 (아래 참고).

## 에뮬레이터 검증 (2026-09-29)
환경: `.tools/avd`의 AVD `pm` (Pixel 5, Android 15 / API 35, x86_64 google_apis). SDK/에뮬레이터는 `.tools/android-sdk`에 설치됨.
```
# PowerShell, 프로젝트 루트에서
$env:ANDROID_AVD_HOME="$PWD\.tools\avd"
Start-Process .tools\android-sdk\emulator\emulator.exe -ArgumentList '-avd','pm','-no-audio','-gpu','swiftshader_indirect','-no-snapshot'
.tools\android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```
**확인됨(동작 OK)**: 앱 실행·탭 4개, 예배문 목록/주기도문 상세(독음·뜻), 회화 목록·상세·큰 글씨 모드(가로 회전 유지, 뒤로 가기로 복귀), 현장 자료 목록, 선교 일정 PDF 9쪽 열기·페이지 이동·가로 회전 시 페이지 유지, 준비 점검(해시 정상), 개인 연락처 저장·입력 검증(이름 누락 오류), 전화 앱 열기(번호 채워진 다이얼러, 자동 발신 없음), 강제 종료 후 개인 연락처 유지.

**검증 중 발견해 고친 버그**
1. PDF가 열리지 않음: 압축된 asset에 `openFd` 사용 → 스트림으로 cache에 복사하도록 수정.
2. PDF 열자마자 크래시("Document already closed"): DisposableEffect가 새로 연 문서를 닫음 → 효과 시작 시점 값을 캡처하도록 수정.

**고쳤지만 에뮬레이터에서 재확인 안 한 것 (빌드만 성공)**
- 가로 화면에서 PDF/이미지 버튼 줄이 화면 절반을 차지하던 문제 → 조작 버튼을 한 줄로 합침(`Viewers.kt`). 세로에서도 버튼이 한 줄에 다 들어가는지 확인 필요.
- 상태바 시계·아이콘이 흰색이라 안 보이던 문제 → `enableEdgeToEdge(SystemBarStyle.light)` 적용(`MainActivity.kt`).

**아직 화면 확인 못 한 것**
- 전도 탭(카드 이전/다음, 안내자용 설명 접기, 영접 기도문), 찬양 상세(코드 표시 토글, 영어 가창 곡), 예배 순서 JPG 확대·이동, 가정심방 말씀, 설정(글자 크기·화면 켜짐), 언어(영어) 전환 시 위치 유지, 즐겨찾기 추가/해제·재실행 유지, 번호 복사 버튼, 개인 연락처 수정·삭제.
- 최대 글자 크기·작은 화면, 비행기 모드 최초 실행, 이전 APK 위에 덮어설치 시 데이터 유지.
- 참고: 에뮬레이터 키보드 입력 시 Gboard 스타일러스 안내가 뜨고 다이얼로그가 키보드 위로 올라가 좌표가 바뀜(`adb input text`는 영문/숫자만 가능).

## 구현된 것
- 4탭 구조(예배/회화/전도/더보기), 화면 경로 스택을 saveable 문자열로 저장(회전 복원).
- 예배: 예배문 4종(주기도문·사도신경은 사용자 제공 예배순서 JPG의 따갈로그어 전사), 찬양 5곡(제공 2곡 전사 + 전통 찬송 3곡 영어 1절), 가정심방 말씀(HWPX 텍스트), 예배 순서 JPG 확대/이동 뷰어.
- 회화 50문장: 검색·카테고리·즐겨찾기(Room)·상세·큰 글씨 모드.
- 전도: 5장 카드 + 영접 기도문, 이전/다음, 안내자용 한국어 설명 접기.
- 더보기: 현장 자료(선교 일정 PDF 뷰어 = PdfRenderer, 페이지 이동·확대·회전 시 페이지 유지), 기본/개인 연락처(CRUD, ACTION_DIAL, 실패 시 복사), 준비 점검(SHA-256), 설정(글자 3단계, 화면 켜짐).
- Room DB v2 + 1→2 마이그레이션(파괴적 fallback 없음), DataStore 설정.

## 검수/배포 차단 사항 (의도된 것)
- `python tools/validate_content.py --release` 는 현재 **실패**해야 정상: 검수 미승인, 선교 일정 PDF 미정리.
- **선교 일정.pdf 원본에 가족별 신앙·건강·가정 사정이 있음** → 지금은 개발용으로 원본을 그대로 assets에 복사. 배포 전 개인 사정 제거본으로 교체하고 `scheduleSanitized`를 true로.
- 모든 따갈로그어/영어/독음/한국어 뜻은 초안(검수 대기). 십계명·가정 기도문·전도 카드·영어 번역은 직접 작성 초안.
- 찬양 3곡(Amazing Grace 등)은 영어 1절만, 따갈로그어 가창 가사 없음.
- 연락처: 숙소 주소 메모만 있음(전화번호 없음). 공관·병원·긴급 연락망은 공식 자료 확인 후 `content/extra.json`에 추가 필요.
- 가정심방 말씀 텍스트는 3개 언어 문단이 섞인 원문 그대로(문단별 대응·영어 번역 미작업).

## 아직 안 한 것
- 선택형 문장 음성(AudioController): 음원 파일이 없어 미구현. 제공된 M4A는 설교 전체 녹음이라 문장에 연결하지 않음.
- 릴리스 서명/서명 키 설정, 릴리스 APK, SHA-256 기록.
- 실기기 점검, 작은 화면·큰 글자·가로 화면 확인, 업데이트 덮어설치 테스트.
- 회화 50문장은 현지 협력자 검수 전.
- 전도 자료 채택 여부, 찬양 곡 최종 선정은 사용자 결정 필요.

## 내일 이어서 할 일 (제안 순서)
1. 에뮬레이터 부팅 → 최신 APK 설치 → 위 '재확인' 2건과 '아직 못 본 화면' 점검.
2. 발견 사항 수정 후 `testDebugUnitTest` 재실행.
3. 선교 일정 PDF 개인 사정 제거본 제작 → `content/`에 두고 prepare 스크립트가 사용하도록 변경, `scheduleSanitized=true`.
4. 기본 연락처(공관·병원·긴급망)를 공식 자료로 채우기 — 사용자가 번호/출처 제공 필요.
5. lint 문제 해결(아래), 릴리스 서명 키 생성(저장소 밖 보관), 검수 완료 후 `--release` 검증 통과 확인.

## 재개 방법
**GitHub(https://github.com/genej-ITC/PhilMission)에는 `DATA/`와 생성 assets(`app/src/main/assets/`)가 없다.** 사용자 제공 원본에 가족 개인 사정이 있고 저장소가 공개라서 제외했다. 다른 PC에서 이어가려면 `DATA/`를 별도로 복사한 뒤 `python tools/prepare_content.py`로 assets를 생성해야 빌드된다. 툴체인은 `tools/install_toolchain.py`로 재설치한다.

```
python tools/prepare_content.py          # content/*.tsv|json + DATA → app/src/main/assets
python tools/validate_content.py         # 초안 구조 검증
python -m unittest tests.test_content    # 프로젝트 루트에서
powershell -File build.ps1 -Tasks assembleDebug
powershell -File build.ps1 -Tasks testDebugUnitTest
```
`build.ps1 -Tasks a,b` 처럼 쉼표로 여러 작업 전달(공백 구분은 무시됨).
콘텐츠 원본은 `content/phrases.tsv`, `content/extra.json`. `app/src/main/assets`는 생성물.

## lint 관련
`lintDebug`가 `generateDebugAndroidTestLintModel`에서 Gradle 캐시 이동 오류(Windows 파일 잠금)로 실패하거나 오래 걸림. 원인 미해결.
