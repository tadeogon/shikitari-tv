@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
REM Deshace los cambios hechos por Claude en el Xiaomi Stick.

set "ADB=%~dp0..\platform-tools-latest-windows\platform-tools\adb.exe"
set /p IP=IP del Xiaomi Stick (Enter para 192.168.100.84):
if "%IP%"=="" set IP=192.168.100.84
set SERIAL=%IP%:5555

"%ADB%" connect %SERIAL%
"%ADB%" -s %SERIAL% get-state >nul 2>&1
if errorlevel 1 (
  echo No se pudo conectar a %SERIAL%. Revisa la IP y la Depuracion USB.
  pause
  exit /b 1
)

echo.
echo == Reactivando apps desactivadas ==
if exist desactivados_xiaomi.txt (
  for /f "usebackq tokens=* delims=" %%P in ("desactivados_xiaomi.txt") do (
    echo %%P
    "%ADB%" -s %SERIAL% shell pm enable %%P
  )
) else echo (no hay desactivados_xiaomi.txt)

echo.
echo == Restaurando animaciones ==
for %%K in (window_animation_scale transition_animation_scale animator_duration_scale) do (
  "%ADB%" -s %SERIAL% shell settings put global %%K 1.0
)

"%ADB%" -s %SERIAL% shell device_config delete activity_manager max_cached_processes

echo.
echo Listo. Reinicia el Xiaomi Stick para terminar.
echo Magis TV, Kodi y Spotify no vuelven con esto: Magis esta en apk_respaldo, Kodi y Spotify en la Play Store.
pause
