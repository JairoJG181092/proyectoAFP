package afp2pdf.reader;

import java.nio.charset.Charset;

/** Campo estructurado MO:DCA y utilidades binarias. */
public final class Sf {
    public static final int BDT = 0xD3A8A8, BPG = 0xD3A8AF, EPG = 0xD3A9AF,
            BRS = 0xD3A8CE, ERS = 0xD3A9CE, PGD = 0xD3A6AF,
            PTD = 0xD3B19B, PTD1 = 0xD3A69B, BPT = 0xD3A89B, EPT = 0xD3A99B, PTX = 0xD3EE9B,
            MCF = 0xD3AB8A, MCF1 = 0xD3B18A, IOB = 0xD3AFC3, IPS = 0xD3AF5F, IPO = 0xD3AFD8,
            BGR = 0xD3A8BB, BIM = 0xD3A8FB, BBC = 0xD3A8EB,
            BFN = 0xD3A889, EFN = 0xD3A989, FND = 0xD3A689, FNC = 0xD3A789, FNO = 0xD3AE89,
            FNI = 0xD38C89, FNM = 0xD3A289, FNG = 0xD3EE89,
            BCP = 0xD3A887, ECP = 0xD3A987, CPC = 0xD3A787, CPI = 0xD38C87,
            OBD = 0xD3A66B, OBP = 0xD3AC6B, MIO = 0xD3ABFB, MGO = 0xD3ABBB,
            GDD = 0xD3A6BB, GAD = 0xD3EEBB, EGR = 0xD3A9BB,
            IDD = 0xD3A6FB, IPD = 0xD3EEFB, EIM = 0xD3A9FB,
            BCF = 0xD3A88A, ECF = 0xD3A98A, CFI = 0xD38C8A;

    private static final Charset EBCDIC = Charset.forName("IBM500");

    public final int id;
    public final byte[] data;
    public final long offset;

    public Sf(int id, byte[] data, long offset) {
        this.id = id;
        this.data = data;
        this.offset = offset;
    }

    public static int u8(byte[] b, int i) { return b[i] & 0xFF; }
    public static int u16(byte[] b, int i) { return u8(b, i) << 8 | u8(b, i + 1); }
    public static int s16(byte[] b, int i) { return (short) u16(b, i); }
    public static int u24(byte[] b, int i) { return u16(b, i) << 8 | u8(b, i + 2); }

    public static String text(byte[] b, int from, int len) {
        int n = Math.max(0, Math.min(len, b.length - from));
        return new String(b, from, n, EBCDIC).trim();
    }
}
