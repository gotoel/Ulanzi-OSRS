# Ulanzi OSRS Clock

RuneLite plugin that drives an [Ulanzi TC001](https://github.com/rroels/ulanzi_tc001_hardware) pixel clock over [AWTRIX NG](https://blueforcer.github.io/awtrix-ng/).

While you play it can show hitpoints, prayer, run energy and special attack on the 32×8 panel, with an icon for the skill you are training or the combat style you are using. When you go idle it takes over the display with an AFK indicator, low hitpoints and prayer can flash and beep, and level-ups get a short celebration.

Stock Ulanzi firmware cannot push a custom full-screen message. Flash AWTRIX NG once, then point this plugin at the clock's IP.

## 1. Back up stock firmware

Connect the TC001 with a **data** USB-C cable (charge-only cables will not appear as a serial port). On Windows you may need the CH340 driver.

Detect flash size — units vary between **4 MB** and **8 MB**:

```bash
python -m esptool --chip esp32 flash_id
```

Back up the whole chip (pick the size that `flash_id` reported):

```bash
# 4 MB
python -m esptool --chip esp32 --port COM5 --baud 115200 read_flash 0x0 0x400000 tc001-stock-4mb.bin

# 8 MB
python -m esptool --chip esp32 --port COM5 --baud 115200 read_flash 0x0 0x800000 tc001-stock-8mb.bin
```

The file must be exactly 4,194,304 bytes (4 MB) or 8,388,608 bytes (8 MB). Anything shorter is a failed read. Keep that file; restore with `write_flash 0x0 <backup.bin>` if you want stock firmware back.

If the read fails with connection errors, add `--no-stub` and retry.

## 2. Flash AWTRIX NG

Easiest path: open the [AWTRIX NG browser flasher](https://blueforcer.github.io/awtrix-ng/getting-started/flashing/) in Chrome or Edge, choose **Fresh install**, and select the TC001 serial port. It detects chip and flash size for you.

Manual flash from the [latest release](https://github.com/Blueforcer/awtrix-ng/releases) (`usb-awtrix-ng.zip`):

```bash
# 4 MB TC001
python -m esptool --chip esp32 --port COM5 --baud 460800 write_flash 0x0 usb-awtrix-ng-4mb.bin

# 8 MB TC001
python -m esptool --chip esp32 --port COM5 --baud 460800 write_flash 0x0 usb-awtrix-ng-8mb.bin
```

**Do not flash at 921600.** A write at that baud can abort halfway on the TC001 USB bridge and leave the chip unbootable until a successful slower write.

After flashing, the device opens its own Wi-Fi access point. Join it (2.4 GHz only), finish first boot, then read the IP from the scrolling panel.

This plugin does **not** ship or flash firmware. AWTRIX NG is PolyForm Noncommercial — fine for personal use.

## 3. Run the plugin

```bash
./gradlew run
```

In the plugin config:

1. Set **Clock address** to the IP shown on the panel (e.g. `192.168.1.50`).
2. Toggle **Test connection**. The clock should flash a green `OK`, and the result is posted in the game chat.
3. Enable the AFK and stats options you want.

If the clock stops answering while you play, the plugin says so in the game chat, and again when it is back.

### AFK

- Idle timeout (mouse/keyboard, same idea as Idle Notifier)
- **Full panel** takes over the display with your text, colors and an effect: solid, blinking, color waves, plasma, or looking eyes
- **On the stats page** keeps your stats up and grays them out (or adds an AFK label) instead
- Optional buzzer when AFK starts
- The held notification is re-sent every few seconds if you dismiss it with the center button

### Stats

- **Big**: one large value at a time with its orb icon (heart, prayer star, boot, crossed swords), rotating
- **Compact**: every value on one line
- Each stat is **Off**, **Value**, **Bar**, or **Both**
- The activity icon shows the skill you are training, or melee, ranged, or magic while fighting. If the compact line no longer fits beside it, the last values switch to bars.

### Skill progress

- Optional. While the activity icon is up, the stats make way for the skill you are training: its **level**, **XP per hour**, or **both** (side by side when they fit, taking turns when they do not)
- **XP/h source** picks how the rate is worked out. *Sliding window*, the default, averages only the last **Sliding window** seconds, so the rate follows what you are doing now: it climbs from zero over the first window and falls back to zero when you stop, the way the [XP Meter](https://github.com/Toofifty/xp-meter) plugin's sliding window does. *Session* averages the skill from your second XP drop instead. Both start over after 5 minutes without XP. *RuneLite XP Tracker* matches its panel and globes, including its pause and reset settings
- The bottom row fills toward the next level with a gradient: the skill's color, red to green, or rainbow
- Combat keeps hitpoints and prayer up unless **Also in combat** is on; melee follows whichever of Attack, Strength or Defence the XP goes to
- **XP drops** show each gain, like `+175 xp`, beside the skill icon: right to left, left to right, bottom to top, top to bottom, or in place, which briefly swaps out the level and XP/h without moving. Gains that land mid-flight are added to the next one
- **Pulse** is the drop direction for anyone who would rather not have text moving about: nothing is written, and instead the whole panel — text, bars, progress gradient and the activity icon — brightens the moment XP lands and fades back over a second. It pulses whatever is on screen, including an AFK or low-hitpoints overlay

### Alerts

- Low hitpoints and prayer, as a percent of your max (default) or in points
- Flash on the stats page or as a full-panel popup, with optional beeps, and again on each further drop
- When AFK and an alert are active together, pick which wins or rotate between them

### Level ups

- The skill's icon and your new level in rainbow text for a few seconds, with an optional jingle

## Notes

- Uses AWTRIX NG HTTP API v1 (`PUT /api/v1/apps/pushed/osrs`, `POST /api/v1/notifications`). Not the older AWTRIX 3 paths.
- Pushed apps live in device RAM and disappear on clock reboot; the plugin re-sends them while you are logged in.
- While you are logged in the rotation is the stats page and Time. Your own rotation is saved first and put back when you log out or turn the plugin off.
- HTTP runs off the client thread via OkHttp `enqueue()`.
