# AGENTS: How to be productive in this repo

Kurz, praktisch, auf das Projekt zugeschnitten. Ziel: einem AI-Coding-Agenten in den ersten Minuten vollen Kontext geben.

1) Big picture
- Java Spring Boot webapp (entry: `dev.filebridge.FileBridgeApplication`).
- Purpose: Browser-gestützte Upload/Download-UI, die serverseitig per FTP an ein gemeinsames Filesystem schreibt/liest.
- Major components:
  - Config: `src/main/java/dev/filebridge/config/*` (`AppProperties`, `SecurityConfig`)
  - Web layer: `controller/FileBrowserController` (Thymeleaf templates in `src/main/resources/templates`)
  - Service layer: `service/FtpFileService`, `service/RemotePathPolicy`
  - Model: `model/FileEntry`, `model/DirectoryListing`

2) Important design & constraints (must preserve)
- Demo mode: `filebridge.demo-mode` (see `AppProperties`). If true, `FtpFileService` operates on `demo-remote-data` instead of real FTP (create directories, copy files). Use this for local development and unit tests.
- Path safety: `RemotePathPolicy` normalizes and enforces a configured FTP root; rejects `..`, `/` escapes and unsafe child names. Any change to path handling must keep these checks.
- FTP client: uses Apache Commons Net `FTPClient` / `FTPSClient` in `FtpFileService.withClient(...)`. Connection/login, passive mode, binary mode and FTPS-specific PBSZ/PROT are handled here.
- Upload uniqueness: `uniqueRemotePath` / `uniqueDemoTarget` create non-colliding target names rather than overwriting existing files.
- Security: `SecurityConfig` requires either `APP_PASSWORD` or `APP_PASSWORD_HASH` (BCrypt). Startup will fail if required FTP creds are missing (unless demo-mode).

3) Developer workflows / commands
- Local quick run (recommended):
  - Use profile `local` (example `.env.local.example`) or set `SPRING_PROFILES_ACTIVE=local`.
  - IntelliJ: Run configuration `Secure FileBridge Local` (project README mentions it).
  - Terminal:
    mvn spring-boot:run -Dspring-boot.run.profiles=local
- Build artifact / tests:
    mvn -DskipTests=false clean package
    mvn test
- Docker / production (build then run):
    cp .env.production.example .env && edit .env
    docker compose up --build -d
    docker compose ps
    docker compose logs -f

4) Project-specific patterns & gotchas
- Configuration via environment variables mapped in `src/main/resources/application.yml` (see `filebridge.*` and `spring.servlet.multipart.*`). Prefer editing `.env` for docker runs.
- Thymeleaf templates expect CSRF tokens (look at `index.html` and form inputs). Controller injects `_csrf.parameterName` and `_csrf.token`.
- All user-facing errors are simplified by `FileBrowserController.userMessage(...)` — SecurityExceptions become a generic access-denied message. Tests and UI rely on those messages.
- Logging: controller writes audit lines via SLF4J (`log.info("audit upload ...")`). If adding audit sinks, keep this format to preserve log-parsing.

5) Tests & important unit coverage
- `src/test/java/dev/filebridge/service/RemotePathPolicyTest` covers path normalization and rejection rules. Changing `RemotePathPolicy` requires updating these tests.
- There are demo-mode tests that rely on filesystem copies (`FtpFileServiceDemoModeTest`). Use the `local` profile for running them reliably.

6) Quick file map for reference
- `README.md` — deployment and local run instructions
- `application.yml`, `.env.local.example`, `.env.production.example` — env/config mapping
- `src/main/java/dev/filebridge/config/SecurityConfig.java` — auth + startup validation rules
- `src/main/java/dev/filebridge/service/FtpFileService.java` — FTP operations, demo-mode behaviour
- `src/main/java/dev/filebridge/service/RemotePathPolicy.java` — normalization & root enforcement
- `src/main/resources/templates/index.html` — user flows (upload form, download link, remote listing)

If you need, I can also: extract runnable small dev scripts, add automated checks for missing envs, or produce a CONTRIBUTING.md focusing on these patterns.

