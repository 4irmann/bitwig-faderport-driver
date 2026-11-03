/*
 * 4irmann's FaderPort Driver for Bitwig Studio: controller extension
 *
 * Wires the FaderPort MIDI ports, Bitwig's preferences and BitwigDaw to the
 * driver logic. Bitwig calls flush() after changes; a periodic task drives the
 * release timeouts of fader and pan encoder.
 *
 * Copyright 2010-2026 Matthias Ehrmann,
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github._4irmann.faderport;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.MidiOut;
import com.bitwig.extension.controller.api.Preferences;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.SettableEnumValue;
import com.bitwig.extension.controller.api.SettableRangedValue;

import io.github._4irmann.faderport.core.Driver;
import io.github._4irmann.faderport.core.Settings;

public class FaderPortExtension extends ControllerExtension
{
   private static final int TICK_MS = 50;
   private static final String CATEGORY = "FaderPort";

   private Driver driver;
   private boolean running = false;

   protected FaderPortExtension(FaderPortExtensionDefinition definition, ControllerHost host)
   {
      super(definition, host);
   }

   private static double now()
   {
      return System.nanoTime() / 1e9;
   }

   @Override
   public void init()
   {
      ControllerHost host = getHost();
      Settings settings = new Settings();
      loadPreferences(host.getPreferences(), settings);

      MidiOut midiOut = host.getMidiOutPort(0);
      BitwigDaw daw = new BitwigDaw(host, host::requestFlush, () -> {
         if (driver != null)
            driver.bindingChanged();
         host.requestFlush();
      });
      driver = new Driver(daw, settings, midiOut::sendMidi, host::println);

      host.getMidiInPort(0).setMidiCallback((status, data1, data2) -> {
         driver.midiReceived(status, data1, data2, now());
         host.requestFlush();
      });

      running = true;
      driver.start();
      scheduleTick(host);
      host.showPopupNotification("FaderPort (classic) initialized");
   }

   private void loadPreferences(Preferences preferences, Settings settings)
   {
      SettableEnumValue writeButton = preferences.getEnumSetting("Write button", CATEGORY,
         new String[] { "Latch (Shift: Write)", "Write (Shift: Latch)" }, "Latch (Shift: Write)");
      writeButton.addValueObserver(value -> settings.latchIsDefaultWriteMode = value.startsWith("Latch"));

      SettableRangedValue panResolution = preferences.getNumberSetting("Pan resolution (steps)", CATEGORY,
         16, 1024, 1, "", settings.virtualPanResolution);
      panResolution.addRawValueObserver(value -> {
         settings.virtualPanResolution = (int) Math.round(value);
         if (driver != null)
            driver.bindingChanged();
      });

      SettableRangedValue antiSuck = preferences.getNumberSetting("Pan anti-suck (clicks per step)", CATEGORY,
         1, 5, 1, "", settings.antiSuckMinTurns);
      antiSuck.addRawValueObserver(value -> settings.antiSuckMinTurns = (int) Math.round(value));

      SettableEnumValue footswitch = preferences.getEnumSetting("Footswitch pressed signal", CATEGORY,
         new String[] { "1", "0" }, "1");
      footswitch.addValueObserver(value -> settings.footswitchPressedSignal = Integer.parseInt(value));

      SettableBooleanValue valuePopups = preferences.getBooleanSetting("Show value changes", CATEGORY,
         settings.valuePopups);
      valuePopups.addValueObserver(value -> settings.valuePopups = value);

      SettableBooleanValue dumpMidi = preferences.getBooleanSetting("Dump MIDI to console", CATEGORY,
         settings.dumpMidi);
      dumpMidi.addValueObserver(value -> settings.dumpMidi = value);
   }

   private void scheduleTick(ControllerHost host)
   {
      host.scheduleTask(() -> {
         if (!running)
            return;
         driver.tick(now());
         host.requestFlush();
         scheduleTick(host);
      }, TICK_MS);
   }

   @Override
   public void exit()
   {
      running = false;
      if (driver != null)
         driver.shutdown();
      getHost().showPopupNotification("FaderPort (classic) exited");
   }

   @Override
   public void flush()
   {
      if (driver != null)
         driver.flush(now());
   }
}
