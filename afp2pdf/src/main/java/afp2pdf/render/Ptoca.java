package afp2pdf.render;

import afp2pdf.reader.Sf;
import java.io.IOException;
import java.util.Arrays;

/** Decodificador de secuencias de control PTOCA. */
public final class Ptoca {
    public static final int SIM = 0xC0, SIA = 0xC2, SVI = 0xC4, AMI = 0xC6, RMI = 0xC8,
            SBI = 0xD0, AMB = 0xD2, RMB = 0xD4, BLN = 0xD8, TRN = 0xDA, DIR = 0xE4, DBR = 0xE6,
            RPS = 0xEE, SCFL = 0xF0, BSU = 0xF2, ESU = 0xF4, STO = 0xF6, NOP = 0xF8,
            OVS = 0x72, STC = 0x74, USC = 0x76, TBM = 0x78, SEC = 0x80;

    public interface Handler {
        /** @param function codigo sin el bit de encadenamiento; TEXT para texto suelto. */
        void control(int function, byte[] params) throws IOException;
    }

    public static void parse(byte[] d, Handler handler) throws IOException {
        boolean chained = false;
        int i = 0;
        while (i < d.length) {
            if (!chained) {
                if (i + 1 < d.length && Sf.u8(d, i) == 0x2B && Sf.u8(d, i + 1) == 0xD3) {
                    chained = true;
                    i += 2;
                } else {                                   // texto sin secuencia de control
                    int j = i + 1;
                    while (j < d.length && !(Sf.u8(d, j) == 0x2B && j + 1 < d.length
                            && Sf.u8(d, j + 1) == 0xD3)) j++;
                    handler.control(TRN, Arrays.copyOfRange(d, i, j));
                    i = j;
                }
                continue;
            }
            int len = Sf.u8(d, i);
            if (len < 2 || i + len > d.length) throw new IOException("PTOCA: secuencia invalida");
            int type = Sf.u8(d, i + 1);
            handler.control(type & 0xFE, Arrays.copyOfRange(d, i + 2, i + len));
            chained = (type & 1) != 0;
            i += len;
        }
    }
}
