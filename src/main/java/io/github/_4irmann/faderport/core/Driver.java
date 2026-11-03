/*
 * FaderPort driver main (port of the Renoise driver and the REAPER driver)
 *
 * Bitwig is event driven like Renoise: observers report changes, Bitwig then
 * calls flush(), and the driver sends only what changed (lights, motor fader).
 * tick() is called periodically for the release timeouts of fader and encoder.
 *
 * Differences to the Renoise driver:
 *   - devices: the 8 remote controls of the selected remote controls page,
 *     bound pairwise (1,2), (3,4), ... instead of a binding list
 *   - sticky mode: Bitwig's native pinning of the cursor track / cursor device
 *   - automation: Bitwig writes automation itself; the mode buttons switch the
 *     (global) automation write mode, gestures are reported via touch()
 *
 * Copyright 2010-2026 Matthias Ehrmann, Apache License 2.0
 */
package io.github._4irmann.faderport.core;

import static io.github._4irmann.faderport.core.Protocol.*;

import java.util.Arrays;
import java.util.function.Consumer;

public class Driver
{
   public static final String MODE_LATCH = "latch";
   public static final String MODE_TOUCH = "touch";
   public static final String MODE_WRITE = "write";

   public static final String LAYOUT_ARRANGE = "ARRANGE";
   public static final String LAYOUT_MIX = "MIX";

   private static final int PAIRS_PER_PAGE = Daw.REMOTE_CONTROLS / 2;

   private final Daw daw;
   private final Settings settings;
   private final MidiSender out;
   private final Consumer<String> log;

   // modes
   private boolean dspMode = false;
   private boolean masterMode = false;
   private boolean swapped = false;
   private int pairIndex = 0; // binding pair within the remote controls page

   // binding change handling
   private boolean bindingDirty = true;
   private boolean announceBinding = false;
   private Param touchedFaderParam = null; // param that received touch(true)
   private Param touchedPanParam = null;

   // fader
   private int lastMsb = 0;
   private int motorPos = -1; // last position sent to the motor fader, -1 = unknown
   private boolean faderTouched = false; // hardware touch state (needs power supply)
   private double lastFaderRx = -1;

   // pan
   private int panRes;
   private int virtualPanValue = 0;
   private int panLeftCount = 0, panRightCount = 0;
   private double lastPanTime = -1;
   private double lastPanRx = -1;

   // trim (shift + pan)
   private int trimLeftCount = 0, trimRightCount = 0;
   private double lastTrimTime = -1;
   private double lastTrimRx = -1;

   // buttons
   private boolean shiftPressed = false;
   private boolean rewindPressed = false;
   private boolean forwardPressed = false;
   private final boolean[] momentary = new boolean[LIGHT_COUNT];
   private final int[] lightCache = new int[LIGHT_COUNT]; // -1 unknown, 0 off, 1 on

   public Driver(Daw daw, Settings settings, MidiSender out, Consumer<String> log)
   {
      this.daw = daw;
      this.settings = settings;
      this.out = out;
      this.log = log;
      this.panRes = settings.virtualPanResolution;
      Arrays.fill(lightCache, -1);
   }

   // ------------------------------------------------------------ lifecycle

   public void start()
   {
      Protocol.sendNativeMode(this::send);
      invalidate();
   }

   public void shutdown()
   {
      endAllEdits();
      for (int nr = 0; nr <= 0x30; nr++)
         Protocol.sendLight(this::send, nr, false);
      Arrays.fill(lightCache, -1);
   }

   /** force resending lights and fader position */
   public void invalidate()
   {
      Arrays.fill(lightCache, -1);
      motorPos = -1;
      bindingDirty = true;
   }

   /** track, device or remote page changed (reported by Bitwig's observers) */
   public void bindingChanged()
   {
      bindingDirty = true;
   }

   // ------------------------------------------------------------- state access

   public boolean isDspMode()
   {
      return dspMode;
   }

   public boolean isMasterMode()
   {
      return masterMode;
   }

   public boolean isSwapped()
   {
      return swapped;
   }

   public int pairIndex()
   {
      return pairIndex;
   }

   // ------------------------------------------------------------------ MIDI out

   private void send(int status, int data1, int data2)
   {
      if (settings.dumpMidi)
         log.accept(String.format("send %02X %02X %02X", status, data1, data2));
      out.send(status, data1, data2);
   }

   // ----------------------------------------------------------------- binding

   private Param unswappedFaderParam()
   {
      if (dspMode)
         return daw.deviceExists() ? daw.remoteControl(pairIndex * 2) : null;
      return masterMode ? daw.masterVolume() : daw.trackVolume();
   }

   private Param unswappedPanParam()
   {
      if (dspMode)
         return daw.deviceExists() ? daw.remoteControl(pairIndex * 2 + 1) : null;
      return masterMode ? daw.masterPan() : daw.trackPan();
   }

   /** parameter bound to the fader, null if nothing is bound */
   public Param faderParam()
   {
      Param param = swapped ? unswappedPanParam() : unswappedFaderParam();
      return param != null && param.exists() ? param : null;
   }

   /** parameter bound to the pan control, null if nothing is bound */
   public Param panParam()
   {
      Param param = swapped ? unswappedFaderParam() : unswappedPanParam();
      return param != null && param.exists() ? param : null;
   }

   private void endAllEdits()
   {
      if (touchedFaderParam != null)
      {
         touchedFaderParam.touch(false);
         touchedFaderParam = null;
      }
      if (touchedPanParam != null)
      {
         touchedPanParam.touch(false);
         touchedPanParam = null;
      }
      lastTrimRx = -1;
      lastPanRx = -1;
   }

   private void handleBindingChange()
   {
      bindingDirty = false;
      endAllEdits();

      motorPos = -1; // force motor update
      lastTrimTime = -1;
      trimLeftCount = trimRightCount = 0;

      Param pan = panParam();
      panRes = resolution(pan, settings.virtualPanResolution);
      lastPanTime = -1;
      panLeftCount = panRightCount = 0;
      if (pan != null)
         virtualPanValue = (int) Math.floor(pan.get() * (panRes - 1) + 0.5);

      if (announceBinding)
      {
         announceBinding = false;
         showBinding();
      }
   }

   /** number of steps of a parameter, def for continuous parameters */
   private static int resolution(Param param, int def)
   {
      if (param == null)
         return def;
      int count = param.discreteValueCount();
      return count >= 2 && count < def ? count : def;
   }

   // ------------------------------------------------------------- status popup

   private String statusPrefix()
   {
      StringBuilder message = new StringBuilder(masterMode && !dspMode ? "Master" : daw.trackName());
      if (dspMode)
      {
         message.append(" / ").append(daw.deviceExists() ? daw.deviceName() : "(no device)");
         if (daw.deviceExists() && daw.remotePageCount() > 0)
            message.append(" / ").append(daw.remotePageName());
      }
      if (isSticky())
         message.append(" [sticky]");
      return message.toString();
   }

   public void showBinding()
   {
      StringBuilder message = new StringBuilder(statusPrefix());
      Param fader = faderParam();
      Param pan = panParam();
      if (fader != null)
         message.append("   ^").append(fader.name());
      if (pan != null)
         message.append("   >").append(pan.name());
      if (fader == null && pan == null)
         message.append("   nothing bound");
      daw.showNotification(message.toString());
   }

   private void showValue(Param param, String symbol)
   {
      if (settings.valuePopups && param != null)
         daw.showNotification(statusPrefix() + "   " + symbol + param.name() + " -> " + param.displayValue());
   }

   // ------------------------------------------------------------------- fader

   private void onReceiveFaderValue(int value, double time)
   {
      lastFaderRx = time;

      Param param = faderParam();
      if (param == null)
         return;

      double n = (double) value / FADER_MAX;

      // round to exact center value (snap to center)
      if (param.bipolar() && Math.abs(n - 0.5) < 1.0 / 1024)
         n = 0.5;

      // quantize discrete parameters
      int res = resolution(param, 1024);
      if (res < 1024)
         n = Math.floor(n * (res - 1) + 0.5) / (res - 1);

      beginFaderGesture(param);
      param.set(n);
      showValue(param, "^");
   }

   private void beginFaderGesture(Param param)
   {
      if (touchedFaderParam != param)
      {
         if (touchedFaderParam != null)
            touchedFaderParam.touch(false);
         param.touch(true);
         touchedFaderParam = param;
      }
   }

   private void endFaderGesture()
   {
      if (touchedFaderParam != null)
      {
         touchedFaderParam.touch(false);
         touchedFaderParam = null;
      }
   }

   private void onFaderTouch(boolean touched)
   {
      faderTouched = touched;
      if (touched)
      {
         Param param = faderParam();
         if (param != null)
            beginFaderGesture(param);
      }
      else
      {
         endFaderGesture();
         lastFaderRx = -1;
         motorPos = -1; // sync fader with Bitwig
      }
   }

   /** reset the parameter bound to the fader to its default value */
   private void resetFaderParamToDefault()
   {
      Param param = faderParam();
      if (param == null)
         return;
      param.reset();
      daw.showNotification(statusPrefix() + "   ^" + param.name() + " reset");
   }

   /** motor fader follows the bound parameter (incl. automation playback) */
   private void updateMotor(double now)
   {
      Param param = faderParam();
      if (param == null || faderTouched)
         return;
      if (lastFaderRx >= 0 && now - lastFaderRx < settings.faderReleaseTimeout)
         return;
      int pos = (int) Math.floor(param.get() * FADER_MAX + 0.5);
      if (motorPos < 0 || Math.abs(pos - motorPos) > 1)
      {
         motorPos = pos;
         Protocol.sendFader(this::send, pos);
      }
   }

   // ---------------------------------------------------------------- pan / trim

   /**
    * encoder speed based on the interval between two effective steps
    * (Renoise ballistics: speed = 1 + 0.2 s / interval, min 1, max 20)
    */
   private static int computeSpeed(double lastTime, double time)
   {
      if (lastTime < 0)
         return 1;
      double interval = Math.max(time - lastTime, 0.001);
      return Math.max(1, Math.min(20, 1 + (int) Math.floor(0.20 / interval + 0.5)));
   }

   /** encoder tick with anti suck handling, returns direction or 0 */
   private int encoderDirection(int value, int[] counts)
   {
      // counts[0] = left, counts[1] = right
      if (value < 63) // turn right
      {
         if (value != PAN_RIGHT || ++counts[1] < settings.antiSuckMinTurns)
            return 0;
         counts[0] = counts[1] = 0;
         return 1;
      }
      // turn left
      if (value != PAN_LEFT || ++counts[0] < settings.antiSuckMinTurns)
         return 0;
      counts[0] = counts[1] = 0;
      return -1;
   }

   private void onReceivePanValue(int value, double time)
   {
      Param param = panParam();
      if (param == null)
         return;
      lastPanRx = time;

      int[] counts = { panLeftCount, panRightCount };
      int direction = encoderDirection(value, counts);
      panLeftCount = counts[0];
      panRightCount = counts[1];
      if (direction == 0)
         return;

      // if resolution is low, don't use speed feature at all
      int speed = panRes > 20 ? computeSpeed(lastPanTime, time) : 1;
      lastPanTime = time;

      int panMaxValue = panRes - 1;
      virtualPanValue = Math.max(0, Math.min(panMaxValue, virtualPanValue + direction * speed));

      double n = (double) virtualPanValue / panMaxValue;

      // round to exact center value (snap to center)
      if (param.bipolar() && Math.abs(n - 0.5) < 1.0 / (panMaxValue + 1))
         n = 0.5;

      if (touchedPanParam != param)
      {
         param.touch(true);
         touchedPanParam = param;
      }
      param.set(n);
      showValue(param, ">");
   }

   /** shift + pan: fine adjustment of the parameter bound to the fader */
   private void onReceiveTrimValue(int value, double time)
   {
      Param param = faderParam();
      if (param == null)
         return;
      lastTrimRx = time;

      int[] counts = { trimLeftCount, trimRightCount };
      int direction = encoderDirection(value, counts);
      trimLeftCount = counts[0];
      trimRightCount = counts[1];
      if (direction == 0)
         return;

      int speed = computeSpeed(lastTrimTime, time);
      lastTrimTime = time;

      beginFaderGesture(param);
      param.set(Math.max(0.0, Math.min(1.0, param.get() + 0.0005 * direction * speed)));
      showValue(param, "^");
   }

   /** keep the virtual pan value in sync with Bitwig while the encoder isn't moved */
   private void syncVirtualPan(double now)
   {
      Param param = panParam();
      if (param == null || touchedPanParam != null)
         return;
      if (lastPanRx >= 0 && now - lastPanRx < settings.panReleaseTimeout)
         return;
      virtualPanValue = (int) Math.floor(param.get() * (panRes - 1) + 0.5);
   }

   // ------------------------------------------------------------- periodic tick

   /**
    * release detection: fader without touch messages (no power supply),
    * pan and trim (encoders never send touch messages)
    */
   public void tick(double now)
   {
      if (touchedFaderParam != null && !faderTouched)
      {
         double last = Math.max(lastFaderRx, lastTrimRx);
         if (last >= 0 && now - last > (lastTrimRx > lastFaderRx ? settings.panReleaseTimeout
                                                                 : settings.faderReleaseTimeout))
         {
            endFaderGesture();
            lastTrimRx = -1;
         }
      }
      if (touchedPanParam != null && lastPanRx >= 0 && now - lastPanRx > settings.panReleaseTimeout)
      {
         touchedPanParam.touch(false);
         touchedPanParam = null;
      }
   }

   // -------------------------------------------------------------------- flush

   /** send everything that changed to the FaderPort */
   public void flush(double now)
   {
      if (bindingDirty)
         handleBindingChange();
      syncVirtualPan(now);
      updateMotor(now);
      updateLights();
   }

   private boolean isSticky()
   {
      return dspMode ? daw.devicePinned() : daw.trackPinned();
   }

   private void updateLights()
   {
      boolean[] desired = new boolean[LIGHT_COUNT];

      if (masterMode && !dspMode)
      {
         desired[LIGHT_MUTE] = daw.masterMute();
      }
      else if (daw.trackExists())
      {
         desired[LIGHT_MUTE] = daw.trackMute();
         desired[LIGHT_SOLO] = daw.trackSolo();
         desired[LIGHT_REC] = daw.trackArm();
      }
      desired[LIGHT_OUTPUT] = masterMode;
      desired[LIGHT_BANK] = dspMode;
      desired[LIGHT_TRNS] = isSticky();
      desired[LIGHT_USER] = swapped;
      desired[LIGHT_SHIFT] = shiftPressed;

      boolean write = daw.isAutomationWriteEnabled();
      String mode = daw.automationWriteMode();
      desired[LIGHT_READ] = !write; // Bitwig always reads automation unless writing
      desired[LIGHT_TOUCH] = write && MODE_TOUCH.equals(mode);
      desired[LIGHT_WRITE] = write && (MODE_LATCH.equals(mode) || MODE_WRITE.equals(mode));

      String layout = daw.panelLayout();
      desired[LIGHT_MIX] = LAYOUT_MIX.equals(layout);
      desired[LIGHT_PROJ] = LAYOUT_ARRANGE.equals(layout);

      desired[LIGHT_PUNCH] = daw.isPunchEnabled();
      desired[LIGHT_LOOP] = daw.isLoopEnabled();
      desired[LIGHT_PLAY] = daw.isPlaying();
      desired[LIGHT_STOP] = !daw.isPlaying();
      desired[LIGHT_RECORD] = daw.isRecording();

      // HINT: the OFF light is a momentary light only. Whenever it is on,
      // the FaderPort doesn't send fader values (see handling of the off button)
      for (int nr = 0; nr < LIGHT_COUNT; nr++)
      {
         int on = desired[nr] || momentary[nr] ? 1 : 0;
         if (lightCache[nr] != on)
         {
            lightCache[nr] = on;
            Protocol.sendLight(this::send, nr, on != 0);
         }
      }
   }

   // ---------------------------------------------------------------- MIDI input

   /** MIDI message from the FaderPort, time in seconds (monotonic) */
   public void midiReceived(int status, int data1, int data2, double time)
   {
      if (settings.dumpMidi)
         log.accept(String.format("received %02X %02X %02X", status, data1, data2));

      if (status == 0xb0) // fader value
      {
         if (data1 == 0x00)
            lastMsb = data2; // store msb, wait for lsb
         else if (data1 == 0x20)
            onReceiveFaderValue(Protocol.decodeFader(lastMsb, data2), time);
      }
      else if (status == 0xe0 && data1 == 0x00) // pan and trim value
      {
         if (shiftPressed)
            onReceiveTrimValue(data2, time); // trim parameter bound to fader
         else
            onReceivePanValue(data2, time);
      }
      else if (status == 0xa0) // buttons
      {
         onButton(data1, data2);
      }
   }

   private void changeBinding()
   {
      bindingDirty = true;
      announceBinding = true;
   }

   private void toggleAutomationMode(String mode)
   {
      boolean write = daw.isAutomationWriteEnabled();
      String current = daw.automationWriteMode();
      boolean bothWrite = isWriteMode(current) && isWriteMode(mode);

      // if the mode is already active, switch writing off (read)
      if (write && (mode.equals(current) || bothWrite))
      {
         daw.setAutomationWriteEnabled(false);
      }
      else
      {
         daw.setAutomationWriteMode(mode);
         daw.setAutomationWriteEnabled(true);
      }
   }

   private static boolean isWriteMode(String mode)
   {
      return MODE_LATCH.equals(mode) || MODE_WRITE.equals(mode);
   }

   private void selectPair(int delta)
   {
      int pages = daw.remotePageCount();
      if (!daw.deviceExists() || pages <= 0)
         return;
      int index = pairIndex + delta;
      int page = daw.remotePageIndex();
      if (index >= PAIRS_PER_PAGE)
      {
         index = 0;
         page = (page + 1) % pages;
      }
      else if (index < 0)
      {
         index = PAIRS_PER_PAGE - 1;
         page = (page - 1 + pages) % pages;
      }
      pairIndex = index;
      if (page != daw.remotePageIndex())
         daw.selectRemotePage(page);
      changeBinding();
   }

   private void toggleSticky()
   {
      if (dspMode)
         daw.setDevicePinned(!daw.devicePinned());
      else
         daw.setTrackPinned(!daw.trackPinned());
      announceBinding = true;
      bindingDirty = true;
   }

   private void onButton(int button, int value)
   {
      boolean pressed = value == 1;

      switch (button)
      {
         case BTN_FADER_TOUCH: // fader touch state
            onFaderTouch(pressed);
            break;

         case BTN_SHIFT: // transport shift
            shiftPressed = pressed;
            break;

         case BTN_PLAY: // transport play
            if (pressed)
            {
               if (shiftPressed)
                  daw.continuePlayback();
               else if (daw.isPlaying())
                  daw.stop();
               else
                  daw.play();
            }
            break;

         case BTN_FOOTSWITCH:
            if (value == settings.footswitchPressedSignal)
            {
               if (daw.isPlaying())
                  daw.stop();
               else
                  daw.play();
            }
            break;

         case BTN_STOP: // transport stop (pressed again: Bitwig returns to the play start position)
            if (pressed)
               daw.stop();
            break;

         case BTN_RECORD: // transport record
            if (pressed)
               daw.toggleRecord();
            break;

         case BTN_LOOP: // transport loop
            if (pressed && shiftPressed)
               showBinding();
            else if (pressed)
               daw.toggleLoop();
            momentary[LIGHT_LOOP] = pressed && shiftPressed;
            break;

         case BTN_PUNCH: // transport punch
            if (pressed)
            {
               if (shiftPressed)
                  daw.moveLoop(-1);
               else
                  daw.togglePunch();
            }
            break;

         case BTN_USER: // transport user: swap fader and pan
            if (pressed && shiftPressed)
               daw.moveLoop(1);
            else if (pressed)
            {
               swapped = !swapped;
               changeBinding();
            }
            momentary[LIGHT_USER] = pressed && shiftPressed;
            break;

         case BTN_REWIND: // transport rewind
            momentary[LIGHT_REWIND] = pressed;
            if (pressed)
            {
               rewindPressed = true;
               if (forwardPressed)
                  resetFaderParamToDefault();
            }
            else if (!forwardPressed)
            {
               if (shiftPressed)
                  daw.previousCueMarker();
               else
                  daw.rewind();
               rewindPressed = false;
            }
            else
            {
               forwardPressed = false;
            }
            break;

         case BTN_FORWARD: // transport fast forward
            momentary[LIGHT_FORWARD] = pressed;
            if (pressed)
            {
               forwardPressed = true;
               if (rewindPressed)
                  resetFaderParamToDefault();
            }
            else if (!rewindPressed)
            {
               if (shiftPressed)
                  daw.nextCueMarker();
               else
                  daw.fastForward();
               forwardPressed = false;
            }
            else
            {
               rewindPressed = false;
            }
            break;

         case BTN_MUTE: // channel mute
            if (pressed)
            {
               if (masterMode && !dspMode)
                  daw.toggleMasterMute();
               else if (daw.trackExists())
                  daw.toggleTrackMute();
            }
            break;

         case BTN_SOLO: // channel solo (not for the master track)
            if (pressed && !(masterMode && !dspMode) && daw.trackExists())
               daw.toggleTrackSolo();
            break;

         case BTN_REC: // channel rec arm (not for the master track)
            if (pressed && !(masterMode && !dspMode) && daw.trackExists())
               daw.toggleTrackArm();
            break;

         case BTN_READ: // fader mode read: stop writing, follow automation again
            if (pressed)
            {
               daw.setAutomationWriteEnabled(false);
               daw.resetAutomationOverrides();
            }
            break;

         case BTN_TOUCH: // fader mode touch
            if (pressed)
               toggleAutomationMode(MODE_TOUCH);
            break;

         case BTN_WRITE: // fader mode write / latch
            if (pressed)
            {
               boolean latch = shiftPressed;
               if (settings.latchIsDefaultWriteMode)
                  latch = !latch;
               toggleAutomationMode(latch ? MODE_LATCH : MODE_WRITE);
            }
            break;

         // fader mode: fader off
         // HINT: as long as the button is pressed the light is switched on.
         // Whenever the light is switched on, no fader values are sent
         // from FaderPort while fader is being moved !
         // This may be helpful for doing fader readjustments in write modes
         // without actually writing values.
         case BTN_OFF:
            momentary[LIGHT_OFF] = pressed;
            break;

         case BTN_LEFT: // channel select left / right
         case BTN_RIGHT:
         {
            int delta = button == BTN_LEFT ? -1 : 1;
            momentary[button == BTN_LEFT ? LIGHT_LEFT : LIGHT_RIGHT] = pressed;
            if (pressed)
            {
               if (!dspMode)
               {
                  masterMode = false;
                  if (delta < 0)
                     daw.selectPreviousTrack();
                  else
                     daw.selectNextTrack();
                  changeBinding();
               }
               else if (shiftPressed)
               {
                  pairIndex = 0;
                  if (delta < 0)
                     daw.selectPreviousDevice();
                  else
                     daw.selectNextDevice();
                  changeBinding();
               }
               else
               {
                  selectPair(delta);
               }
            }
            break;
         }

         case BTN_BANK: // channel select bank: toggle dsp mode
            if (pressed)
            {
               dspMode = !dspMode;
               changeBinding();
            }
            break;

         case BTN_OUTPUT: // channel select output: toggle master track
            if (pressed)
            {
               masterMode = !masterMode;
               if (masterMode)
                  dspMode = false;
               changeBinding();
            }
            break;

         case BTN_MIX: // window view mix (shift: mixer panel)
            if (pressed)
            {
               if (shiftPressed)
                  daw.toggleMixerPanel();
               else
                  daw.setPanelLayout(LAYOUT_MIX.equals(daw.panelLayout()) ? LAYOUT_ARRANGE : LAYOUT_MIX);
            }
            break;

         case BTN_PROJ: // window view proj: arrange layout (shift: device panel)
            if (pressed)
            {
               if (shiftPressed)
                  daw.toggleDevicePanel();
               else
                  daw.setPanelLayout(LAYOUT_ARRANGE);
            }
            break;

         case BTN_TRNS: // window view trns: sticky mode (shift: metronome)
            if (pressed)
            {
               if (shiftPressed)
                  daw.toggleMetronome();
               else
                  toggleSticky();
            }
            break;

         case BTN_UNDO: // window view undo / redo
            momentary[LIGHT_UNDO] = pressed;
            if (pressed)
            {
               if (shiftPressed)
                  daw.redo();
               else
                  daw.undo();
            }
            break;

         default:
            break;
      }
   }
}
