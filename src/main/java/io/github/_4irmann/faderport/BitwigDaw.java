/*
 * Daw on top of the Bitwig controller API
 *
 * All values the driver reads are marked as interested, observers request a
 * flush (lights, motor fader) and report binding changes (track, device, page).
 *
 * Copyright 2010-2026 Matthias Ehrmann, Apache License 2.0
 */
package io.github._4irmann.faderport;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.MasterTrack;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.bitwig.extension.controller.api.Transport;

import io.github._4irmann.faderport.core.Daw;
import io.github._4irmann.faderport.core.Param;

final class BitwigDaw implements Daw
{
   private final ControllerHost host;
   private final Transport transport;
   private final Application application;
   private final CursorTrack cursorTrack;
   private final MasterTrack masterTrack;
   private final PinnableCursorDevice cursorDevice;
   private final CursorRemoteControlsPage remotes;

   private final Param trackVolume;
   private final Param trackPan;
   private final Param masterVolume;
   private final Param masterPan;
   private final Param[] remoteControls = new Param[REMOTE_CONTROLS];

   /**
    * @param onChange called for any change (requests a flush)
    * @param onBindingChange called when track, device or remote page change
    */
   BitwigDaw(ControllerHost host, Runnable onChange, Runnable onBindingChange)
   {
      this.host = host;

      transport = host.createTransport();
      application = host.createApplication();
      cursorTrack = host.createCursorTrack("FADERPORT_TRACK", "FaderPort", 0, 0, true);
      masterTrack = host.createMasterTrack(0);
      cursorDevice = cursorTrack.createCursorDevice("FADERPORT_DEVICE", "FaderPort", 0,
         CursorDeviceFollowMode.FOLLOW_SELECTION);
      remotes = cursorDevice.createCursorRemoteControlsPage(REMOTE_CONTROLS);

      trackVolume = new BitwigParam(cursorTrack.volume(), false, onChange);
      trackPan = new BitwigParam(cursorTrack.pan(), true, onChange);
      masterVolume = new BitwigParam(masterTrack.volume(), false, onChange);
      masterPan = new BitwigParam(masterTrack.pan(), true, onChange);
      for (int i = 0; i < REMOTE_CONTROLS; i++)
         remoteControls[i] = new BitwigParam(remotes.getParameter(i), false, onChange);

      // track: binding changes
      cursorTrack.exists().addValueObserver(v -> onBindingChange.run());
      cursorTrack.name().addValueObserver(v -> onBindingChange.run());
      cursorTrack.position().addValueObserver(v -> onBindingChange.run());
      cursorTrack.isPinned().addValueObserver(v -> onBindingChange.run());

      // device / remote page: binding changes
      cursorDevice.exists().addValueObserver(v -> onBindingChange.run());
      cursorDevice.name().addValueObserver(v -> onBindingChange.run());
      cursorDevice.isPinned().addValueObserver(v -> onBindingChange.run());
      remotes.selectedPageIndex().addValueObserver(v -> onBindingChange.run());
      remotes.pageCount().addValueObserver(v -> onBindingChange.run());
      remotes.pageNames().markInterested();

      // lights
      cursorTrack.mute().addValueObserver(v -> onChange.run());
      cursorTrack.solo().addValueObserver(v -> onChange.run());
      cursorTrack.arm().addValueObserver(v -> onChange.run());
      masterTrack.mute().addValueObserver(v -> onChange.run());

      transport.isPlaying().addValueObserver(v -> onChange.run());
      transport.isArrangerRecordEnabled().addValueObserver(v -> onChange.run());
      transport.isArrangerLoopEnabled().addValueObserver(v -> onChange.run());
      transport.isPunchInEnabled().addValueObserver(v -> onChange.run());
      transport.isPunchOutEnabled().addValueObserver(v -> onChange.run());
      transport.isArrangerAutomationWriteEnabled().addValueObserver(v -> onChange.run());
      transport.automationWriteMode().addValueObserver(v -> onChange.run());
      transport.isMetronomeEnabled().markInterested();
      transport.arrangerLoopStart().markInterested();
      transport.arrangerLoopDuration().markInterested();

      application.panelLayout().addValueObserver(v -> onChange.run());
   }

   // ---------------------------------------------------------- track / master

   @Override
   public Param trackVolume()
   {
      return trackVolume;
   }

   @Override
   public Param trackPan()
   {
      return trackPan;
   }

   @Override
   public Param masterVolume()
   {
      return masterVolume;
   }

   @Override
   public Param masterPan()
   {
      return masterPan;
   }

   @Override
   public String trackName()
   {
      return cursorTrack.exists().get() ? cursorTrack.name().get() : "(no track)";
   }

   @Override
   public boolean trackExists()
   {
      return cursorTrack.exists().get();
   }

   @Override
   public boolean trackMute()
   {
      return cursorTrack.mute().get();
   }

   @Override
   public boolean trackSolo()
   {
      return cursorTrack.solo().get();
   }

   @Override
   public boolean trackArm()
   {
      return cursorTrack.arm().get();
   }

   @Override
   public void toggleTrackMute()
   {
      cursorTrack.mute().toggle();
   }

   @Override
   public void toggleTrackSolo()
   {
      cursorTrack.solo().toggle();
   }

   @Override
   public void toggleTrackArm()
   {
      cursorTrack.arm().toggle();
   }

   @Override
   public void toggleMasterMute()
   {
      masterTrack.mute().toggle();
   }

   @Override
   public boolean masterMute()
   {
      return masterTrack.mute().get();
   }

   @Override
   public void selectPreviousTrack()
   {
      cursorTrack.selectPrevious();
   }

   @Override
   public void selectNextTrack()
   {
      cursorTrack.selectNext();
   }

   // ------------------------------------------------------- device / remotes

   @Override
   public boolean deviceExists()
   {
      return cursorDevice.exists().get();
   }

   @Override
   public String deviceName()
   {
      return cursorDevice.name().get();
   }

   @Override
   public Param remoteControl(int index)
   {
      return remoteControls[index];
   }

   @Override
   public int remotePageCount()
   {
      return remotes.pageCount().get();
   }

   @Override
   public int remotePageIndex()
   {
      return remotes.selectedPageIndex().get();
   }

   @Override
   public String remotePageName()
   {
      String[] names = remotes.pageNames().get();
      int index = remotePageIndex();
      return names != null && index >= 0 && index < names.length ? names[index] : "";
   }

   @Override
   public void selectRemotePage(int index)
   {
      remotes.selectedPageIndex().set(index);
   }

   @Override
   public void selectPreviousDevice()
   {
      cursorDevice.selectPrevious();
   }

   @Override
   public void selectNextDevice()
   {
      cursorDevice.selectNext();
   }

   // --------------------------------------------------------- sticky (pinning)

   @Override
   public boolean trackPinned()
   {
      return cursorTrack.isPinned().get();
   }

   @Override
   public void setTrackPinned(boolean pinned)
   {
      cursorTrack.isPinned().set(pinned);
   }

   @Override
   public boolean devicePinned()
   {
      return cursorDevice.isPinned().get();
   }

   @Override
   public void setDevicePinned(boolean pinned)
   {
      cursorDevice.isPinned().set(pinned);
   }

   // ---------------------------------------------------------------- transport

   @Override
   public boolean isPlaying()
   {
      return transport.isPlaying().get();
   }

   @Override
   public void play()
   {
      transport.play();
   }

   @Override
   public void continuePlayback()
   {
      transport.continuePlayback();
   }

   @Override
   public void stop()
   {
      transport.stop();
   }

   @Override
   public boolean isRecording()
   {
      return transport.isArrangerRecordEnabled().get();
   }

   @Override
   public void toggleRecord()
   {
      transport.isArrangerRecordEnabled().toggle();
   }

   @Override
   public boolean isLoopEnabled()
   {
      return transport.isArrangerLoopEnabled().get();
   }

   @Override
   public void toggleLoop()
   {
      transport.isArrangerLoopEnabled().toggle();
   }

   @Override
   public boolean isPunchEnabled()
   {
      return transport.isPunchInEnabled().get() || transport.isPunchOutEnabled().get();
   }

   @Override
   public void togglePunch()
   {
      boolean enable = !isPunchEnabled();
      transport.isPunchInEnabled().set(enable);
      transport.isPunchOutEnabled().set(enable);
   }

   @Override
   public void moveLoop(int direction)
   {
      double start = transport.arrangerLoopStart().get();
      double duration = transport.arrangerLoopDuration().get();
      if (duration <= 0)
         return;
      transport.arrangerLoopStart().set(Math.max(0.0, start + direction * duration));
   }

   @Override
   public void rewind()
   {
      transport.rewind();
   }

   @Override
   public void fastForward()
   {
      transport.fastForward();
   }

   @Override
   public void previousCueMarker()
   {
      transport.jumpToPreviousCueMarker();
   }

   @Override
   public void nextCueMarker()
   {
      transport.jumpToNextCueMarker();
   }

   @Override
   public void toggleMetronome()
   {
      transport.isMetronomeEnabled().toggle();
   }

   // --------------------------------------------------------------- automation

   @Override
   public boolean isAutomationWriteEnabled()
   {
      return transport.isArrangerAutomationWriteEnabled().get();
   }

   @Override
   public void setAutomationWriteEnabled(boolean enabled)
   {
      transport.isArrangerAutomationWriteEnabled().set(enabled);
   }

   @Override
   public String automationWriteMode()
   {
      return transport.automationWriteMode().get();
   }

   @Override
   public void setAutomationWriteMode(String mode)
   {
      transport.automationWriteMode().set(mode);
   }

   @Override
   public void resetAutomationOverrides()
   {
      transport.resetAutomationOverrides();
   }

   // -------------------------------------------------------------- application

   @Override
   public String panelLayout()
   {
      return application.panelLayout().get();
   }

   @Override
   public void setPanelLayout(String layout)
   {
      application.setPanelLayout(layout);
   }

   @Override
   public void toggleMixerPanel()
   {
      application.toggleMixer();
   }

   @Override
   public void toggleDevicePanel()
   {
      application.toggleDevices();
   }

   @Override
   public void undo()
   {
      application.undo();
   }

   @Override
   public void redo()
   {
      application.redo();
   }

   @Override
   public void showNotification(String text)
   {
      host.showPopupNotification(text);
   }
}
