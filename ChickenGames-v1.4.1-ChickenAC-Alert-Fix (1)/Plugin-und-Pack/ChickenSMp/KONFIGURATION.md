# ChickenGames – Konfiguration (v1.3.0)

Die zentrale Datei ist `plugins/ChickenSMPCore/config.yml`.
Nach Änderungen im laufenden Betrieb als OP/Admin `/chicken reload` ausführen.
Die neue Version ergänzt beim Start fehlende Standardoptionen automatisch und behält bereits gesetzte Werte bei.

## Shop vollständig konfigurieren

Der Shop ist komplett datengetrieben. Kategorien und Produkte können beliebig ergänzt, deaktiviert oder umsortiert werden.

### Hauptmenü und allgemeine Shop-Einstellungen

Unter `shop`:

- `enabled`: gesamten Shop an-/ausschalten.
- `title`: Titel des Hauptmenüs.
- `size`: Größe des Hauptmenüs (9, 18, 27, 36, 45 oder 54 Slots; ungültige Werte werden auf gültige Größe gerundet).
- `category-size`: Standardgröße für Kategorie-Menüs.
- `drop-overflow`: legt bei vollem Inventar übrige gekaufte Items neben dem Spieler ab. Bei `false` wird der Kauf abgebrochen und das Geld zurückerstattet.
- `display.price-line`: Preiszeile der Produkte; Platzhalter `{currency}`, `{price}` und `{amount}`.
- `display.click-line`: Hinweis unter dem Preis; leer lassen, um ihn auszublenden.
- `sounds.open`, `sounds.purchase`, `sounds.error`: Bukkit/Paper-Soundnamen; `NONE` oder leer deaktiviert den jeweiligen Sound.
- `back-button`: Zurück-Button mit `enabled`, `slot`, `material`, `name` und `lore`.

### Kategorien

Jede Kategorie liegt unter `shop.categories.<id>` und hat:

- `enabled`: Kategorie aktivieren/deaktivieren.
- `menu-slot`: Slot im Hauptmenü (0 bis Menügröße minus 1).
- `icon`, `name`, `lore`: Symbol und Darstellung im Hauptmenü.
- `title`: Titel des Kategorie-Menüs.
- `size`: eigene Menügröße; bei Weglassen gilt `category-size`.
- `permission` (optional): Permission zum Öffnen der Kategorie. Ohne diesen Eintrag ist sie für alle zugänglich.

### Produkte

Jedes Produkt liegt unter `shop.categories.<id>.items.<produkt-id>` und unterstützt:

- `enabled`: Produkt anzeigen/verkaufen oder deaktivieren.
- `slot`: GUI-Slot.
- `material`: gültiger Bukkit-Materialname, z. B. `ENDER_PEARL`, `DIAMOND` oder `GOLDEN_APPLE`.
- `name`: Name im Shop-Menü (Farbcodes mit `&`).
- `lore`: zusätzliche Beschreibung im Shop-Menü.
- `amount`: Anzahl pro Kauf (1 bis 64).
- `price`: Gesamtpreis für genau diese konfigurierte Menge.
- `permission` (optional): Permission zum Kaufen. Spieler ohne Permission sehen das Produkt nicht.
- `item-display-name` (optional): eigener Name des gekauften Items im Spielerinventar.
- `item-lore` (optional): Lore des gekauften Items.

### Beispiel: eigenes Produkt ergänzen

Unter `shop.categories.equipment.items` ergänzen:

```yaml
  diamond-pack:
    enabled: true
    slot: 19
    material: DIAMOND
    name: "&bDiamant-Paket"
    lore:
      - "&7Ein Paket mit Diamanten"
    amount: 8
    price: 500
    permission: "chickenshop.buy.diamond"
    item-display-name: "&bChicken-Diamanten"
    item-lore:
      - "&7Im ChickenGames-Shop gekauft"
```

Achte darauf, dass sich keine zwei Produkte denselben Slot teilen und dass der Slot nicht durch den Zurück-Button belegt ist.
Wenn eine Kategorie 27 Slots hat, sind die Slots 0 bis 26 gültig.

## Weitere Plugin-Einstellungen

- `discord-console`: Discord-Konsolenweiterleitung, URL, Secret, Mindest-Loglevel, Intervall und IP-Redaktion.
- `resource-pack`: Resource-Pack aktivieren, Download-URL, SHA1 und Pflichtannahme.
- `scoreboard`: Pack-Icons und Rahmen.
- `economy`: Startguthaben und Währungssymbol.
- `homes.max-per-player`: maximale Homes pro Spieler; `0` oder kleiner bedeutet unbegrenzt.
- `teleport`: Teleport-Countdown, TPA-Ablaufzeit und RTP-Radius/Welt.
- `combat.tag-seconds`: Dauer der Kampfmarkierung.
- `messages`: Shop-Meldungen und Fehlermeldungen.

## Hinweis zur Kompatibilität

Die Shop-Menüs werden über einen eigenen Inventory-Holder identifiziert und nicht mehr anhand des sichtbaren Titels. Dadurch brechen Shop-Klicks nicht, wenn Farbcodes oder Resource-Pack-Zeichen den Inventartitel verändern.
