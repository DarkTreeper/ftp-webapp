# Security Checklist für Repo-Betreuer

Dieses Dokument hilft beim Überprüfen der Sicherheit vor Releases und Merges.

## Vor jedem Commit / PR

- [ ] **Git-Hook installiert?**
  ```bash
  cp scripts/git-security-check.sh .git/hooks/pre-commit
  chmod +x .git/hooks/pre-commit
  ```

- [ ] **Keine .env Dateien staged?**
  ```bash
  git status | grep ".env"
  # Sollte leer sein (außer .env.*.example)
  ```

- [ ] **Keine Secrets im Staging?**
  ```bash
  git diff --cached | grep -iE "(password|secret|token|ftp_password)\s*=\s*[^$]"
  # Sollte leer sein
  ```

---

## Vor PR / Release

- [ ] **Große Dateien im Repo?**
  ```bash
  git ls-files | awk '{print $1}' | while read f; do
    size=$(wc -c < "$f")
    if [ $size -gt 1048576 ]; then
      echo "Large file: $f ($size bytes)"
    fi
  done
  ```

- [ ] **Keine lokalen Daten committed?**
  ```bash
  git ls-files | grep -E "^(local-data|demo-remote-data)" | grep -v ".gitkeep"
  # Sollte leer sein
  ```

- [ ] **Nur Template-Env-Dateien im Repo?**
  ```bash
  git ls-files | grep ".env"
  # Sollte nur .env.*.example enthalten
  ```

---

## Dependency Vulnerabilities

- [ ] **Maven-Dependencies auf CVEs überprüfen?**
  ```bash
  mvn dependency-check:check
  # Oder: mvn org.owasp:dependency-check-maven:check
  ```

- [ ] **Spring Boot aktuell?**
  - Aktuell: `3.3+` ohne bekannte CVEs
  - Check: `src/main/resources/application.yml`

- [ ] **Apache Commons Net aktuell?**
  - Verwendet für FTP-Verbindungen
  - Check: `pom.xml`

---

## Code Review Security

- [ ] **No hardcoded credentials** 
  ```bash
  git log -p -- "*.java" | grep -i "password.*=" | grep -v "new PasswordEncoder"
  ```

- [ ] **Path Traversal Prevention**
  - `RemotePathPolicy` normalisiert alle Pfade
  - Check: `src/main/java/dev/filebridge/service/RemotePathPolicy.java`
  - Tests: `src/test/java/dev/filebridge/service/RemotePathPolicyTest.java`

- [ ] **CSRF Protection aktiv**
  - Template check: Alle `<form>` haben `_csrf` token (`index.html`)
  - Config check: `SecurityConfig.java` hat CSRF enabled

- [ ] **Error Messages nicht zu ausführlich**
  - Check: `FileBrowserController.userMessage()` — generic error messages für SecurityException

---

## Production Deployment

- [ ] **.env.production.example ausgefüllt und überprüft?**
  - FTP_HOST referenziert korrekte VIP
  - APP_PASSWORD oder APP_PASSWORD_HASH gesetzt
  - FTP_FTPS=true (falls unterstützt)

- [ ] **Docker Image scanned?**
  ```bash
  docker scan secure-filebridge:latest
  # Oder: trivy image secure-filebridge:latest
  ```

- [ ] **Container läuft ohne Root?**
  - Dockerfile check: `USER` Directive?
  - Check: `Dockerfile` sollte nicht als root laufen

- [ ] **Session Cookies sicher?**
  - `SESSION_COOKIE_SECURE=true` (hinter HTTPS)
  - `SAME_SITE=strict`
  - `HTTP_ONLY=true`
  - Check: `application.yml`

- [ ] **Nginx konfiguriert?**
  - HTTPS aktiviert mit gültigem Cert
  - Client header trusted (`X-Forwarded-For`, `X-Forwarded-Proto`)
  - Check: `deploy/nginx/secure-filebridge.conf`

---

## Post-Deployment auf Production

- [ ] **HTTPS funktioniert?**
  ```bash
  curl -I https://your-domain | grep "Secure"
  ```

- [ ] **Session-Verhalten getestet?**
  - Login funktioniert
  - Logout cleared Cookies
  - Session-Timeout nach 30m

- [ ] **FTPS-Verbindung funktioniert?**
  - Test-Upload durchführen
  - FTP-VIP `172.16.120.30` erreichbar
  - Credentials korrekt

- [ ] **Audit Logs schreiben?**
  ```bash
  docker compose logs app | grep "audit"
  # Sollte "audit upload" und "audit download" enthalten
  ```

- [ ] **Fehlerbehandlung funktioniert?**
  - Ungültige Credentials → generische Meldung
  - FTP-Timeout → Fehlermeldung ohne Stack-Trace
  - Path-Escape-Versuch → "Zugriff verweigert"

---

## Regelmäßige Überprüfungen

| Zeitpunkt | Aufgabe | Command |
|-----------|---------|---------|
| Weekly | Dependencies auf CVEs | `mvn dependency-check:check` |
| Monthly | Secret-Scan des Repos | `git log -p \| grep -i "password"` |
| Quarterly | Security Audit | OWASP Top 10 Review |
| Vor Release | Full Checklist | Dieses Dokument durchgehen |

---

## ⚠️ Kritische Fehlerpfade (NIEMALS)

-  Niemals `APP_PASSWORD` in plain-text ins Repo
-  Niemals `FTP_PASSWORD` ohne Verschlüsselung versendet  
-  Niemals `.env` committen (auch nicht mit `--force`)
-  Niemals Zertifikat-Private-Keys ins Repo
-  Niemals Bearer-Tokens oder API-Keys hardcoden
-  Niemals als Root im Container starten

Wenn ein dieser Punkte passiert ist:
1. **Sofort danach rotieren** (neues Password, neuer Token, etc.)
2. **Git-History cleanen** (falls öffentliches Repo): `git filter-branch` oder `bfg-repo-cleaner`
3. **Team informieren**

