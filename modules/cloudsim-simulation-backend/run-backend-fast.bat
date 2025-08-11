@echo off
echo =========================================
echo CloudSim Backend - Fast Plot Mode
echo =========================================
echo.

:: Check if running from the backend directory
if not exist "pom.xml" (
    echo Please run this script from the cloudsim-simulation-backend directory
    exit /b 1
)

echo Select plot generation mode:
echo 1. Fast (Low quality, 2x faster)
echo 2. Optimized (No file I/O, 3x faster)
echo 3. Minimal (Data only, 10x faster)
echo 4. Disabled (No plots, instant)
echo 5. Normal (Full quality)
echo.

set /p mode="Enter choice (1-5): "

if "%mode%"=="1" (
    set PLOT_MODE=fast
    echo Using FAST mode - Lower quality plots, faster generation
) else if "%mode%"=="2" (
    set PLOT_MODE=optimized
    echo Using OPTIMIZED mode - No file I/O, data only
) else if "%mode%"=="3" (
    set PLOT_MODE=minimal
    echo Using MINIMAL mode - Only essential data
) else if "%mode%"=="4" (
    set PLOT_MODE=disabled
    echo Using DISABLED mode - No plot generation
) else if "%mode%"=="5" (
    set PLOT_MODE=normal
    echo Using NORMAL mode - Full quality plots
) else (
    echo Invalid choice, using FAST mode as default
    set PLOT_MODE=fast
)

echo.
echo Starting backend with plot mode: %PLOT_MODE%
echo.

:: Set Java options for better performance
set JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseG1GC

:: Set MATLAB library path
set MATLAB_PATH=C:\Program Files\MATLAB\R2025a\bin\win64;C:\Program Files\MATLAB\R2025a\extern\engines\java\win64

:: Run with the selected mode
java %JAVA_OPTS% ^
  -Djava.library.path="%MATLAB_PATH%" ^
  -Dmatlab.plot.mode=%PLOT_MODE% ^
  -Dmatlab.plot.enabled=true ^
  -Dserver.port=8081 ^
  -jar target\cloudsim-simulation-backend-1.0.0-SNAPSHOT.jar

pause
