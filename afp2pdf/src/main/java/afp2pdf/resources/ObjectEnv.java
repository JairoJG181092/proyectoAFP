package afp2pdf.resources;

import afp2pdf.reader.Sf;

/** Posicion, tamano y modo de ajuste del area de un objeto (OBD, OBP, MIO/MGO). */
public final class ObjectEnv {
    public double x, y;                 // puntos, desde la esquina superior izquierda
    public double width, height;        // puntos
    public int orientation;             // grados
    public int mapping;                 // 0x00 posicionar y recortar, 0x10 ajustar

    public void add(Sf f, double pageUnit) {
        byte[] d = f.data;
        if (f.id == Sf.OBD) {
            double ux = 0.05, uy = 0.05;
            for (int i = 0; i + 2 <= d.length && Sf.u8(d, i) >= 2; i += Sf.u8(d, i)) {
                int type = Sf.u8(d, i + 1);
                if (type == 0x4B) {
                    ux = 720.0 / Sf.u16(d, i + 4);
                    uy = 720.0 / Sf.u16(d, i + 6);
                } else if (type == 0x4C) {
                    width = Sf.u24(d, i + 3) * ux;
                    height = Sf.u24(d, i + 6) * uy;
                }
            }
        } else if (f.id == Sf.OBP && d.length >= 12) {
            x = signed(d, 2) * pageUnit;
            y = signed(d, 5) * pageUnit;
            orientation = Sf.u16(d, 8) >> 7;
        } else if ((f.id == Sf.MIO || f.id == Sf.MGO) && d.length >= 5) {
            mapping = Sf.u8(d, 4);
        }
    }

    public static int signed(byte[] d, int i) {
        return Sf.u24(d, i) << 8 >> 8;
    }
}
