package io.github.murabitof.api;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@RestController
class ApiController {

    record ProductoResumen(String ean, String descripcion, String marca) {}
    record Busqueda(int pagina, boolean hayMas, List<ProductoResumen> productos) {}
    record Precio(long comercio, long bandera, String nombre, String fecha, long centavos, long sucursales) {}
    record Ficha(String ean, String descripcion, String marca, List<Precio> precios) {}
    record PuntoSerie(String fecha, long centavos, long sucursales) {}
    record SerieBandera(long comercio, long bandera, String nombre, List<PuntoSerie> puntos) {}
    record Serie(String ean, String descripcion, List<SerieBandera> banderas) {}
    record ItemCanasta(String ean, String descripcion) {}
    record Canasta(List<ItemCanasta> productos) {}
    record VariacionProducto(String ean, String descripcion, double variacion, int banderas) {}
    record IndiceCanasta(String desde, String hasta, double variacion, List<VariacionProducto> productos, List<ItemCanasta> excluidos) {}

    static final int POR_PAGINA = 20;
    static final int MAX_CANASTA = 50;
    private static final Pattern EAN = Pattern.compile("\\d{13}");
    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final Datos datos;
    private final List<ItemCanasta> canasta;

    ApiController(Datos datos) throws Exception {
        this.datos = datos;
        try (var in = new ClassPathResource("canasta.json").getInputStream()) {
            this.canasta = List.of(new ObjectMapper().readValue(in, ItemCanasta[].class));
        }
    }

    static boolean eanValido(String ean) {
        return ean != null && EAN.matcher(ean).matches();
    }

    private static <T> ResponseEntity<T> ok(T cuerpo) {
        return ResponseEntity.ok().cacheControl(CACHE).body(cuerpo);
    }

    private static ResponseStatusException error(int estado, String motivo) {
        return new ResponseStatusException(HttpStatusCode.valueOf(estado), motivo);
    }

    @GetMapping("/fechas")
    ResponseEntity<List<String>> fechas() {
        return ok(datos.estado().fechas());
    }

    @GetMapping("/productos")
    ResponseEntity<Busqueda> buscar(@RequestParam String q, @RequestParam(defaultValue = "1") int pagina) {
        var texto = q.strip();
        if (texto.length() < 3) throw error(400, "el texto de búsqueda necesita al menos 3 caracteres");
        if (pagina < 1) throw error(400, "la página empieza en 1");
        var estado = datos.estado();
        List<Datos.Producto> encontrados;
        if (eanValido(texto)) {
            var p = estado.porEan().get(texto);
            encontrados = p == null ? List.of() : List.of(p);
        } else {
            var buscado = Datos.normalizar(texto);
            encontrados = estado.catalogo().stream().filter(p -> p.normalizado().contains(buscado)).toList();
        }
        int desde = Math.min((pagina - 1) * POR_PAGINA, encontrados.size());
        int hasta = Math.min(desde + POR_PAGINA, encontrados.size());
        var productos = encontrados.subList(desde, hasta).stream()
                .map(p -> new ProductoResumen(p.ean(), p.descripcion(), p.marca())).toList();
        return ok(new Busqueda(pagina, hasta < encontrados.size(), productos));
    }

    private Datos.Producto producto(String ean) {
        if (!eanValido(ean)) throw error(400, "el EAN tiene que tener 13 dígitos");
        var p = datos.estado().porEan().get(ean);
        if (p == null) throw error(404, "no hay ningún producto con EAN " + ean);
        return p;
    }

    private String nombreBandera(long comercio, long bandera) {
        return datos.estado().banderas().getOrDefault(List.of(comercio, bandera), "");
    }

    /** Los puntos de la serie agrupados por bandera, en orden de fecha. */
    private Map<List<Long>, List<Datos.Punto>> porBandera(String ean) {
        var r = new LinkedHashMap<List<Long>, List<Datos.Punto>>();
        for (var p : datos.serie(ean)) r.computeIfAbsent(List.of(p.comercio(), p.bandera()), k -> new ArrayList<>()).add(p);
        return r;
    }

    @GetMapping("/productos/{ean}")
    ResponseEntity<Ficha> ficha(@PathVariable String ean) {
        var p = producto(ean);
        var precios = porBandera(ean).values().stream().map(List::getLast)
                .map(u -> new Precio(u.comercio(), u.bandera(), nombreBandera(u.comercio(), u.bandera()), u.fecha(), u.centavos(), u.sucursales()))
                .sorted(Comparator.comparing(Precio::nombre)).toList();
        return ok(new Ficha(p.ean(), p.descripcion(), p.marca(), precios));
    }

    @GetMapping("/productos/{ean}/serie")
    ResponseEntity<Serie> serie(@PathVariable String ean) {
        var p = producto(ean);
        var banderas = porBandera(ean).entrySet().stream()
                .map(e -> new SerieBandera(e.getKey().get(0), e.getKey().get(1), nombreBandera(e.getKey().get(0), e.getKey().get(1)),
                        e.getValue().stream().map(u -> new PuntoSerie(u.fecha(), u.centavos(), u.sucursales())).toList()))
                .sorted(Comparator.comparing(SerieBandera::nombre)).toList();
        return ok(new Serie(p.ean(), p.descripcion(), banderas));
    }

    @GetMapping("/canasta")
    ResponseEntity<Canasta> canasta() {
        return ok(new Canasta(canasta));
    }

    @GetMapping("/canasta/indice")
    ResponseEntity<IndiceCanasta> indice(@RequestParam(required = false) String desde, @RequestParam(required = false) String hasta,
                                        @RequestParam(required = false) List<String> ean) {
        List<String> eans;
        if (ean == null || ean.isEmpty()) {
            eans = canasta.stream().map(ItemCanasta::ean).toList();
        } else {
            if (ean.size() > MAX_CANASTA) throw error(400, "una canasta admite hasta " + MAX_CANASTA + " productos");
            ean.stream().filter(e -> !eanValido(e)).findFirst()
                    .ifPresent(e -> { throw error(400, "EAN inválido: " + e); });
            eans = ean.stream().distinct().toList();
        }
        var fechas = datos.estado().fechas();
        if (desde == null && hasta == null) {
            if (fechas.size() < 2) throw error(422, "hacen falta al menos dos fechas disponibles");
            desde = fechas.get(fechas.size() - 2);
            hasta = fechas.getLast();
        }
        if (desde == null || hasta == null) throw error(400, "desde y hasta van juntos");
        for (var f : List.of(desde, hasta)) {
            try {
                LocalDate.parse(f);
            } catch (DateTimeParseException e) {
                throw error(400, "fecha inválida: " + f);
            }
            if (!datos.estado().completas().contains(f)) throw error(400, "no es una fecha disponible: " + f);
        }
        if (desde.compareTo(hasta) >= 0) throw error(400, "desde tiene que ser anterior a hasta");

        var r = Indice.calcular(datos.pares(eans, desde, hasta));
        if (r.variacion() == null) throw error(422, "ningún producto de la canasta se puede comparar entre esas fechas");
        var productos = r.productos().stream()
                .map(p -> new VariacionProducto(p.ean(), descripcion(p.ean()), p.variacion(), p.banderas())).toList();
        var excluidos = r.excluidos().stream().map(e -> new ItemCanasta(e, descripcion(e))).toList();
        return ok(new IndiceCanasta(desde, hasta, r.variacion(), productos, excluidos));
    }

    /** El nombre legible de la canasta por defecto, o la descripción del catálogo. */
    private String descripcion(String ean) {
        return canasta.stream().filter(i -> i.ean().equals(ean)).map(ItemCanasta::descripcion).findFirst()
                .orElseGet(() -> {
                    var p = datos.estado().porEan().get(ean);
                    return p == null ? "" : p.descripcion();
                });
    }
}
