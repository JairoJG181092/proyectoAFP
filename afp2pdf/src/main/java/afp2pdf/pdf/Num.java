package afp2pdf.pdf;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Numeros en sintaxis PDF. */
public final class Num {
    public static String of(double v) {
        if (v == Math.rint(v)) return Long.toString((long) v);
        return BigDecimal.valueOf(v).setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
