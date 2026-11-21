package io.github.murabitof.cargador;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Cuenta, por EAN, en cuántas filas aparece cada combinación de descripción y marca. */
final class Descripciones {

    record Combinacion(String descripcion, String marca) {}

    // La de más filas; ante empate, la primera en orden alfabético.
    private static final Comparator<Map.Entry<Combinacion, Integer>> MEJOR =
            Map.Entry.<Combinacion, Integer>comparingByValue().reversed()
                    .thenComparing(e -> e.getKey().descripcion())
                    .thenComparing(e -> e.getKey().marca());

    private final Map<String, Map<Combinacion, Integer>> conteo = new HashMap<>();

    void contar(Fila f) {
        conteo.computeIfAbsent(f.ean(), k -> new HashMap<>())
                .merge(new Combinacion(f.descripcion(), f.marca()), 1, Integer::sum);
    }

    Map<String, Combinacion> masFrecuentes() {
        var r = new HashMap<String, Combinacion>(conteo.size() * 2);
        conteo.forEach((ean, c) -> r.put(ean, masFrecuente(c)));
        return r;
    }

    static Combinacion masFrecuente(Map<Combinacion, Integer> conteos) {
        return conteos.entrySet().stream().sorted(MEJOR).findFirst().orElseThrow().getKey();
    }
}
