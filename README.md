# Secure FileBridge

Eine Java-Webapplikation fuer produktionsnahe Dateiuebertragungen in einen gemeinsamen Speicher.

- Browser-Benutzer melden sich an der Weboberflaeche an
- Dateien werden vom eigenen Rechner direkt im Browser ausgewaehlt
- das Backend uebertraegt die Dateien per FTP in den gemeinsamen Speicher
- Downloads aus dem gemeinsamen Speicher laufen ebenfalls ueber die Webapp

## Version 1

Die erste Version ist bewusst reduziert und produktiv testbar:

- Upload vom Benutzerrechner in den gemeinsamen Speicher
- Download aus dem gemeinsamen Speicher
- Navigation durch die FTP-Verzeichnisstruktur
- fester FTP-Backend-Zugang
- keine Anzeige der FTP-Zugangsdaten im Frontend
- keine Loesch-, Umbenenn- oder Server-Schreibaktionen ausser Upload

Die linke Seite ist deshalb kein echtes lokales Dateisystem wie in Desktop-FileZilla, sondern ein sicherer Browser-Upload-Bereich.

## Zielsystem

Die produktive Verbindung erfolgt ueber die virtuelle FTP-IP:

- `172.16.120.30` = FTP-VIP
- `172.16.120.31` und `172.16.120.32` = Fileserver / Failover-Umgebung dahinter

Die Anwendung spricht nur die VIP `172.16.120.30` an.

## Sicherheitsmodell

- Login-Pflicht mit Spring Security
- CSRF-Schutz fuer alle veraendernden Aktionen
- Session-Fixation-Schutz, HttpOnly- und SameSite-Cookies
- Forwarded Header Support fuer Reverse Proxy / HTTPS
- keine Standard-Zugangsdaten im Produktivbetrieb
- FTP-Zugriff nur innerhalb von `FILEBRIDGE_REMOTE_ROOT`
- Pfadnormalisierung blockiert `..` und Root-Ausbrueche
- Audit-Logging fuer Login-Erfolg, Login-Fehler, Upload und Download
- Container laeuft ohne Root-Rechte, ohne Linux-Capabilities und mit read-only Root-Dateisystem
- Dateiuebertragungen erzeugen eindeutige Zielnamen statt bestehende Dateien zu ueberschreiben

## Lokal testen

Es gibt ein Spring-Profil `local`, bei dem die rechte Seite durch `demo-remote-data` simuliert wird.

In IntelliJ:

1. Projektordner oeffnen: `/Users/tyrell/dev/projects/ftp-webapp`
2. Run Configuration `Secure FileBridge Local` starten
3. Browser oeffnen: `http://localhost:8080`
4. Einloggen mit:

   ```text
   Benutzer: admin
   Passwort: admin12345
   ```

Alternativ im Terminal:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Produktiv mit Docker

### 1. `.env` anlegen

Nimm fuer den echten Server am besten direkt diese Vorlage:

[/Users/tyrell/dev/projects/ftp-webapp/.env.production.example](/Users/tyrell/dev/projects/ftp-webapp/.env.production.example)

Kopieren:

   ```bash
   cp .env.production.example .env
   ```

### 2. `.env` anpassen

Diese Felder musst du anpassen:

   ```bash
   APP_USERNAME=webadmin
   APP_PASSWORD=ein-sehr-langes-zufaelliges-passwort
   FTP_HOST=172.16.120.30
   FTP_PORT=21
   FTP_USERNAME=dein-ftp-user
   FTP_PASSWORD=dein-ftp-passwort
   FTP_PASSIVE_MODE=true
   FTP_FTPS=false
   SESSION_COOKIE_SECURE=true
   ```

Erklaerung der wichtigsten Felder:

- `APP_USERNAME`
  Der Login-Name fuer die Weboberflaeche

- `APP_PASSWORD`
  Das Passwort fuer die Weboberflaeche

- `FTP_HOST`
  Immer die virtuelle FTP-IP, also bei dir `172.16.120.30`

- `FTP_USERNAME` / `FTP_PASSWORD`
  Der technische FTP-Benutzer, mit dem die App im Hintergrund arbeitet

- `FTP_FTPS`
  Auf `true`, wenn dein FTP-System FTPS unterstuetzt

- `FILEBRIDGE_REMOTE_ROOT`
  Begrenzung auf einen bestimmten FTP-Unterordner
  Beispiel:

  ```text
  FILEBRIDGE_REMOTE_ROOT=/freigabe
  ```

- `MAX_UPLOAD_SIZE`
  Maximale Upload-Groesse aus dem Browser

- `SESSION_COOKIE_SECURE`
  Im Produktivbetrieb hinter HTTPS immer `true`

### 3. Docker-Container bauen und starten

   ```bash
   docker compose up --build -d
   ```

Pruefen:

```bash
docker compose ps
```

Logs ansehen:

```bash
docker compose logs -f
```

### 4. Nginx davor setzen

Die Docker-Compose-Datei startet nur die Java-Anwendung. Der empfohlene Reverse Proxy liegt davor. Eine Beispielkonfiguration findest du hier:

[secure-filebridge.conf](/Users/tyrell/dev/projects/ftp-webapp/deploy/nginx/secure-filebridge.conf)

### 5. Nginx-Konfiguration anpassen

Diese Felder musst du in der Nginx-Datei anpassen:

- `server_name _;`
  ersetzen durch deinen echten Hostnamen, z. B.

  ```nginx
  server_name files.example.de;
  ```

- `ssl_certificate`
  Pfad zu deinem echten Zertifikat

- `ssl_certificate_key`
  Pfad zu deinem echten Private Key

- `client_max_body_size 1024m;`
  anpassen, wenn du groessere oder kleinere Uploads willst

Wenn du noch kein Zertifikat hast und erst intern testen willst, kannst du Nginx auch voruebergehend nur auf HTTP laufen lassen. Fuer echten Internetbetrieb sollte aber HTTPS Pflicht sein.

### 6. Nginx aktivieren

Typischer Ablauf auf Ubuntu/Debian:

```bash
sudo cp deploy/nginx/secure-filebridge.conf /etc/nginx/sites-available/secure-filebridge
sudo ln -s /etc/nginx/sites-available/secure-filebridge /etc/nginx/sites-enabled/secure-filebridge
sudo nginx -t
sudo systemctl reload nginx
```

### 7. Anwendung testen

Dann im Browser:

```text
https://DEIN-HOSTNAME
```

Einloggen mit:

```text
APP_USERNAME
APP_PASSWORD
```

## Nginx vor der App

Empfohlenes Setup:

```text
Internet / Benutzer
    ->
Nginx (HTTPS, Reverse Proxy)
    ->
Secure FileBridge
    ->
FTP VIP 172.16.120.30
```

Wichtige Punkte:

- TLS / HTTPS an Nginx terminieren
- `client_max_body_size` passend zu deinen Uploads setzen
- nur Nginx nach aussen freigeben
- App intern auf Port `8080` lassen

## Beispiel fuer deine Umgebung

Wenn du erstmal mit deinen bekannten Adressen startest, dann ist das eine gute Basis:

```bash
APP_USERNAME=webadmin
APP_PASSWORD=BitteEinSehrLangesNeuesPasswortSetzen
FTP_HOST=172.16.120.30
FTP_PORT=21
FTP_USERNAME=DEIN_FTP_BENUTZER
FTP_PASSWORD=DEIN_FTP_PASSWORT
FTP_PASSIVE_MODE=true
FTP_FTPS=false
FILEBRIDGE_REMOTE_ROOT=/
MAX_UPLOAD_SIZE=1024MB
FILEBRIDGE_MAX_UPLOAD_BYTES=1073741824
SESSION_COOKIE_SECURE=true
SESSION_TIMEOUT=30m
SERVER_PORT=8080
```

## Was du spaeter selbst anpassen kannst

- Web-Login:
  `APP_USERNAME`, `APP_PASSWORD`

- FTP-Ziel:
  `FTP_HOST`, `FTP_PORT`, `FTP_USERNAME`, `FTP_PASSWORD`

- FTPS an/aus:
  `FTP_FTPS`

- Startordner / Begrenzung:
  `FILEBRIDGE_REMOTE_ROOT`

- Upload-Limit:
  `MAX_UPLOAD_SIZE`, `FILEBRIDGE_MAX_UPLOAD_BYTES`

- Session-Verhalten:
  `SESSION_TIMEOUT`

- Zertifikate und Domain in Nginx:
  `server_name`, `ssl_certificate`, `ssl_certificate_key`

## FTPS

Wenn die FTP-Failover-Umgebung FTPS unterstuetzt, aktiviere es unbedingt:

```bash
FTP_FTPS=true
```

Normales FTP verschluesselt weder Zugangsdaten noch Nutzdaten.
