# 🐔 ChickenSMPCore

**ChickenSMPCore** ist ein leistungsstarkes, maßgeschneidertes Paper-Plugin (Minecraft 1.21.4), entwickelt für das ultimative SMP-Erlebnis. Es kombiniert Wirtschaftssysteme, custom Item-Werte, PvP-Combat-Systeme, Teleportation und modernste GUIs im beliebten HugoSMP-Stil.

---

## 📑 Inhaltsverzeichnis
- [Features](#-features)
- [Befehle & Permissions](#-befehle--permissions)
  - [Spieler-Befehle](#-spieler-befehle)
  - [Wirtschaft & Shop](#-wirtschaft--shop)
  - [Staff & Admin-Befehle](#-staff--admin-befehle)
- [Installation](#-installation)
- [Technischer Stack](#-technischer-stack)

---

## ✨ Features

- 📊 **Dynamic Scoreboard:** Live-Anzeige von Kontostand, Kills, Toden, Online-Spielern und Server-Info (vollständig persistent).
- 🛒 **Aesthetic Shop System:** GUI-Shop-Kategorien (Nether, Ausrüstung, Essen, Specials) inklusive Zurück-Buttons und Custom-Items.
- 💰 **Custom Item Valuation (`/sell` & `/meta`):** Dynamisches Verkaufen von Items per Hand-Klick mit visueller Lore-Anzeige (`Wert: $XX.XX`).
- ⏱️ **Teleport- & Combat-System:**
  - 3 Sekunden Teleport-Timer über der Actionbar (mit Bewegungs- & Kampf-Abbruch) für `/tpa`, `/home`, `/rtp` und `/spawn`.
  - 30 Sekunden Combat-Log-Timer mit automatischer Bestrafung (Tod & Inventar-Drop beim Ausloggen im Kampf).
- 🏆 **Leaderboards:** `/baltop` zur Anzeige der reichsten Spieler auf dem Server.
- 💾 **100% Persistent:** Kontostände, Ränge, Homes, Kills und Tode überleben Server-Restarts automatisch.

---

## 📜 Befehle & Permissions

### 👤 Spieler-Befehle

| Befehl | Beschreibung | Nutzung |
| :--- | :--- | :--- |
| `/spawn` | Teleportiert dich zum World-Spawn (3s Countdown). | `/spawn` |
| `/rtp` | Öffnet das Menü für den Zufalls-Teleport. | `/rtp` |
| `/sethome <Name>` | Setzt ein neues Home an deiner aktuellen Position. | `/sethome <Name>` |
| `/home [Name]` | Teleportiert dich zu einem Home oder öffnet das Homes-GUI. | `/home home1` |
| `/delhome <Name>` | Löscht ein gespeichertes Home. | `/delhome <Name>` |
| `/homes` | Öffnet das interaktive Homes-GUI. | `/homes` |
| `/tpa <Spieler>` | Sendet eine Teleport-Anfrage an einen Spieler. | `/tpa <Spieler>` |
| `/tpaccept` | Nimmt eine eingehende TPA-Anfrage an. | `/tpaccept` |
| `/tpdeny` | Lehnt eine eingehende TPA-Anfrage ab. | `/tpdeny` |

---

### 💵 Wirtschaft & Shop

| Befehl | Beschreibung | Nutzung |
| :--- | :--- | :--- |
| `/balance`, `/bal` | Zeigt deinen aktuellen Kontostand an. | `/balance` |
| `/baltop` | Zeigt die Top 10 der reichsten Spieler an. | `/baltop` |
| `/pay <Spieler> <Betrag>` | Überweist Geld an einen anderen Spieler. | `/pay Player1 250` |
| `/sell` | Verkauft das Item in deiner Haupthand zum festgelegten Wert. | `/sell` |
| `/shop` | Öffnet das Haupt-Shop-GUI. | `/shop` |
| `/ah` | Öffnet das Auktionshaus-GUI. | `/ah` |
| `/order`, `/orders` | Öffnet das Ankauf- & Bestell-GUI. | `/order` |

---

### 🛡️ Staff & Admin-Befehle

| Befehl | Beschreibung | Permission / Rolle |
| :--- | :--- | :--- |
| `/vanish` | Schaltet den Vanish-Modus um (Spectator-Versteck). | Mod, Developer, Owner |
| `/punish <Spieler> <Grund>` | Bannt einen Spieler dauerhaft mit Grund. | Mod, Developer, Owner |
| `/timeout <Spieler> <Sek> <Grund>` | Kickt einen Spieler für eine bestimmte Zeit. | Mod, Developer, Owner |
| `/setmeta <Preis>` | Setzt einen benutzerdefinierten Verkaufswert für das gehaltene Item. | Mod, Developer, Owner |
| `/setrank <Spieler> <Rang>` | Ändert den Rang eines Spielers (Owner, Admin, Dev, Mod, VIP, Spieler). | `chickensmp.admin` |
| `/adminpay <Spieler> <Betrag>` | Generiert Geld für einen Spieler (Developer-Tools). | Developer |

---

## 📦 Installation

1. Lade die neueste `ChickenSMPCore-1.2.0.jar` aus dem `target/`-Ordner herunter.
2. Platziere die `.jar`-Datei in den `plugins/`-Ordner deines **Paper 1.21.4** Serververzeichnisses.
3. Starte den Server neu. Die Konfigurations- & Speicherdateien (`balances.yml`, `homes.yml`, `ranks.yml`, `stats.yml`) werden automatisch generiert.

---

## 🛠️ Technischer Stack

- **Java Version:** 21
- **API:** Paper-API 1.21.4-R0.1-SNAPSHOT
- **UI & Messaging:** Kyori Component / Adventure API
- **Build-Tool:** Apache Maven
