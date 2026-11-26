package afp2pdf.resources;

import afp2pdf.reader.Sf;
import java.io.IOException;
import java.nio.charset.Charset;

/** Code page de un byte: codigo -> GCGID y Unicode. */
public final class CodePage {
    private static final Charset FALLBACK = Charset.forName("IBM01148");

    public final String name;
    public final String[] gcgid = new String[256];
    public final String[] unicode = new String[256];
    public String defaultGcgid;
    private int groupLength = 10;

    CodePage(String name) { this.name = name; }

    void add(Sf f) throws IOException {
        byte[] d = f.data;
        if (f.id == Sf.CPC) {
            defaultGcgid = Sf.text(d, 0, 8);
            groupLength = Sf.u8(d, 9);
            if (groupLength != 0x0A && groupLength != 0xFE)
                throw new IOException("Code page " + name + ": solo se soporta un byte");
        } else if (f.id == Sf.CPI) {
            int i = 0;
            while (i + 10 <= d.length) {
                int code = Sf.u8(d, i + 9);
                gcgid[code] = Sf.text(d, i, 8);
                if (groupLength == 0xFE) {          // incluye valores Unicode
                    int n = Sf.u8(d, i + 10);
                    StringBuilder s = new StringBuilder();
                    for (int j = 0; j < n; j++) {
                        int p = i + 11 + j * 4;
                        s.appendCodePoint(Sf.u16(d, p) << 16 | Sf.u16(d, p + 2));
                    }
                    if (n > 0) unicode[code] = s.toString();
                    i += 11 + n * 4;
                } else {
                    i += groupLength;
                }
            }
        }
    }

    /** Unicode declarado o, si no existe, el de la pagina 1148. */
    public String unicodeOf(int code) {
        return unicode[code] != null ? unicode[code] : new String(new byte[] {(byte) code}, FALLBACK);
    }
}
