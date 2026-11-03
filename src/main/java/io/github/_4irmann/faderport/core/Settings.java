/*
 * FaderPort driver preferences (edited in Bitwig's controller preferences)
 *
 * Copyright 2010-2026 Matthias Ehrmann, Apache License 2.0
 */
package io.github._4irmann.faderport.core;

public class Settings
{
   /** true: "write" button = latch, shift+write = write; false: the other way round */
   public boolean latchIsDefaultWriteMode = true;

   /** resolution of the virtual pan control */
   public int virtualPanResolution = 128;

   /** minimum number of encoder ticks for a virtual pan value change */
   public int antiSuckMinTurns = 3;

   /** 0 or 1, swaps footswitch pressed/released signal */
   public int footswitchPressedSignal = 1;

   /** fader treated as released without touch messages (FaderPort without power supply) */
   public double faderReleaseTimeout = 0.3;

   /** pan/trim encoder treated as released */
   public double panReleaseTimeout = 0.5;

   /** popup notification for value changes, not only for binding changes */
   public boolean valuePopups = false;

   /** print received/sent MIDI messages to the controller script console */
   public boolean dumpMidi = false;
}
