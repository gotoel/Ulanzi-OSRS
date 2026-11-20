# Virtual clock, phase 1: faithful emulator

Goal: anyone without a TC001 gets the same 32×8 pages in a window on their desktop, with sound.
The window can be moved, resized, and optionally kept on top of other windows. Every page the
plugin already sends (compact, orbs, focus, skill bar, XP drops, alerts, AFK, forestry,
level-ups, preview) shows up with no change to how those pages are built.

Out of scope for phase 1: the higher-resolution "Enhanced" look (phase 2), GIF export,
and emulating AWTRIX features the plugin never sends.

## 1. Where it plugs in

Every request already goes through `AwtrixClient.send(method, path, body, test, done)`.
That is the only seam needed.

- New `ClockSink` interface: `void request(String method, String path, String body, Consumer<Result> done)`.
- `HttpClockSink`: today's OkHttp code, moved out of `send` unchanged (auth, status, reachability messages).
- `VirtualClockSink`: hands the request to the emulator. It never touches the network.
- `send` picks the sink from a new **Clock output** setting: *Real clock* (default, today's behaviour),
  *Virtual clock*, or *Both* (mirror, which helps with development).
- In *Virtual* mode the "no clock address" early return is skipped. The status line reads
  "Virtual clock", and the HTTP warning on the address field doesn't apply.

The virtual sink has to answer the GETs `AwtrixClient` already makes, or brightness and
rotation handling will stall:

| Request | Virtual answer |
|---|---|
| `GET /api/v1/settings` | `{brightness: <virtual brightness>, autoBrightness: false, …}` |
| `GET /api/v1/device` | `{hostname: "virtual"}` |
| `GET /api/v1/apps` | the emulator's own app list |
| `PATCH /api/v1/settings` | stores brightness, which dims the virtual LEDs |
| `PUT /api/v1/apps/order`, `/active` | updates the emulator's rotation |

The test button turns into "Show test page" and gets an instant "Connected to the virtual clock."

## 2. The emulator (`com.ulanzi.osrs.virtual`)

All emulator state lives on the Swing EDT. `VirtualClockSink` parses the JSON on the caller's
thread, then `SwingUtilities.invokeLater`s the change. A `javax.swing.Timer` ticks at 30 fps.
`Thread.sleep` is never used, because the Plugin Hub rejects it.

### 2.1 `VirtualClock`: the model

- **Pushed apps:** a map from name to page, plus the rotation order. The plugin only
  pushes `osrs` and makes it the active app, so the rotation can be minimal: show the
  active pushed app, and if there is none, a dim built-in "time" page so the window is never blank.
- **Notification queue** (up to 32), following AWTRIX NG's documented rules:
  - `stack: true` queues it at the back. `stack: false` replaces what's on screen and restarts it.
  - `hold: true` stays until it is DELETEd and blocks the queue behind it.
  - `durationMs` (0 or less means 7000), and `repeat` counts finished scroll runs.
  - `DELETE /notifications/{name}` removes a match anywhere in the queue. `/active` removes the one on screen.
  - Not documented, so we pick: re-POSTing a name that is on screen with `stack: false`
    replaces it in place. Forestry frames and the AFK eyes rely on this.
- `wakeup` and power state: ignored, since the window is always "on".
- Page change: cut straight over in phase 1. A simple fade can come later (the clock's default is "Rain").

### 2.2 `PageRenderer`: one page at time *t* into `int[32*8]`

Layers in AWTRIX NG's order, back to front:
1. Background: `effect` (only **Plasma** is needed) if set, else `backgroundColor`, else black.
2. Text and `draw`: drawings first when `textInFront` is true, otherwise text first.
3. Progress bar on the bottom row, starting right of the icon, with `progressColor` (or `"palette"`) over `progressTrackColor`.
4. Icon (column 0–7, `iconMode: fixed`), then the `icons[]` extras at their x/y (the vertical XP drops).

Text features to support, which is everything the plugin sends:
- `font` `small` / `large`; `textCase` `asTyped` / upper; `text` as a string or as `{text,color}` fragments.
- Centring in the area right of the icon (`textCenter`, and also `textAlign`, since the docs name it that), plus `textOffsetX`.
- `textBlinkMs`: dark for the first half, lit for the second.
- `textColor: "palette"` with `palette`, `paletteSpan`, `paletteSpeed`, `paletteBlend`.
- `scroll`: `static`, and `wrap` with `direction`, `entry: offscreen`, `whenFits: scroll`, `speed`, `holdMs`, at 21 px/s × speed/100.

`draw` ops: implement all nine documented ones (`pixel`, `pixels`, `line`, `rect`, `rectFill`,
`circle`, `circleFill`, `text`, `bitmap`). The plugin uses three of them, and the rest are only a few lines each.

### 2.3 Supporting pieces

- **`PixelFont`**: `small` (capitals 5 px tall, 4 px advance, 2 px space) and `large` (7 px tall
  plus a descender row). The glyph data goes in a resource file read with `getResourceAsStream`.
  AWTRIX NG says its font data is under the **SIL OFL 1.1** (built from `awtrix.bdf` and the Matrix
  fonts), so it can ship with the OFL notice next to it. Fallback: draw our own glyphs to the same metrics.
  A test checks every character the plugin sends against `SMALL_CHAR_WIDTH = 4`.
- **`Palettes`**: Rainbow, Lava, Ocean, Party, Heat, Forest, Cloud. AWTRIX doesn't publish the hex values,
  but these are FastLED's built-in palettes (MIT), so their 16-entry tables are used.
- **`Effects`**: Plasma, a classic sine plasma sampled through the palette. Any other name falls back to the background colour.
- **`GifIcons`**: decodes a base64 GIF, with or without a `data:image/gif;base64,` prefix, into
  frames plus per-frame delays using `ImageIO` and the GIF metadata tree. Cached by the string
  (the pulse code already makes about 64 variants). JPEG icons go through the same path.

### 2.4 Sound: `Rtttl` + `ChipPlayer`

- Parse RTTTL (`name:d=,o=,b=:notes`) into a list of (frequency, ms).
- Synthesise a square wave with a short attack and release to 16-bit PCM in memory (44.1 kHz).
  Play it through `javax.sound.sampled.SourceDataLine` on one daemon executor thread, so a
  blocking `write` is fine. There is no file IO.
- Read `soundRtttl` (what the plugin sends) **and** `sound` / `sound.rtttl` (what the NG docs name it).
- One tune at a time: a new alert cuts off the old one. Volume and mute come from config.
- `shutDown` stops and closes the line.

## 3. The window: `VirtualClockWindow`

- An owner-less undecorated `JFrame` (`Type.UTILITY`, so no taskbar button on Windows) that holds an `LedPanel`.
- **Move:** drag anywhere. **Resize:** a bottom-right grip that keeps the 4:1 shape (bezel included),
  snapping to whole-pixel LED pitches so the dots stay crisp.
- **Right-click menu:** Always on top · Lock position · Size (1×, 2×, 4×, 8×) · Opacity ·
  LED style · Mute · Hide. Each one is mirrored in config.
- **Saved bounds:** x, y, w, h in hidden config keys. On start, if the saved spot is off every
  screen (a monitor was unplugged), move it onto the primary screen.
- **Show only when RuneLite isn't focused** (optional): listen for RuneLite's `FocusChanged` event and hide or show.
- **Always on top** (optional): `setAlwaysOnTop`. Never touches the client's own frame.
- Opacity uses `setOpacity`. When per-pixel translucency isn't supported it is skipped quietly.

`LedPanel` paints the 32×8 buffer:
- **Classic** (round dots, dark gaps), **Square** (flat pixels), and **Glow** (classic plus a soft halo drawn under each lit dot).
- Unlit LEDs show as a faint grey so the grid reads as hardware. Optional black bezel.
- Brightness from the emulator scales the colours, with the same shape as the clock (×(b+1)/256), but with a floor so it never goes fully black.
- A `BufferedImage` is reused, so nothing is allocated per frame.

## 4. Config: new "Virtual clock" section

| Key | Type | Default |
|---|---|---|
| `clockOutput` | Real clock / Virtual clock / Both | Real clock |
| `virtualAlwaysOnTop` | bool | off |
| `virtualOnlyWhenUnfocused` | bool | off |
| `virtualStyle` | Classic / Square / Glow | Classic |
| `virtualOpacity` | 20–100 % | 100 |
| `virtualSound` | bool | on |
| `virtualVolume` | 0–100 | 60 |
| `virtualLocked` | bool | off |
| `virtualBounds` | hidden string | unset |

Existing users see no change: the default output is the real clock.

## 5. Plugin wiring

- `startUp`: build the sink from config. If it's virtual, create the window on the EDT.
- `shutDown`: run `clearAll()` as today, which the virtual sink treats as clear-and-blank, then dispose the window, stop audio and stop the timer.
- `ConfigChanged` for the virtual keys: switch sinks live, open or close the window, and apply the window options.
- `FocusChanged`: drives "only when unfocused".
- **Preview** already works logged out, so it doubles as the demo for virtual users.

## 6. Tests

- `PageRendererTest`: golden frames. Render the compact, focus, big-stat, skill-progress and alert
  pages and compare them with expected pixel strings checked into `src/test/resources`.
  This replaces the in-bounds-only check in `CompactRenderTest` with real pixels.
- `VirtualClockTest`: queue rules (stack, hold, replace by name, DELETE, duration expiry) driven by a fake clock.
- `RtttlTest`: parses all six of the plugin's tunes. Durations add up to the expected length.
- `GifIconsTest`: round-trips `SkillActivityIcons.gif` and `XpDropGif.encode` (frame count and delays).
- `PixelFontTest`: widths match `SMALL_CHAR_WIDTH` and what `CompactLayout` assumes.
- A smoke test: start the emulator headless (`GraphicsEnvironment.isHeadless()` skips the window).

## 7. Plugin Hub checklist

- No new dependencies. Swing, `javax.sound.sampled` and `ImageIO` are all in the JDK.
- No reflection, JNI, `Runtime`, `Robot`, `Thread.sleep` or file IO. Resources come from `getResourceAsStream`.
- Never touches the client frame. The window is our own, and it is disposed in `shutDown`.
- No network traffic in virtual mode.
- Bundle the OFL licence text with the font resource.
- Update `runelite-plugin.properties`: change the description to say no hardware is needed, and add `virtual` and `led` tags.

## 8. Order of work

0. Gentler sound (see that section): the Soft tunes, Clock volume, and Play sounds on: Clock / Off. *Computer* follows in step 4.
1. `ClockSink` split. Behaviour is unchanged and the existing tests stay green.
2. `PixelFont`, `PageRenderer` for static pages, a bare `LedPanel` window, and *Virtual clock* output. Checkpoint: Preview shows the compact page.
3. Notification queue, blink, scroll, palettes, Plasma, GIF icons and XP drops.
4. RTTTL sound.
5. Window polish: on top, lock, sizes, opacity, styles, saved bounds, only-when-unfocused.
6. Tests, README "No clock? Use the virtual one" section and screenshots, properties.

## Gentler sound (step 0, also helps people who own the clock)

The clock's buzzer is too sharp and too loud (even the cats object). All six tunes sit at octaves 6–7
(about 1–2.6 kHz). That is about where small piezo buzzers are loudest and most piercing, and it is
high-pitched enough to bother pets. So:

- **Sound style:** *Soft* (new default) moves every tune down to octaves 4–5 and trims each to two or
  three notes. *Classic* keeps today's tunes for anyone who likes them.
- **Clock volume** (0–100): AWTRIX NG has `alertVolume` (0–100, a share of the master `volume`)
  in `PATCH /api/v1/settings`. It applies to every notification sound, and there is no per-sound volume.
  Snapshot it and put it back on shutdown, the same as brightness and the rotation. The docs imply the
  buzzer honours it but don't say so outright, so check this on the clock.
- **Play sounds on:** *Clock* / *Computer* / *Both* / *Off*. *Computer* plays the tune through
  `ChipPlayer` (section 2.4) even when you have the real clock, so the clock can stay silent.
- **Per-alert toggles stay** (`afkSound`, `forestryBeep`, `levelUpSound`, the beep options on the alerts).

`ChipPlayer` voice: a **soft** tone by default (sine with a slow attack and release, which has nothing in
common with a piezo), plus *Chime* (sine with a decaying bell partial) and *Retro* (softened square,
for the authentic look). Default volume 40.

## Things to check on the real clock (found while reading the NG docs)

The current AWTRIX NG docs name some fields differently from what the plugin sends. The
firmware may still accept the old names, but it's worth a quick check on your clock:

- Sound: the docs use `sound: {"rtttl": "…"}`. The plugin sends `soundRtttl`, which you've confirmed plays.
- Centring: the docs use `textAlign: "center"` (already the default). The plugin sends `textCenter`.
- Icons: the docs require a `data:image/gif;base64,` prefix for inline icons. The plugin sends plain base64, which evidently works, since the gallery shows icons.

The emulator accepts both forms either way.
