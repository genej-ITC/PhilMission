# PhilMission Implementation Plan

Goal: 오프라인 Android 선교 앱을 구현하고 중단 시 재개 가능한 기록을 남긴다.
Architecture: Compose UI, assets JSON, Room 사용자 데이터, DataStore 설정.
Tech Stack: Kotlin 2.1.20, AGP 8.9.2, Gradle 8.11.1, JDK 17, SDK 35, minSdk 26.
Spec: ../specs/2026-09-29-philmission-design.md

사용자가 설계 실행과 사용량 제한에 따른 중간 종료를 명시했다. 이번 세션에서 직접 순서대로 구현하고 추가 계획 승인 요청은 생략한다. Git 저장소가 없는 신규 디렉터리이므로 지정 경로에 작업하며 DATA 원본을 보존한다. 완료하지 못한 항목은 Progress.md에 명시한다.

## Global Constraints
- 한국어 UI, 따갈로그어/영어, 인터넷 권한 없음, minSdk 26.
- 자료 미검수를 검수 완료로 표시하지 않음. 릴리스는 검수 및 허가 확인 후.
- 말씀 텍스트, 예배순서 JPG, 일정 PDF.

## Tasks
- [ ] 1. .tools 개발 도구 설치, Gradle 프로젝트 및 빌드 스크립트. 설치 체크섬 기록.
- [ ] 2. tools/validate_content.py와 tests/test_content.py: 중복 ID, 누락 번역, 50문장 분포, 미검수 릴리스 실패를 테스트 먼저 작성하여 확인. assets/content.json 작성.
- [ ] 3. data/Content.kt: Sentence(id,category,tl,en,ko,pronunciationTl), Document(id,title,sentences), ContentBundle. 말씀 HWPX 문단 변환 및 제공 파일 보존.
- [ ] 4. data/UserStore.kt: Room favorites/contacts, DataStore language/font/keepAwake. 재실행 저장 및 ID 안정성 검증.
- [ ] 5. ui/PhilMissionApp.kt: 예배/회화/전도/더보기, 검색·필터·즐겨찾기·상세·큰 글씨·언어·글자 크기. 단위 테스트와 기기 테스트 구분.
- [ ] 6. 찬양 5곡, 예배문 4종, 전도 카드, 개인 연락처 CRUD, JPG/PDF 뷰어, 선택 음성, 해시 점검.
- [ ] 7. 전체 콘텐츠 검사, unit tests, lint, debug APK. 기기 검증 불가 시 명시. 최종 배포와 개발 APK 구분.
- [ ] 8. Progress.md에 결과·실패 로그·정확한 실행 명령·남은 요구사항 기록.

## Review Focus
- 빈 검색 결과와 긴 원문: 충돌 없이 스크롤·복귀 가능.
- 회전·프로세스 복원: 현재 읽기 위치/카드 보존.
- 잘못된 JSON·누락 파일: 안내 후 다른 기능 사용 가능.
- 미검수 초안: 릴리스 검증에서 반드시 거부.
- 업데이트: 기존 사용자 DB를 삭제하는 fallback 금지.
