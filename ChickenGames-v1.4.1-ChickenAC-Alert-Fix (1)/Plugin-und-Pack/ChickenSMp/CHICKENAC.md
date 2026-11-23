# ChickenAC – AntiCheat für ChickenGames

ChickenAC ist in ChickenSMPCore 1.4.1 integriert. Die erste Version erkennt auffällige Muster und meldet sie an Teammitglieder. **Automatische Bans oder Kicks sind absichtlich nicht aktiviert.** Eine Meldung allein ist kein Beweis für Cheats.

## Befehle

| Befehl | Zweck |
|---|---|
| `/chickenac help` | Hilfe anzeigen |
| `/chickenac alerts [on|off|toggle|status]` | Eigene Team-Meldungen einstellen (ohne Zusatz wird eingeschaltet) |
| `/chickenac test` | Testmeldung an alle berechtigten Teammitglieder senden |
| `/chickenac status` | Alert-Einstellungen, aktive Checks und Online-Empfänger anzeigen |
| `/chickenac info <spieler>` | Ping-Anzeige, Ausnahme und Violation-Zähler anzeigen |
| `/chickenac check <spieler>` | Spielerstatus anzeigen |
| `/chickenac exempt <spieler>` | Online-Spieler von Checks ausnehmen |
| `/chickenac unexempt <spieler>` | Ausnahme entfernen |
| `/chickenac debug <on\|off>` | Zusätzliche Debug-Ausgabe aktivieren/deaktivieren |
| `/chickenac reload` | `config.yml` neu laden |

Der Alias ist `/cac`. Admin-Befehle benötigen `chickenac.admin` (standardmäßig OP). Team-Meldungen benötigen `chickenac.alerts` (standardmäßig OP). Spieler mit `chickenac.bypass` sowie OP-Spieler werden von den Checks ausgenommen.

## Aktive Checks

- `fly`: erkennt anhaltendes Steigen oder ungewöhnlich langes Schweben in der Luft.
- `speed`: erkennt sehr große horizontale Bewegungen pro Bewegungs-Event.
- `reach`: prüft die Entfernung vom Auge des Angreifers zur nächstgelegenen Stelle der Ziel-Hitbox.
- `autoclicker`: zählt Armschwünge pro gleitendem Ein-Sekunden-Fenster.
- `fastplace`: zählt platzierte Blöcke pro gleitendem Ein-Sekunden-Fenster.

Alle Checks können einzeln unter `anticheat.checks` in `plugins/ChickenSMPCore/config.yml` aktiviert, deaktiviert und angepasst werden. `anticheat.alerts.cooldown-seconds` begrenzt die Häufigkeit gleicher Team-Meldungen.

## Daten und Hinweise

Ausnahmen und Violation-Zähler werden in `plugins/ChickenSMPCore/chickenac-data.yml` gespeichert. Die Checks sind bewusst vorsichtig eingestellt und können je nach Server-Lag, Effekten, Plugins, Weltregeln oder speziellen Bewegungsmechaniken trotzdem Fehlalarme erzeugen. Vor einer Nutzung im Live-Betrieb sollten die Werte auf einem Testserver beobachtet und angepasst werden.

Nicht enthalten in dieser Version sind automatische Bans, NoFall-, Scaffold-, Timer- oder Jesus-Erkennung.


## Alert-Fehlerdiagnose (1.4.1)

1. `/chickenac alerts on` schaltet die eigenen Meldungen ausdrücklich ein.
2. `/chickenac test` prüft den Alert-Kanal unabhängig davon, ob gerade ein Cheat erkannt wird.
3. `/chickenac status` zeigt, welche Checks aktiv sind und wie viele berechtigte Teammitglieder Alerts erhalten können.
4. Teammitglieder benötigen `chickenac.alerts` oder `chickenac.admin` (standardmäßig OP).
5. Spieler mit OP oder `chickenac.bypass` werden selbst nicht überprüft.

Hinweis: Der Test prüft nur die Weiterleitung. Er beweist nicht, dass ein bestimmtes Cheat-Verhalten von den vorhandenen Checks erkannt werden kann. Derzeit sind Fly, Speed, Reach, AutoClicker und FastPlace enthalten; z. B. X-Ray und KillAura werden von dieser Version nicht geprüft.
