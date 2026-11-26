package afp2pdf.render;

import java.util.Map;
import java.util.TreeMap;

/** Resumen de conversion: paginas y advertencias agrupadas. */
public final class Report {
    public int pages, blankPages, omittedPages;
    public final Map<String, Long> warnings = new TreeMap<>();

    public void warn(String message) { warn(message, 1); }

    public void warn(String message, long times) {
        Long n = warnings.get(message);
        warnings.put(message, (n == null ? 0 : n) + times);
    }
}
