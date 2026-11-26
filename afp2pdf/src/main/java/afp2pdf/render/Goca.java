package afp2pdf.render;

import afp2pdf.reader.Sf;
import afp2pdf.resources.ObjectEnv;

/** Gráficos GOCA: lineas y areas. El eje Y de GOCA crece hacia arriba. */
final class Goca {
    private static final int NO_FILL = 0x0F, INVISIBLE = 0x08;

    private final StringBuilder out;
    private final Report report;
    private double left, top, sx, sy;
    private int curX, curY;
    private String fill = "0 g", stroke = "0 G";
    private int pattern, lineType;
    private double lineWidth = 0.5;
    private boolean area, boundary, open;
    private final StringBuilder path = new StringBuilder();

    private Goca(StringBuilder out, Report report) {
        this.out = out;
        this.report = report;
    }

    /** Agrega a out los operadores en coordenadas locales del area (v hacia abajo). */
    static boolean draw(byte[] gdd, byte[] gad, ObjectEnv env, StringBuilder out, Report report) {
        Goca g = new Goca(out, report);
        if (!g.window(gdd, env)) {
            report.warn("Grafico sin ventana (GDD) reconocible");
            return false;
        }
        int before = out.length();
        g.run(gad);
        return out.length() > before;
    }

    private boolean window(byte[] d, ObjectEnv env) {
        for (int i = 0; i + 2 <= d.length; i += 2 + Sf.u8(d, i + 1)) {
            if (Sf.u8(d, i) != 0xF6 || i + 20 > d.length) continue;
            int p = i + 2;
            double ux = 720.0 / Sf.u16(d, p + 4), uy = 720.0 / Sf.u16(d, p + 6);
            int xl = Sf.s16(d, p + 10), xr = Sf.s16(d, p + 12);
            int yb = Sf.s16(d, p + 14), yt = Sf.s16(d, p + 16);
            left = xl;
            top = yt;
            sx = ux;
            sy = uy;
            if (env.mapping == 0x10 && env.width > 0 && xr > xl && yt > yb) {
                double k = Math.min(env.width / ((xr - xl) * ux), env.height / ((yt - yb) * uy));
                sx *= k;
                sy *= k;
            }
            return true;
        }
        return false;
    }

    private void run(byte[] d) {
        int i = 0;
        while (i < d.length) {
            int op = Sf.u8(d, i);
            if (op == 0x00) { i++; continue; }
            if (op == 0xFE) {
                report.warn("Orden GOCA extendida no soportada");
                if (i + 4 > d.length) break;
                i += 4 + Sf.u16(d, i + 2);
                continue;
            }
            if (i + 2 > d.length) break;
            if (op < 0x80 && (op & 0x08) != 0 && op != 0x70) {        // orden de 2 bytes
                shortOrder(op, Sf.u8(d, i + 1));
                i += 2;
                continue;
            }
            int len = Sf.u8(d, i + 1), p = i + 2;
            if (p + len > d.length) { report.warn("Grafico truncado"); break; }
            switch (op) {
                case 0x70: case 0x01: case 0x71: break;               // segmento, comentario
                case 0x60: endArea(); break;
                case 0x21: curX = Sf.s16(d, p); curY = Sf.s16(d, p + 2); open = false; break;
                case 0x81: line(d, p, len); break;
                case 0xC1:
                    curX = Sf.s16(d, p); curY = Sf.s16(d, p + 2); open = false;
                    line(d, p + 4, len - 4);
                    break;
                case 0xB2: color(d, p, len); break;
                case 0x11: if (len >= 2) lineWidth = 0.5 * Sf.u16(d, p) / 256.0; break;
                default: report.warn(String.format("Orden GOCA X'%02X' no soportada", op));
            }
            i = p + len;
        }
        endArea();
    }

    private void shortOrder(int op, int value) {
        switch (op) {
            case 0x68: area = true; boundary = (value & 0x40) != 0; path.setLength(0); open = false; break;
            case 0x28: pattern = value;
                if (value != 0 && value != 0x10 && value != NO_FILL)
                    report.warn("Patron de relleno aproximado como solido");
                break;
            case 0x18: lineType = value;
                if (value > 0 && value < 7) report.warn("Linea punteada dibujada continua");
                break;
            case 0x19: lineWidth = 0.5 * Math.max(1, value); break;
            case 0x08: break;                                          // juego de patrones
            default: report.warn(String.format("Orden GOCA X'%02X' no soportada", op));
        }
    }

    private void line(byte[] d, int from, int len) {
        if (len < 4) return;
        StringBuilder target = area ? path : new StringBuilder();
        if (!area || !open) {
            if (area && path.length() > 0) path.append("h ");
            point(target, curX, curY).append("m ");
            open = true;
        }
        for (int p = from; p + 4 <= from + len; p += 4) {
            curX = Sf.s16(d, p);
            curY = Sf.s16(d, p + 2);
            point(target, curX, curY).append("l ");
        }
        if (!area && lineType != INVISIBLE)
            out.append(stroke).append(' ').append(afp2pdf.pdf.Num.of(lineWidth)).append(" w ")
                    .append(target).append("S\n");
    }

    private StringBuilder point(StringBuilder s, int x, int y) {
        return s.append(afp2pdf.pdf.Num.of((x - left) * sx)).append(' ')
                .append(afp2pdf.pdf.Num.of((top - y) * sy)).append(' ');
    }

    /** Las figuras de un area se rellenan en modo alterno (par-impar): conserva los huecos. */
    private void endArea() {
        if (!area) return;
        area = open = false;
        if (path.length() == 0) return;
        boolean filled = pattern != NO_FILL, stroked = boundary && lineType != INVISIBLE;
        if (!filled && !stroked) return;
        out.append(fill).append(' ').append(stroke).append(' ')
                .append(afp2pdf.pdf.Num.of(lineWidth)).append(" w ").append(path)
                .append(filled ? stroked ? "h B*\n" : "h f*\n" : "h S\n");
    }

    private void color(byte[] d, int p, int len) {
        String[] c = Colors.process(d, p, len);
        if (c == null) report.warn("Color GOCA no soportado");
        else { fill = c[0]; stroke = c[1]; }
    }
}
