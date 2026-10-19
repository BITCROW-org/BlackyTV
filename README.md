<div align="center">

# 📺 BlackyTV

**Fernseher in Minecraft, auf denen ein Fenster oder Monitor von deinem PC läuft, mit Ton.**
Über einen kleinen Relay-Server streamen mehrere Leute gleichzeitig auf eigenen Kanälen, und alle mit der Mod schauen zu.

[![Release](https://img.shields.io/github/v/release/BITCROW-org/BlackyTV?label=Download)](https://github.com/BITCROW-org/BlackyTV/releases/latest)
[![Build](https://github.com/BITCROW-org/BlackyTV/actions/workflows/build.yml/badge.svg)](https://github.com/BITCROW-org/BlackyTV/actions/workflows/build.yml)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-green)
![Fabric](https://img.shields.io/badge/Loader-Fabric-dbd0b4)

[Download](https://github.com/BITCROW-org/BlackyTV/releases/latest) · [Verbinden](VERBINDEN.md) · [Anleitung](ANLEITUNG.md) · [Relay-Server](#-relay-server) · [Bauen](#-selbst-bauen)

</div>

---

## ✨ Features

- **Beliebiges Fenster oder ganzer Monitor** als Bild, auswählbar im Spiel mit `N`
- **Ton mit übertragen**: bei Fenstern nur die App, bei Monitoren alles außer Minecraft
- **Für alle sichtbar** über einen eigenen Relay-Server, mehrere Streamer gleichzeitig
- **Kanäle mit Passwort**: wer einen Kanal zuerst benutzt, dem gehört er
- **Fernseher per Schild**, beliebige Größe bis 100×56 Blöcke, verschiebbar
- **Ton mit Abstand**: je näher am Fernseher, desto lauter
- **Läuft im Hintergrund weiter**, auch bei verdeckten oder minimierten Fenstern
- **Fenster im Spiel steuern**: mit dem Fadenkreuz klicken und scrollen, mit `G` auch tippen und ziehen
- **Einstellbar**: Qualität bis 1920 px, 5–60 FPS, Lautstärke bis 200 %
- **4 Sprachen**: Deutsch, Englisch, Französisch, Spanisch

| | Streamen | Zuschauen |
|---|:---:|:---:|
| Windows 10 (2004+) / 11 | ✅ | ✅ |
| macOS / Linux | ❌ | ✅ |

---

## 📥 Installation

1. [Fabric Loader](https://fabricmc.net/use/) für Minecraft **26.2** installieren
2. [Fabric API](https://modrinth.com/mod/fabric-api) in den `mods`-Ordner legen
3. Die neueste **BlackyTV-Jar** aus den [Releases](https://github.com/BITCROW-org/BlackyTV/releases/latest) in den `mods`-Ordner legen

Jeder, der zuschauen will, braucht die Mod. Ohne Mod sieht man nur ein Schild.

---

## 🚀 Schnellstart

Relay aufsetzen und alles Schritt für Schritt verbinden: **[VERBINDEN.md](VERBINDEN.md)**

**1. Fernseher aufstellen.** Ein Schild beschriften mit:

```
blackytv:screen
16x9
ch:kino
```

**2. Quelle wählen.** `N` drücken und ein Fenster oder einen Monitor anklicken.

**3. Senden.**

```
/blackytv relay deine-server-ip:8765
/blackytv stream kino meinPasswort
```

Fertig. Alle mit einem `ch:kino`-Schild sehen jetzt deinen Stream. Ohne `ch:` zeigt ein Fernseher nur deinen eigenen PC, und zwar nur dir.

➡️ Alles im Detail: [Fernseher aufstellen](ANLEITUNG.md#4-fernseher-aufstellen) · [Streamen](ANLEITUNG.md#5-streamen) · [Fenster steuern](ANLEITUNG.md#fenster-im-spiel-steuern)

---

## 🪧 Schild-Aufbau

| Zeile | Inhalt | Beispiel |
|---|---|---|
| 1 | `blackytv:screen` | |
| 2 | Größe in Blöcken `BreitexHöhe`, `0` = automatisch | `16x9`, `20x0`, `1x0.5` |
| 3 / 4 | `ch:kanal` Stream vom Relay | `ch:kino` |
| | `@host:port` anderer Relay | `@1.2.3.4:8765` |
| | `off:rechts,hoch,vor` verschieben | `off:0,0,2` |

**Lautsprecher** für große Räume: ein Schild mit `blackytv:speaker` und darunter `ch:kino`. Der Ton kommt dann auch von dort. → [Mehr dazu](ANLEITUNG.md#lautsprecher)

Die Leinwand erscheint hinter dem Schild. So kannst du das Schild in einem Block verstecken. → [Mehr dazu](ANLEITUNG.md#verschieben-mit-off)

---

## ⌨️ Befehle & Tasten

| Was | Befehl |
|---|---|
| Menü öffnen | **`N`** (änderbar in der Steuerung) |
| Relay einstellen | `/blackytv relay <host:port>` |
| Senden starten | `/blackytv stream <kanal> <passwort>` |
| Senden stoppen | `/blackytv stop` |
| Fenster steuern | **`G`**, beenden mit `Esc` |
| Lautstärke der Fernseher (für dich) | `/blackytv volume <0-200>` |
| Lautstärke beim Streamen (für alle) | `/blackytv streamvolume <0-200>` |

Die deutschen Befehle `lautstaerke` und `streamton` funktionieren auch. → [Alle Befehle](ANLEITUNG.md#8-alle-befehle-und-tasten)

---

## 🖥 Relay-Server

Der Relay verteilt Bild und Ton an alle Zuschauer. Er ist **eine einzige Java-Datei** ohne Abhängigkeiten und liegt in [`relay/BlackyTVRelay.java`](relay/BlackyTVRelay.java). Außerdem hängt er an jedem [Release](https://github.com/BITCROW-org/BlackyTV/releases/latest).

```
java BlackyTVRelay.java [port]
```

- Braucht **Java 21+**, Standard-Port ist **8765** (TCP freigeben)
- Kein Start-Passwort: **Wer einen Kanal zuerst benutzt, legt dessen Passwort fest**
- Gespeichert wird nur ein SHA-256-Hash in `blackytv-kanaele.properties`
- Zuschauen braucht kein Passwort
- Bild und Ton laufen über **eine offene Verbindung** pro Spieler, nicht über eine Anfrage pro Bild
- Mod **1.1.0+** braucht den neuen Relay, der neue Relay kann aber auch noch 1.0.x

Damit deine Spieler nichts einstellen müssen, trag deinen Relay in [`Settings.java`](src/main/java/org/bitcrow/blackytv/config/Settings.java) bei `DEFAULT_RELAY` ein und bau die Mod neu.

➡️ [Relay aufsetzen](ANLEITUNG.md#3-relay-server-aufsetzen) · [Kanäle & Passwörter](ANLEITUNG.md#6-kanäle-und-passwörter) · [Traffic](ANLEITUNG.md#traffic)

> ⚠️ Die Verbindung ist unverschlüsselt (HTTP). Nimm als Kanal-Passwort nichts, was du woanders benutzt.

---

## ❓ Probleme?

Die häufigsten Fragen, z. B. schwarzer Fernseher, Bild bleibt stehen, kein Ton oder Relay nicht erreichbar, stehen mit Lösung in der [Anleitung](ANLEITUNG.md#10-probleme-und-lösungen).

Bleibt bei Chrome oder Discord das Bild stehen, sobald etwas darüber liegt, im Menü **Wach halten** einschalten. → [Warum?](ANLEITUNG.md#fenster-wach-halten-browser-discord--co)

---

## 🔨 Selbst bauen

Voraussetzung: **JDK 25** und **Gradle 9.7+**

```
gradle build
```

Die Jar liegt dann in `build/libs/`. In IntelliJ unter *Settings → Build Tools → Gradle → Gradle JVM* JDK 25 wählen, dann *Gradle → Tasks → build → build*.

### Projektstruktur

```
src/main/java/org/bitcrow/blackytv/
├── BlackyTV.java      Einstiegspunkt
├── capture/           Bild und Ton vom PC (Windows, JNA)
├── stream/            Senden an und Empfangen vom Relay
├── tv/                Schilder finden, Leinwand rendern
├── command/           /blackytv
├── gui/               Menü (Taste N)
└── config/            Einstellungen
src/main/resources/assets/blackytv/lang/   Übersetzungen (de, en, fr, es)
relay/BlackyTVRelay.java                   Relay-Server
```

### Releases

Releases erstellt GitHub Actions automatisch. Die Version steht in [`gradle.properties`](gradle.properties):

```
mod_version=1.0.0
```

Bei jedem Push wird gebaut. Gibt es für die Version noch kein Release, entsteht automatisch `v1.0.0` mit Jar und Relay-Datei. Für ein neues Release einfach die Version erhöhen und pushen.

---

<div align="center">

Made by [BitCrow.org](https://github.com/BITCROW-org)

</div>
