package afp2pdf.resources;

import afp2pdf.reader.Sf;
import java.util.HashMap;
import java.util.Map;

/** Referencia de fuente declarada en un MCF. */
public final class FontRef {
    public String codedFont, charSet, codePage;
    public int rotation;

    /** @return identificador local -> fuente. */
    public static Map<Integer, FontRef> parseMcf(byte[] d) {
        Map<Integer, FontRef> out = new HashMap<>();
        for (int g = 0; g + 2 <= d.length; ) {
            int len = Sf.u16(d, g);
            if (len < 2 || g + len > d.length) break;
            FontRef ref = new FontRef();
            int id = -1;
            for (int t = g + 2; t + 2 <= g + len && Sf.u8(d, t) >= 2; t += Sf.u8(d, t)) {
                int size = Sf.u8(d, t), type = Sf.u8(d, t + 1);
                if (type == 0x02 && size > 4) {
                    String name = Sf.text(d, t + 4, size - 4);
                    int kind = Sf.u8(d, t + 2);
                    if (kind == 0x8E) ref.codedFont = name;
                    else if (kind == 0x86) ref.charSet = name;
                    else if (kind == 0x85) ref.codePage = name;
                } else if (type == 0x24 && size >= 4) {
                    id = Sf.u8(d, t + 3);
                } else if (type == 0x26 && size >= 4) {
                    ref.rotation = Sf.u16(d, t + 2);
                }
            }
            if (id >= 0) out.put(id, ref);
            g += len;
        }
        return out;
    }

    /** Completa charSet y codePage a partir del coded font. */
    public FontRef resolve(ResourceStore store) {
        if (charSet == null && codedFont != null) {
            String[] pair = store.codedFonts.get(codedFont);
            if (pair != null) { charSet = pair[0]; codePage = pair[1]; }
        }
        return this;
    }
}
