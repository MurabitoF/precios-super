package io.github.murabitof.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Índice de una canasta entre dos fechas: media geométrica (Jevons) en dos niveles, con igual peso.
 * Por producto, sobre las banderas que lo publicaron en las dos fechas; por canasta, sobre los productos.
 */
final class Indice {

    /** Precio de un producto en una bandera en las dos fechas, en centavos. */
    record Par(long desde, long hasta) {}

    record Producto(String ean, double variacion, int banderas) {}

    /** variacion es null si ningún producto tiene banderas comparables. */
    record Resultado(Double variacion, List<Producto> productos, List<String> excluidos) {}

    static Resultado calcular(Map<String, List<Par>> paresPorEan) {
        var productos = new ArrayList<Producto>();
        var excluidos = new ArrayList<String>();
        double sumaLog = 0;
        for (var e : paresPorEan.entrySet()) {
            var pares = e.getValue();
            if (pares.isEmpty()) {
                excluidos.add(e.getKey());
                continue;
            }
            double logProducto = pares.stream().mapToDouble(p -> Math.log((double) p.hasta() / p.desde())).average().orElseThrow();
            sumaLog += logProducto;
            productos.add(new Producto(e.getKey(), porcentaje(logProducto), pares.size()));
        }
        Double variacion = productos.isEmpty() ? null : porcentaje(sumaLog / productos.size());
        return new Resultado(variacion, productos, excluidos);
    }

    /** Del logaritmo del relativo a variación porcentual, redondeada a dos decimales solo al final. */
    private static double porcentaje(double logRelativo) {
        return BigDecimal.valueOf((Math.exp(logRelativo) - 1) * 100).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
