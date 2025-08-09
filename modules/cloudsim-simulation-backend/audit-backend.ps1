# Backend Audit & Testing Script for CloudSim Backend
# Run this script to perform comprehensive backend auditing

Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "    Backend Software Audit & Testing Suite     " -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan

$projectPath = "C:\Users\Kier\OneDrive\Documents\Research_2025\backend_thesis\cloudsim-7.0\modules\cloudsim-simulation-backend"
Set-Location $projectPath

# 1. Clean and Compile
Write-Host "`n[1] Cleaning and compiling project..." -ForegroundColor Yellow
mvn clean compile

# 2. Run Unit Tests
Write-Host "`n[2] Running unit tests..." -ForegroundColor Yellow
mvn test

# 3. Generate Test Coverage Report
Write-Host "`n[3] Generating test coverage report with JaCoCo..." -ForegroundColor Yellow
mvn jacoco:prepare-agent test jacoco:report

# 4. Static Code Analysis with SpotBugs
Write-Host "`n[4] Running SpotBugs static analysis..." -ForegroundColor Yellow
mvn spotbugs:check

# 5. Check Dependencies for Vulnerabilities
Write-Host "`n[5] Checking dependencies for vulnerabilities..." -ForegroundColor Yellow
mvn dependency-check:check

# 6. Code Quality with PMD
Write-Host "`n[6] Running PMD code quality analysis..." -ForegroundColor Yellow
mvn pmd:check

# 7. Check for Outdated Dependencies
Write-Host "`n[7] Checking for outdated dependencies..." -ForegroundColor Yellow
mvn versions:display-dependency-updates

# 8. Generate Project Documentation
Write-Host "`n[8] Generating Javadoc..." -ForegroundColor Yellow
mvn javadoc:javadoc

# 9. Package Application
Write-Host "`n[9] Building JAR package..." -ForegroundColor Yellow
mvn package -DskipTests

# 10. Check JAR Size
Write-Host "`n[10] Analyzing JAR size..." -ForegroundColor Yellow
$jarPath = ".\target"
if (Test-Path $jarPath) {
    $jarFiles = Get-ChildItem $jarPath -Filter "*.jar"
    foreach ($jar in $jarFiles) {
        $size = [math]::Round($jar.Length / 1MB, 2)
        Write-Host "$($jar.Name): $size MB" -ForegroundColor Green
    }
}

Write-Host "`n===============================================" -ForegroundColor Green
Write-Host "    Backend Audit Complete! Check results.     " -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Green

# Generate HTML report summary
Write-Host "`nReports generated in:" -ForegroundColor Cyan
Write-Host "  - Test Coverage: target/site/jacoco/index.html" -ForegroundColor White
Write-Host "  - SpotBugs: target/spotbugsXml.xml" -ForegroundColor White
Write-Host "  - PMD: target/pmd.xml" -ForegroundColor White
Write-Host "  - Javadoc: target/site/apidocs/index.html" -ForegroundColor White
