package io.github.murabitof.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnitariosTest {

    @Test
    @DisplayName("U1 Índice del ejemplo del spec")
    void indiceDelEjemplo() {
        var pares = new LinkedHashMap<String, List<Indice.Par>>();
        pares.put("A", List.of(new Indice.Par(10000, 11000), new Indice.Par(20000, 20000)));
        pares.put("B", List.of(new Indice.Par(5000, 6000)));
        var r = Indice.calcular(pares);
        assertEquals(4.88, r.productos().get(0).variacion());
        assertEquals(20.00, r.productos().get(1).variacion());
        assertEquals(12.19, r.variacion());
    }

    @Test
    @DisplayName("U2 Normalización de búsqueda")
    void normalizacion() {
        assertEquals("CANUELAS", Datos.normalizar("Cañuelas"));
        assertEquals("CANUELAS", Datos.normalizar("CAÑUELAS"));
        assertEquals("CANUELAS", Datos.normalizar("canuelas"));
    }

    @Test
    @DisplayName("U3 Validación de EAN")
    void validacionDeEan() {
        assertTrue(ApiController.eanValido("7790895000997"));
        assertFalse(ApiController.eanValido("779089500099A"));
        assertFalse(ApiController.eanValido("779089500099"));
        assertFalse(ApiController.eanValido("77908950009970"));
    }
}
