package afp2pdf.render;

import afp2pdf.pdf.PdfWriter;
import afp2pdf.pdf.Type3Font;
import afp2pdf.reader.AfpReader;
import afp2pdf.reader.Sf;
import afp2pdf.resources.CodePage;
import afp2pdf.resources.FontCharSet;
import afp2pdf.resources.FontRef;
import afp2pdf.resources.ImageObject;
import afp2pdf.resources.ObjectEnv;
import afp2pdf.resources.ResourceStore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Convierte un AFP en un PDF, pagina por pagina. */
public final class Converter {
    private final boolean skipBlank;
    private final ResourceStore store = new ResourceStore();
    private final Report report = new Report();
    private final Map<String, Type3Font> fontCache = new HashMap<>();
    private final Map<ImageObject, Integer> imageCache = new HashMap<>();
    private PdfWriter pdf;

    private PageBuilder page;
    private ByteArrayOutputStream text, graphicData;
    private byte[] graphicDescriptor;
    private ObjectEnv graphicEnv;
    private ImageObject pageImage;

    private Converter(boolean skipBlank) { this.skipBlank = skipBlank; }

    public static Report convert(Path input, Path output, boolean skipBlank) throws IOException {
        if (Files.exists(output) && Files.isSameFile(input, output))
            throw new IOException("La salida no puede ser el archivo de entrada");
        Converter c = new Converter(skipBlank);
        // nombre unico; hereda los permisos normales (umask), a diferencia de createTempFile
        Path temp = output.resolveSibling(output.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            try (AfpReader reader = new AfpReader(Files.newInputStream(input));
                 PdfWriter pdf = new PdfWriter(temp)) {
                c.pdf = pdf;
                Sf f;
                while ((f = reader.next()) != null) {
                    if (c.page != null) c.pageField(f);
                    else if (f.id == Sf.BPG) c.page = new PageBuilder(c.fonts, c.report);
                    else c.store.accept(f);
                }
                if (c.page != null) throw new IOException("Archivo truncado: pagina sin cierre");
                if (c.report.pages == 0) throw new IOException("No hay paginas para escribir");
                pdf.finish();
            }
            try {
                Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return c.report;
    }

    private final PageBuilder.Fonts fonts = new PageBuilder.Fonts() {
        @Override
        public Type3Font get(FontRef ref) throws IOException {
            ref.resolve(store);
            FontCharSet set = store.fonts.get(ref.charSet);
            CodePage codePage = store.codePages.get(ref.codePage);
            if (set == null || codePage == null) {
                report.warn("Falta el recurso " + ref.charSet + " / " + ref.codePage);
                return null;
            }
            String key = set.name + '/' + codePage.name;
            Type3Font font = fontCache.get(key);
            if (font == null) fontCache.put(key, font = Type3Font.create(pdf, set, codePage));
            return font;
        }
    };

    private void pageField(Sf f) throws IOException {
        if (pageImage != null) {
            if (f.id == Sf.EIM) {
                ObjectEnv e = pageImage.env;
                draw(pageImage, e.x, e.y, e.orientation);
                pageImage = null;
            } else {
                pageImage.add(f);
            }
            return;
        }
        if (graphicData != null) {
            if (f.id == Sf.GDD) graphicDescriptor = f.data;
            else if (f.id == Sf.GAD) graphicData.write(f.data);
            else if (f.id == Sf.EGR) {
                if (graphicDescriptor == null) report.warn("Grafico sin descriptor");
                else page.graphics(graphicDescriptor, graphicData.toByteArray(), graphicEnv);
                graphicData = null;
            } else {
                graphicEnv.add(f, page.unit);
            }
            return;
        }
        switch (f.id) {
            case Sf.PGD: page.pageDescriptor(f.data); break;
            case Sf.PTD: case Sf.PTD1: page.textDescriptor(f.data); break;
            case Sf.MCF: page.mapFonts(f.data); break;
            case Sf.MCF1: report.warn("MCF formato 1 no soportado"); break;
            case Sf.BPT: text = new ByteArrayOutputStream(); break;
            case Sf.PTX: if (text != null) text.write(f.data); break;
            case Sf.EPT:
                if (text != null) page.text(text.toByteArray());
                text = null;
                break;
            case Sf.BGR:
                graphicData = new ByteArrayOutputStream();
                graphicDescriptor = null;
                graphicEnv = new ObjectEnv();
                break;
            case Sf.BIM: pageImage = new ImageObject("de pagina"); break;
            case Sf.IOB: include(f.data); break;
            case Sf.IPS: report.warn("Page segments (IPS) no soportados"); break;
            case Sf.IPO: report.warn("Overlays no soportados"); break;
            case Sf.BBC: report.warn("Codigos de barras BCOCA no soportados"); break;
            case Sf.EPG: endPage(); break;
            default: break;
        }
    }

    private void include(byte[] d) throws IOException {
        String name = Sf.text(d, 0, 8);
        ImageObject image = store.images.get(name);
        if (image == null || d.length < 18) {
            report.warn("Objeto incluido no disponible o no soportado: " + name);
            return;
        }
        draw(image, ObjectEnv.signed(d, 10) * page.unit, ObjectEnv.signed(d, 13) * page.unit,
                Sf.u16(d, 16) >> 7);
    }

    private void draw(ImageObject image, double x, double y, int degrees) throws IOException {
        Integer id = imageCache.get(image);
        if (id == null) {
            ImageObject.Pixels p;
            try {
                p = image.decode();
            } catch (IOException e) {
                report.warn(e.getMessage());
                return;
            }
            String space = p.components == 4 ? "/DeviceCMYK"
                    : p.components == 3 ? "/DeviceRGB" : "/DeviceGray";
            id = pdf.stream("/Type /XObject /Subtype /Image /Width " + p.width + " /Height "
                    + p.height + " /ColorSpace " + space + " /BitsPerComponent 8", p.samples);
            imageCache.put(image, id);
        }
        double w = image.widthPoints, h = image.heightPoints;
        if (image.env.mapping == 0x10 && image.env.width > 0 && w > 0 && h > 0) {
            double k = Math.min(image.env.width / w, image.env.height / h);
            w *= k;
            h *= k;
        }
        page.image(id, x, y, degrees, w, h);
    }

    private void endPage() throws IOException {
        if (page.isBlank()) report.blankPages++;
        if (page.isBlank() && skipBlank) {
            report.omittedPages++;
        } else {
            page.finish(pdf);
            report.pages++;
        }
        page = null;
    }
}
