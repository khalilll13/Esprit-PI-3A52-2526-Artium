@echo off
setlocal
set "ROOT=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%setup-smtp.ps1" %*
exit /b %ERRORLEVEL%

