package afp2pdf;

import afp2pdf.reader.AfpReader;
import afp2pdf.reader.Sf;
import afp2pdf.render.Ptoca;
import afp2pdf.resources.FontRef;
import afp2pdf.resources.ResourceStore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Recorre el archivo, cuenta contenido y valida que los recursos existan. */
final class Inventory {

    /** @return numero de recursos faltantes. */
    static int run(Path input, PrintStream out) throws IOException {
        ResourceStore store = new ResourceStore();
        final Map<String, Long> counts = new TreeMap<>();
        Set<FontRef> refs = new java.util.LinkedHashSet<>();
        Set<String> objects = new TreeSet<>();
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        long records = 0;
        boolean inPage = false;
        try (AfpReader reader = new AfpReader(Files.newInputStream(input))) {
            Sf f;
            while ((f = reader.next()) != null) {
                records++;
                if (f.id == Sf.BPG) { inPage = true; count(counts, "Paginas"); }
                if (f.id == Sf.EPG) inPage = false;
                if (!inPage) { store.accept(f); continue; }
                switch (f.id) {
                    case Sf.MCF: refs.addAll(FontRef.parseMcf(f.data).values()); break;
                    case Sf.BPT: text.reset(); break;
                    case Sf.PTX: text.write(f.data); break;
                    case Sf.EPT:
                        Ptoca.parse(text.toByteArray(), new Ptoca.Handler() {
                            @Override
                            public void control(int function, byte[] p) {
                                count(counts, String.format("PTOCA X'%02X'", function));
                            }
                        });
                        break;
                    case Sf.IOB: case Sf.IPS: case Sf.IPO:
                        objects.add(Sf.text(f.data, 0, 8));
                        break;
                    case Sf.BGR: count(counts, "Objetos graficos (GOCA)"); break;
                    case Sf.BIM: count(counts, "Imagenes en pagina (IOCA)"); break;
                    case Sf.BBC: count(counts, "Codigos de barras (BCOCA)"); break;
                    default: break;
                }
            }
        }
        Set<String> missing = new TreeSet<>();
        for (FontRef ref : refs) {
            ref.resolve(store);
            if (ref.charSet == null) missing.add("coded font " + ref.codedFont);
            else {
                if (!store.fonts.containsKey(ref.charSet)) missing.add("fuente " + ref.charSet);
                if (!store.codePages.containsKey(ref.codePage)) missing.add("code page " + ref.codePage);
            }
        }
        for (String name : objects)
            if (!store.declared.containsKey(name)) missing.add("objeto " + name);

        out.println("Archivo: " + input.getFileName() + " (" + Files.size(input) + " bytes)");
        out.println("Registros: " + records);
        for (Map.Entry<String, Long> e : counts.entrySet())
            out.println("  " + e.getKey() + ": " + e.getValue());
        out.println("Recursos incluidos: " + store.declared.size()
                + " (fuentes " + store.fonts.size() + ", code pages " + store.codePages.size()
                + ", coded fonts " + store.codedFonts.size() + ")");
        out.println("Objetos referenciados por las paginas: " + objects);
        out.println(missing.isEmpty() ? "Validacion: no faltan recursos referenciados"
                : "Faltan recursos: " + missing);
        return missing.size();
    }

    private static void count(Map<String, Long> m, String key) {
        Long n = m.get(key);
        m.put(key, n == null ? 1 : n + 1);
    }
}
