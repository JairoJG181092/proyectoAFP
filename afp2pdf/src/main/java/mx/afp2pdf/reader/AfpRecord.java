package mx.afp2pdf.reader;

/** Un campo estructurado MO:DCA (structured field). */
public final class AfpRecord {
    public final long offset;     // posicion del byte 0x5A en el archivo
    public final int length;      // longitud declarada (sin contar el 0x5A)
    public final int sfClass;     // normalmente 0xD3
    public final int type;        // ej. 0xA8 = Begin, 0xA9 = End, 0xEE = Data
    public final int category;    // ej. 0xAF = Page
    public final int flags;
    public final int sequence;
    public final byte[] data;     // payload (puede ser vacio)

    public AfpRecord(long offset, int length, int sfClass, int type, int category,
                     int flags, int sequence, byte[] data) {
        this.offset = offset; this.length = length; this.sfClass = sfClass;
        this.type = type; this.category = category; this.flags = flags;
        this.sequence = sequence; this.data = data;
    }

    /** Identificador de 3 bytes en hex, ej. "D3A8AF". */
    public String id() {
        return String.format("%02X%02X%02X", sfClass, type, category);
    }

    public boolean isBegin() { return type == 0xA8; }
    public boolean isEnd()   { return type == 0xA9; }

    /** Nombre de 8 caracteres (EBCDIC cp037) al inicio de los Begin/End; null si no aplica. */
    public String name() {
        if ((!isBegin() && !isEnd()) || data.length < 8) return null;
        return new String(data, 0, 8, java.nio.charset.Charset.forName("Cp037")).trim();
    }
}
