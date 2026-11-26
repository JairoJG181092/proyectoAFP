@echo off
setlocal
set "JAR=%~dp0afp2pdf.jar"
set "JAVA=java"
if defined JAVA_HOME set "JAVA=%JAVA_HOME%\bin\java"
if not defined AFP2PDF_XMX set "AFP2PDF_XMX=1g"
"%JAVA%" -Xmx%AFP2PDF_XMX% -jar "%JAR%" %*
exit /b %ERRORLEVEL%
