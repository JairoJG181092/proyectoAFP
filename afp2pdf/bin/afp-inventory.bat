@echo off
set DIR=%~dp0..
java -Xmx512m -cp "%DIR%\build" mx.afp2pdf.reader.AfpInventory %*
