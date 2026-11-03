; 4irmann's FaderPort Driver for Bitwig Studio - Windows installer (Inno Setup 6)
;
; Build (after "mvn package", ExtensionPath absolute or relative to this file):
;   iscc /DAppVersion=1.0.0 installer\windows\bitwig-faderport-driver.iss
;
; Installs:
;   <Bitwig user folder>\Extensions\FaderPort4irmann.bwextension   the extension
;   %LOCALAPPDATA%\4irmann\bitwig-faderport-driver\                  manual, license, uninstaller
; The Bitwig user folder defaults to Documents\Bitwig Studio and can be changed
; in Bitwig (Settings > Locations), so it is selectable here.

#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#ifndef ExtensionPath
  #define ExtensionPath "..\..\target\FaderPort4irmann.bwextension"
#endif

#define AppName "4irmann's FaderPort Driver for Bitwig Studio"
#define ExtensionFile "FaderPort4irmann.bwextension"

[Setup]
AppId={{3D8F2A61-9C4B-4E7A-B5D2-6A1C8E0F9B47}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=4irmann
AppPublisherURL=https://github.com/4irmann/bitwig-faderport-driver
AppSupportURL=https://github.com/4irmann/bitwig-faderport-driver/issues
; {app} is the Bitwig user folder, the extension goes to its Extensions subfolder
DefaultDirName={userdocs}\Bitwig Studio
DirExistsWarning=no
AppendDefaultDirName=no
UsePreviousAppDir=yes
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir=..\..\dist
OutputBaseFilename=bitwig-faderport-driver-{#AppVersion}-setup
LicenseFile=..\..\LICENSE
UninstallFilesDir={localappdata}\4irmann\bitwig-faderport-driver
UninstallDisplayName={#AppName}
WizardStyle=modern
Compression=lzma2
SolidCompression=yes
ShowLanguageDialog=auto

[Languages]
Name: "en"; MessagesFile: "compiler:Default.isl"
Name: "de"; MessagesFile: "compiler:Languages\German.isl"

[Messages]
en.SelectDirLabel3=Setup will install the extension into the Extensions folder of the following Bitwig user folder (Bitwig: Settings > Locations).
de.SelectDirLabel3=Die Extension wird in den Extensions-Ordner des folgenden Bitwig-Benutzerordners installiert (Bitwig: Settings > Locations).
en.SelectDirBrowseLabel=If you moved your Bitwig user folder, select it here, then click Next.
de.SelectDirBrowseLabel=Falls der Bitwig-Benutzerordner verschoben wurde, hier auswählen und auf Weiter klicken.

[CustomMessages]
en.BitwigRunning=Bitwig Studio is running. Please close Bitwig Studio and click Retry.
de.BitwigRunning=Bitwig Studio läuft. Bitte Bitwig Studio schließen und auf Wiederholen klicken.
en.NoBitwigFolder=The selected folder doesn't look like a Bitwig user folder (no "Extensions" or "Library" subfolder).%n%nInstall anyway?
de.NoBitwigFolder=Der gewählte Ordner sieht nicht wie ein Bitwig-Benutzerordner aus (kein Unterordner "Extensions" oder "Library").%n%nTrotzdem installieren?
en.SetupHint=Next step: start Bitwig Studio, open Settings > Controllers and add "PreSonus > FaderPort Classic (4irmann)" if it isn't detected automatically. Don't use the "FaderPort 8/16" entries, they are for the newer models.
de.SetupHint=Nächster Schritt: Bitwig Studio starten, Settings > Controllers öffnen und "PreSonus > FaderPort Classic (4irmann)" hinzufügen, falls er nicht automatisch erkannt wird. Nicht die Einträge "FaderPort 8/16" verwenden, die sind für die neueren Modelle.
en.StartBitwig=Start Bitwig Studio
de.StartBitwig=Bitwig Studio starten
en.OpenManual=Open the manual
de.OpenManual=Handbuch öffnen

[Files]
Source: "{#ExtensionPath}"; DestDir: "{app}\Extensions"; DestName: "{#ExtensionFile}"; Flags: ignoreversion
Source: "..\..\README.md"; DestDir: "{localappdata}\4irmann\bitwig-faderport-driver"; Flags: ignoreversion
Source: "..\..\MANUAL.md"; DestDir: "{localappdata}\4irmann\bitwig-faderport-driver"; Flags: ignoreversion
Source: "..\..\docs\*"; DestDir: "{localappdata}\4irmann\bitwig-faderport-driver\docs"; Flags: ignoreversion
Source: "..\..\LICENSE"; DestDir: "{localappdata}\4irmann\bitwig-faderport-driver"; DestName: "LICENSE.txt"; Flags: ignoreversion
Source: "..\..\NOTICE"; DestDir: "{localappdata}\4irmann\bitwig-faderport-driver"; DestName: "NOTICE.txt"; Flags: ignoreversion

[Run]
Filename: "{code:BitwigExe}"; Description: "{cm:StartBitwig}"; Flags: postinstall nowait skipifsilent; Check: BitwigExeExists
Filename: "https://github.com/4irmann/bitwig-faderport-driver/blob/main/MANUAL.md"; Description: "{cm:OpenManual}"; Flags: postinstall shellexec nowait skipifsilent unchecked

[UninstallDelete]
Type: dirifempty; Name: "{localappdata}\4irmann\bitwig-faderport-driver\docs"
Type: dirifempty; Name: "{localappdata}\4irmann\bitwig-faderport-driver"
Type: dirifempty; Name: "{localappdata}\4irmann"

[Code]
// Bitwig Studio has no unique main window class, so the running processes are checked via WMI
function IsBitwigRunning: Boolean;
var
  Locator, Service, Processes: Variant;
begin
  Result := False;
  try
    Locator := CreateOleObject('WbemScripting.SWbemLocator');
    Service := Locator.ConnectServer('.', 'root\CIMV2');
    Processes := Service.ExecQuery('SELECT Name FROM Win32_Process WHERE Name = ''Bitwig Studio.exe'' OR Name = ''BitwigStudio.exe'' OR Name = ''BitwigStudioApp.exe''');
    Result := Processes.Count > 0;
  except
    // WMI not available: don't block the installation
    Result := False;
  end;
end;

function WaitForBitwigClosed: Boolean;
begin
  Result := True;
  while IsBitwigRunning do
  begin
    if MsgBox(CustomMessage('BitwigRunning'), mbError, MB_RETRYCANCEL) = IDCANCEL then
    begin
      Result := False;
      Exit;
    end;
  end;
end;

function BitwigExe(Param: String): String;
begin
  Result := ExpandConstant('{commonpf64}\Bitwig Studio\Bitwig Studio.exe');
end;

function BitwigExeExists: Boolean;
begin
  Result := FileExists(BitwigExe(''));
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  Dir: String;
begin
  Result := True;
  if CurPageID = wpSelectDir then
  begin
    Dir := AddBackslash(WizardDirValue);
    if not (DirExists(Dir + 'Extensions') or DirExists(Dir + 'Library')) then
      Result := MsgBox(CustomMessage('NoBitwigFolder'), mbConfirmation, MB_YESNO) = IDYES;
  end
  else if CurPageID = wpReady then
    Result := WaitForBitwigClosed;
end;

procedure CurPageChanged(CurPageID: Integer);
begin
  if CurPageID = wpFinished then
    WizardForm.FinishedLabel.Caption := WizardForm.FinishedLabel.Caption + #13#10#13#10 + CustomMessage('SetupHint');
end;

function InitializeUninstall: Boolean;
begin
  Result := WaitForBitwigClosed;
end;
