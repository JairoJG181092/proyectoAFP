package afp2pdf.pdf;

import afp2pdf.resources.CodePage;
import afp2pdf.resources.FontCharSet;
import afp2pdf.resources.Glyph;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Convierte (juego de caracteres + code page) en una fuente Type 3 con ToUnicode. */
public final class Type3Font {
    public int objectId;
    public double sizePoints;
    public final double[] advancePoints = new double[256];
    public final boolean[] defined = new boolean[256];
    public final boolean[] visible = new boolean[256];

    public static Type3Font create(PdfWriter pdf, FontCharSet set, CodePage page) throws IOException {
        Type3Font font = new Type3Font();
        double pointsPerPel = 720.0 / set.pelsPer10Inches;
        font.sizePoints = set.emPels * pointsPerPel;

        StringBuilder procs = new StringBuilder(), names = new StringBuilder(),
                widths = new StringBuilder();
        List<String> cmap = new ArrayList<>();
        double minX = 0, minY = 0, maxX = 1, maxY = 1;

        for (int code = 0; code < 256; code++) {
            String id = page.gcgid[code];
            Glyph g = id == null ? null : set.glyphs.get(id);
            boolean space = code == 0x40 && "SP010000".equals(id);
            boolean defined = g != null || space;
            if (g == null && !space) g = set.glyphs.get(page.defaultGcgid);
            double advance = space ? set.spaceIncrement : g != null ? g.increment : set.spaceIncrement;
            String name = String.format("g%02X", code);
            names.append('/').append(name).append(' ');
            widths.append(Num.of(advance)).append(' ');
            font.advancePoints[code] = advance * pointsPerPel;
            font.defined[code] = defined;

            ByteArrayOutputStream proc = new ByteArrayOutputStream();
            if (g == null || space) {
                write(proc, Num.of(advance) + " 0 d0\n");
            } else {
                for (byte b : g.bitmap) if (b != 0) { font.visible[code] = true; break; }
                double x = g.aSpace, y = g.baselineOffset - g.height;
                write(proc, Num.of(advance) + " 0 " + Num.of(x) + " " + Num.of(y) + " "
                        + Num.of(x + g.width) + " " + Num.of(y + g.height) + " d1\n"
                        + "q " + g.width + " 0 0 " + g.height + " " + Num.of(x) + " " + Num.of(y)
                        + " cm\nBI /IM true /W " + g.width + " /H " + g.height + " /D [1 0] ID\n");
                proc.write(g.bitmap);
                write(proc, "\nEI Q\n");
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x + g.width);
                maxY = Math.max(maxY, y + g.height);
            }
            procs.append('/').append(name).append(' ')
                    .append(pdf.stream("", proc.toByteArray())).append(" 0 R ");
            if (defined) {
                StringBuilder hex = new StringBuilder();
                for (byte b : page.unicodeOf(code).getBytes(StandardCharsets.UTF_16BE))
                    hex.append(String.format("%02X", b & 0xFF));
                cmap.add(String.format("<%02X> <%s>", code, hex));
            }
        }

        int toUnicode = pdf.stream("", toUnicode(cmap));
        String scale = Num.of(1 / set.emPels);
        font.objectId = pdf.object("<< /Type /Font /Subtype /Type3 /FontBBox [" + Num.of(minX) + " "
                + Num.of(minY) + " " + Num.of(maxX) + " " + Num.of(maxY) + "] /FontMatrix [" + scale
                + " 0 0 " + scale + " 0 0] /CharProcs << " + procs + ">> /Encoding << /Type /Encoding "
                + "/Differences [0 " + names + "] >> /FirstChar 0 /LastChar 255 /Widths [" + widths
                + "] /Resources << >> /ToUnicode " + toUnicode + " 0 R >>");
        return font;
    }

    private static byte[] toUnicode(List<String> entries) {
        StringBuilder s = new StringBuilder("/CIDInit /ProcSet findresource begin\n12 dict begin\n"
                + "begincmap\n/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n"
                + "1 begincodespacerange\n<00> <FF>\nendcodespacerange\n");
        for (int i = 0; i < entries.size(); i += 100) {
            List<String> block = entries.subList(i, Math.min(i + 100, entries.size()));
            s.append(block.size()).append(" beginbfchar\n");
            for (String e : block) s.append(e).append('\n');
            s.append("endbfchar\n");
        }
        s.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n");
        return s.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static void write(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.US_ASCII));
    }
}
