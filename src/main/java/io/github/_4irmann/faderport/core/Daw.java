/*
 * Everything the driver needs from the DAW.
 *
 * Implemented by BitwigDaw on top of the Bitwig controller API, and by a fake in
 * the unit tests. Getters return the last state reported by Bitwig's observers.
 *
 * Copyright 2010-2026 Matthias Ehrmann, Apache License 2.0
 */
package io.github._4irmann.faderport.core;

public interface Daw
{
   int REMOTE_CONTROLS = 8;

   // ---------------------------------------------------------- track / master

   Param trackVolume();

   Param trackPan();

   Param masterVolume();

   Param masterPan();

   String trackName();

   boolean trackExists();

   boolean trackMute();

   boolean trackSolo();

   boolean trackArm();

   void toggleTrackMute();

   void toggleTrackSolo();

   void toggleTrackArm();

   void toggleMasterMute();

   boolean masterMute();

   void selectPreviousTrack();

   void selectNextTrack();

   // ------------------------------------------------------- device / remotes

   boolean deviceExists();

   String deviceName();

   /** remote control 0..7 of the selected remote controls page of the cursor device */
   Param remoteControl(int index);

   int remotePageCount();

   int remotePageIndex();

   String remotePageName();

   void selectRemotePage(int index);

   void selectPreviousDevice();

   void selectNextDevice();

   // --------------------------------------------------------- sticky (pinning)

   boolean trackPinned();

   void setTrackPinned(boolean pinned);

   boolean devicePinned();

   void setDevicePinned(boolean pinned);

   // ---------------------------------------------------------------- transport

   boolean isPlaying();

   void play();

   void continuePlayback();

   void stop();

   boolean isRecording();

   void toggleRecord();

   boolean isLoopEnabled();

   void toggleLoop();

   boolean isPunchEnabled();

   void togglePunch();

   /** move the loop region by its length, direction -1 or 1 */
   void moveLoop(int direction);

   void rewind();

   void fastForward();

   void previousCueMarker();

   void nextCueMarker();

   void toggleMetronome();

   // --------------------------------------------------------------- automation

   /** automation write (global in Bitwig) */
   boolean isAutomationWriteEnabled();

   void setAutomationWriteEnabled(boolean enabled);

   /** "latch", "touch" or "write" */
   String automationWriteMode();

   void setAutomationWriteMode(String mode);

   void resetAutomationOverrides();

   // -------------------------------------------------------------- application

   /** "ARRANGE", "MIX" or "EDIT" */
   String panelLayout();

   void setPanelLayout(String layout);

   void toggleMixerPanel();

   void toggleDevicePanel();

   void undo();

   void redo();

   void showNotification(String text);
}
