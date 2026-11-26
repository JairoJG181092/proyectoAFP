package afp2pdf;

import afp2pdf.render.Converter;
import afp2pdf.render.Report;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class Main {

    /** Salida: 0 correcto, 1 error, 2 uso incorrecto, 3 terminado con advertencias. */
    public static void main(String[] args) {
        try {
            if (args.length == 2 && args[0].equals("inventario")) {
                System.exit(Inventory.run(Paths.get(args[1]), System.out) == 0 ? 0 : 3);
            }
            List<String> a = new ArrayList<>(Arrays.asList(args));
            boolean skipBlank = a.remove("--omitir-blancas");
            if ((a.size() == 2 || a.size() == 3) && a.get(0).equals("convertir")) {
                Path in = Paths.get(a.get(1));
                Path out = Paths.get(a.size() == 3 ? a.get(2)
                        : a.get(1).replaceAll("(?i)\\.afp$", "") + ".pdf");
                long start = System.currentTimeMillis();
                Report r = Converter.convert(in, out, skipBlank);
                System.out.printf("OK: %d paginas en %.1f s -> %s%n", r.pages,
                        (System.currentTimeMillis() - start) / 1000.0, out);
                System.out.println("Paginas sin contenido en el AFP: " + r.blankPages
                        + " (omitidas: " + r.omittedPages + ")");
                for (Map.Entry<String, Long> w : r.warnings.entrySet())
                    System.out.println("AVISO (" + w.getValue() + "): " + w.getKey());
                System.exit(r.warnings.isEmpty() ? 0 : 3);
            }
            System.err.println("Uso:\n  afp2pdf inventario <archivo.afp>\n"
                    + "  afp2pdf convertir <archivo.afp> [salida.pdf] [--omitir-blancas]");
            System.exit(2);
        } catch (Exception e) {
            System.err.println("ERROR: " + e);
            System.exit(1);
        }
    }
}
