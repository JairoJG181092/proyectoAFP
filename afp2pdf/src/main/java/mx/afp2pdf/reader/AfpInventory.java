package mx.afp2pdf.reader;

import java.io.File;
import java.util.*;

/** Fase 1: recorre el AFP en streaming, hace inventario y valida el balance Begin/End. */
public final class AfpInventory {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) { System.err.println("Uso: AfpInventory <archivo.afp>"); System.exit(2); }
        File f = new File(args[0]);
        long records = 0, pages = 0, lastOffset = 0;
        Map<String, Long> counts = new TreeMap<String, Long>();
        Map<String, List<String>> resources = new TreeMap<String, List<String>>();
        Deque<String> stack = new ArrayDeque<String>();
        List<String> problems = new ArrayList<String>();
        long t0 = System.currentTimeMillis();
        AfpReader rd = new AfpReader(f);
        try {
            AfpRecord r;
            while (true) {
                try { r = rd.next(); }
                catch (java.io.IOException e) { problems.add("Lectura detenida: " + e.getMessage()); break; }
                if (r == null) break;
                records++; lastOffset = r.offset;
                Long c = counts.get(r.id()); counts.put(r.id(), c == null ? 1L : c + 1);
                if (r.isBegin()) {
                    stack.push(r.id().substring(0, 2) + String.format("%02X", r.category));
                    if (r.id().equals("D3A8AF")) pages++;
                    String grp = AfpNames.of(r).substring(0, 3);
                    if ("BFN BCP BCF BMO BPS BOC BIM BRS".contains(grp)) {
                        List<String> l = resources.get(grp);
                        if (l == null) { l = new ArrayList<String>(); resources.put(grp, l); }
                        l.add(r.name());
                    }
                } else if (r.isEnd()) {
                    if (stack.isEmpty()) {
                        problems.add("End sin Begin en offset " + r.offset + " (" + AfpNames.of(r) + ")");
                    } else {
                        String top = stack.pop();
                        if (!top.substring(2).equals(String.format("%02X", r.category)))
                            problems.add("Begin/End desbalanceado en offset " + r.offset
                                    + ": abierto " + top + ", cerrado " + r.id());
                    }
                }
            }
        } finally { rd.close(); }

        System.out.printf("Archivo: %s (%,d bytes)%n", f.getName(), f.length());
        System.out.printf("Registros: %,d | Paginas (BPG): %,d | Bytes ignorados (CC): %,d%n",
                records, pages, rd.skippedBytes());
        System.out.println("\nRegistros por tipo:");
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            String id = e.getKey();
            System.out.printf("  %s  %,10d  %s%n", id, e.getValue(),
                    AfpNames.of(new AfpRecord(0, 8, 0xD3, Integer.parseInt(id.substring(2, 4), 16),
                            Integer.parseInt(id.substring(4, 6), 16), 0, 0, new byte[0])));
        }
        System.out.println("\nRecursos embebidos:");
        for (Map.Entry<String, List<String>> e : resources.entrySet())
            System.out.printf("  %s: %d%n", e.getKey(), e.getValue().size());
        if (!stack.isEmpty()) problems.add("Quedaron " + stack.size() + " estructuras sin cerrar");
        System.out.println("\nValidacion: " + (problems.isEmpty() ? "OK" : problems.size() + " problema(s)"));
        for (int i = 0; i < Math.min(20, problems.size()); i++) System.out.println("  - " + problems.get(i));
        System.out.printf("Tiempo: %d ms%n", System.currentTimeMillis() - t0);
    }
}
