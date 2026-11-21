package io.github.murabitof.cargador;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Precios de lista de un comercio agrupados por bandera y EAN, para sacar la mediana de cada grupo. */
final class Resumen {

    record Precio(int comercio, int bandera, String ean, long centavos, int sucursales) {}

    private record Clave(int comercio, int bandera, String ean) {}

    private static final class Valores {
        long[] v = new long[4];
        int n;

        void agregar(long x) {
            if (n == v.length) v = Arrays.copyOf(v, n * 2);
            v[n++] = x;
        }
    }

    private final Map<Clave, Valores> grupos = new HashMap<>();

    void agregar(Fila f) {
        grupos.computeIfAbsent(new Clave(f.comercio(), f.bandera(), f.ean()), k -> new Valores()).agregar(f.centavos());
    }

    List<Precio> medianas() {
        var r = new ArrayList<Precio>(grupos.size());
        grupos.forEach((k, g) -> r.add(new Precio(k.comercio(), k.bandera(), k.ean(), mediana(g.v, g.n), g.n)));
        return r;
    }

    /** Con cantidad par, el promedio de los dos centrales redondeado al centavo, el medio centavo hacia arriba. */
    static long mediana(long[] v, int n) {
        Arrays.sort(v, 0, n);
        return n % 2 == 1 ? v[n / 2] : (v[n / 2 - 1] + v[n / 2] + 1) / 2;
    }
}
