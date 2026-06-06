@echo off
setlocal
set "ROOT=%~dp0"

if not exist "%ROOT%.env" (
	echo Le fichier .env est manquant.
	echo Lancement de la configuration SMTP...
	call "%ROOT%setup-smtp.bat"
	if errorlevel 1 exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
	echo Maven introuvable dans le PATH.
	echo Ouvrez le projet dans IntelliJ ou installez Maven pour utiliser ce script.
	exit /b 1
)

cd /d "%ROOT%"
mvn -q javafx:run
exit /b %ERRORLEVEL%

