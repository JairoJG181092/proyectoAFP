package mx.afp2pdf.reader;

import java.util.*;

/** Nombres legibles de los campos estructurados mas comunes (clave = ID de 3 bytes en hex). */
public final class AfpNames {
    private static final Map<String, String> M = new HashMap<String, String>();
    private static void a(String id, String n) { M.put(id, n); }
    static {
        a("D3A8A8","BDT Begin Document");   a("D3A9A8","EDT End Document");
        a("D3A8AD","BNG Begin Named Page Group"); a("D3A9AD","ENG End Named Page Group");
        a("D3A8AF","BPG Begin Page");       a("D3A9AF","EPG End Page");
        a("D3A8C9","BAG Begin Active Env Group"); a("D3A9C9","EAG End Active Env Group");
        a("D3A89B","BPT Begin Presentation Text"); a("D3A99B","EPT End Presentation Text");
        a("D3EE9B","PTX Presentation Text Data");
        a("D3A8BB","BGR Begin Graphics");   a("D3A9BB","EGR End Graphics");
        a("D3EEBB","GAD Graphics Data");
        a("D3A8FB","BIM Begin Image");      a("D3A9FB","EIM End Image");
        a("D3EEFB","IPD Image Picture Data");
        a("D3A8EB","BBC Begin Bar Code");   a("D3A9EB","EBC End Bar Code");
        a("D3EEEB","BDD Bar Code Data");
        a("D3A892","BOC Begin Object Container"); a("D3A992","EOC End Object Container");
        a("D3A8C6","BRG Begin Resource Group");   a("D3A9C6","ERG End Resource Group");
        a("D3A8CE","BRS Begin Resource");         a("D3A9CE","ERS End Resource");
        a("D3A8DF","BMO Begin Overlay");    a("D3A9DF","EMO End Overlay");
        a("D3A85F","BPS Begin Page Segment"); a("D3A95F","EPS End Page Segment");
        a("D3A8CD","BFM Begin Form Map");   a("D3A9CD","EFM End Form Map");
        a("D3A8CC","BMM Begin Medium Map"); a("D3A9CC","EMM End Medium Map");
        a("D3A88A","BCF Begin Coded Font"); a("D3A98A","ECF End Coded Font");
        a("D3A889","BFN Begin Font");       a("D3A989","EFN End Font");
        a("D3A887","BCP Begin Code Page");  a("D3A987","ECP End Code Page");
    }
    public static String of(AfpRecord r) {
        String n = M.get(r.id());
        return n != null ? n : "?? (sin catalogar)";
    }
    private AfpNames() {}
}
