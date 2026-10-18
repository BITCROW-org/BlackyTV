# BlackyTV

Fernseher in Minecraft, auf denen ein Fenster oder Monitor vom PC läuft, mit Ton. Über einen kleinen Relay-Server können mehrere Leute gleichzeitig auf eigenen Kanälen streamen, und alle Spieler mit der Mod schauen zu.

- Minecraft 26.2 · Fabric · Client-Mod
- Streamen: Windows 10 (2004+) / 11
- Zuschauen: überall

**Wie man die Mod benutzt, steht in der [ANLEITUNG.md](ANLEITUNG.md).**

## Bauen

Voraussetzung ist JDK 25 und Gradle 9.7+.

```
gradle build
```

Die Jar liegt danach in `build/libs/`. In IntelliJ: unter *Settings → Build Tools → Gradle → Gradle JVM* ein JDK 25 auswählen und dann *Gradle → Tasks → build → build*.

## Projektstruktur

```
src/main/java/org/bitcrow/blackytv/
├── BlackyTV.java          Einstiegspunkt
├── capture/               Aufnahme von Bild und Ton (Windows, JNA)
├── stream/                Senden an und Empfangen vom Relay
├── tv/                    Fernseher-Schilder finden und rendern
├── command/               /blackytv
├── gui/                   Menü (Taste N)
└── config/                Einstellungen
relay/
└── BlackyTVRelay.java     Relay-Server, eine Datei, Java 21+
```

## Relay

```
java relay/BlackyTVRelay.java [port]
```

Standard-Port ist 8765. Kanäle gehören dem, der sie zuerst mit einem Passwort benutzt. Gespeichert wird nur ein SHA-256-Hash in `blackytv-kanaele.properties`.
