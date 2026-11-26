package afp2pdf.pdf;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

/** Escritor PDF secuencial: cada objeto va a disco en cuanto se crea. */
public final class PdfWriter implements Closeable {
    private static final long MAX_OFFSET = 9_999_999_999L;

    private final OutputStream out;
    private long position;
    private final List<Long> offsets = new ArrayList<>();
    private final List<Integer> pages = new ArrayList<>();
    private final int catalog, pageTree;

    public PdfWriter(Path file) throws IOException {
        out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 16);
        write("%PDF-1.5\n%âãÏÓ\n");
        catalog = reserve();
        pageTree = reserve();
    }

    private int reserve() {
        offsets.add(0L);
        return offsets.size();
    }

    public int object(String body) throws IOException {
        int id = reserve();
        begin(id);
        write(body + "\nendobj\n");
        return id;
    }

    /** Flujo comprimido; entries son claves adicionales del diccionario. */
    public int stream(String entries, byte[] data) throws IOException {
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(packed)) {
            z.write(data);
        }
        int id = reserve();
        begin(id);
        write("<< " + entries + " /Filter /FlateDecode /Length " + packed.size() + " >>\nstream\n");
        packed.writeTo(out);
        position += packed.size();
        write("\nendstream\nendobj\n");
        return id;
    }

    public void page(double width, double height, String resources, byte[] content) throws IOException {
        int contents = stream("", content);
        pages.add(object("<< /Type /Page /Parent " + pageTree + " 0 R /MediaBox [0 0 "
                + Num.of(width) + " " + Num.of(height) + "] /Resources << " + resources
                + " >> /Contents " + contents + " 0 R >>"));
    }

    public void finish() throws IOException {
        begin(pageTree);
        write("<< /Type /Pages /Count " + pages.size() + " /Kids [");
        for (int p : pages) write(p + " 0 R\n");
        write("] >>\nendobj\n");
        begin(catalog);
        write("<< /Type /Catalog /Pages " + pageTree + " 0 R >>\nendobj\n");
        long xref = position;
        if (xref > MAX_OFFSET) throw new IOException("El PDF supera el tamano maximo; hay que dividirlo");
        write("xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
        for (long o : offsets) write(String.format("%010d 00000 n \n", o));
        write("trailer\n<< /Size " + (offsets.size() + 1) + " /Root " + catalog
                + " 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        out.flush();
    }

    private void begin(int id) throws IOException {
        offsets.set(id - 1, position);
        write(id + " 0 obj\n");
    }

    private void write(String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        out.write(b);
        position += b.length;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
