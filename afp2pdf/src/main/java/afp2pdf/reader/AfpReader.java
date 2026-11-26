package afp2pdf.reader;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Lectura secuencial: solo un registro en memoria a la vez. */
public final class AfpReader implements Closeable {
    private final InputStream in;
    private long position;

    public AfpReader(InputStream in) {
        this.in = new BufferedInputStream(in, 1 << 16);
    }

    /** @return siguiente campo, o null al final del archivo. */
    public Sf next() throws IOException {
        int c = in.read();
        while (c == 0x0D || c == 0x0A) {
            position++;
            c = in.read();
        }
        if (c < 0) return null;
        long start = position;
        if (c != 0x5A) throw new IOException("Registro sin X'5A' en la posicion " + start);
        byte[] head = read(8, start);
        int length = Sf.u16(head, 0);
        if (length < 8) throw new IOException("Longitud invalida en la posicion " + start);
        byte[] data = read(length - 8, start);
        position += 1 + length;
        int flags = Sf.u8(head, 5);
        int from = 0, to = data.length;
        if ((flags & 0x80) != 0 && to > 0) from = Sf.u8(data, 0);          // extension
        if ((flags & 0x08) != 0 && to > from) {                            // relleno
            int pad = Sf.u8(data, to - 1);
            if (pad == 0 && to >= 3) pad = Sf.u16(data, to - 3);
            to -= pad;
        }
        if (from > to || to < 0) throw new IOException("Registro corrupto en la posicion " + start);
        if (from != 0 || to != data.length) data = Arrays.copyOfRange(data, from, to);
        return new Sf(Sf.u24(head, 2), data, start);
    }

    private byte[] read(int n, long start) throws IOException {
        byte[] b = new byte[n];
        int done = 0;
        while (done < n) {
            int r = in.read(b, done, n - done);
            if (r < 0) throw new EOFException("Archivo truncado en el registro de la posicion " + start);
            done += r;
        }
        return b;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
