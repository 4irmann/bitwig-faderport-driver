package io.github._4irmann.faderport.core;

import static io.github._4irmann.faderport.core.Protocol.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverTest
{
   private FakeDaw daw;
   private Settings settings;
   private Driver driver;
   private final List<int[]> sent = new ArrayList<>();
   private double now = 100.0;

   @BeforeEach
   void setUp()
   {
      daw = new FakeDaw();
      settings = new Settings();
      driver = new Driver(daw, settings, (s, d1, d2) -> sent.add(new int[] { s, d1, d2 }), msg -> {});
      driver.start();
      flush(0);
      sent.clear();
   }

   // --------------------------------------------------------------- helpers

   private void flush(double dt)
   {
      now += dt;
      driver.tick(now);
      driver.flush(now);
   }

   private void midi(int s, int d1, int d2)
   {
      driver.midiReceived(s, d1, d2, now);
   }

   private void press(int button)
   {
      midi(0xa0, button, 1);
      midi(0xa0, button, 0);
      flush(0.01);
   }

   private void withShift(Runnable action)
   {
      midi(0xa0, BTN_SHIFT, 1);
      action.run();
      midi(0xa0, BTN_SHIFT, 0);
      flush(0.01);
   }

   private void fader(int value)
   {
      int[] bytes = Protocol.encodeFader(value);
      midi(0xb0, 0x00, bytes[0]);
      midi(0xb0, 0x20, bytes[1]);
   }

   private void panTicks(int value, int count)
   {
      for (int i = 0; i < count; i++)
         midi(0xe0, 0x00, value);
   }

   /** last state sent for a light, -1 if never sent */
   private int light(int nr)
   {
      int on = -1;
      for (int[] m : sent)
         if (m[0] == 0xa0 && m[1] == nr)
            on = m[2];
      return on;
   }

   /** last motor position sent, -1 if none */
   private int motor()
   {
      int msb = 0, value = -1;
      for (int[] m : sent)
      {
         if (m[0] == 0xb0 && m[1] == 0x00)
            msb = m[2];
         else if (m[0] == 0xb0 && m[1] == 0x20)
            value = (msb << 7) | m[2];
      }
      return value;
   }

   // ----------------------------------------------------------------- tests

   @Test
   void protocolFaderRoundtrip()
   {
      for (int v = 0; v <= FADER_MAX; v++)
      {
         int[] hw = Protocol.encodeFader(v);
         assertEquals(v, Protocol.decodeFader(hw[0], hw[1]));
         List<int[]> out = new ArrayList<>();
         Protocol.sendFader((s, d1, d2) -> out.add(new int[] { d2 }), v);
         assertEquals(v, (out.get(0)[0] << 7) | out.get(1)[0]);
      }
   }

   @Test
   void startSendsNativeMode()
   {
      List<int[]> out = new ArrayList<>();
      new Driver(daw, settings, (s, d1, d2) -> out.add(new int[] { s, d1, d2 }), m -> {}).start();
      assertArrayEquals(new int[] { 0x91, 0x00, 0x64 }, out.get(0));
   }

   @Test
   void faderSetsVolumeWithTouchGesture()
   {
      midi(0xa0, BTN_FADER_TOUCH, 1);
      assertTrue(daw.trackVolume.touched, "touch on hardware touch");
      fader(1023);
      assertEquals(1.0, daw.trackVolume.value, 1e-9);
      midi(0xa0, BTN_FADER_TOUCH, 0);
      assertFalse(daw.trackVolume.touched, "release ends gesture");
      fader(0);
      assertEquals(0.0, daw.trackVolume.value, 1e-9);
   }

   @Test
   void faderReleaseTimeoutWithoutPowerSupply()
   {
      fader(500);
      assertTrue(daw.trackVolume.touched, "first value starts gesture");
      flush(0.1);
      assertTrue(daw.trackVolume.touched);
      flush(0.5);
      assertFalse(daw.trackVolume.touched, "fake release after timeout");
   }

   @Test
   void motorFollowsVolume()
   {
      driver.invalidate();
      flush(0.01);
      assertEquals((int) Math.floor(0.7 * 1023 + 0.5), motor());
      sent.clear();
      flush(0.01);
      assertEquals(-1, motor(), "no resend without change");
      daw.trackVolume.value = 0.2;
      flush(0.01);
      assertEquals((int) Math.floor(0.2 * 1023 + 0.5), motor());

      sent.clear();
      midi(0xa0, BTN_FADER_TOUCH, 1);
      daw.trackVolume.value = 0.9;
      flush(0.01);
      assertEquals(-1, motor(), "no motor while touched");
   }

   @Test
   void buttonsAndLights()
   {
      press(BTN_MUTE);
      assertTrue(daw.mute);
      assertEquals(1, light(LIGHT_MUTE));
      press(BTN_SOLO);
      press(BTN_REC);
      assertTrue(daw.solo && daw.arm);
      assertEquals(1, light(LIGHT_SOLO));
      assertEquals(1, light(LIGHT_REC));
      press(BTN_MUTE);
      assertEquals(0, light(LIGHT_MUTE));
   }

   @Test
   void lightsOnlySentOnChange()
   {
      flush(0.01);
      sent.clear();
      flush(0.01);
      assertTrue(sent.isEmpty());
   }

   @Test
   void trackNavigationLeavesMasterMode()
   {
      press(BTN_OUTPUT);
      assertTrue(driver.isMasterMode());
      assertEquals(1, light(LIGHT_OUTPUT));
      assertSame(daw.masterVolume, driver.faderParam());
      press(BTN_MUTE);
      assertTrue(daw.masterMute, "mute acts on master");
      press(BTN_RIGHT);
      assertFalse(driver.isMasterMode());
      assertEquals(1, daw.trackIndex);
      press(BTN_LEFT);
      assertEquals(0, daw.trackIndex);
      assertSame(daw.trackVolume, driver.faderParam());
   }

   @Test
   void automationModes()
   {
      driver.invalidate(); // lights of the initial flush were cleared in setUp
      flush(0.01);
      assertEquals(1, light(LIGHT_READ), "read light while not writing");
      press(BTN_WRITE); // latch by default
      assertTrue(daw.automationWrite);
      assertEquals("latch", daw.automationMode);
      assertEquals(1, light(LIGHT_WRITE));
      assertEquals(0, light(LIGHT_READ));
      press(BTN_WRITE);
      assertFalse(daw.automationWrite, "toggle off");

      withShift(() -> press(BTN_WRITE));
      assertEquals("write", daw.automationMode);
      assertTrue(daw.automationWrite);

      press(BTN_TOUCH);
      assertEquals("touch", daw.automationMode);
      assertEquals(1, light(LIGHT_TOUCH));
      press(BTN_TOUCH);
      assertFalse(daw.automationWrite);

      press(BTN_WRITE);
      press(BTN_READ);
      assertFalse(daw.automationWrite);
      assertEquals(1, daw.overrideResets, "read restores automation");
   }

   @Test
   void panAntiSuckBallisticsAndCenterSnap()
   {
      panTicks(PAN_RIGHT, 2);
      flush(0.01);
      assertEquals(0.5, daw.trackPan.value, 1e-9, "two ticks are not enough (anti suck)");

      panTicks(PAN_RIGHT, 1); // third tick: one step
      assertEquals(65.0 / 127, daw.trackPan.value, 1e-9);
      assertTrue(daw.trackPan.touched, "pan gesture");

      flush(1.0);
      assertFalse(daw.trackPan.touched, "pan released after timeout");

      // slow: one step back to center snaps exactly
      now += 1.0;
      panTicks(PAN_LEFT, 3);
      assertEquals(0.5, daw.trackPan.value, 1e-9);

      // fast: steps 10 ms apart accelerate
      double before = daw.trackPan.value;
      for (int step = 0; step < 4; step++)
      {
         now += 0.01;
         panTicks(PAN_RIGHT, 3);
      }
      assertTrue(daw.trackPan.value - before > 0.3, "accelerated " + (daw.trackPan.value - before));
   }

   @Test
   void trimWithShift()
   {
      midi(0xa0, BTN_SHIFT, 1);
      panTicks(PAN_RIGHT, 3);
      midi(0xa0, BTN_SHIFT, 0);
      assertEquals(0.7005, daw.trackVolume.value, 1e-9);
      assertEquals(0.5, daw.trackPan.value, 1e-9, "pan unchanged");
      assertTrue(daw.trackVolume.touched);
      flush(1.0);
      assertFalse(daw.trackVolume.touched, "trim gesture released");
   }

   @Test
   void swapFaderAndPan()
   {
      press(BTN_USER);
      assertTrue(driver.isSwapped());
      assertSame(daw.trackPan, driver.faderParam());
      assertSame(daw.trackVolume, driver.panParam());
      assertEquals(1, light(LIGHT_USER));
      fader(1023);
      assertEquals(1.0, daw.trackPan.value, 1e-9);
   }

   @Test
   void rewindForwardResetsDefault()
   {
      daw.trackVolume.value = 0.1;
      midi(0xa0, BTN_REWIND, 1);
      midi(0xa0, BTN_FORWARD, 1);
      midi(0xa0, BTN_REWIND, 0);
      midi(0xa0, BTN_FORWARD, 0);
      assertEquals(0.7, daw.trackVolume.value, 1e-9);
      assertEquals("", daw.lastTransport, "no navigation when both pressed");
      press(BTN_REWIND);
      assertEquals("rewind", daw.lastTransport);
      withShift(() -> press(BTN_FORWARD));
      assertEquals("nextCue", daw.lastTransport);
   }

   @Test
   void transport()
   {
      press(BTN_PLAY);
      assertTrue(daw.playing);
      assertEquals(1, light(LIGHT_PLAY));
      assertEquals(0, light(LIGHT_STOP));
      press(BTN_PLAY);
      assertFalse(daw.playing, "play again stops");
      withShift(() -> press(BTN_PLAY));
      assertEquals("continue", daw.lastTransport);
      press(BTN_STOP);
      assertFalse(daw.playing);
      press(BTN_RECORD);
      assertTrue(daw.recording);
      press(BTN_LOOP);
      assertTrue(daw.loop);
      press(BTN_PUNCH);
      assertTrue(daw.punch);
      withShift(() -> press(BTN_PUNCH));
      withShift(() -> press(BTN_USER));
      withShift(() -> press(BTN_USER));
      assertEquals(1, daw.loopMoves);
      midi(0xa0, BTN_FOOTSWITCH, 1);
      assertTrue(daw.playing, "footswitch");
   }

   @Test
   void dspModeRemotePairsAcrossPages()
   {
      press(BTN_BANK);
      assertTrue(driver.isDspMode());
      assertEquals(1, light(LIGHT_BANK));
      assertSame(daw.remotes[0], driver.faderParam());
      assertSame(daw.remotes[1], driver.panParam());

      fader(1023);
      assertEquals(1.0, daw.remotes[0].value, 1e-9);

      for (int i = 0; i < 3; i++)
         press(BTN_RIGHT);
      assertSame(daw.remotes[6], driver.faderParam());
      assertEquals(0, daw.pageIndex);
      press(BTN_RIGHT); // beyond the page: next page, first pair
      assertEquals(1, daw.pageIndex);
      assertEquals(0, driver.pairIndex());
      press(BTN_LEFT); // back: previous page, last pair
      assertEquals(0, daw.pageIndex);
      assertEquals(3, driver.pairIndex());
      press(BTN_RIGHT);
      press(BTN_RIGHT);
      press(BTN_RIGHT);
      press(BTN_RIGHT);
      press(BTN_RIGHT); // last pair of the last page wraps to the first page
      assertEquals(0, daw.pageIndex);
      assertEquals(0, driver.pairIndex());

      withShift(() -> press(BTN_RIGHT));
      assertEquals(1, daw.deviceIndex, "shift+right: next device");
      assertFalse(daw.notifications.isEmpty(), "binding is announced");
   }

   @Test
   void dspModeWithoutDeviceBindsNothing()
   {
      daw.deviceExists = false;
      press(BTN_BANK);
      assertNull(driver.faderParam());
      fader(1023);
      assertEquals(0.7, daw.trackVolume.value, 1e-9, "volume untouched");
   }

   @Test
   void discreteParameterIsQuantized()
   {
      daw.remotes[0].discrete = 5;
      daw.remotes[1].discrete = 3;
      press(BTN_BANK);
      fader(300); // 0.293 -> step 1 of 0..4 = 0.25
      assertEquals(0.25, daw.remotes[0].value, 1e-9);
      daw.remotes[1].value = 0.0;
      driver.bindingChanged();
      flush(1.0);
      panTicks(PAN_RIGHT, 3); // resolution 3: one step = 0.5, no acceleration
      assertEquals(0.5, daw.remotes[1].value, 1e-9);
   }

   @Test
   void stickyModeUsesPinning()
   {
      press(BTN_TRNS);
      assertTrue(daw.trackPinned);
      assertEquals(1, light(LIGHT_TRNS));
      press(BTN_BANK);
      assertEquals(0, light(LIGHT_TRNS), "dsp mode shows device pin state");
      press(BTN_TRNS);
      assertTrue(daw.devicePinned);
      assertEquals(1, light(LIGHT_TRNS));
      withShift(() -> press(BTN_TRNS));
      assertTrue(daw.metronome, "shift+trns: metronome");
   }

   @Test
   void windowView()
   {
      press(BTN_MIX);
      assertEquals("MIX", daw.layout);
      assertEquals(1, light(LIGHT_MIX));
      press(BTN_MIX);
      assertEquals("ARRANGE", daw.layout);
      press(BTN_MIX);
      press(BTN_PROJ);
      assertEquals("ARRANGE", daw.layout);
      assertEquals(1, light(LIGHT_PROJ));
      withShift(() -> press(BTN_MIX));
      withShift(() -> press(BTN_PROJ));
      assertEquals(1, daw.mixerToggles);
      assertEquals(1, daw.deviceToggles);
      press(BTN_UNDO);
      withShift(() -> press(BTN_UNDO));
      assertEquals(1, daw.undos);
      assertEquals(1, daw.redos);
   }

   @Test
   void offLightIsMomentary()
   {
      midi(0xa0, BTN_OFF, 1);
      flush(0.01);
      assertEquals(1, light(LIGHT_OFF));
      midi(0xa0, BTN_OFF, 0);
      flush(0.01);
      assertEquals(0, light(LIGHT_OFF));
   }

   @Test
   void bindingChangeEndsGestureAndResyncsPan()
   {
      fader(400);
      assertTrue(daw.trackVolume.touched);
      daw.trackPan.value = 0.0;
      driver.bindingChanged(); // e.g. other track selected
      flush(0.01);
      assertFalse(daw.trackVolume.touched, "gesture ended on binding change");
      now += 1.0;
      panTicks(PAN_RIGHT, 3);
      assertEquals(1.0 / 127, daw.trackPan.value, 1e-9, "virtual pan resynced to new value");
   }

   @Test
   void shutdownSwitchesLightsOff()
   {
      press(BTN_MUTE);
      sent.clear();
      driver.shutdown();
      assertEquals(0, light(LIGHT_MUTE));
   }
}
