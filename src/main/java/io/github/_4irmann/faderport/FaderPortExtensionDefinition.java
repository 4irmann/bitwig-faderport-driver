/*
 * 4irmann's FaderPort Driver for Bitwig Studio: extension definition
 *
 * Copyright 2010-2026 Matthias Ehrmann, Apache License 2.0
 */
package io.github._4irmann.faderport;

import java.util.UUID;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.AutoDetectionMidiPortNamesList;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;

public class FaderPortExtensionDefinition extends ControllerExtensionDefinition
{
   private static final UUID ID = UUID.fromString("7b1c6a52-3e7d-4c9a-9b1e-2f4a9d6c8e10");

   @Override
   public String getName()
   {
      return "FaderPort Classic (4irmann)";
   }

   @Override
   public String getAuthor()
   {
      return "4irmann";
   }

   @Override
   public String getVersion()
   {
      String version = getClass().getPackage().getImplementationVersion();
      return version != null ? version : "1.0.0";
   }

   @Override
   public UUID getId()
   {
      return ID;
   }

   @Override
   public String getHardwareVendor()
   {
      return "PreSonus";
   }

   @Override
   public String getHardwareModel()
   {
      return "FaderPort Classic (4irmann)";
   }

   /** API 18 = Bitwig Studio 5.0. Only API functions available since then are used. */
   @Override
   public int getRequiredAPIVersion()
   {
      return 18;
   }

   @Override
   public int getNumMidiInPorts()
   {
      return 1;
   }

   @Override
   public int getNumMidiOutPorts()
   {
      return 1;
   }

   @Override
   public void listAutoDetectionMidiPortNames(AutoDetectionMidiPortNamesList list, PlatformType platformType)
   {
      switch (platformType)
      {
         case WINDOWS:
         case MAC:
            list.add(new String[] { "FaderPort" }, new String[] { "FaderPort" });
            break;
         case LINUX:
            list.add(new String[] { "FaderPort MIDI 1" }, new String[] { "FaderPort MIDI 1" });
            list.add(new String[] { "FaderPort" }, new String[] { "FaderPort" });
            break;
         default:
            break;
      }
   }

   @Override
   public FaderPortExtension createInstance(ControllerHost host)
   {
      return new FaderPortExtension(this, host);
   }
}
