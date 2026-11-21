package io.github.murabitof.cargador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.murabitof.cargador.Descripciones.Combinacion;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UnitariosTest {

    @Test
    @DisplayName("U1 Línea de producto válida")
    void lineaValida() {
        var f = Fila.parse("12|1|104|7790070012050|1|ACEITE GIRASOL COCINERO PET 900 CC|900|ml|COCINERO|4370.50|4855.55|1|ltr|3500.00|PROMO||");
        assertEquals(new Fila(12, 1, "7790070012050", "1", "ACEITE GIRASOL COCINERO PET 900 CC", "COCINERO", 437050), f);
    }

    @Test
    @DisplayName("U2 Precios a centavos")
    void preciosACentavos() {
        assertEquals(OptionalLong.of(60), Fila.centavos(".60"));
        assertEquals(OptionalLong.of(150000), Fila.centavos("1500"));
        assertEquals(OptionalLong.of(3290050), Fila.centavos("32900.5"));
        assertEquals(OptionalLong.of(3290000), Fila.centavos("32900.00"));
    }

    @Test
    @DisplayName("U3 Precios que no son válidos")
    void preciosInvalidos() {
        assertTrue(Fila.centavos("").isEmpty());
        assertTrue(Fila.centavos("abc").isEmpty());
        assertTrue(Fila.centavos("1,5").isEmpty());
    }

    @Test
    @DisplayName("U4 Mediana con cantidad impar")
    void medianaImpar() {
        assertEquals(12000, Resumen.mediana(new long[] {10000, 30000, 12000}, 3));
    }

    @Test
    @DisplayName("U5 Mediana con cantidad par, medio centavo hacia arriba")
    void medianaPar() {
        assertEquals(10001, Resumen.mediana(new long[] {10000, 10001}, 2));
    }

    @Test
    @DisplayName("U6 Cabecera con BOM")
    void cabeceraConBom() {
        assertTrue(Fila.mismaCabecera("﻿" + Fila.CABECERA, Fila.CABECERA));
    }

    @Test
    @DisplayName("U7 Combinación más frecuente, con y sin empate")
    void combinacionMasFrecuente() {
        var a = new Combinacion("A", "M");
        var b = new Combinacion("B", "M");
        assertEquals(a, Descripciones.masFrecuente(Map.of(b, 2, a, 5)));

        var gaseosa = new Combinacion("Gaseosa cola", "M");
        var coca = new Combinacion("Coca cola", "M");
        assertEquals(coca, Descripciones.masFrecuente(Map.of(gaseosa, 3, coca, 3)));
    }
}
