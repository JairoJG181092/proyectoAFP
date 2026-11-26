package mx.afp2pdf.reader;

import java.io.*;

/**
 * Lector secuencial (streaming) de registros MO:DCA. Nunca carga el archivo completo:
 * cada llamada a next() devuelve un solo registro. Las posiciones son long (archivos > 2 GB).
 * Tolera bytes de control de carro (CC) antes del 0x5A y cuenta los bytes ignorados.
 */
public final class AfpReader implements Closeable {
    private final InputStream in;
    private long pos = 0;
    private long skippedBytes = 0;

    public AfpReader(File f) throws IOException {
        this.in = new BufferedInputStream(new FileInputStream(f), 1 << 20);
    }

    public long position() { return pos; }
    public long skippedBytes() { return skippedBytes; }

    private int read() throws IOException {
        int b = in.read();
        if (b >= 0) pos++;
        return b;
    }

    private void readFully(byte[] buf, int off, int len) throws IOException {
        while (len > 0) {
            int n = in.read(buf, off, len);
            if (n < 0) throw new EOFException("Registro truncado en posicion " + pos);
            off += n; len -= n; pos += n;
        }
    }

    /** @return siguiente registro, o null al llegar al fin del archivo. */
    public AfpRecord next() throws IOException {
        int b;
        while ((b = read()) != 0x5A) {
            if (b < 0) return null;
            skippedBytes++;
        }
        long offset = pos - 1;
        int len = (read() << 8) | read();
        if (len < 8) throw new IOException("Longitud invalida (" + len + ") en offset " + offset);
        byte[] hdr = new byte[6];
        readFully(hdr, 0, 6);
        byte[] data = new byte[len - 8];
        readFully(data, 0, data.length);
        return new AfpRecord(offset, len, hdr[0] & 0xFF, hdr[1] & 0xFF, hdr[2] & 0xFF,
                hdr[3] & 0xFF, ((hdr[4] & 0xFF) << 8) | (hdr[5] & 0xFF), data);
    }

    @Override public void close() throws IOException { in.close(); }
}
