/*
 * FaderPort (classic, native mode) MIDI protocol
 *
 * Buttons send  : 0xA0 <button> <0|1>      (poly aftertouch)
 * Lights receive: 0xA0 <light>  <0|1>      HINT: light numbers differ from button numbers!
 * Fader send    : 0xB0 0x00 <msb>, 0xB0 0x20 <lsb>   value = msb<<3 | lsb>>4 (10 bit)
 * Fader receive : 0xB0 0x00 <v>>7>, 0xB0 0x20 <v&127>
 * Pan encoder   : 0xE0 0x00 <0x01 = right | 0x7E = left>
 * Native mode   : 0x91 0x00 0x64 switches FaderPort from HUI into native mode
 *
 * HINT: the fader resolution is 10 bit in both directions. That's a limit of the
 * hardware/protocol, not of the driver.
 *
 * Copyright 2010-2026 Matthias Ehrmann, Apache License 2.0
 */
package io.github._4irmann.faderport.core;

public final class Protocol
{
   private Protocol()
   {
   }

   public static final int FADER_MAX = 1023;
   public static final int LIGHT_COUNT = 0x18;

   public static final int PAN_RIGHT = 0x01;
   public static final int PAN_LEFT = 0x7e;

   // button numbers (FaderPort -> host)
   public static final int BTN_USER = 0x00, BTN_PUNCH = 0x01, BTN_SHIFT = 0x02, BTN_REWIND = 0x03,
      BTN_FORWARD = 0x04, BTN_STOP = 0x05, BTN_PLAY = 0x06, BTN_RECORD = 0x07, BTN_TOUCH = 0x08,
      BTN_WRITE = 0x09, BTN_READ = 0x0a, BTN_MIX = 0x0b, BTN_PROJ = 0x0c, BTN_TRNS = 0x0d, BTN_UNDO = 0x0e,
      BTN_LOOP = 0x0f, BTN_REC = 0x10, BTN_SOLO = 0x11, BTN_MUTE = 0x12, BTN_LEFT = 0x13, BTN_BANK = 0x14,
      BTN_RIGHT = 0x15, BTN_OUTPUT = 0x16, BTN_OFF = 0x17, BTN_FOOTSWITCH = 0x7e, BTN_FADER_TOUCH = 0x7f;

   // light numbers (host -> FaderPort)
   public static final int LIGHT_RECORD = 0x00, LIGHT_PLAY = 0x01, LIGHT_STOP = 0x02, LIGHT_FORWARD = 0x03,
      LIGHT_REWIND = 0x04, LIGHT_SHIFT = 0x05, LIGHT_PUNCH = 0x06, LIGHT_USER = 0x07, LIGHT_LOOP = 0x08,
      LIGHT_UNDO = 0x09, LIGHT_TRNS = 0x0a, LIGHT_PROJ = 0x0b, LIGHT_MIX = 0x0c, LIGHT_READ = 0x0d,
      LIGHT_WRITE = 0x0e, LIGHT_TOUCH = 0x0f, LIGHT_OFF = 0x10, LIGHT_OUTPUT = 0x11, LIGHT_RIGHT = 0x12,
      LIGHT_BANK = 0x13, LIGHT_LEFT = 0x14, LIGHT_MUTE = 0x15, LIGHT_SOLO = 0x16, LIGHT_REC = 0x17;

   /** Sends one MIDI message to the FaderPort. */
   @FunctionalInterface
   public interface MidiSender
   {
      void send(int status, int data1, int data2);
   }

   public static void sendNativeMode(MidiSender out)
   {
      out.send(0x91, 0x00, 0x64);
   }

   public static void sendLight(MidiSender out, int light, boolean on)
   {
      out.send(0xa0, light, on ? 1 : 0);
   }

   /** host -> FaderPort: motor fader position (0..1023) */
   public static void sendFader(MidiSender out, int value)
   {
      out.send(0xb0, 0x00, value >> 7);
      out.send(0xb0, 0x20, value & 0x7f);
   }

   /** FaderPort -> host: combine msb/lsb of a received fader value (0..1023) */
   public static int decodeFader(int msb, int lsb)
   {
      return (msb << 3) | (lsb >> 4);
   }

   /** FaderPort -> host encoding (tests) */
   public static int[] encodeFader(int value)
   {
      return new int[] { value >> 3, (value & 0x07) << 4 };
   }
}
