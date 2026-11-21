package io.github.murabitof.cargador;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Los ZIP archivados en el bucket, uno por fecha: sepa/AAAA-MM-DD.zip. */
@Component
class Archivo {

    private static final Pattern CLAVE = Pattern.compile("sepa/(\\d{4}-\\d{2}-\\d{2})\\.zip");

    private final S3Client s3;
    private final String bucket;

    Archivo(@Value("${carga.bucket}") String bucket, @Value("${carga.s3-endpoint:}") String endpoint) {
        var b = S3Client.builder().region(Region.US_EAST_1);
        // Solo para floci en los tests.
        if (!endpoint.isEmpty()) b.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        this.s3 = b.build();
        this.bucket = bucket;
    }

    List<LocalDate> fechas() {
        return s3.listObjectsV2Paginator(r -> r.bucket(bucket).prefix("sepa/")).contents().stream()
                .map(o -> CLAVE.matcher(o.key()))
                .filter(m -> m.matches())
                .map(m -> LocalDate.parse(m.group(1)))
                .sorted()
                .toList();
    }

    /** Descarga el ZIP de la fecha a un temporal. Falla si la fecha no está archivada. */
    Path descargar(LocalDate fecha) throws IOException {
        var tmp = Files.createTempFile("sepa-" + fecha, ".zip");
        try (var in = s3.getObject(r -> r.bucket(bucket).key("sepa/" + fecha + ".zip"))) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }
}
