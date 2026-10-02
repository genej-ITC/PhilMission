# PhilMission 진행 상황

마지막 갱신: 2026-10-02 (Claude. 실기기(삼성 S25 FE)에 debug APK 설치해 확인 시작. 통역 한국어↔영어, 선교 일정·예배 순서 앱 내 등록 기능 추가)

## 현재 상태: 개발용 debug APK 빌드 가능 (현장 배포 불가)
- `app/build/outputs/apk/debug/app-debug.apk` 빌드 성공 (약 97MB. ML Kit 번역 라이브러리 때문). 에뮬레이터 `pm`과 실기기(S25 FE)에 설치해 봄.
- Python 콘텐츠 테스트 5개, `validate_content.py`(초안 구조) 통과. Kotlin 단위 테스트(`LogicTest`)는 어제 통과, 오늘은 재실행 안 함.
- lint: 환경 문제로 완료 확인 못 함 (아래 참고).

## 10/2 바뀐 것
- **통역 한국어↔영어 추가**(`Translate.kt`): 통역 화면 위의 칩으로 "한국어↔따갈로그어 / 한국어↔영어" 선택. 버튼은 선택한 쌍에 맞춰 "한국어로 말하기 / 영어로 말하기"(영어 음성 인식 `en-US`, ML Kit 영어↔한국어). 듣는 중에는 쌍 변경 불가, 바꾸면 이전 결과 삭제. 실기기에서 영어 기능 확인함.
- **선교 일정·예배 순서를 앱에 내장하지 않고 사용자가 등록**: 더보기 → 현장 자료에서 "선교 일정 PDF 등록"(PDF만) / "예배 순서 등록"(PDF·JPG·PNG). 등록 전에는 카드 안에 등록 안내 문구와 버튼, 등록 후에는 등록일과 "다른 파일로 바꾸기"·"삭제" 버튼(모두 카드 안).
  - 고른 파일은 열어 보고(PDF는 `PdfRenderer` 1쪽 이상, 이미지는 디코딩) 앱 내부 저장소(`filesDir`)에 복사. 실패하면 기존 등록본 유지. 코드는 `Viewers.kt`의 `importUserDoc`/`userDocFile`/`deleteUserDoc`, `Screens.kt`의 `DocsScreen`. 저장 이름은 `schedule.pdf`, `service-order-custom.pdf|img`(등록본은 자료당 하나, 형식을 바꾸면 이전 형식 삭제).
  - 큰 사진은 긴 변 4096px로 줄여 읽고 EXIF 회전 반영. 오프라인에서도 열림.
  - 앱 실행 시 예전 버전이 캐시에 복사해 둔 선교 일정 PDF(`*_schedule.pdf`)를 지움(개인 정보 잔존 방지).
  - 개인 사정이 있는 원본이 APK에 들어가지 않으므로 `scheduleSanitized`와 준비 점검의 "개인 사정 포함 원본" 경고, 릴리스 검증의 sanitized 항목을 삭제. `content.json`의 `files`(해시 점검)는 비어 있고 준비 점검 화면에 선교 일정 등록 여부만 표시.
  - **출국 전에 반드시 폰에서 선교 일정·예배 순서를 한 번 등록해 둘 것**(미등록이면 안내 문구만 나옴).
- `prepare_content.py`는 더 이상 `선교 일정.pdf`, `가정심방 예배순서.jpg`를 assets에 복사하지 않고 남아 있던 `schedule.pdf`·`service-order.jpg`는 지움. 가정심방 말씀 HWPX는 여전히 내장.

## 9/30 바뀐 것
- **가정 기도문** 교체: `DATA/가정심방 기도문_따갈로그어 6주차.docx` 기준 4문장(따갈로그어·한글 음독·한국어 뜻). 영어는 문서 하단 영문을 문장별로 나눈 것. 문서 원문의 "까닝랑/까닐랑" 표기 불일치는 그대로 둠.
- **음독 표시 규칙**: 한글 음독은 초록색(주기도문과 동일 primary)·본문 크기, 따갈로그어 원문·한국어 뜻은 0.75배·촘촘한 줄 간격. 예배문, 회화 상세, 찬양, 복음 카드, 가정심방 말씀에 적용. 영어 모드에서는 본문을 줄이지 않음.
- **가정심방 말씀**: 원문이 한국어/따갈로그어/음독 블록이 섞여 있어, "따갈로그어(라틴 문자 우세) 블록 뒤에 나오는 같은 개수의 한글 블록 = 음독"으로 판정(`Screens.kt`의 `classifyMessageBlocks`). 전체 블록 시험 결과 정확했으나, 원문 구조가 바뀌면 재확인 필요.
- **복음 안내 카드**: 카드 2~5번에 성경 구절(요 3:16, 롬 3:23, 롬 5:8, 요 1:12)을 따갈로그어·음독·한국어로 표시(`content/extra.json`의 `verse`). 카드 본문 아래 한국어 뜻을 항상 표시. "안내자용 한국어 설명" 버튼은 복음 카드·영접 기도문 모두 삭제(메모 텍스트는 json에 남아 있고 화면엔 안 나옴).
- **목록 화면**: 예배문 카드의 "N문장" 문구 삭제. 회화 목록에 초록색 한글 음독 추가.
- **찬양**: 3곡(Amazing Grace, What a Friend, Jesus Loves Me) 삭제 → 할리낫 사마사마, 마부띵 디요스 2곡. 릴리스 검증의 찬양 개수 기준도 5→2로 수정(`tools/validate_content.py`).
- **유튜브 링크**: 두 곡 페이지에 "유튜브에서 듣기" 버튼(링크는 `content/extra.json`의 곡별 `youtube`). 인터넷이 없으면 비활성화(연결 상태 실시간 반영). `ACCESS_NETWORK_STATE` 권한 추가.
- **통역 기능(신규)**: 회화 탭 맨 위 "음성 통역" 버튼 → 통역 화면(`Translate.kt`). 한국어/따갈로그어 버튼을 누르고 말하면 인식된 글자와 번역문이 실시간으로 표시. 음성 인식 = 안드로이드 `SpeechRecognizer`(ko-KR / fil-PH), 번역 = ML Kit 온디바이스 번역(`com.google.mlkit:translate:17.0.3`). 인터넷이 없으면 버튼 비활성화. `RECORD_AUDIO`, `INTERNET` 권한 추가.
- 참고: `content/extra.json`은 오늘 저장 방식이 바뀌어 줄 모양(들여쓰기) diff가 크게 나옴. 내용 변경 아님.

## 실기기(안드로이드 폰)에서 꼭 확인해야 할 것
에뮬레이터로는 확인할 수 없거나 확인하지 못한 항목.

**통역 기능 (최우선)**
- [ ] 마이크 권한 허용 후 실제로 말하면 글자가 나오는지 (한국어 / 따갈로그어 각각)
- [ ] 한국어→따갈로그어, 따갈로그어→한국어 번역이 나오는지, 말하는 동안 실시간으로 바뀌는지
- [ ] (영어는 확인됨) 첫 사용 때 번역 모델(ko·tl·en 약 수십 MB)을 내려받는지, 받은 뒤에는 인터넷이 없어도 번역이 되는지
- [ ] 인터넷이 없을 때 버튼이 비활성화되고 안내 문구가 나오는지 (비행기 모드)
- [ ] (검토 중) 비행기 모드 통역: 번역은 모델을 받아 두면 오프라인 가능하나, 음성 인식은 현재 인터넷 필수라 버튼을 막고 있음. 구글 앱의 오프라인 음성 인식 언어팩(한국어·영어)을 받으면 `EXTRA_PREFER_OFFLINE`로 오프라인 인식이 가능할 수 있음(따갈로그어는 불확실). 먼저 폰에서 오프라인 언어팩이 되는지 확인 후 결정.
- [ ] 따갈로그어(fil-PH) 음성 인식이 기기에서 지원되는지 — 안 되면 "이 기기에서 해당 언어 음성 인식을 쓸 수 없습니다"가 나옴. 구글 앱/음성 서비스 버전에 따라 다름.
- [ ] 번역 품질(교회 용어·존댓말). 부족하면 클라우드 번역 API 전환 검토(API 키 유출 방지를 위해 중계 서버 필요).
- 출국 전 현지 사용자에게 알릴 점: 음성은 구글 음성 인식으로 전송될 수 있음(화면에 안내 문구 있음).

**유튜브 링크**
- [ ] 두 곡 모두 버튼이 유튜브 앱/브라우저로 열리고 영상이 재생되는지 (에뮬레이터에서는 Chrome 첫 실행 화면까지만 확인)
- [ ] 마부띵 디요스 페이지 링크(화면으로 못 봄)

**오늘 바꾼 화면 중 눈으로 못 본 것**
- 복음 안내 카드 3~5번(성경 구절), 영접 기도문의 최종 배치, 찬양 상세의 음독 크기·줄 간격, 회화 상세·예배문 상세(영어 모드 포함), 가정심방 말씀 끝까지 스크롤(음독 분류 오류 없는지)
- 글자 크기 "크게" 설정에서 긴 음독 줄바꿈이 어색하지 않은지, 작은 화면

**10/2에 추가한 등록 기능 (폰에서 확인할 것)**
- [ ] 선교 일정: PDF 등록·열기(페이지 이동·확대), 비행기 모드에서 열림, 다른 PDF로 바꾸기, 삭제, PDF 아닌 파일 거부
- [ ] 예배 순서: JPG·PNG·PDF 각각 등록, 세로 사진 방향, 형식 바꿔 재등록, 삭제, 등록 전 안내 문구
- [ ] 이전 버전 위에 덮어설치해도 정상 동작(예전 캐시 정리)

**어제부터 남은 것**
- 상태바 시계·아이콘이 잘 보이는지(`enableEdgeToEdge`), PDF/이미지 조작 버튼이 세로·가로에서 한 줄에 들어가는지
- 설정(글자 크기·화면 켜짐), 즐겨찾기 추가/해제·재실행 유지, 번호 복사, 개인 연락처 수정·삭제
- 비행기 모드에서 최초 실행, 이전 APK 위에 덮어설치 시 데이터 유지
- 전화 걸기: 실제 통신 환경에서 다이얼러 동작

## 에뮬레이터 검증 이력
환경: `.tools/avd`의 AVD `pm` (Pixel 5, Android 15 / API 35, x86_64 google_apis). SDK/에뮬레이터는 `.tools/android-sdk`.
```
# PowerShell, 프로젝트 루트에서
$env:ANDROID_AVD_HOME="$PWD\.tools\avd"
Start-Process .tools\android-sdk\emulator\emulator.exe -ArgumentList '-avd','pm','-no-audio','-gpu','swiftshader_indirect','-no-snapshot'
.tools\android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```
- 부팅 직후 "System UI isn't responding" 팝업이 자주 뜸(소프트웨어 렌더링 때문). Wait를 누르면 됨. 앱 문제 아님.
- 스크린샷은 PowerShell 리다이렉트(`>`)가 바이너리를 깨뜨림 → Git Bash에서 `adb exec-out screencap -p > 파일.png` 사용.
- 9/29 확인: 앱 실행·탭, 예배문/주기도문, 회화 목록·상세·큰 글씨, 현장 자료, 선교 일정 PDF, 준비 점검, 개인 연락처 저장·검증, 전화 앱 열기, 강제 종료 후 연락처 유지. 그때 고친 버그: PDF 열기 실패(압축 asset `openFd`), PDF 크래시(DisposableEffect).
- 10/2 실기기(S25 FE)에서 통역 영어 기능, 현장 자료 카드 버튼 위치 확인.
- 9/30 확인: 가정 기도문, 가정심방 말씀 음독 분류·색·크기, 예배 목록, 회화 목록 음독, 복음 카드 1·2번, 영접 기도문, 찬양(할리낫 사마사마) 유튜브 버튼의 온라인/오프라인 전환, 통역 화면·권한 요청·마이크 켜짐(음성 인식 결과는 못 봄).

## 구현된 것
- 4탭 구조(예배/회화/전도/더보기), 화면 경로 스택을 saveable 문자열로 저장(회전 복원).
- 예배: 예배문 4종(주기도문·사도신경은 사용자 제공 예배순서 JPG 전사, 가정 기도문은 6주차 docx), 찬양 2곡(코드 표시 토글, 유튜브 링크), 가정심방 말씀(HWPX 텍스트), 예배 순서(사용자가 등록한 JPG/PNG/PDF를 이미지 확대/이동 뷰어 또는 PDF 뷰어로 열람).
- 회화 50문장: 검색·카테고리·즐겨찾기(Room)·상세·큰 글씨 모드, 음성 통역(한국어↔따갈로그어/영어).
- 전도: 복음 안내 카드 5장(성경 구절 포함) + 영접 기도문, 이전/다음.
- 더보기: 현장 자료(선교 일정·예배 순서는 앱에서 등록, 가정심방 말씀은 내장), 기본/개인 연락처(CRUD, ACTION_DIAL, 실패 시 복사), 준비 점검(SHA-256), 설정(글자 3단계, 화면 켜짐).
- Room DB v2 + 1→2 마이그레이션(파괴적 fallback 없음), DataStore 설정.

## 검수/배포 차단 사항 (의도된 것)
- `python tools/validate_content.py --release` 는 현재 **실패**해야 정상: 검수 미승인.
- **선교 일정.pdf 원본에 가족별 신앙·건강·가정 사정이 있음** → 앱에 넣지 않고 사용자가 폰에서 직접 등록(10/2 변경). 개인 정보가 APK에 들어가지 않음. 다만 `DATA/`의 가정심방 말씀 HWPX는 아직 내장이므로 배포 전 그 내용에 개인 사정이 없는지 확인할 것. APK를 여러 사람에게 보내기 전에도 확인.
- 모든 따갈로그어/영어/독음/한국어 뜻은 초안(현지 검수 대기).
- **성경 구절 4개(따갈로그어 Ang Dating Biblia 1905, 영어 WEB, 한국어 개역한글)는 Claude가 기억으로 입력**한 것 — 실제 성경 본문과 대조 필수. 음독도 초안.
- 십계명·전도 카드 문장·영어 번역은 직접 작성 초안.
- 연락처: 숙소 주소 메모만 있음(전화번호 없음). 공관·병원·긴급 연락망은 공식 자료 확인 후 `content/extra.json`에 추가 필요.
- 가정심방 말씀 텍스트는 3개 언어 문단이 섞인 원문 그대로(영어 번역 미작업).
- 통역은 "참고용" 기능. 자동 번역 오류 가능성을 화면에 안내 중.

## 아직 안 한 것
- 선택형 문장 음성(AudioController): 음원 파일이 없어 미구현. 제공된 M4A는 설교 전체 녹음이라 문장에 연결하지 않음.
- 릴리스 서명/서명 키 설정, 릴리스 APK, SHA-256 기록. 릴리스 APK 크기(ML Kit 포함) 확인.
- 회화 50문장 현지 협력자 검수.
- 전도 자료 채택 여부는 사용자 결정 필요.

## 다음에 이어서 할 일 (제안 순서)
1. 폰에 debug APK 설치 → 위 '실기기에서 꼭 확인' 체크리스트 점검, 특히 통역.
2. 발견 사항 수정. `testDebugUnitTest` 재실행.
3. 성경 구절 4개를 실제 따갈로그어 성경과 대조·수정.
4. 가정심방 말씀(내장 HWPX 텍스트)에 개인 사정이 없는지 확인. 필요하면 선교 일정·예배 순서처럼 앱 내 등록 방식으로 전환 검토.
5. 기본 연락처(공관·병원·긴급망)를 공식 자료로 채우기 — 사용자가 번호/출처 제공 필요.
6. lint 문제 해결, 릴리스 서명 키 생성(저장소 밖 보관), 검수 완료 후 `--release` 검증 통과 확인.

## 재개 방법
**GitHub(https://github.com/genej-ITC/PhilMission)에는 `DATA/`와 생성 assets(`app/src/main/assets/`)가 없다.** 사용자 제공 원본에 가족 개인 사정이 있고 저장소가 공개라서 제외했다. 다른 PC에서 이어가려면 `DATA/`를 별도로 복사한 뒤 `python tools/prepare_content.py`로 assets를 생성해야 빌드된다. 툴체인은 `tools/install_toolchain.py`로 재설치한다.

```
python tools/prepare_content.py          # content/*.tsv|json + DATA → app/src/main/assets
python tools/validate_content.py         # 초안 구조 검증
python -m unittest tests.test_content    # 프로젝트 루트에서
powershell -File build.ps1 -Tasks assembleDebug
powershell -File build.ps1 -Tasks testDebugUnitTest
```
`build.ps1 -Tasks a,b` 처럼 쉼표로 여러 작업 전달(공백 구분은 무시됨). 옵션(`--no-parallel` 등)은 작업 이름으로 합쳐지므로 넘길 수 없음.
콘텐츠 원본은 `content/phrases.tsv`, `content/extra.json`. `app/src/main/assets`는 생성물.

**폰에 설치**: USB 디버깅이 되면 `.tools\android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk`. 안 되면 APK를 폰 `Download` 폴더로 복사해 "내 파일"에서 설치(Windows에서 MTP 복사는 같은 이름으로 다시 쓰면 조용히 실패해서 매번 새 이름으로 복사했음). 삼성 폰은 개발자 옵션이 설정 맨 아래에 있고, adb에 기기가 안 잡히면 USB 디버깅/드라이버 문제.

## 빌드 문제: Gradle 캐시 파일 잠금 (Windows)
- 9/30 ML Kit 의존성을 추가한 뒤 `build.ps1`(캐시 = `.tools/gradle-cache`)로 빌드하면 `checkDebugAarMetadata`에서 "Could not move temporary workspace ... to immutable location" 오류로 반복 실패했다. 데몬 종료·임시 폴더 삭제·`--no-daemon`·파일 감시 끄기·재시도 5회 모두 소용없었다.
- **해결(우회)**: Gradle 캐시를 짧은 경로로 옮겨 빌드에 성공했다(첫 빌드 6분, 의존성 재다운로드). 이후 빌드는 아래처럼 한다.
```
# PowerShell, 프로젝트 루트에서
$jdk = Get-ChildItem -Directory ".tools/jdk-*" | Select -First 1
$env:JAVA_HOME=$jdk.FullName; $env:ANDROID_HOME="$PWD/.tools/android-sdk"; $env:GRADLE_USER_HOME="C:\gh"; $env:PATH="$env:JAVA_HOME/bin;$env:PATH"
.tools/gradle-8.11.1/bin/gradle.bat assembleDebug --no-daemon --console=plain
```
- 원인은 확정하지 못했다(경로 길이 또는 백신/색인의 잠금 추정). `build.ps1`은 아직 `.tools/gradle-cache`를 쓰므로, 필요하면 `C:\gh`를 쓰도록 고칠 것. 아래 lint 문제도 같은 종류로 보인다.
- 이 오류가 나면 `.tools/gradle-cache/caches/8.11.1/transforms` 안의 `-xxxxxxxx-xxxx-` 형태 임시 폴더는 지워도 된다.

## lint 관련
`lintDebug`가 `generateDebugAndroidTestLintModel`에서 Gradle 캐시 이동 오류(Windows 파일 잠금)로 실패하거나 오래 걸림. 원인 미해결. 위 '빌드 문제'와 같은 종류일 가능성이 높으므로 `GRADLE_USER_HOME=C:\gh`로 시도해 볼 것.
