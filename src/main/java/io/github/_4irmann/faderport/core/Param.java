/*
 * Parameter bound to fader or pan control (track volume/pan, remote control).
 *
 * Values are normalized 0..1 like in Bitwig. touch() is forwarded to Bitwig,
 * which uses it for touch/latch automation.
 *
 * Copyright 2010-2026 Matthias Ehrmann, Apache License 2.0
 */
package io.github._4irmann.faderport.core;

public interface Param
{
   /** parameter exists (e.g. a remote control slot may be unassigned) */
   boolean exists();

   /** current value 0..1 including automation playback */
   double get();

   void set(double value);

   /** begin/end of a gesture, drives touch/latch automation */
   void touch(boolean touched);

   /** reset to default value */
   void reset();

   String name();

   String displayValue();

   /** number of discrete values, 0 or less for continuous parameters */
   int discreteValueCount();

   /** snap to center (pan) */
   boolean bipolar();
}
