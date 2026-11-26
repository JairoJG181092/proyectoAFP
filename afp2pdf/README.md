# afp2pdf (Java 8, sin dependencias)

    sh build.sh                                    # genera bin/afp2pdf.jar
    sh bin/afp2pdf.sh inventario "archivo.afp"
    sh bin/afp2pdf.sh convertir "archivo.afp" salida.pdf [--omitir-blancas]

Windows: `bin\afp2pdf.bat` (mismos argumentos). Memoria: variable `AFP2PDF_XMX` (def. 1g).

Codigos de salida: 0 correcto, 1 error, 2 uso, 3 terminado con avisos.

## Soportado
Texto PTOCA, fuentes raster FOCA, code pages de un byte, graficos GOCA (lineas y areas),
imagenes IOCA JPEG incluidas con IOB o en pagina.

## No soportado
Overlays, page segments (IPS), BCOCA, fuentes outline, doble byte, otras compresiones de imagen.

## Pendiente (fase 4)
Varios archivos en paralelo, division del PDF en partes y reanudacion.
