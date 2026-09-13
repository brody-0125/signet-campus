# Signet Campus

Evidence-based digital skills credentials for campus learning, built with Open Badges and Signet.

교육기관에서 학습 증거를 제출하고, 역량을 심사받고, 디지털 배지를 발급·공유·검증하는 서비스를 개발합니다. Bowdoin College의 디지털 접근성 배지와 학습 경로 사례에서 착안한 독립 프로젝트입니다.

**Version: 1.0.0 · Development status: foundation / dependency integration.**
현재 버전은 저장소의 초기 기준점입니다. 운영 서비스 완성이나 표준 인증을 뜻하지 않습니다. 웹, 업무 API, 데이터베이스, Docker Compose 서비스는 아직 구현 전입니다.

## 현재 검증된 내용

- JitPack의 `signet-spring-boot-starter:abaf5c173f`와 전이 의존성을 실제로 해석합니다.
- Kotlin 계약 테스트에서 Spring 자동 설정, Data Integrity 서명, 다른 키·변조 거부를 검사합니다.
- 통합 테스트 1개가 통과했습니다. 라이브러리 수정은 각 `fix/*` 브랜치에 있으며 main에 병합하지 않았습니다.
- 해석된 런타임·테스트 의존성 142개의 좌표, SHA-256, POM 및 포함된 라이선스·고지를 기록했습니다.

## 기술 구성

| 영역 | 현재 / 계획 |
|---|---|
| 서버 빌드 | Kotlin 2.2.21, Spring Boot 3.5.11, Gradle 8.14.3, Java 17 |
| 테스트 | Kotest 6.0.4; Konsist 0.17.3, Kover 0.9.3 선언 완료, 아키텍처·커버리지 기준 구현 예정 |
| 로깅 | kotlin-logging 7.0.13 의존성 선언 |
| 웹 (예정) | React, Zustand, TanStack Query, Vite |
| 구조·운영 (예정) | Hexagonal architecture, PostgreSQL, OIDC, Docker Compose, transactional outbox |

## 테스트 실행

Java 17이 필요합니다. 별도 Gradle 설치는 필요하지 않습니다.

```bash
git clone https://github.com/brody-0125/signet-campus.git
cd signet-campus/server
sh ./gradlew test
```

Windows에서는 `gradlew.bat test`를 실행합니다. Maven Central 및 JitPack에 접근할 수 있어야 합니다. 아직 `bootRun`이나 `bootJar`로 실행할 애플리케이션 진입점은 없습니다.

Docker로 테스트하려면 저장소 루트에서:

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace/server eclipse-temurin:17-jdk sh ./gradlew test --no-daemon
```

## 개발과 문서

- [기능 범위·테스트 매트릭스·인수 조건](docs/PROJECT_PLAN.md)
- [검증 결과와 다음 작업](WORKLOG.md)
- [기여 및 브랜치·커밋 규약](CONTRIBUTING.md)
- [보안 제보](SECURITY.md)
- [변경 이력](CHANGELOG.md)
- [제3자 소프트웨어 고지](THIRD_PARTY_NOTICES.md)
- [해석된 의존성 라이선스 목록](docs/DEPENDENCY_LICENSES.md)
- [표준·상표·배포 및 개인정보 검토](docs/COMPLIANCE.md)

## 사례와 표준

Bowdoin의 [디지털 배지 안내](https://bowdoin.teamdynamix.com/TDClient/1814/Portal/KB/Article/157576/What-is-Digital-Badging)와 [학습 경로 안내](https://bowdoin.teamdynamix.com/TDClient/1814/Portal/KB/Article/157578/Understand-the-Digital-Badge-Learning-Pathway-Subscription-Email)를 제품 요구사항의 출발점으로 사용합니다. 기관의 실제 데이터·로고·평가 정책은 복제하지 않습니다.

이 프로젝트는 [1EdTech Open Badges 3.0](https://www.imsglobal.org/spec/ob/v3p0) 및 [W3C Verifiable Credentials Data Model 2.0](https://www.w3.org/TR/vc-data-model-2.0/) 기반 구현을 개발합니다. 1EdTech 인증을 받지 않았으며 Bowdoin, 1EdTech 또는 W3C의 보증·제휴를 주장하지 않습니다. 표준 문서의 권리는 각 권리자에게 있습니다. [NOTICE](NOTICE)를 참고하십시오.

## License

Signet Campus의 자체 코드와 문서는 [MIT](LICENSE)입니다. 제3자 구성요소, 복사된 Gradle wrapper, 보존된 라이선스·고지 문서는 해당 원래 조건을 따릅니다. MIT는 제3자 소프트웨어나 표준 문서를 재라이선스하지 않습니다.
