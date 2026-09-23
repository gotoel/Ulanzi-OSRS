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

**Brightness** decides who sets the panel level. *Leave to the clock* follows its light
sensor, which takes a dark room far enough down that the dimmer half of a page — bar
tracks, the gradient's low end, the resting dim of a pulse — falls under what the LEDs can
show. *Fixed* pins the panel instead, since the firmware has no floor under its automatic
setting. Whatever the clock was set to is read once and put back when you log out or
switch the plugin off, the same as your app rotation.

If the clock stops answering while you play, the plugin says so in the game chat, and again when it is back.

### AFK

- Idle timeout (mouse/keyboard, same idea as Idle Notifier)
- **Full panel** takes over the display with your text, colors and an effect: solid, blinking, color waves, plasma, or looking eyes
- **On the stats page** keeps your stats up and grays them out (or adds an AFK label) instead
- Optional buzzer when AFK starts
- The held notification is re-sent every few seconds if you dismiss it with the center button

### Stats

- **Big**: one large value at a time with its orb icon (heart, prayer star, boot, crossed swords), rotating
- **Compact**: every value on one line, read in three tiers. A row of dashes along the top names each value in the colour that stat is always known by; the values sit in the middle; the bottom row is a strip of bars sharing the full width. Each value keeps a slot sized for the largest number that stat can reach and is right aligned in it, so a number changing from 100 to 99 changes in place instead of sliding the rest of the line sideways
- **Focus**: whichever stat moved last, large, with every stat you have switched on keeping its bar on the bottom strip. It holds a stat for a couple of seconds once it has the panel, and only a bigger move takes it early, so run energy ticking over while you walk does not keep the panel to itself
- Each stat is **Off**, **Value**, **Bar**, or **Both**
- Every stat now answers its own value: full it keeps its own colour, and it ramps through amber to red as it drains, so a glance says something is running out before any of the digits have been read. Hitpoints used to be the only one that did this, and only in three steps
- Bars fill below a whole pixel — the pixel the fill stops on is lit in proportion to how far into it the value reaches — so a bar answers a change of a percent or two instead of sitting still until it has earned a whole pixel
- When a stat drops, what it was a moment ago is left showing dimly behind the new reading for about a second, so damage and drain are visible as they happen rather than only once a threshold is crossed
- How dim the panel is changes what the plugin sends it. The clock scales every channel for the room it is in, and what decides whether a pixel exists at all is its brightest channel after that scaling — on a TC001 at brightness 1, a green sent at peak 180 is still green while the same green sent at peak 40 is simply not there. So the floor below is worked backwards from the level the panel is running at, activity icons trade their own shading for hue as it falls (a tree trunk's brown holds its blue instead of collapsing into red), and the trail behind a dropped reading is left out once there are too few steps left to tell it from the track under it. None of this changes anything at an ordinary brightness
- Below about 8 the panel has barely a step of colour left per channel and no amount of this brings a hue back, so that is as low as **Fixed brightness** goes
- Nothing lit is sent close to black. The clock scales every channel again for the room it is in, so a colour already most of the way down lands under what an LED can show once the lights go out — a bar's track, its trail and its fill were all being squeezed together. Dim things keep a floor, and their hue, so the three stay three
- The activity icon shows the skill you are training, or melee, ranged, or magic while fighting. If the values no longer fit beside it, they turn into bars on the bottom strip, which costs the line nothing horizontally. **Keep on screen** picks which stat gives up its digits last

### Skill progress

- Optional. While the activity icon is up, the stats make way for the skill you are training: its **level**, **XP per hour**, or **both** (side by side when they fit, taking turns when they do not)
- **XP/h source** picks how the rate is worked out. *Sliding window*, the default, averages only the last **Sliding window** seconds, so the rate follows what you are doing now: it climbs from zero over the first window and falls back to zero when you stop, the way the [XP Meter](https://github.com/Toofifty/xp-meter) plugin's sliding window does. *Session* averages the skill from your second XP drop instead. Both start over after 5 minutes without XP. *RuneLite XP Tracker* matches its panel and globes, including its pause and reset settings
- The bottom row fills toward the next level with a gradient: the skill's color, red to green, or rainbow
- Combat keeps hitpoints and prayer up unless **Also in combat** is on; melee follows whichever of Attack, Strength or Defence the XP goes to
- **XP drops** show each gain, like `+175 xp`, beside the skill icon: right to left, left to right, bottom to top, top to bottom, or in place, which briefly swaps out the level and XP/h without moving. Gains that land mid-flight are added to the next one
- **Pulse** is the drop direction for anyone who would rather not have text moving about: nothing is written, and instead the whole panel — text, bars, progress gradient and the activity icon — snaps to full colour the moment XP lands and fades back over a second. Between drops the panel rests dimmed, which is what gives white anything to brighten from: white is already at full, so there is no headroom above it. The dim only applies to the ordinary page: a low-hitpoints or low-prayer alert, and the AFK display, always run at full brightness, since they are up to be noticed

### Alerts

- Low hitpoints and prayer, as a percent of your max (default) or in points
- Flash on the stats page or as a full-panel popup, with optional beeps, and again on each further drop
- When AFK and an alert are active together, pick which wins or rotate between them

### Level ups

- The skill's icon and your new level in rainbow text for a few seconds, with an optional jingle

## Notes

- Uses AWTRIX NG HTTP API v1 (`PUT /api/v1/apps/pushed/osrs`, `POST /api/v1/notifications`). Not the older AWTRIX 3 paths.
- The compact and focus pages are drawn with the `draw` command list rather than handed over as text for the firmware to centre, which is what lets a value keep a fixed column. Text is placed with `["text", x, y, ...]`, where y is the top of the glyph — the draw list's convention, not the scripting API's, where `text()` takes a baseline instead.
- Pushed apps live in device RAM and disappear on clock reboot; the plugin re-sends them while you are logged in.
- While you are logged in the rotation is the stats page and Time. Your own rotation is saved first and put back when you log out or turn the plugin off.
- HTTP runs off the client thread via OkHttp `enqueue()`.
