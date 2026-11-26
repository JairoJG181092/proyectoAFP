package afp2pdf.resources;

import afp2pdf.reader.Sf;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Recursos incluidos en el propio AFP. */
public final class ResourceStore {
    public final Map<String, FontCharSet> fonts = new LinkedHashMap<>();
    public final Map<String, CodePage> codePages = new LinkedHashMap<>();
    /** Coded font -> {juego de caracteres, code page}. */
    public final Map<String, String[]> codedFonts = new LinkedHashMap<>();
    /** Todos los nombres declarados con BRS -> tipo de objeto. */
    public final Map<String, Integer> declared = new LinkedHashMap<>();

    public final Map<String, ImageObject> images = new LinkedHashMap<>();

    private ImageObject image;
    private String resourceName, codedFontName;
    private FontCharSet font;
    private CodePage codePage;

    /** @return true si el campo pertenece a un recurso. */
    public boolean accept(Sf f) throws IOException {
        switch (f.id) {
            case Sf.BRS:
                resourceName = Sf.text(f.data, 0, 8);
                declared.put(resourceName, resourceType(f.data));
                return true;
            case Sf.ERS: resourceName = null; return true;
            case Sf.BFN: font = new FontCharSet(name(f)); return true;
            case Sf.EFN:
                if (font != null) { font.build(); fonts.put(font.name, font); font = null; }
                return true;
            case Sf.BCP: codePage = new CodePage(name(f)); return true;
            case Sf.ECP:
                if (codePage != null) { codePages.put(codePage.name, codePage); codePage = null; }
                return true;
            case Sf.BCF: codedFontName = name(f); return true;
            case Sf.ECF: codedFontName = null; return true;
            case Sf.CFI:
                if (codedFontName != null && f.data.length >= 16)
                    codedFonts.put(codedFontName,
                            new String[] {Sf.text(f.data, 0, 8), Sf.text(f.data, 8, 8)});
                return true;
            case Sf.BIM: image = new ImageObject(name(f)); return true;
            case Sf.EIM:
                if (image != null) { images.put(image.name, image); image = null; }
                return true;
            default:
                if (image != null) { image.add(f); return true; }
                if (font != null) { font.add(f); return true; }
                if (codePage != null) { codePage.add(f); return true; }
                return resourceName != null || codedFontName != null;
        }
    }

    private String name(Sf f) {
        return resourceName != null ? resourceName : Sf.text(f.data, 0, 8);
    }

    private static int resourceType(byte[] d) {
        for (int i = 10; i + 2 < d.length && Sf.u8(d, i) > 1; i += Sf.u8(d, i))
            if (Sf.u8(d, i + 1) == 0x21) return Sf.u8(d, i + 2);
        return -1;
    }
}
