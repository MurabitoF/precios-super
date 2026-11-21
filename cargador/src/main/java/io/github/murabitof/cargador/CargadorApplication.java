package io.github.murabitof.cargador;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

/** Sin argumentos carga toda fecha archivada que no esté completa; con una fecha, recarga esa fecha. */
@SpringBootApplication
public class CargadorApplication {

    public static void main(String[] args) {
        var ctx = SpringApplication.run(CargadorApplication.class, args);
        var cargador = ctx.getBean(Cargador.class);
        boolean ok = args.length == 0 ? cargador.ponerAlDia() : cargador.recargar(LocalDate.parse(args[0]));
        System.exit(SpringApplication.exit(ctx, () -> ok ? 0 : 1));
    }

    @Component
    static class Cargador {

        private final Archivo archivo;
        private final Carga carga;
        private final Turso turso;

        Cargador(Archivo archivo, Carga carga, Turso turso) {
            this.archivo = archivo;
            this.carga = carga;
            this.turso = turso;
        }

        /** Carga en orden las fechas pendientes; una falla no frena a las demás. Devuelve false si alguna falló. */
        boolean ponerAlDia() {
            var completas = new HashSet<LocalDate>();
            turso.consultar("SELECT fecha FROM carga WHERE completa_en IS NOT NULL")
                    .forEach(f -> completas.add(LocalDate.parse((String) f.get(0))));
            var pendientes = new ArrayList<>(archivo.fechas());
            pendientes.removeAll(completas);
            System.out.println("Fechas pendientes: " + pendientes);
            boolean ok = true;
            for (var fecha : pendientes) ok &= recargar(fecha);
            return ok;
        }

        boolean recargar(LocalDate fecha) {
            try {
                var zip = archivo.descargar(fecha);
                try {
                    carga.cargar(fecha, zip);
                } finally {
                    Files.delete(zip);
                }
                return true;
            } catch (Exception e) {
                System.err.println(fecha + ": falló la carga: " + e);
                return false;
            }
        }
    }
}
