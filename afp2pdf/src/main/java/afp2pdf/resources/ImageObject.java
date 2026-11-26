package afp2pdf.resources;

import afp2pdf.reader.Sf;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/** Imagen IOCA. Se decodifica solo al usarse por primera vez. */
public final class ImageObject {
    public final String name;
    public final ObjectEnv env = new ObjectEnv();
    public double widthPoints, heightPoints;

    private final ByteArrayOutputStream ioca = new ByteArrayOutputStream();

    /** Muestras intercaladas, 8 bits por componente. */
    public static final class Pixels {
        public int width, height, components;
        public byte[] samples;
    }

    public ImageObject(String name) { this.name = name; }

    public void add(Sf f) {
        if (f.id == Sf.IDD && f.data.length >= 9) {
            widthPoints = Sf.u16(f.data, 5) * 720.0 / Sf.u16(f.data, 1);
            heightPoints = Sf.u16(f.data, 7) * 720.0 / Sf.u16(f.data, 3);
        } else if (f.id == Sf.IPD) {
            ioca.write(f.data, 0, f.data.length);
        } else {
            env.add(f, 0.05);
        }
    }

    public Pixels decode() throws IOException {
        byte[] d = ioca.toByteArray();
        int compression = -1;
        Map<Integer, ByteArrayOutputStream> bands = new TreeMap<>();
        for (int i = 0; i + 2 <= d.length; ) {
            int code = Sf.u8(d, i);
            if (code == 0xFE) {
                if (i + 4 > d.length) break;
                int id = Sf.u8(d, i + 1), len = Sf.u16(d, i + 2);
                if (i + 4 + len > d.length) fail("datos truncados");
                if (id == 0x9C && len >= 3) band(bands, Sf.u8(d, i + 4)).write(d, i + 7, len - 3);
                else if (id == 0x92) band(bands, 0).write(d, i + 4, len);
                i += 4 + len;
            } else {
                int len = Sf.u8(d, i + 1);
                if (code == 0x95 && len >= 1) compression = Sf.u8(d, i + 2);
                i += 2 + len;
            }
        }
        if (compression != 0x83) fail("compresion X'" + Integer.toHexString(compression) + "' no soportada");
        if (bands.isEmpty()) fail("sin datos");

        Pixels out = null;
        int k = 0;
        for (ByteArrayOutputStream jpeg : bands.values()) {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg.toByteArray()));
            if (img == null) fail("JPEG no valido");
            Raster raster = img.getRaster();
            if (raster.getNumBands() != 1) fail("solo se soportan bandas de un componente");
            if (out == null) {
                out = new Pixels();
                out.width = img.getWidth();
                out.height = img.getHeight();
                out.components = bands.size();
                if (out.components != 1 && out.components != 3 && out.components != 4)
                    fail(out.components + " bandas");
                out.samples = new byte[out.width * out.height * out.components];
            } else if (img.getWidth() != out.width || img.getHeight() != out.height) {
                fail("bandas de distinto tamano");
            }
            int[] row = new int[out.width];
            for (int y = 0; y < out.height; y++) {
                raster.getSamples(0, y, out.width, 1, 0, row);   // sin conversion de color
                for (int x = 0; x < out.width; x++)
                    out.samples[(y * out.width + x) * out.components + k] = (byte) row[x];
            }
            k++;
        }
        return out;
    }

    private static ByteArrayOutputStream band(Map<Integer, ByteArrayOutputStream> m, int n) {
        ByteArrayOutputStream b = m.get(n);
        if (b == null) m.put(n, b = new ByteArrayOutputStream());
        return b;
    }

    private void fail(String why) throws IOException {
        throw new IOException("Imagen " + name + ": " + why);
    }
}
