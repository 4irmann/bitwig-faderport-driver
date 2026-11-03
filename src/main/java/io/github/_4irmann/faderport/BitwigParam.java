/*
 * Param on top of a Bitwig Parameter (track volume/pan, remote control)
 *
 * Copyright 2010-2026 Matthias Ehrmann, Apache License 2.0
 */
package io.github._4irmann.faderport;

import com.bitwig.extension.controller.api.Parameter;

import io.github._4irmann.faderport.core.Param;

final class BitwigParam implements Param
{
   private final Parameter parameter;
   private final boolean bipolar;

   BitwigParam(Parameter parameter, boolean bipolar, Runnable onChange)
   {
      this.parameter = parameter;
      this.bipolar = bipolar;

      // values read via get() must be marked as interesting
      parameter.exists().markInterested();
      parameter.name().markInterested();
      parameter.value().markInterested();
      parameter.value().displayedValue().markInterested();
      parameter.value().discreteValueCount().markInterested();

      parameter.value().addValueObserver(value -> onChange.run());
   }

   @Override
   public boolean exists()
   {
      return parameter.exists().get();
   }

   @Override
   public double get()
   {
      return parameter.value().get();
   }

   @Override
   public void set(double value)
   {
      parameter.value().set(value);
   }

   @Override
   public void touch(boolean touched)
   {
      parameter.touch(touched);
   }

   @Override
   public void reset()
   {
      parameter.reset();
   }

   @Override
   public String name()
   {
      return parameter.name().get();
   }

   @Override
   public String displayValue()
   {
      return parameter.value().displayedValue().get();
   }

   @Override
   public int discreteValueCount()
   {
      return parameter.value().discreteValueCount().get();
   }

   @Override
   public boolean bipolar()
   {
      return bipolar;
   }
}
