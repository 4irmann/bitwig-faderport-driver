package io.github._4irmann.faderport.core;

import java.util.ArrayList;
import java.util.List;

/** In-memory Daw for the unit tests */
class FakeDaw implements Daw
{
   static class FakeParam implements Param
   {
      String name;
      double value;
      double defaultValue;
      boolean exists = true;
      boolean bipolar;
      int discrete = -1;
      boolean touched = false;
      final List<Boolean> touches = new ArrayList<>();
      final List<Double> sets = new ArrayList<>();

      FakeParam(String name, double value, boolean bipolar)
      {
         this.name = name;
         this.value = value;
         this.defaultValue = value;
         this.bipolar = bipolar;
      }

      @Override public boolean exists() { return exists; }
      @Override public double get() { return value; }
      @Override public void set(double v) { value = v; sets.add(v); }
      @Override public void touch(boolean t) { touched = t; touches.add(t); }
      @Override public void reset() { value = defaultValue; }
      @Override public String name() { return name; }
      @Override public String displayValue() { return String.format("%.2f", value); }
      @Override public int discreteValueCount() { return discrete; }
      @Override public boolean bipolar() { return bipolar; }
   }

   final FakeParam trackVolume = new FakeParam("Volume", 0.7, false);
   final FakeParam trackPan = new FakeParam("Pan", 0.5, true);
   final FakeParam masterVolume = new FakeParam("Master Volume", 0.8, false);
   final FakeParam masterPan = new FakeParam("Master Pan", 0.5, true);
   final FakeParam[] remotes = new FakeParam[REMOTE_CONTROLS];

   String trackName = "Track 1";
   boolean trackExists = true, mute, solo, arm, masterMute;
   int trackIndex = 0;
   boolean deviceExists = true;
   String deviceName = "EQ+";
   int pageCount = 2, pageIndex = 0;
   int deviceIndex = 0;
   boolean trackPinned, devicePinned;
   boolean playing, recording, loop, punch, metronome;
   int loopMoves;
   String lastTransport = "";
   boolean automationWrite;
   String automationMode = "latch";
   int overrideResets;
   String layout = "ARRANGE";
   int mixerToggles, deviceToggles, undos, redos;
   final List<String> notifications = new ArrayList<>();

   FakeDaw()
   {
      for (int i = 0; i < REMOTE_CONTROLS; i++)
         remotes[i] = new FakeParam("P" + (i + 1), 0.25, false);
   }

   @Override public Param trackVolume() { return trackVolume; }
   @Override public Param trackPan() { return trackPan; }
   @Override public Param masterVolume() { return masterVolume; }
   @Override public Param masterPan() { return masterPan; }
   @Override public String trackName() { return trackName; }
   @Override public boolean trackExists() { return trackExists; }
   @Override public boolean trackMute() { return mute; }
   @Override public boolean trackSolo() { return solo; }
   @Override public boolean trackArm() { return arm; }
   @Override public void toggleTrackMute() { mute = !mute; }
   @Override public void toggleTrackSolo() { solo = !solo; }
   @Override public void toggleTrackArm() { arm = !arm; }
   @Override public void toggleMasterMute() { masterMute = !masterMute; }
   @Override public boolean masterMute() { return masterMute; }
   @Override public void selectPreviousTrack() { trackIndex--; }
   @Override public void selectNextTrack() { trackIndex++; }

   @Override public boolean deviceExists() { return deviceExists; }
   @Override public String deviceName() { return deviceName; }
   @Override public Param remoteControl(int index) { return remotes[index]; }
   @Override public int remotePageCount() { return pageCount; }
   @Override public int remotePageIndex() { return pageIndex; }
   @Override public String remotePageName() { return "Page " + (pageIndex + 1); }
   @Override public void selectRemotePage(int index) { pageIndex = index; }
   @Override public void selectPreviousDevice() { deviceIndex--; }
   @Override public void selectNextDevice() { deviceIndex++; }

   @Override public boolean trackPinned() { return trackPinned; }
   @Override public void setTrackPinned(boolean pinned) { trackPinned = pinned; }
   @Override public boolean devicePinned() { return devicePinned; }
   @Override public void setDevicePinned(boolean pinned) { devicePinned = pinned; }

   @Override public boolean isPlaying() { return playing; }
   @Override public void play() { playing = true; lastTransport = "play"; }
   @Override public void continuePlayback() { playing = true; lastTransport = "continue"; }
   @Override public void stop() { playing = false; lastTransport = "stop"; }
   @Override public boolean isRecording() { return recording; }
   @Override public void toggleRecord() { recording = !recording; }
   @Override public boolean isLoopEnabled() { return loop; }
   @Override public void toggleLoop() { loop = !loop; }
   @Override public boolean isPunchEnabled() { return punch; }
   @Override public void togglePunch() { punch = !punch; }
   @Override public void moveLoop(int direction) { loopMoves += direction; }
   @Override public void rewind() { lastTransport = "rewind"; }
   @Override public void fastForward() { lastTransport = "fastForward"; }
   @Override public void previousCueMarker() { lastTransport = "previousCue"; }
   @Override public void nextCueMarker() { lastTransport = "nextCue"; }
   @Override public void toggleMetronome() { metronome = !metronome; }

   @Override public boolean isAutomationWriteEnabled() { return automationWrite; }
   @Override public void setAutomationWriteEnabled(boolean enabled) { automationWrite = enabled; }
   @Override public String automationWriteMode() { return automationMode; }
   @Override public void setAutomationWriteMode(String mode) { automationMode = mode; }
   @Override public void resetAutomationOverrides() { overrideResets++; }

   @Override public String panelLayout() { return layout; }
   @Override public void setPanelLayout(String l) { layout = l; }
   @Override public void toggleMixerPanel() { mixerToggles++; }
   @Override public void toggleDevicePanel() { deviceToggles++; }
   @Override public void undo() { undos++; }
   @Override public void redo() { redos++; }
   @Override public void showNotification(String text) { notifications.add(text); }
}
