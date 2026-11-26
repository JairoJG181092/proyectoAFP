package afp2pdf.resources;

import afp2pdf.reader.Sf;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Juego de caracteres FOCA raster (orientacion 0). */
public final class FontCharSet {
    public final String name;
    public int pelsPer10Inches;
    public double emPels, spaceIncrement;
    public final Map<String, Glyph> glyphs = new LinkedHashMap<>();

    private byte[] fnc, fnd, fno, fni;
    private final ByteArrayOutputStream fnm = new ByteArrayOutputStream();
    private final ByteArrayOutputStream fng = new ByteArrayOutputStream();

    FontCharSet(String name) { this.name = name; }

    void add(Sf f) {
        switch (f.id) {
            case Sf.FNC: fnc = f.data; break;
            case Sf.FND: fnd = f.data; break;
            case Sf.FNO: if (fno == null) fno = f.data; break;
            case Sf.FNI: if (fni == null) fni = f.data; break;   // primera = 0 grados
            case Sf.FNM: fnm.write(f.data, 0, f.data.length); break;
            case Sf.FNG: fng.write(f.data, 0, f.data.length); break;
            default: break;
        }
    }

    void build() throws IOException {
        if (fnc == null || fnc.length < 26 || fni == null || fno == null) fail("incompleta");
        if (Sf.u8(fnc, 1) != 0x05) fail("no es raster (tecnologia " + Sf.u8(fnc, 1) + ")");
        int unitBase = Sf.u8(fnc, 4);
        int metricUnits = Sf.u16(fnc, 6);
        pelsPer10Inches = Sf.u16(fnc, 24);
        if (metricUnits == 0 || pelsPer10Inches == 0) fail("sin resolucion");
        int tenths = fnd != null && fnd.length >= 38 ? Sf.u16(fnd, 36) : 0;
        emPels = tenths / 10.0 * pelsPer10Inches / 720.0;
        // metricas -> pels: relativas (unidades por em) o fijas (unidades por 10 pulgadas)
        double k;
        if (unitBase == 0x02) {
            if (tenths == 0) fail("sin tamano nominal");
            k = emPels / metricUnits;
        } else if (unitBase == 0x00) {
            k = (double) pelsPer10Inches / metricUnits;
        } else {
            fail("unidad de metricas " + unitBase + " no soportada");
            return;
        }
        int align = 1 << Sf.u8(fnc, 16);                          // 0=1, 2=4, 3=8 bytes
        int fniLen = Sf.u8(fnc, 15), fnmLen = Sf.u8(fnc, 21);
        spaceIncrement = Sf.u16(fno, 8) * k;
        byte[] maps = fnm.toByteArray(), patterns = fng.toByteArray();
        int maxHeight = 1;
        for (int i = 0; i + fniLen <= fni.length; i += fniLen) {
            Glyph g = new Glyph();
            g.increment = Sf.u16(fni, i + 8) * k;
            g.aSpace = Sf.s16(fni, i + 18) * k;
            g.baselineOffset = Sf.s16(fni, i + 26) * k;
            int m = Sf.u16(fni, i + 16) * fnmLen;
            if (m + 8 > maps.length) fail("indice de patron fuera de rango");
            g.width = Sf.u16(maps, m) + 1;
            g.height = Sf.u16(maps, m + 2) + 1;
            long from = ((long) Sf.u16(maps, m + 4) << 16 | Sf.u16(maps, m + 6)) * align;
            long size = (long) ((g.width + 7) / 8) * g.height;
            if (from + size > patterns.length) fail("patron fuera de rango");
            g.bitmap = Arrays.copyOfRange(patterns, (int) from, (int) (from + size));
            maxHeight = Math.max(maxHeight, g.height);
            glyphs.put(Sf.text(fni, i, 8), g);
        }
        if (emPels <= 0) emPels = maxHeight;
        fnc = fnd = fno = fni = null;
        fnm.reset();
        fng.reset();
    }

    private void fail(String why) throws IOException {
        throw new IOException("Fuente " + name + ": " + why);
    }
}
