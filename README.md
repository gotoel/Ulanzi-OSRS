# Ulanzi OSRS Clock

**No hardware needed.** Turn the plugin on and a small LED overlay appears on your desktop with your hitpoints, prayer, run energy, special attack, and AFK status. Own an [Ulanzi TC001](https://github.com/rroels/ulanzi_tc001_hardware) pixel clock running [AWTRIX NG](https://blueforcer.github.io/awtrix-ng/)? The plugin can drive that instead, with the same pages.

<img width="644" alt="" src="docs/gallery/divider.png">

## Gallery

Click a picture marked ▶ to watch it move.

### Real clock (Ulanzi TC001)

<table>
<tr>
<td><a href="docs/gallery/skill.gif"><img width="320" alt="Skill page on a real clock" src="docs/gallery/skill-still.png"></a><br><sub>▶ Skill page</sub></td>
<td width="14"></td>
<td><a href="docs/gallery/skill-bar.gif"><img width="320" alt="Bar toward the next level on a real clock" src="docs/gallery/skill-bar-still.png"></a><br><sub>▶ Bar toward the next level</sub></td>
</tr>
</table>

<br>

### Virtual overlay

<table>
<tr>
<td><img width="212" alt="The virtual overlay showing live stats" src="docs/gallery/overlay-stats.png"><br><sub>Live stats</sub></td>
<td width="14"></td>
<td><a href="docs/gallery/xp-pulse.gif"><img width="212" alt="The Firemaking page pulsing as XP lands" src="docs/gallery/xp-pulse-still.png"></a><br><sub>▶ XP drop as a pulse, the default</sub></td>
<td width="14"></td>
<td><a href="docs/gallery/xp-drops.gif"><img width="212" alt="An XP drop rising over the skill page" src="docs/gallery/xp-drops-still.png"></a><br><sub>▶ XP drop rising</sub></td>
</tr>
<tr><td height="14"></td></tr>
<tr>
<td><a href="docs/gallery/xp-alternate.gif"><img width="212" alt="The Magic page alternating between level and XP per hour" src="docs/gallery/xp-alternate-still.png"></a><br><sub>▶ Level and XP per hour taking turns</sub></td>
<td width="14"></td>
<td><a href="docs/gallery/forestry.gif"><img width="212" alt="Forestry event callouts" src="docs/gallery/forestry-still.png"></a><br><sub>▶ Forestry events</sub></td>
<td width="14"></td>
<td><img width="212" alt="The skill page" src="docs/gallery/overlay-skill.png"><br><sub>Skill page</sub></td>
</tr>
<tr><td height="14"></td></tr>
<tr>
<td><img width="212" alt="The low hitpoints alert" src="docs/gallery/overlay-low-hp.png"><br><sub>Low hitpoints alert</sub></td>
<td width="14"></td>
<td><img width="212" alt="The overlay when you go AFK" src="docs/gallery/overlay-afk.png"><br><sub>AFK</sub></td>
<td width="14"></td>
<td><img width="212" alt="The overlay&#x27;s hover controls" src="docs/gallery/overlay-controls.png"><br><sub>Hover controls</sub></td>
</tr>
</table>

<img width="644" alt="" src="docs/gallery/divider.png">

## Features

- Hitpoints, prayer, run energy, and special attack stay on the panel, in **Compact**, **Big**, or **Focus**
- The skill page shows your level, XP per hour, and a bar toward the next level
- Low hitpoints, low prayer, and a ready special attack take over the panel
- **Combat**, **Skilling**, and **Hybrid** set the panel up for that kind of play
- **Preview** puts a sample page up without logging in
- AFK takes the panel when you go idle
- A forestry event can call out while you chop
- A level-up shows the skill and your new level
- Tones can be **Soft** or **Classic**, and play on the clock, on your computer, or not at all

<img width="644" alt="" src="docs/gallery/divider.png">

## Connect a real Ulanzi pixel clock

Hover over the virtual overlay, open its options, and choose **Connect a real Ulanzi pixel clock**, or fill in **Clock address** under **Real clock (Ulanzi TC001)** in the settings. As soon as the clock answers, the overlay steps aside. To keep both, set **Quick setup > Show pages on** to **Both**.

Stock firmware cannot show a custom page. Flash [AWTRIX NG](https://blueforcer.github.io/awtrix-ng/getting-started/flashing/) once: in Chrome or Edge, choose **Fresh install** and pick the clock's port. The clock then starts its own Wi-Fi. Join it, read the IP off the panel, and put that in **Clock address**. This plugin does not flash the device.
