@echo off
echo ==========================================
echo Starting MATLAB Engine for Thesis System
echo ==========================================
echo.

REM Start MATLAB with optimized settings for plot generation
echo Launching MATLAB with performance optimizations...
start "MATLAB Engine" matlab -nosplash -r "try, matlab.engine.shareEngine('thesisEngine'); set(0,'DefaultFigureVisible','off'); set(0,'DefaultFigureRenderer','painters'); fprintf('\n=== MATLAB Engine Ready ===\n'); fprintf('Engine: thesisEngine\n'); fprintf('Status: Running\n'); fprintf('Keep this window open!\n'); fprintf('===========================\n'); while true, pause(10); end; catch ME, disp(ME.message); end"

echo.
echo Waiting for MATLAB to initialize (15 seconds)...
timeout /t 15 /nobreak > nul

echo.
echo ==========================================
echo MATLAB Engine should now be running
echo.
echo Next steps:
echo 1. Keep the MATLAB window open
echo 2. Run Spring Boot: mvn spring-boot:run
echo ==========================================
echo.
pause
