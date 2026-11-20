<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://raw.githubusercontent.com/PoolC/.github/main/profile/assets/poolc.dark.svg" />
  <img src="https://raw.githubusercontent.com/PoolC/.github/main/profile/assets/poolc.vertical.svg" width="100%" alt="PoolC" />
</picture>

연세대학교 공과대학 프로그래밍 학술동아리 **PoolC** 홈페이지 API

<img src="https://img.shields.io/badge/Java-11-437291?style=flat-square&logo=openjdk&logoColor=white" alt="Java 11" />
<img src="https://img.shields.io/badge/Spring%20Boot-2.4-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot" />
<img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL" />
<img src="https://img.shields.io/badge/AWS-Lightsail%20%7C%20S3%20%7C%20SSM-FF9900?style=flat-square&logo=amazonaws&logoColor=white" alt="AWS" />

</div>

<br />

## Features

| 회원 · 운영 | 콘텐츠 | 게임화 |
| :---: | :---: | :---: |
| 인증 · 권한 · 회원 | 세미나 · 게시판 · 도서 · 프로젝트 | 도감 · 퀘스트 · 포켓볼 |

## Architecture

```text
Browser → CloudFront / Cloudflare → Nginx → Spring Boot → PostgreSQL
                                             └────────→ Amazon S3
```

| Area | Stack |
| --- | --- |
| API | Spring Boot · Spring Security · Spring Data JPA |
| Data | PostgreSQL · Hibernate |
| File storage | Amazon S3 |
| Runtime | Docker · Amazon ECR · Lightsail · SSM Parameter Store |
| Delivery | GitHub Actions |
| Documentation | Swagger · Spring REST Docs |

## Local Development

<img src="https://img.shields.io/badge/Docker%20Compose-required-2496ED?style=flat-square&logo=docker&logoColor=white" alt="Docker Compose" />

```bash
docker compose -p poolc-dev -f docker-compose.local.yml up -d --build
```

| Service | Address |
| --- | --- |
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080 |
| PostgreSQL | localhost:5432 |
| MinIO S3 API | http://localhost:9000 |
| MinIO Console | http://localhost:9001 |

로컬 게임 빌드는 MinIO에 저장됩니다. 개발용 접속 정보는 `poolc` / `poolc_dev_password`이며, API가 `poolc-dev` 버킷과 게임 파일의 공개 읽기 정책을 자동으로 준비합니다. Docker 설정은 `localhost:3000`의 업로드를 CORS에서 허용합니다. `GAME_BUILD_S3_ACCESS_KEY`와 `GAME_BUILD_S3_SECRET_KEY`로 개발 자격 증명을 덮어쓸 수 있습니다. 기본 자격 증명은 로컬 Docker 개발 전용입니다.

개발 데이터가 필요하면 API 기동 후 seed를 실행합니다.

```bash
docker compose -p poolc-dev -f docker-compose.local.yml exec -T postgres \
  psql -U poolc -d poolc < scripts/local-dev/seed.sql
```

<details>
<summary>개발 계정</summary>

```text
admin / poolc1234
president / poolc1234
member1 ~ member3 / poolc1234
pending / poolc1234
```

</details>

## Environment

운영 비밀값은 Git에 두지 않고 **AWS SSM Parameter Store**에서 관리합니다.

| Group | Variables |
| --- | --- |
| Database | `DB_HOST`, `DB_NAME`, `DB_USER_NAME`, `DB_PASSWORD` |
| Auth | `PROJECT_NAME_HERE_SECRET_KEY`, `EXPIRE_LENGTH_IN_MILLISECONDS` |
| Files | `FILE_STORAGE`, `FILE_S3_BUCKET`, `AWS_REGION` |

## Test & API Docs

```bash
./gradlew test
./gradlew bootJar
./gradlew asciidoctor
```

<div align="center">

[![Swagger](https://img.shields.io/badge/API%20Docs-Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black)](https://api.poolc.org/swagger-ui/)

</div>

## Delivery

```text
master push
  → test · package
  → ECR image publish
  → SSM Run Command
  → Lightsail deploy
```

## Contributors

<div align="center">

| [Mayne0213](https://github.com/Mayne0213) | [jinhodotchoi](https://github.com/jinhodotchoi) | [mingd1023](https://github.com/mingd1023) | [jimmy0006](https://github.com/jimmy0006) | [hcpak](https://github.com/hcpak) |
| :---: | :---: | :---: | :---: | :---: |
| <img src="https://github.com/Mayne0213.png?size=160" width="88" alt="Mayne0213" /> | <img src="https://github.com/jinhodotchoi.png?size=160" width="88" alt="jinhodotchoi" /> | <img src="https://github.com/mingd1023.png?size=160" width="88" alt="mingd1023" /> | <img src="https://github.com/jimmy0006.png?size=160" width="88" alt="jimmy0006" /> | <img src="https://github.com/hcpak.png?size=160" width="88" alt="hcpak" /> |

| [becooq81](https://github.com/becooq81) | [yoonseokch](https://github.com/yoonseokch) | [Jjungs7](https://github.com/Jjungs7) |
| :---: | :---: | :---: |
| <img src="https://github.com/becooq81.png?size=160" width="88" alt="becooq81" /> | <img src="https://github.com/yoonseokch.png?size=160" width="88" alt="yoonseokch" /> | <img src="https://github.com/Jjungs7.png?size=160" width="88" alt="Jjungs7" /> |

</div>

---

PoolC 내부 운영 프로젝트입니다.
