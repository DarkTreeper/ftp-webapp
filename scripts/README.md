# Scripts - Entwickler-Hilfsmittel

## 🔐 git-security-check.sh

Pre-commit Hook zur Vermeidung von Sicherheitsproblemen.

### Installation (einmalig)

```bash
cp scripts/git-security-check.sh .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

### Was es überprüft
- ❌ `.env` Dateien (Credentials)
- ❌ Hardcodierte Passwords in Java
- ❌ Leak-Verdächtige Strings
- ❌ Build-Artefakte (`target/`, `.class`)
- ⚠️ Lokale Testdaten
- ⚠️ IDE-Konfigurationsdateien

### How to bypass (falls nötig)
```bash
git commit --no-verify
```

---

## 🧪 Lokales Testing

### Schneller Test aller Unit-Tests
```bash
cd /Users/tyrell/dev/projects/ftp-webapp
mvn test
```

### Nur Demo-Mode Tests (ohne echten FTP)
```bash
mvn test -Dtest=*DemoMode*
```

### Integration Test mit Docker
```bash
docker compose up --build -d
mvn verify
docker compose down
```

---

## 🚀 Lokales Development Setup

### 1. .env.local erstellen (nur lokal, nicht committen!)
```bash
cp .env.local.example .env.local
```

### 2. IntelliJ Run Configuration nutzen
Projekt öffnen → "Secure FileBridge Local" Run Config ausführen

### 3. Oder Terminal:
```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Browser: `http://localhost:8080`  
Login: `admin` / `admin12345`

---

## 📦 Build & Deployment

### JAR bauen
```bash
mvn clean package
# JAR in target/secure-filebridge-0.1.0.jar
```

### Docker Image bauen
```bash
docker build -t secure-filebridge:latest .
```

### Production Stack starten
```bash
cp .env.production.example .env
# .env Datei editieren mit echten Credentials
docker compose up --build -d
docker compose logs -f
```

---

## 🔍 Debugging Tips

### Logs ansehen (lokal)
```bash
# Terminal mit Profile:
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run | grep -i "filebridge\|error"

# Docker:
docker compose logs -f app
```

### Remote Path Policy testen
```bash
mvn test -Dtest=RemotePathPolicyTest -v
```

### Demo-Mode Full Test
```bash
FILEBRIDGE_DEMO_MODE=true mvn spring-boot:run
```

---

## 🛠️ Häufige Entwickler-Probleme

### Problem: `FTP_USERNAME` / `FTP_PASSWORD` nicht gesetzt
**Lösung:** Nutze `local` profile oder setze `.env.local`

### Problem: Permission Denied bei `.git/hooks/pre-commit`
**Lösung:** 
```bash
chmod +x .git/hooks/pre-commit
```

### Problem: Demo-Modus Tests schlagen fehl
**Lösung:** Stelle sicher, dass `FILEBRIDGE_DEMO_MODE=true` gesetzt ist
```bash
export FILEBRIDGE_DEMO_MODE=true
mvn test
```


