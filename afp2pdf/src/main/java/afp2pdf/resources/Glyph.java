package afp2pdf.resources;

/** Caracter raster. Las metricas estan en pels. */
public final class Glyph {
    public double increment, aSpace, baselineOffset;
    public int width, height;
    public byte[] bitmap;   // filas alineadas a byte, 1 = negro
}
