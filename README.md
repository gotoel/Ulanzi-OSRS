# Ulanzi OSRS Clock

RuneLite plugin that drives an [Ulanzi TC001](https://github.com/rroels/ulanzi_tc001_hardware) pixel clock over [AWTRIX NG](https://blueforcer.github.io/awtrix-ng/).

While you play it can show hitpoints, prayer, run energy and special attack on the 32×8 panel. When you go idle it takes over the whole display with a large AFK indicator (custom text, colors, optional rainbow, blink, and a short buzzer tone).

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
2. Toggle **Test connection** — the clock should flash green `OK`.
3. Enable the AFK and stats options you want.

### AFK

- Idle timeout (mouse/keyboard, same idea as Idle Notifier)
- Custom text, text color, background color
- Rainbow text, blink, optional buzzer on enter
- Held notification is re-asserted every few seconds if you dismiss it with the center button

### Stats

- **Big**: large font, one value at a time (HP / prayer / energy / spec), rotating
- **Compact**: small colored fragments on one line plus an HP progress bar
- Per-stat toggles and low HP / low prayer flash alerts

## Notes

- Uses AWTRIX NG HTTP API v1 (`PUT /api/v1/apps/pushed/osrs`, `POST /api/v1/notifications`). Not the older AWTRIX 3 paths.
- Pushed apps live in device RAM and disappear on clock reboot; the plugin re-sends them while you are logged in.
- HTTP runs off the client thread via OkHttp `enqueue()`.
