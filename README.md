# Signet Campus

교육기관의 디지털 접근성·실무 역량을 증거 기반으로 인정하는 Open Badges 서비스.
상태: 조사 및 라이브러리 배포 복구 단계. 아직 실행 가능한 서비스가 아니다.

## 근거와 범위

분석일: 2026-09-14. 소스 기준: core `0502e41ef7e66f48af21fe0363071b91e836b511`, starter `c556641173f25a2c43edf364119e3f1600560da9`. 조사 깊이: standard.

Bowdoin은 Digital Accessibility Awareness 배지를 운영하며 배지에 발급자·취득 조건·역량 정보를 담아 공유 및 온라인 검증을 제공한다([공식 사례](https://bowdoin.teamdynamix.com/TDClient/1814/Portal/KB/Article/157576/What-is-Digital-Badging)). 학습 경로 등록 알림과 기관을 떠난 후에도 유지되는 개인 계정을 설명한다([공식 경로 안내](https://bowdoin.teamdynamix.com/TDClient/1814/Portal/KB/Article/157578/Understand-the-Digital-Badge-Learning-Pathway-Subscription-Email)).

선택 주제는 이 사례에서 착안한 가상 기관의 디지털 접근성 교육·역량 인증이다. Bowdoin의 내부 시스템을 복제하거나 실제 심사 정책을 알고 있다고 주장하지 않는다. 증거 심사, 철회, 감사와 분산 처리 요건은 본 프로젝트의 추가 설계다. 공식 인증 취득과 전체 Open Badges 플랫폼 기능 구현은 별개이며, 인증을 주장하지 않는다.

## 개발 계약

1. 사례/명세 → 요구사항 ID → 인수 조건 → 테스트 매트릭스를 먼저 갱신한다.
2. 실패하는 테스트를 확인한 후 최소 구현한다.
3. 단위·통합·계약·보안·브라우저 검증 중 변경에 필요한 검사를 실행한다.
4. ponytail-review와 정확성 리뷰를 분리하고, 타당한 권고만 적용한다.
5. 검증 결과와 미완료 항목을 기록한 뒤 Conventional Commit으로 커밋한다.
6. 다음 수직 기능을 계획한다. 인수 조건을 충족하지 않은 기능을 완료로 표시하지 않는다.

라이브러리 브랜치: 신규 명세 `feature/*`, 결함 `fix/*`, 구조 개선 `refactor/*`. main 직접 병합 금지. JitPack 의존성은 검증한 불변 커밋으로 고정한다.

## 테스트 매트릭스와 인수 조건

| ID | 기능/출처 | 인수 조건 | 검증 | 상태 |
|---|---|---|---|---|
| LIB-01 | JitPack 배포 | 원격 POM/JAR 및 전이 의존성으로 빈 소비자 프로젝트 빌드 성공 | Gradle 배포·소비자 smoke | starter 완료; 최신 core 소비 연결 예정 |
| CAT-01 | 사례: 역량·취득 조건 | 발급자가 배지와 평가 기준을 등록하고 학습자가 열람 | Kotest/API/브라우저 | 예정 |
| PATH-01 | 사례: 학습 경로 | 등록·선행 조건·진행률·완료 상태 일관성 | 도메인/DB/E2E | 예정 |
| NOTIFY-01 | 사례: 등록 알림 | 로컬 Mailpit에서 경로 등록 알림 수신; 재시도 중 중복 억제 | outbox/SMTP 통합 | 예정 |
| REVIEW-01 | 추가: 증거 심사 | 학습자 제출, 심사자 승인·반려, 미승인 발급 거부 | Kotest/인가/API | 예정 |
| ISSUE-01 | 표준: 자격증 발급 | 승인된 성과만 서명; 중복 요청·경합에도 한 번 발급 | 실제 라이브러리/DB 동시성 | 예정 |
| VERIFY-01 | 사례·표준: 검증 | 정상 통과; 변조·다른 키·만료·미래 발급·철회 거부 | 암호/시계/API/E2E | 예정 |
| SHARE-01 | 사례: 휴대·공유 | JSON·PNG·SVG 다운로드/추출; 공개 동의 없이 개인정보 노출 금지 | 왕복/브라우저/인가 | 예정 |
| ACCOUNT-01 | 사례: 개인 계정 | 기관 소속 해제 이후 기존 자격증 접근 유지 | 인증/소유권 통합 | 예정 |
| STATUS-01 | 추가·표준: 철회 | 발급자만 철회, 상태 검증·재시작 후 유지 | API/DB/서명 상태 | 예정 |
| OPS-01 | 사용자: Docker | compose 한 번으로 발급·검증; 재시작 후 키·데이터 유지 | 컨테이너 E2E | 예정 |
| OPS-02 | 사용자: 분산 준비 | 무상태 서버, DB 제약·멱등성·outbox, 외부 키, readiness·metrics·추적 ID | 다중 인스턴스/장애 주입 | 예정 |
| ARCH-01 | 사용자: 기술 구성 | Kotlin/Boot/Gradle, domain/ports/adapters 의존 방향 검증; Kotest·Kover·Konsist·logging | Gradle check | 예정 |
| WEB-01 | 사용자: 웹 구성 | React/Vite, Zustand UI 상태, TanStack Query 서버 상태, 접근성·반응형 | Vitest/Playwright | 예정 |

## 배포 준비 원칙

우선 모듈형 단일 서버 + PostgreSQL + 웹 + 로컬 OIDC + Mailpit을 Compose로 실행한다. 도메인과 어댑터를 분리하고 DB 유일 제약과 트랜잭션으로 멱등성을 보장한다. 외부 전송은 transactional outbox로 처리하며 실제 메일은 보내지 않는다. 키는 재시작 때 새로 생성하지 않고 마운트된 비밀로 공급한다. 과거 공개 키를 유지해 회전을 지원한다. 서명 검증과 발급자 신뢰 정책을 구분한다. 원격 URL 검증은 SSRF 방어 및 크기·시간 제한을 갖춘다.

배포 이전에는 OIDC 설정, TLS, 비밀 관리, DB 백업·복구, 키 회전, 모니터링, 마이그레이션과 다중 인스턴스 시나리오를 실제 검사한다. Kubernetes 등 특정 운영 플랫폼은 미정이며, 설정 파일만으로 운영 준비 완료라 하지 않는다.

## 발견 사항

- FACT: core는 JitPack API에서 `8a98820f9c`, `d70f42fe52` 빌드가 ok다. 최신 main의 배포는 아직 확인하지 않았다.
- FACT: starter JitPack API는 빌드 이력이 비어 있다. build.gradle.kts에 maven-publish와 publication이 없고 jitpack.yml도 없다.
- FACT: starter README의 work.brodykim 좌표는 JitPack 소비 좌표와 불일치한다.
- FACT: Windows checkout의 gradlew CRLF가 Linux 컨테이너 실행을 실패시켰다. LF 속성 고정이 필요하다.
- UNKNOWN: 전체 최신 명세 적합성과 상호운용성. 라이브러리 자체 설명만으로 충족 판정하지 않는다.

## 다음 작업

LIB-01 배포 실패 재현 → publication/LF/설명 수정 → 전체 starter 테스트 및 POM 확인 → fix 브랜치 커밋·push → JitPack 원격 소비 검증 → core 최신 기준 테스트 → ISSUE/VERIFY 수직 기능.

첫 반복 결과와 실제 검증 수치, 이어서 할 작업은 [WORKLOG.md](WORKLOG.md)에 기록한다.
