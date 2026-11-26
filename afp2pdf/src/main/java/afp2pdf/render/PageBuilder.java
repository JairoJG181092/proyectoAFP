package afp2pdf.render;

import afp2pdf.pdf.Num;
import afp2pdf.pdf.PdfWriter;
import afp2pdf.pdf.Type3Font;
import afp2pdf.reader.Sf;
import afp2pdf.resources.FontRef;
import afp2pdf.resources.ObjectEnv;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Estado de una pagina y traduccion de PTOCA a operadores PDF. */
final class PageBuilder implements Ptoca.Handler {

    interface Fonts {
        Type3Font get(FontRef ref) throws IOException;
    }

    private final Fonts fonts;
    private final Report report;
    private final StringBuilder content = new StringBuilder();
    private final Map<String, Integer> images = new LinkedHashMap<>();
    private boolean marked;                                   // la pagina tiene contenido
    private final Map<Type3Font, String> fontNames = new HashMap<>();
    private final Map<Integer, FontRef> localFonts = new HashMap<>();

    private double width = 612, height = 792;                // carta, en puntos
    private double xScale = 0.5, yScale = 0.5;               // unidades de texto -> puntos
    // estado PTOCA (puntos)
    private double i, b, inlineMargin, baselineStep;
    private int orientation;
    private Type3Font font;
    private String color = "0 g 0 G";

    PageBuilder(Fonts fonts, Report report) {
        this.fonts = fonts;
        this.report = report;
    }

    boolean isBlank() { return !marked; }

    /** Unidad de la pagina en puntos. */
    double unit = 0.05;

    void pageDescriptor(byte[] d) {
        unit = 720.0 / Sf.u16(d, 2);
        width = Sf.u24(d, 6) * 720.0 / Sf.u16(d, 2);
        height = Sf.u24(d, 9) * 720.0 / Sf.u16(d, 4);
    }

    void textDescriptor(byte[] d) {
        xScale = 720.0 / Sf.u16(d, 2);
        yScale = 720.0 / Sf.u16(d, 4);
    }

    void mapFonts(byte[] mcf) {
        localFonts.putAll(FontRef.parseMcf(mcf));
    }

    void text(byte[] ptoca) throws IOException {
        i = b = inlineMargin = baselineStep = 0;
        orientation = 0;
        font = null;
        color = "0 g 0 G";
        Ptoca.parse(ptoca, this);
    }

    @Override
    public void control(int function, byte[] p) throws IOException {
        boolean horizontal = orientation == 0 || orientation == 180;
        double is = horizontal ? xScale : yScale, bs = horizontal ? yScale : xScale;
        switch (function) {
            case Ptoca.AMI: i = Sf.s16(p, 0) * is; break;
            case Ptoca.AMB: b = Sf.s16(p, 0) * bs; break;
            case Ptoca.RMI: i += Sf.s16(p, 0) * is; break;
            case Ptoca.RMB: b += Sf.s16(p, 0) * bs; break;
            case Ptoca.SIM: inlineMargin = Sf.s16(p, 0) * is; break;
            case Ptoca.SBI: baselineStep = Sf.s16(p, 0) * bs; break;
            case Ptoca.BLN: i = inlineMargin; b += baselineStep; break;
            case Ptoca.STO: orient(Sf.u16(p, 0) >> 7, Sf.u16(p, 2) >> 7); break;
            case Ptoca.SCFL: select(Sf.u8(p, 0)); break;
            case Ptoca.SEC: extendedColor(p); break;
            case Ptoca.TRN: show(p); break;
            case Ptoca.DIR: rule(p, true, is, bs); break;
            case Ptoca.DBR: rule(p, false, is, bs); break;
            case Ptoca.NOP: case Ptoca.BSU: case Ptoca.ESU: break;
            default: report.warn(String.format("Funcion PTOCA X'%02X' no soportada", function));
        }
    }

    private void orient(int inline, int baseline) {
        if (baseline != (inline + 90) % 360 || inline % 90 != 0)
            report.warn("Orientacion de texto no soportada: " + inline + "/" + baseline);
        else orientation = inline;
    }

    private void select(int localId) throws IOException {
        FontRef ref = localFonts.get(localId);
        font = ref == null ? null : fonts.get(ref);
        if (font == null) report.warn("Fuente local " + localId + " no disponible");
        else if (ref.rotation != 0) report.warn("Rotacion de caracteres no soportada");
    }

    private void extendedColor(byte[] p) {
        String[] c = Colors.process(p, 0, p.length);
        if (c == null) report.warn("Color de texto no soportado");
        else color = c[0] + " " + c[1];
    }

    /** Coordenadas (i, b) de la orientacion actual -> punto PDF. */
    private double[] point(double ip, double bp) {
        switch (orientation) {
            case 90:  return new double[] {width - bp, height - ip};
            case 180: return new double[] {width - ip, bp};
            case 270: return new double[] {bp, ip};
            default:  return new double[] {ip, height - bp};
        }
    }

    private void show(byte[] codes) {
        if (font == null) {
            report.warn("Texto sin fuente seleccionada");
            return;
        }
        if (codes.length == 0) return;
        String name = fontNames.get(font);
        if (name == null) {
            name = "F" + (fontNames.size() + 1);
            fontNames.put(font, name);
        }
        int[][] matrix = {{1, 0, 0, 1}, {0, -1, 1, 0}, {-1, 0, 0, -1}, {0, 1, -1, 0}};
        int[] m = matrix[orientation / 90];
        double[] at = point(i, b);
        content.append(color).append(" BT /").append(name).append(' ')
                .append(Num.of(font.sizePoints)).append(" Tf ")
                .append(m[0]).append(' ').append(m[1]).append(' ')
                .append(m[2]).append(' ').append(m[3]).append(' ')
                .append(Num.of(at[0])).append(' ').append(Num.of(at[1])).append(" Tm <");
        for (byte code : codes) {
            int c = code & 0xFF;
            content.append(Character.forDigit(c >> 4, 16)).append(Character.forDigit(c & 15, 16));
            if (!font.defined[c]) report.warn("Caracter sin glifo en la fuente");
            marked |= font.visible[c];
            i += font.advancePoints[c];
        }
        content.append("> Tj ET\n");
    }

    private void rule(byte[] p, boolean inline, double is, double bs) {
        double length = Sf.s16(p, 0) * (inline ? is : bs);
        double thick = 0;
        if (p.length >= 5)   // 2 bytes enteros + 1 fraccion
            thick = (Sf.s16(p, 2) + Sf.u8(p, 4) / 256.0) * (inline ? bs : is);
        if (thick == 0) thick = 0.3;
        double[] from = point(i, b);
        double[] to = inline ? point(i + length, b + thick) : point(i + thick, b + length);
        marked = true;
        content.append(color).append(' ')
                .append(Num.of(Math.min(from[0], to[0]))).append(' ')
                .append(Num.of(Math.min(from[1], to[1]))).append(' ')
                .append(Num.of(Math.abs(to[0] - from[0]))).append(' ')
                .append(Num.of(Math.abs(to[1] - from[1]))).append(" re f\n");
    }

    /** Imagen en el area indicada; x, y en puntos desde arriba. */
    void image(int objectId, double x, double y, int degrees, double w, double h) {
        String name = "Im" + objectId;
        images.put(name, objectId);
        begin(x, y, degrees);
        content.append(Num.of(w)).append(" 0 0 ").append(Num.of(-h)).append(" 0 ")
                .append(Num.of(h)).append(" cm /").append(name).append(" Do Q\n");
        marked = true;
    }

    void graphics(byte[] gdd, byte[] gad, ObjectEnv env) {
        int start = content.length();
        begin(env.x, env.y, env.orientation);
        if (env.width > 0 && env.height > 0)
            content.append("0 0 ").append(Num.of(env.width)).append(' ')
                    .append(Num.of(env.height)).append(" re W n\n");
        if (Goca.draw(gdd, gad, env, content, report)) {
            content.append("Q\n");
            marked = true;
        } else {
            content.setLength(start);
        }
    }

    /** Abre un sistema local: origen en (x, y), u a la derecha y v hacia abajo, rotado. */
    private void begin(double x, double y, int degrees) {
        String[] m = {"1 0 0 -1", "0 -1 -1 0", "-1 0 0 1", "0 1 1 0"};
        content.append("q ").append(m[(degrees / 90) & 3]).append(' ')
                .append(Num.of(x)).append(' ').append(Num.of(height - y)).append(" cm\n");
    }

    void finish(PdfWriter pdf) throws IOException {
        StringBuilder res = new StringBuilder("/Font << ");
        for (Map.Entry<Type3Font, String> e : fontNames.entrySet())
            res.append('/').append(e.getValue()).append(' ').append(e.getKey().objectId).append(" 0 R ");
        res.append(">> /XObject << ");
        for (Map.Entry<String, Integer> e : images.entrySet())
            res.append('/').append(e.getKey()).append(' ').append(e.getValue()).append(" 0 R ");
        res.append(">>");
        pdf.page(width, height, res.toString(),
                content.toString().getBytes(StandardCharsets.US_ASCII));
    }
}
