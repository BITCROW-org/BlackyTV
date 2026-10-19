# BlackyTV – Komplette Anleitung

BlackyTV baut Fernseher in Minecraft, wie beim LabyMod-WorldCup-Addon. Drauf läuft ein **Fenster oder Monitor von deinem PC**, mit Ton.
Über einen kleinen **Relay-Server** können mehrere Leute gleichzeitig streamen, und alle mit der Mod sehen und hören es.

- Minecraft 26.2, Fabric, Client-Mod
- **Streamen** geht nur auf Windows 10 (ab Version 2004) / Windows 11
- **Zuschauen** geht überall

> Nur schnell verbinden? Die Kurzfassung steht in **[VERBINDEN.md](VERBINDEN.md)**.

---

## Inhalt

1. [Wie das Ganze funktioniert](#1-wie-das-ganze-funktioniert)
2. [Mod bauen und installieren](#2-mod-bauen-und-installieren)
3. [Relay-Server aufsetzen](#3-relay-server-aufsetzen)
4. [Fernseher aufstellen](#4-fernseher-aufstellen)
5. [Streamen](#5-streamen)
6. [Kanäle und Passwörter](#6-kanäle-und-passwörter)
7. [Ton](#7-ton)
8. [Alle Befehle und Tasten](#8-alle-befehle-und-tasten)
9. [Config-Datei](#9-config-datei)
10. [Probleme und Lösungen](#10-probleme-und-lösungen)

---

## 1. Wie das Ganze funktioniert

```
 Streamer (Windows)                 Relay-Server                  Zuschauer
 ┌──────────────────┐   Bild+Ton   ┌──────────────┐  Bild+Ton   ┌──────────────┐
 │ Fenster/Monitor  │ ───────────► │ Kanal "kino" │ ──────────► │ Fernseher mit│
 │ + Sound der App  │   (Passwort) │ Kanal "tom"  │             │ ch:kino      │
 └──────────────────┘              └──────────────┘             └──────────────┘
```

- Der **Streamer** sucht im Spiel aus, was laufen soll, und sendet es auf einen **Kanal** (z. B. `kino`).
- Der **Relay** ist ein kleines Programm auf deinem Server. Er nimmt Bild und Ton an und gibt sie an alle Zuschauer weiter.
- **Zuschauer** stellen ein Schild mit `ch:kino` auf und sehen den Stream dahinter als Leinwand.

Der Relay ist **kein** Minecraft-Server. Er läuft separat, gerne auf dem gleichen Rechner wie dein MC-Server, muss aber nicht.

> Nur Spieler **mit der Mod** sehen die Fernseher. Spieler ohne Mod sehen einfach ein Schild.

---

## 2. Mod bauen und installieren

1. Ordner `BlackyTV` in IntelliJ öffnen (**File → Open**).
2. Warten, bis Gradle fertig geladen hat. Falls nichts passiert: rechts im Gradle-Tab auf **🔄 Reload** klicken.
3. Testen im Spiel: Gradle → Tasks → fabric → **runClient**
4. Jar zum Weitergeben: Gradle → Tasks → build → **build**
   → die Datei liegt dann in `build/libs/BlackyTV-1.0.0.jar`
5. Die Jar in den `mods`-Ordner packen, zusammen mit der **Fabric API**.

Jeder, der zuschauen will, braucht die Jar und die Fabric API.

> Nach Änderungen am Code immer neu bauen. Sonst läuft noch die alte Version.

---

## 3. Relay-Server aufsetzen

Der Relay ist eine **einzige Java-Datei** ohne Abhängigkeiten: `relay/BlackyTVRelay.java`.

### Voraussetzung
Java 21 oder neuer auf dem Server (`java -version` zum Prüfen).

### Starten
Die Datei auf den Server kopieren, dann im selben Ordner:

```
java BlackyTVRelay.java
```

Das war's, kein Passwort beim Start. Anderen Port nehmen (Standard ist **8765**):

```
java BlackyTVRelay.java 9000
```

Wenn's läuft, steht da:
```
BlackyTV Relay laeuft auf Port 8765 (0 Kanaele vergeben)
```

### Verbindung
Streamer und Zuschauer haben je **eine offene Verbindung** zum Relay, über die Bild und Ton laufen. Es wird nicht für jedes Bild neu gefragt. Hängt ein Zuschauer hinterher, bekommt er direkt das neueste Bild statt alter Bilder. Ist 15 Sekunden nichts gekommen, baut die Mod die Verbindung selbst neu auf.

### Relay aktualisieren
Ab Mod **1.1.0** braucht es auch den neuen Relay: `BlackyTVRelay.java` ersetzen und neu starten. Die Datei `blackytv-kanaele.properties` bleibt liegen, alle Kanäle und Passwörter bleiben also erhalten. Der neue Relay versteht auch noch die alten Mods (1.0.x), du kannst also in Ruhe alle umstellen.

### Port freigeben
Der Port (TCP 8765) muss von außen erreichbar sein:
- **VPS/Root-Server**: Firewall öffnen, z. B. `ufw allow 8765/tcp`
- **Zuhause**: im Router eine Portweiterleitung auf den Rechner mit dem Relay

### Dauerhaft laufen lassen (Linux)
Damit er nach dem Schließen vom Terminal weiterläuft:
```
screen -S blackytv
java BlackyTVRelay.java
```
Mit `Strg+A`, dann `D` rausgehen. Mit `screen -r blackytv` kommst du wieder rein.

### Traffic
Hängt von **Qualität** und **FPS** beim Streamer ab. Pro Zuschauer ungefähr:

| Qualität | 20 FPS | 30 FPS |
|---|---|---|
| 960 | ~1 MB/s | ~1,5 MB/s |
| 1280 | ~2 MB/s | ~3 MB/s |
| 1920 | ~4 MB/s | ~6 MB/s |

Plus ca. **0,2 MB/s Ton**. Bei vielen Zuschauern also lieber 960 oder 1280 mit 20 FPS.

---

## 4. Fernseher aufstellen

Ein normales Schild hinstellen und so beschriften:

| Zeile | Was | Beispiel |
|---|---|---|
| 1 | immer `blackytv:screen` | `blackytv:screen` |
| 2 | Größe in Blöcken: `BreitexHöhe` | `16x9` |
| 3 und 4 | Einstellungen, mit Leerzeichen getrennt (alle optional) | `ch:kino` |

### Größe (Zeile 2)
- `16x9` → 16 Blöcke breit, 9 hoch
- `20x0` → 20 breit, Höhe passt sich automatisch ans Bild an
- `0x10` → 10 hoch, Breite automatisch
- Maximal `100x56`
- Passt das Seitenverhältnis nicht, gibt's schwarze Balken (wie beim Fernsehen)

### Einstellungen (Zeile 3/4)

| Einstellung | Was sie macht | Beispiel |
|---|---|---|
| `ch:kanal` | zeigt den Stream von diesem Kanal | `ch:kino` |
| `@host:port` | anderen Relay nehmen als den eingestellten | `@1.2.3.4:8765` |
| `off:rechts,hoch,vor` | Leinwand verschieben (in Blöcken), passend zur Richtung vom Fernseher | `off:0,0,2` |

**Ohne `ch:`** zeigt der Fernseher deinen **eigenen PC**, nur für dich. Gut zum Ausprobieren.

### Wo die Leinwand erscheint
Die Leinwand kommt **hinter** das Schild, ihre Oberkante sitzt an der Unterkante vom Schild.
Du kannst das Schild also in einem Block verstecken (z. B. Glas oder Stein), und man sieht nur noch die Leinwand.

### Verschieben mit `off:`
`off:rechts,hoch,vor`, immer aus Sicht von jemandem, der **vor der Leinwand steht und draufschaut**. Egal, wie das Schild gedreht ist:
- `off:0,0,2` → 2 Blöcke nach **vorne** (zu dir hin), z. B. wenn ein Block im Weg ist
- `off:0,0,-1` → 1 Block nach **hinten**
- `off:0,3,0` → 3 Blöcke **hoch**
- `off:-2,0,0` → 2 Blöcke nach **links**
- Kommazahlen gehen auch: `off:0,0.5,1.5`

### Beispiel
```
blackytv:screen
16x9
ch:kino
off:0,0,1
```

---

## 5. Streamen

### Einmalig: Relay eintragen
```
/blackytv relay 1.2.3.4:8765
```
(Deine Server-IP oder Domain.) Wird gespeichert, musst du nur einmal machen.

### Quelle auswählen
**`N`** drücken. Das Menü zeigt alle Monitore und offenen Fenster.
- Auf eins klicken → das läuft jetzt (grüner ▶ = aktiv)
- **Aus** → nichts mehr aufnehmen
- **FPS** → wie viele Bilder pro Sekunde (5 / 10 / 20 / 30 / 60)
- **Qualität** → Breite in Pixeln (640 / 960 / 1280 / 1920). Mehr = schärfer, kostet aber mehr Leistung und beim Streamen mehr Internet
- **Neu laden** → Fensterliste aktualisieren, falls du gerade was Neues aufgemacht hast
- **Wach halten** → siehe unten, wichtig für Browser und Discord
- **Hören** → wie laut die Fernseher bei **dir** sind (0–200 %)
- **Stream-Ton** → wie laut **dein** Ton beim Streamen bei allen ankommt (0–200 %)

### Senden
```
/blackytv stream kino meinPasswort
```
Ab jetzt sehen alle mit einem `ch:kino`-Schild deinen Stream.
Beim **ersten Mal** wird `meinPasswort` das Passwort für den Kanal `kino` (siehe [Kanäle und Passwörter](#6-kanäle-und-passwörter)).

Stoppen:
```
/blackytv stop
```

Im **N**-Menü gibt's unten auch einen Knopf zum An- und Ausschalten. Er nimmt den letzten Kanal und das letzte Passwort.

> Senden ist nach einem Neustart von Minecraft absichtlich **aus**, damit nicht aus Versehen was rausgeht.

### Fenster vs. Monitor
- **Fenster**: geht auch, wenn es verdeckt ist oder hinter Minecraft liegt. Minimierte Fenster holt BlackyTV automatisch zurück, aber ohne Fokus ganz nach hinten. Du kannst also im Vollbild zocken.
- **Monitor**: nimmt genau das auf, was auf dem Bildschirm ist. Läuft Minecraft im Vollbild auf diesem Monitor, filmst du Minecraft selbst. Dafür also lieber einen **zweiten Monitor** oder ein **Fenster** nehmen.

### Fenster wach halten (Browser, Discord & Co.)
Chrome, Edge, Opera, Discord und andere Apps **hören auf zu malen**, sobald ihr Fenster komplett verdeckt ist, zum Beispiel von Minecraft. Das machen sie, um Strom zu sparen. Ein YouTube-Video bleibt dann auf dem Fernseher einfach stehen.

Dafür gibt's im **N**-Menü den Schalter **Wach halten**:
- Das ausgewählte Fenster wird **unsichtbar, durchklickbar und „immer oben“**.
- Für Windows ist es damit nie verdeckt, und die App malt weiter.
- Du siehst es nicht und klickst einfach durch, als wär's nicht da.

Das Fenster wird **automatisch wieder normal**, wenn:
- du was anderes oder **Aus** auswählst,
- kein Fernseher mehr in der Nähe ist und du nicht sendest,
- du den Schalter ausmachst,
- Minecraft beendet wird.

> Stürzt Minecraft hart ab (Task-Manager → Task beenden), kann das Fenster unsichtbar bleiben. Dann die App einfach einmal schließen und neu öffnen.

Bei normalen Programmen wie VLC oder Spielen brauchst du den Schalter meistens nicht.

### Fenster im Spiel steuern
Als Streamer kannst du dein Fenster direkt über den Fernseher bedienen: klicken, scrollen, ziehen und tippen, ohne Minecraft zu verlassen.

**Schnell antippen, ohne Modus:** Einfach mit dem **Fadenkreuz** auf deinen Fernseher schauen.
- **Linksklick, Rechtsklick, Mittelklick** klicken genau da, wo das Fadenkreuz hinzeigt.
- **Mausrad** scrollt im Fenster, die Hotbar bleibt dabei stehen.
- Du kannst dabei normal herumlaufen. Ein Block, der zwischen dir und dem Fernseher steht, hat Vorrang.
- Gilt nur für dich als Streamer: nur Fernseher mit **deiner** Quelle, und nur bei einem Fenster.
- Stört das, z. B. weil du am Fernseher bauen willst: im N-Menü **Fadenkreuz-Klick** ausschalten. Der G-Modus geht weiterhin.

**Steuer-Modus (`G`)** für alles andere, also Tippen, Ziehen und genaues Zielen mit dem Mauszeiger:

1. **`G`** drücken (oder im N-Menü **Fenster steuern**). Deine Figur bleibt stehen, und ein Mauszeiger erscheint.
2. Mit der Maus auf den Fernseher zeigen. Ein **roter Punkt** zeigt, wo du gerade bist.
3. **Linksklick, Rechtsklick, Mittelklick, Mausrad und Ziehen** gehen ans Fenster.
4. **Tippen** geht ans Fenster, Umlaute inklusive. Dazu Enter, Backspace, Entf, Tab, Pfeile, Pos1, Ende, Bild auf/ab und F1–F12.
5. **`Esc`** beendet den Steuer-Modus. Willst du `Esc` ans Fenster schicken: **`Shift+Esc`**.

Gut zu wissen:
- **Nur du** steuerst, und zwar nur auf deinem PC. Zuschauer können nichts klicken, und über den Relay geht keine Eingabe.
- Geht bei Fernsehern, die deine Quelle zeigen: Schilder ohne `ch:` und Schilder mit deinem Kanal, während du sendest.
- Geht nur mit einem **Fenster** als Quelle, nicht mit einem Monitor.
- Deine echte Maus bewegt sich dabei nicht.
- **Zeiger im Stream** (N-Menü): Während du sendest, sehen die Zuschauer einen Mauszeiger im Bild, damit sie wissen, wo du klickst. Abschaltbar.
- Tastenkürzel mit **Strg** (z. B. Strg+C, Strg+V) werden versucht, klappen aber nicht in jeder App.
- **Spiele** und Programme, die als **Administrator** laufen, nehmen solche Eingaben meistens nicht an.

---

## 6. Kanäle und Passwörter

Der Relay braucht **kein** Start-Passwort. Stattdessen hat **jeder Kanal sein eigenes**:

1. Du sendest zum ersten Mal auf `kino` mit Passwort `rabe123` → der Kanal `kino` gehört jetzt dir.
2. Jemand anders versucht `kino` mit einem anderen Passwort → wird abgelehnt.
3. Du sendest wieder mit `rabe123` → klappt.

Das heißt:
- **Mehrere Leute können gleichzeitig streamen**, jeder auf seinem eigenen Kanal.
- Keiner kann deinen Stream kapern oder stoppen.
- Zum **Zuschauen** braucht niemand ein Passwort.
- Passwort mindestens **4 Zeichen**, keine Leerzeichen.
- Kanalnamen: nur `a-z`, `0-9`, `_` und `-`, maximal 32 Zeichen.

### Wo die Kanäle gespeichert sind
In `blackytv-kanaele.properties` im selben Ordner wie der Relay. Die Kanäle bleiben auch nach einem Neustart vom Relay vergeben.
Die Passwörter stehen da **nicht im Klartext** drin, nur ein Hash (SHA-256).

### Kanal wieder freigeben
Passwort vergessen oder Kanal soll jemand anders kriegen:
1. Relay stoppen
2. In `blackytv-kanaele.properties` die Zeile mit dem Kanal löschen
3. Relay neu starten

> Die Verbindung ist **unverschlüsselt** (HTTP). Nimm also **nicht** dein echtes Passwort von irgendwas, sondern einfach was Ausgedachtes.

---

## 7. Ton

- **Fenster als Quelle** → nur der Sound von der App, der das Fenster gehört (z. B. nur Chrome)
- **Monitor als Quelle** → alles, was am PC läuft, **außer Minecraft** (sonst gäb's ein Echo)

Zuschauer hören den Ton:
- **voll** bis 4 Blöcke Abstand von der **Leinwand**, gemessen zum nächsten Punkt der Leinwand. Bei großen Leinwänden ist es also egal, ob du vor der Mitte oder vor dem Rand stehst.
- dann immer leiser, ab **32 Blöcken** gar nicht mehr
- **Große Leinwand = größere Reichweite:** ab 16 Blöcken Breite wächst die Reichweite mit. Eine 32 Blöcke breite Leinwand ist voll bis 8 und still ab 64 Blöcken. Maximal das Dreifache (voll bis 12, still ab 96).
- Der **Master-Regler** von Minecraft zählt mit
- Dazu kommt deine eigene **Hören**-Einstellung: im N-Menü per Knopf (0 / 25 / 50 / 75 / 100 / 150 / 200 %) oder genau mit `/blackytv lautstaerke 80`. Gilt nur für dich, die anderen stellen ihre eigene ein.

**Als Streamer** stellst du mit **Stream-Ton** ein, wie laut dein Ton rausgeht, z. B. wenn die App zu leise ist: im N-Menü per Knopf oder genau mit `/blackytv streamton 150`. Das hören dann **alle** so. Über 100 % wird lauter, bis es oben abgeschnitten wird, also nicht übertreiben, sonst klingt's kratzig.
- Stehen mehrere Fernseher oder Lautsprecher mit dem gleichen Kanal rum, zählt der, der bei dir am lautesten ist

### Lautsprecher
Für große Kinos oder Hallen kannst du zusätzliche **Lautsprecher** aufstellen, also Schilder, die nur Ton machen:
```
blackytv:speaker
ch:kino
```
- Der Ton kommt dann auch von dort, mit denselben Abständen (voll bis 4 Blöcke, ab 32 Blöcken still).
- So viele, wie du willst: Es zählt immer der lauteste, also meistens der nächste Fernseher oder Lautsprecher.
- Anderer Relay geht wie beim Fernseher: `@1.2.3.4` in eine eigene Zeile.
- Das Schild kannst du wie beim Fernseher in einem Block verstecken.

Deinen **eigenen** Stream hörst du nicht doppelt. Der wird bei dir stumm geschaltet, weil du den Ton ja eh direkt vom PC hörst.

---

## 8. Alle Befehle und Tasten

| Was | Wie |
|---|---|
| Menü öffnen | **`N`** (änderbar unter Optionen → Steuerung → BlackyTV) |
| Relay einstellen | `/blackytv relay <host:port>` |
| Lautstärke der Fernseher (für dich) | `/blackytv volume <0-200>` oder `/blackytv lautstaerke <0-200>` |
| Lautstärke beim Streamen (für alle) | `/blackytv streamvolume <0-200>` oder `/blackytv streamton <0-200>` |
| Senden starten | `/blackytv stream <kanal> <passwort>` |
| Senden stoppen | `/blackytv stop` |
| Fenster steuern | **`G`** (änderbar), beenden mit `Esc`, `Shift+Esc` schickt `Esc` ans Fenster |
| Relay starten (Server) | `java BlackyTVRelay.java [port]` |

---

### Sprachen
Alle Texte im Spiel gibt's auf **Deutsch, Englisch, Französisch und Spanisch**. BlackyTV nimmt automatisch die Sprache, die in Minecraft eingestellt ist. Die Befehle heißen überall gleich: Die englischen Namen (`volume`, `streamvolume`) und die deutschen (`lautstaerke`, `streamton`) funktionieren beide.

---

## 9. Config-Datei

Liegt bei jedem Spieler in `.minecraft/config/blackytv.properties`:

| Eintrag | Bedeutung |
|---|---|
| `source` | zuletzt ausgewähltes Fenster/Monitor |
| `fps` | Bilder pro Sekunde |
| `relay` | Standard-Relay (z. B. `http://1.2.3.4:8765`) |
| `channel` | letzter Kanal zum Senden |
| `key` | letztes Passwort zum Senden |
| `awake` | Fenster wach halten an/aus |
| `cursor` | Zeiger im Stream an/aus |
| `tap` | Fadenkreuz-Klick an/aus |
| `maxw` | Qualität (Breite in Pixeln) |
| `volume` | Lautstärke der Fernseher in % (Hören) |
| `sendVolume` | Lautstärke beim Streamen in % (Stream-Ton) |

Für Zuschauer reicht es, wenn `relay` stimmt. Alternativ kommt `@host:port` direkt aufs Schild, dann muss keiner was einstellen.

---

## 10. Probleme und Lösungen

| Problem | Lösung |
|---|---|
| `G` macht nichts / „nur mit einem Fenster“ | Im N-Menü ein **Fenster** als Quelle wählen, kein Monitor |
| Steuer-Modus an, aber kein roter Punkt | Auf einen Fernseher zeigen, der **dein** Fenster zeigt (ohne `ch:` oder mit deinem Kanal beim Senden) |
| Klick landet daneben | Melde es mit deiner Windows-Skalierung (100 %, 125 %, 150 %). Kurz testweise auf 100 % stellen hilft beim Eingrenzen. |
| „Fenster nimmt keine Eingaben an“ | Läuft das Programm als Administrator? Dann normal starten. Spiele unterstützen das oft gar nicht. |
| Tippen kommt nicht an | Erst einmal ins Textfeld **klicken**, dann tippen |
| Fernseher bleibt schwarz | Steht der Kanal richtig auf dem Schild (`ch:kino`)? Streamt gerade jemand? Ist der Relay erreichbar? |
| „Relay-Fehler 404“ beim Senden | Der Relay ist noch alt. Neue `BlackyTVRelay.java` hochladen und neu starten ([Relay aktualisieren](#relay-aktualisieren)) |
| „Relay nicht erreichbar“ | IP/Port mit `/blackytv relay` prüfen, Port freigegeben? Relay gestartet? |
| „Kanal gehört wem anders / falsches Passwort“ | Anderen Kanalnamen nehmen oder das richtige Passwort. Notfalls Zeile in `blackytv-kanaele.properties` löschen. |
| Bild bleibt stehen, sobald was über dem Fenster liegt | Im N-Menü **Wach halten** anmachen (Browser/Discord malen sonst nicht weiter) |
| Ein Fenster ist „verschwunden“ | Das ist noch im Wach-halten-Modus. In BlackyTV was anderes auswählen oder **Aus**, oder die App neu starten. |
| Bild ist unscharf | Im N-Menü **Qualität** hochstellen (1280 oder 1920). Ist das Quell-Fenster selbst klein, hilft's, das Fenster größer zu ziehen. |
| Block steckt in der Leinwand / Leinwand an falscher Stelle | Mit `off:0,0,1` nach vorne schieben, `off:0,0,-1` nach hinten |
| Bild ruckelt | FPS im Menü runter (z. B. 20). Große Fenster brauchen länger zum Abgreifen. Langsames Internet beim Streamer → es kommen automatisch weniger Bilder an. |
| Fenster zeigt nur schwarz | Manche Apps (geschützte Videos, z. B. Netflix im Browser) blockieren Aufnahmen absichtlich. Anderes Fenster oder Monitor probieren. |
| Kein Ton | Windows 10 älter als Version 2004? Dann geht kein Ton. Sonst: läuft in der App überhaupt was? Zuschauer: zu weit weg vom Fernseher? |
| Fenster taucht nicht in der Liste auf | Im Menü auf **Neu laden** klicken |
| Leinwand ist auf der falschen Seite | Schild umdrehen oder mit `off:` verschieben |
| Änderungen am Code wirken nicht | In IntelliJ Gradle neu laden und neu bauen |
