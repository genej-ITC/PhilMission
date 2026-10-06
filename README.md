# PhilMission (필리핀 선교)

필리핀 단기선교 현장에서 쓰는 오프라인 우선 안드로이드 앱입니다. 예배문·찬양·말씀, 현지어 회화, 전도 자료, 음성 통역, 연락처를 한곳에 모았습니다. 현재는 **개발용 debug APK** 단계이며, 번역·독음은 현지 검수 전 초안입니다.

## 주요 기능
- **예배**: 주기도문·사도신경·십계명·가정 기도문(따갈로그어/영어, 한글 독음, 한국어 뜻), 찬양(코드 표시, 유튜브 링크), 말씀(가정심방 말씀, 예배 순서).
- **회화**: 50문장 검색·카테고리·즐겨찾기, 큰 글씨 보여주기, 음성 통역(한국어↔따갈로그어/영어).
- **전도**: 복음 안내 카드 5장과 영접 기도문.
- **더보기**
  - **자료 추가**: 찬양·말씀(PDF/JPG/PNG, 찬양은 유튜브 링크 선택), 선교 일정(PDF), 예배 순서(PDF/JPG/PNG)를 폰에서 직접 등록·삭제. 등록한 파일은 앱 내부 저장소에만 보관됩니다.
  - **연락처**: 기본 연락처(수정·기본값 복원 가능)와 개인 연락처.
  - **설정**: 글자 크기, 읽기 화면 켜짐 유지.

## 개인 정보와 저장소 구성
사용자 제공 원본(`DATA/`)과 그로부터 생성되는 `app/src/main/assets/`는 가족별 사정이 들어 있을 수 있어 **공개 저장소에 올리지 않습니다**(`.gitignore`). 선교 일정은 APK에 넣지 않고 사용자가 폰에서 등록합니다. 다른 PC에서 빌드하려면 `DATA/`를 별도로 복사한 뒤 아래 순서로 assets를 생성합니다.

## 빌드
```
python tools/install_toolchain.py        # JDK, Gradle, Android SDK 설치(.tools/)
python tools/prepare_content.py          # content/*.tsv|json + DATA → app/src/main/assets
python tools/validate_content.py         # 콘텐츠 구조 검증
python -m unittest tests.test_content
powershell -File build.ps1 -Tasks assembleDebug
powershell -File build.ps1 -Tasks testDebugUnitTest
```
결과물은 `app/build/outputs/apk/debug/app-debug.apk`(약 97MB, ML Kit 번역 라이브러리 포함)입니다. 콘텐츠 원본은 `content/phrases.tsv`, `content/extra.json`입니다.

## 폰에 설치
- USB 디버깅이 켜져 있으면 `adb install -r app-debug.apk`.
- 아니면 APK를 폰 `Download` 폴더로 복사한 뒤 "내 파일"에서 설치합니다(출처를 알 수 없는 앱 허용 필요). 기존 앱 위에 덮어설치해도 등록한 자료와 연락처는 유지됩니다.

## 문서
- 진행 상황·확인할 것·알려진 문제: [Progress.md](Progress.md)
- 기획: [PhilMission_PRD_v2.md](PhilMission_PRD_v2.md)
