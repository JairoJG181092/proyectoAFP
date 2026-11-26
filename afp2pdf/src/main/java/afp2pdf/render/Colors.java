package afp2pdf.render;

import afp2pdf.pdf.Num;
import afp2pdf.reader.Sf;

/** Color de proceso (PTOCA SEC y GOCA GSPCOL) sin convertir de espacio. */
final class Colors {
    /** @return {relleno, trazo} o null si no se soporta. */
    static String[] process(byte[] d, int from, int len) {
        if (len < 11) return null;
        int space = Sf.u8(d, from + 1);
        int n = space == 0x01 ? 3 : space == 0x04 ? 4 : 0;
        if (n == 0 || len < 10 + n) return null;
        StringBuilder c = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (Sf.u8(d, from + 6 + i) != 8) return null;
            c.append(Num.of(Sf.u8(d, from + 10 + i) / 255.0)).append(' ');
        }
        return n == 3 ? new String[] {c + "rg", c + "RG"} : new String[] {c + "k", c + "K"};
    }
}
