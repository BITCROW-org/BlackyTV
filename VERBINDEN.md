# BlackyTV – Verbinden in 3 Schritten

Diese Anleitung bringt dich vom leeren Server bis zum laufenden Stream. Alles Weitere steht in der [ANLEITUNG.md](ANLEITUNG.md).

```
 Streamer ──(offene Leitung: Bild + Ton)──► Relay ──(offene Leitung)──► Zuschauer
```

| Wer | Was er macht | Braucht |
|---|---|---|
| **Server-Admin** | startet einmal den Relay | Rechner mit Java 21+, offener Port |
| **Streamer** | zeigt sein Fenster auf einem Kanal | Mod, Windows 10 (2004+) / 11, Kanal + Passwort |
| **Zuschauer** | stellt ein Schild auf | nur die Mod |

---

## Schritt 1: Relay starten (Server-Admin, einmalig)

1. **Java prüfen:** `java -version` muss 21 oder höher zeigen.
2. **Datei holen:** `BlackyTVRelay.java` aus dem neuesten [Release](https://github.com/BITCROW-org/BlackyTV/releases/latest) auf den Server kopieren, in einen eigenen Ordner.
3. **Starten**, im selben Ordner:
   ```
   java BlackyTVRelay.java
   ```
   Klappt es, steht da: `BlackyTV Relay laeuft auf Port 8765 (0 Kanaele vergeben)`
4. **Port 8765 (TCP) freigeben:**

   | Wo läuft der Relay? | Was tun |
   |---|---|
   | Linux-Server / VPS | `ufw allow 8765/tcp` |
   | Windows | PowerShell als Admin: `New-NetFirewallRule -DisplayName "BlackyTV" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow` |
   | Zuhause hinterm Router | Zusätzlich im Router eine **Portweiterleitung** 8765 → dieser Rechner. Deine Adresse für die anderen ist dann deine **öffentliche** IP (z. B. über [wieistmeineip.de](https://www.wieistmeineip.de)) |

5. **Weiterlaufen lassen** (Linux): `screen -S blackytv`, dann starten, dann `Strg+A` und `D`. Zurück mit `screen -r blackytv`.

> **Update vom Relay:** Neue `BlackyTVRelay.java` drüberkopieren, Relay neu starten. Kanäle und Passwörter bleiben erhalten, weil `blackytv-kanaele.properties` liegen bleibt.

### Läuft er? Kurz testen
Von einem **anderen** Rechner aus (deine IP einsetzen):
```
curl -i http://DEINE-IP:8765/watch/test
```

| Antwort | Bedeutung |
|---|---|
| `HTTP/1.1 404` | ✅ Relay erreichbar, auf dem Kanal läuft nur gerade nichts |
| `Connection refused` | Relay läuft nicht, oder falscher Port |
| hängt / `timed out` | Port ist zu: Firewall oder Portweiterleitung prüfen |

---

## Schritt 2: Streamen (Streamer)

1. **Relay eintragen**, einmalig, wird gespeichert:
   ```
   /blackytv relay DEINE-IP:8765
   ```
2. **Quelle wählen:** `N` drücken und ein Fenster oder einen Monitor anklicken.
3. **Senden:**
   ```
   /blackytv stream kino meinPasswort
   ```
   Benutzt du `kino` zum **ersten Mal**, wird `meinPasswort` das Passwort dafür. Ab dann gehört der Kanal dir.
4. **Prüfen:** Oben im `N`-Menü steht **„Sendet auf Kanal kino“** in grün.

Nach einem Minecraft-Neustart reicht `N` und dann **Senden**: Kanal und Passwort sind gespeichert. Stoppen geht mit `/blackytv stop` oder demselben Knopf.

| Meldung im Menü | Was tun |
|---|---|
| „Kanal gehört wem anders / falsches Passwort“ | Richtiges Passwort nehmen oder einen anderen Kanalnamen |
| „Relay nicht erreichbar“ | Adresse mit `/blackytv relay` prüfen, Test aus Schritt 1 machen |
| „Relay antwortet mit 404“ | Relay ist noch alt, siehe **Update vom Relay** oben |

---

## Schritt 3: Zuschauen (alle)

Schild aufstellen:
```
blackytv:screen
16x9
ch:kino @DEINE-IP:8765
```

- Mit `@DEINE-IP:8765` auf dem Schild muss **keiner** etwas einstellen. Alternativ trägt jeder einmal `/blackytv relay DEINE-IP:8765` ein, dann reicht `ch:kino`.
- Läuft gerade nichts, ist der Fernseher schwarz. Er springt von selbst an, sobald jemand sendet.
- Ton hört man in der Nähe des Fernsehers. Lautstärke: `/blackytv volume 0-200`.

---

## Alles auf einem PC ausprobieren

Zum Testen geht auch alles auf deinem eigenen Rechner:

1. Relay starten: `java BlackyTVRelay.java`
2. Im Spiel: `/blackytv relay localhost:8765`
3. Schild mit `ch:test`, dann `/blackytv stream test test1234`

Siehst du dein Fenster auf dem Fernseher, läuft die ganze Kette. Den Ton hörst du dabei absichtlich nicht doppelt.

---

## Gut zu wissen

- **Verbindung:** Streamer und Zuschauer halten je **eine offene Leitung** zum Relay. Reißt sie ab (WLAN weg, Relay neu gestartet), verbindet sich die Mod nach ein paar Sekunden von selbst neu.
- **Sicherheit:** Die Verbindung ist **unverschlüsselt**. Nimm als Kanal-Passwort nichts, was du woanders benutzt.
- **Internet:** Pro Zuschauer grob 1–3 MB/s, je nach Qualität und FPS. Bei vielen Zuschauern Qualität 960 oder 1280 und 20 FPS nehmen. → [Traffic-Tabelle](ANLEITUNG.md#traffic)
- **Kanal vergessen / freigeben:** Relay stoppen, Zeile in `blackytv-kanaele.properties` löschen, Relay starten.
