# 4irmann's FaderPort Driver for Bitwig Studio

A controller extension for the **PreSonus FaderPort (classic, single fader)** in Bitwig Studio.
It is a port of the Renoise tool *4irmann's FaderPort Driver* (v1.1), and has a sibling driver for REAPER
([reaper-faderport-driver](https://github.com/4irmann/reaper-faderport-driver)).

Bitwig ships drivers for the FaderPort 8 and 16, but not for the classic FaderPort. This extension
fills that gap.

- Motor fader with touch automation (Bitwig `touch()` gestures), 10-bit resolution
- Pan encoder with speed-sensitive ballistics, anti-suck protection and snap to center; shift+pan for fine trim
- DSP mode: fader and pan control the **remote controls** of the selected device, two at a time
- Sticky mode: **Bitwig's pinning** of the cursor track and the cursor device
- Automation: Read / Touch / Latch / Write map to Bitwig's automation write mode
- Transport, loop, punch, cue markers, metronome, undo/redo, mixer/arrange layouts
- One `.bwextension` file for Windows, macOS and Linux

Requires **Bitwig Studio 5.0 or later** (controller API 18). Developed with Bitwig Studio 6.0.

## Installation

### Windows installer

1. Download `bitwig-faderport-driver-<version>-setup.exe` from the
   [releases](https://github.com/4irmann/bitwig-faderport-driver/releases).
2. Close Bitwig Studio and run the installer. No administrator rights are needed.
3. Check the Bitwig user folder. The installer suggests `Documents\Bitwig Studio`; if you moved it in Bitwig
   (Settings → Locations), select that folder. The extension goes to its `Extensions` subfolder.
4. Continue with step 2 below (connect the FaderPort, add the controller in Bitwig).

To uninstall, use Windows Settings → Apps → "4irmann's FaderPort Driver for Bitwig Studio".

### Manual installation (all platforms)

1. Copy `FaderPort4irmann.bwextension` to the Bitwig extensions folder:
   - Windows: `%USERPROFILE%\Documents\Bitwig Studio\Extensions\`
   - macOS: `~/Documents/Bitwig Studio/Extensions/`
   - Linux: `~/Bitwig Studio/Extensions/`
2. Connect the FaderPort. The additional power supply is strongly recommended: without it the motor
   and touch detection don't work.
3. In Bitwig: Settings → Controllers. The FaderPort is usually detected automatically. Otherwise click
   *Add controller* → **PreSonus** → **FaderPort Classic (4irmann)**, and select the FaderPort as
   MIDI input and output.
   Don't use Bitwig's "FaderPort 8" / "FaderPort 16" entries; those are for the newer models.

## Button mapping

| Button | Bitwig | Shift + button |
|---|---|---|
| Fader | volume of the selected track (DSP mode: remote control 1, 3, 5, 7 of the pair) | – |
| Pan | pan of the selected track (DSP mode: remote control 2, 4, 6, 8) | fine trim of the fader parameter |
| Mute / Solo / Rec | mute, solo, arm of the selected track | – |
| ◀ / ▶ | previous/next track (DSP mode: previous/next remote control pair, continuing on the next page) | DSP mode: previous/next device |
| Bank | DSP mode on/off | – |
| Output | master track on/off (fader/pan control master volume/pan) | – |
| Read | stop writing automation, give overridden parameters back to their automation | – |
| Touch | automation write mode "touch" on/off | – |
| Write | automation write mode "latch" on/off | automation write mode "write" on/off |
| Off | light is on while pressed; the FaderPort sends no fader values meanwhile | – |
| Mix | toggle mix/arrange layout | show/hide the mixer panel |
| Proj | arrange layout | show/hide the device panel |
| Trns | sticky mode: pin the track (DSP mode: pin the device) | metronome on/off |
| Undo | undo | redo |
| Punch | punch in/out on/off | move the loop region left by its length |
| User | swap fader and pan | move the loop region right by its length |
| Loop | arranger loop on/off | show the current binding |
| Rewind / Fast Fwd | rewind / fast forward | previous/next cue marker |
| Rewind + Fast Fwd | reset the fader parameter to its default value | – |
| Stop | stop (pressed again: Bitwig returns to the play start position) | – |
| Play / footswitch | play/stop | continue playback |
| Record | arranger record on/off | – |

The controls work on Bitwig's **selected track**. In DSP mode they work on its **selected device**,
using the device's remote control pages. Changes made with the FaderPort's buttons are shown in a popup
(for example `Bass / EQ+ / Page 1   ^Low Freq   >Low Gain`).

### Differences from the REAPER and Renoise drivers

- **Automation is global in Bitwig.** The mode buttons switch Bitwig's arranger automation write
  (all tracks), not a per-track mode. Bitwig always reads automation, so the Read light is on whenever
  nothing is being written.
- **DSP bindings are Bitwig's remote control pages.** Bitwig devices already provide them, and for
  plugins you can define your own in Bitwig's device panel. A binding list is not needed.
- **Sticky mode is Bitwig's pinning.** The Trns light shows the pin state, and pinning in Bitwig's UI
  shows on the FaderPort too.
- **No emulator window.** Bitwig extensions can't open their own windows.

## Preferences

Settings → Controllers → FaderPort Classic (4irmann):

| Setting | Default | Description |
|---|---|---|
| Write button | Latch (Shift: Write) | which mode the Write button selects without Shift |
| Pan resolution (steps) | 128 | resolution of the virtual pan control; the encoder has no hardware limit |
| Pan anti-suck (clicks per step) | 3 | encoder clicks per step; lower values react faster, but let more bouncing values through |
| Footswitch pressed signal | 1 | 0 for footswitches with reversed wiring |
| Show value changes | off | popup for every value change, not only for binding changes |
| Dump MIDI to console | off | print FaderPort MIDI messages to the controller script console |

## Build

Requirements: JDK 17 or later and Maven 3.9.

```
mvn package
```

The build runs the unit tests and creates `target/FaderPort4irmann.bwextension`. Set the version with
`mvn package -Drevision=1.2.0`.

The Windows installer needs [Inno Setup 6](https://jrsoftware.org/isinfo.php):

```
iscc /DAppVersion=1.0.0 installer\windows\bitwig-faderport-driver.iss
```

It creates `dist/bitwig-faderport-driver-<version>-setup.exe`. CI (`.github/workflows/build.yml`) builds
and tests everything on every push. A tag `v<version>` creates a GitHub release with the installer and the
extension. The Bitwig API comes
from Bitwig's Maven repository (`com.bitwig:extension-api:25`). Compatibility with older Bitwig versions
comes from the required API version 18 declared in the extension.

## Structure

| File | Purpose |
|---|---|
| `FaderPortExtensionDefinition` | name, vendor, MIDI ports, auto detection, required API version |
| `FaderPortExtension` | wires MIDI ports, preferences, flush and the periodic tick to the driver |
| `BitwigDaw`, `BitwigParam` | adapter to the Bitwig API (cursor track/device, remote controls, transport, application) |
| `core/Driver` | driver logic: buttons, fader, pan/trim ballistics, lights (port of the REAPER driver) |
| `core/Daw`, `core/Param`, `core/Settings`, `core/Protocol` | interfaces, settings and FaderPort MIDI protocol |
| `src/test` | unit tests against a fake `Daw`; no Bitwig needed |

## Status

The extension builds, its unit tests pass, and it loads with Bitwig's own Java runtime. It has **not yet
been tested in Bitwig with the FaderPort hardware**. Feedback is welcome.

## License

Apache License 2.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE).
