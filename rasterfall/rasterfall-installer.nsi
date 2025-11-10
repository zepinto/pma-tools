; ========================================================================
; NSIS Installer Script for Rasterfall Application
; ========================================================================
; This script creates a professional Windows installer for the Java-based
; Rasterfall sidescan sonar viewer application.
;
; Requirements:
; - NSIS 3.x or higher (https://nsis.sourceforge.io/)
; - Built shadow JAR at: rasterfall/build/libs/rasterfall-all.jar
;
; To build the installer:
;   makensis rasterfall-installer.nsi
; ========================================================================

!define PRODUCT_NAME "Rasterfall"
!define PRODUCT_VERSION "2025.11.00"
!define PRODUCT_PUBLISHER "OceanScan Marine Systems and Technology (OMST)"
!define PRODUCT_WEB_SITE "https://www.oceanscan-mst.com/"
!define PRODUCT_DIR_REGKEY "Software\Microsoft\Windows\CurrentVersion\App Paths\Rasterfall.exe"
!define PRODUCT_UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}"
!define PRODUCT_UNINST_ROOT_KEY "HKLM"

; ========================================================================
; General Settings
; ========================================================================

; Application name and output file
Name "${PRODUCT_NAME} ${PRODUCT_VERSION}"
OutFile "RasterfallSetup-${PRODUCT_VERSION}.exe"

; Default installation folder
InstallDir "$PROGRAMFILES64\OMST\Rasterfall"

; Get installation folder from registry if available
InstallDirRegKey HKLM "${PRODUCT_DIR_REGKEY}" ""

; Request application privileges for Windows Vista and later
RequestExecutionLevel admin

; ========================================================================
; Modern UI Configuration
; ========================================================================

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"

; MUI Settings
!define MUI_ABORTWARNING
!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\modern-install.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\modern-uninstall.ico"

; Welcome page
!insertmacro MUI_PAGE_WELCOME

; License page (installer license, not application license)
; !insertmacro MUI_PAGE_LICENSE "path\to\installer_license.txt"

; Components page (if needed in future)
; !insertmacro MUI_PAGE_COMPONENTS

; Directory page
!insertmacro MUI_PAGE_DIRECTORY

; Custom page for license file selection
Page custom LicenseFilePage LicenseFilePageLeave

; Instfiles page
!insertmacro MUI_PAGE_INSTFILES

; Finish page
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_TEXT "Launch Rasterfall"
!define MUI_FINISHPAGE_RUN_FUNCTION "LaunchRasterfall"
!insertmacro MUI_PAGE_FINISH

; Uninstaller pages
!insertmacro MUI_UNPAGE_INSTFILES

; Language files
!insertmacro MUI_LANGUAGE "English"

; ========================================================================
; Variables
; ========================================================================

Var LicenseFilePath
Var LicenseFileName
Var JavaFound
Var JavaVersion

; ========================================================================
; Functions
; ========================================================================

; Function to check if Java is installed
Function CheckJava
  StrCpy $JavaFound "0"
  
  ; Try to find Java in PATH
  nsExec::ExecToStack 'java -version'
  Pop $0 ; Return value
  Pop $1 ; Output
  
  ${If} $0 == 0
    StrCpy $JavaFound "1"
    ; Parse Java version from output
    StrCpy $JavaVersion $1
  ${EndIf}
  
  ; If not found in PATH, check registry for Java installations
  ${If} $JavaFound == "0"
    ClearErrors
    ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"
    ${If} ${Errors}
      ClearErrors
      ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\JRE" "CurrentVersion"
    ${EndIf}
    
    ${If} ${Errors}
      ClearErrors
      ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Development Kit" "CurrentVersion"
    ${EndIf}
    
    ${If} ${Errors}
      ClearErrors
      ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\JDK" "CurrentVersion"
    ${EndIf}
    
    ${IfNot} ${Errors}
      StrCpy $JavaFound "1"
      StrCpy $JavaVersion $2
    ${EndIf}
  ${EndIf}
  
  ; Show warning if Java not found
  ${If} $JavaFound == "0"
    MessageBox MB_OKCANCEL|MB_ICONEXCLAMATION \
      "Java Runtime Environment (JRE 21 or higher) was not detected on your system.$\r$\n$\r$\n\
      Rasterfall requires Java to run.$\r$\n$\r$\n\
      Please install Java JRE 21 or higher from:$\r$\n\
      https://adoptium.net/$\r$\n$\r$\n\
      Do you want to continue with the installation?" \
      IDOK continueInstall
    Abort
    continueInstall:
  ${Else}
    ; Inform user Java was found
    MessageBox MB_OK|MB_ICONINFORMATION \
      "Java installation detected.$\r$\n$\r$\n\
      Please ensure you have Java JRE 21 or higher installed.$\r$\n\
      Current Java version info: $JavaVersion"
  ${EndIf}
FunctionEnd

; Custom page for license file selection
Function LicenseFilePage
  !insertmacro MUI_HEADER_TEXT "Select License File" "Choose your Rasterfall license file"
  
  nsDialogs::Create 1018
  Pop $0
  
  ${If} $0 == error
    Abort
  ${EndIf}
  
  ${NSD_CreateLabel} 0 0 100% 24u "Please select your Rasterfall license file (.lic). This file will be installed in the application's configuration directory."
  Pop $0
  
  ${NSD_CreateLabel} 0 30u 25% 12u "License File:"
  Pop $0
  
  ${NSD_CreateText} 27% 28u 58% 14u "$LicenseFilePath"
  Pop $1
  
  ${NSD_CreateButton} 86% 28u 14% 14u "Browse..."
  Pop $2
  ${NSD_OnClick} $2 BrowseLicenseFile
  
  nsDialogs::Show
FunctionEnd

; Browse button click handler
Function BrowseLicenseFile
  nsDialogs::SelectFileDialog open "" "License Files (*.lic)|*.lic|All Files (*.*)|*.*"
  Pop $3
  
  ${If} $3 != ""
    StrCpy $LicenseFilePath $3
    ; Extract filename from full path
    ${GetFileName} $LicenseFilePath $LicenseFileName
    SendMessage $1 ${WM_SETTEXT} 0 "STR:$LicenseFilePath"
  ${EndIf}
FunctionEnd

; Validate license file selection
Function LicenseFilePageLeave
  ; License file is optional - user can add it later
  ${If} $LicenseFilePath != ""
    IfFileExists "$LicenseFilePath" +3 0
    MessageBox MB_OK|MB_ICONEXCLAMATION "The selected license file does not exist. Please select a valid file or leave it empty to configure later."
    Abort
  ${EndIf}
FunctionEnd

; Launch Rasterfall after installation
Function LaunchRasterfall
  ExecShell "" "javaw" '-jar "$INSTDIR\rasterfall-all.jar"' SW_SHOWNORMAL
FunctionEnd

; ========================================================================
; Installer Sections
; ========================================================================

Section "Rasterfall Application" SEC01
  SectionIn RO  ; This section is required
  
  ; Check for Java installation
  Call CheckJava
  
  ; Set output path to the installation directory
  SetOutPath "$INSTDIR"
  SetOverwrite ifnewer
  
  ; Copy the shadow JAR file
  File "build\libs\rasterfall-all.jar"
  
  ; Copy license file if selected
  ${If} $LicenseFilePath != ""
    CreateDirectory "$INSTDIR\conf"
    CreateDirectory "$INSTDIR\conf\licenses"
    CopyFiles "$LicenseFilePath" "$INSTDIR\conf\licenses\$LicenseFileName"
  ${Else}
    ; Create empty directories for future license files
    CreateDirectory "$INSTDIR\conf"
    CreateDirectory "$INSTDIR\conf\licenses"
  ${EndIf}
  
  ; Create batch file to launch Rasterfall
  FileOpen $4 "$INSTDIR\Rasterfall.bat" w
  FileWrite $4 '@echo off$\r$\n'
  FileWrite $4 'cd /d "%~dp0"$\r$\n'
  FileWrite $4 'javaw -jar "rasterfall-all.jar"$\r$\n'
  FileClose $4
  
  ; Create desktop shortcut
  CreateShortCut "$DESKTOP\Rasterfall.lnk" "$INSTDIR\Rasterfall.bat" "" "$INSTDIR\rasterfall-all.jar" 0
  
  ; Create Start Menu shortcuts
  CreateDirectory "$SMPROGRAMS\OMST"
  CreateShortCut "$SMPROGRAMS\OMST\Rasterfall.lnk" "$INSTDIR\Rasterfall.bat" "" "$INSTDIR\rasterfall-all.jar" 0
  CreateShortCut "$SMPROGRAMS\OMST\Uninstall Rasterfall.lnk" "$INSTDIR\uninst.exe"
SectionEnd

Section -AdditionalIcons
  SetOutPath $INSTDIR
  WriteIniStr "$INSTDIR\${PRODUCT_NAME}.url" "InternetShortcut" "URL" "${PRODUCT_WEB_SITE}"
  CreateShortCut "$SMPROGRAMS\OMST\Website.lnk" "$INSTDIR\${PRODUCT_NAME}.url"
SectionEnd

Section -Post
  ; Write uninstaller
  WriteUninstaller "$INSTDIR\uninst.exe"
  
  ; Write registry keys for uninstaller
  WriteRegStr HKLM "${PRODUCT_DIR_REGKEY}" "" "$INSTDIR\Rasterfall.bat"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "DisplayName" "$(^Name)"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "UninstallString" "$INSTDIR\uninst.exe"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "DisplayIcon" "$INSTDIR\rasterfall-all.jar"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "DisplayVersion" "${PRODUCT_VERSION}"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "URLInfoAbout" "${PRODUCT_WEB_SITE}"
  WriteRegStr ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "Publisher" "${PRODUCT_PUBLISHER}"
  
  ; Get installed size
  ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
  IntFmt $0 "0x%08X" $0
  WriteRegDWORD ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}" "EstimatedSize" "$0"
SectionEnd

; ========================================================================
; Uninstaller Section
; ========================================================================

Function un.onUninstSuccess
  HideWindow
  MessageBox MB_ICONINFORMATION|MB_OK "$(^Name) was successfully removed from your computer."
FunctionEnd

Function un.onInit
  MessageBox MB_ICONQUESTION|MB_YESNO|MB_DEFBUTTON2 "Are you sure you want to completely remove $(^Name) and all of its components?" IDYES +2
  Abort
FunctionEnd

Section Uninstall
  ; Remove files and directories
  Delete "$INSTDIR\rasterfall-all.jar"
  Delete "$INSTDIR\Rasterfall.bat"
  Delete "$INSTDIR\${PRODUCT_NAME}.url"
  Delete "$INSTDIR\uninst.exe"
  
  ; Remove license files
  RMDir /r "$INSTDIR\conf\licenses"
  RMDir "$INSTDIR\conf"
  
  ; Remove shortcuts
  Delete "$DESKTOP\Rasterfall.lnk"
  Delete "$SMPROGRAMS\OMST\Rasterfall.lnk"
  Delete "$SMPROGRAMS\OMST\Uninstall Rasterfall.lnk"
  Delete "$SMPROGRAMS\OMST\Website.lnk"
  RMDir "$SMPROGRAMS\OMST"
  
  ; Remove installation directory
  RMDir "$INSTDIR"
  
  ; Remove registry keys
  DeleteRegKey ${PRODUCT_UNINST_ROOT_KEY} "${PRODUCT_UNINST_KEY}"
  DeleteRegKey HKLM "${PRODUCT_DIR_REGKEY}"
  
  SetAutoClose true
SectionEnd
