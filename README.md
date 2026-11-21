# precios-super

Seguimiento de precios de supermercados argentinos a partir de la base pública SEPA (Precios Claros): archivo semanal, series históricas, API y dashboard de inflación por producto.

Estado: el archivo semanal y la carga a la base están andando. Lo siguiente es la API.

## Archivo semanal

El portal de Precios Claros publica un ZIP por día de la semana y lo pisa a los 7 días. Sin un archivo propio no hay serie histórica, así que el primer paso es guardar una foto por semana: el ZIP del miércoles, sin tocarlo.

[`scripts/archivar.sh`](scripts/archivar.sh) corre todos los días a las 19:00 UTC en GitHub Actions ([`archivar.yml`](.github/workflows/archivar.yml)):

1. Consulta la API CKAN del portal y toma el recurso `Miércoles`.
2. Lee la fecha del final de la descripción y verifica que sea un miércoles.
3. Si `s3://<bucket>/sepa/AAAA-MM-DD.zip` ya existe, termina sin descargar nada.
4. Si no, descarga el ZIP, verifica tamaño mínimo e integridad (`unzip -t`) y lo sube con escritura condicional, que nunca pisa un objeto.

Como corre a diario, una corrida fallida se compensa sola en la siguiente: el portal da 7 días para archivar cada miércoles. Si falla, GitHub avisa por mail.

**Infraestructura** ([`infra/archivo.yaml`](infra/archivo.yaml), CloudFormation): un bucket S3 privado y un rol IAM que solo el workflow de este repo en `main` puede asumir por OIDC, sin claves guardadas. Se despliega una vez:

```sh
aws cloudformation deploy --stack-name precios-super-archivo --template-file infra/archivo.yaml \
  --capabilities CAPABILITY_NAMED_IAM --region us-east-1
```

El workflow toma el bucket y el rol de las variables del repo `BUCKET` y `ROLE_ARN`.

## Carga de precios

Un ZIP trae 14,3 millones de filas, una por sucursal y producto. El cargador ([`cargador/`](cargador/), Java 25 con Spring Boot) las resume a **un precio por producto, bandera y semana**: la mediana del precio de lista entre las sucursales de esa bandera. Quedan unas 240 mil filas por semana.

Qué entra al cálculo: solo filas con EAN real (`productos_ean = 1`), de 13 dígitos, que no empiece con 2 (códigos internos de cada tienda) y con precio de lista mayor a 0. Los precios de promoción no se usan. El catálogo de productos guarda la descripción y la marca más frecuentes de la semana.

Corre todos los días a las 20:00 UTC en GitHub Actions ([`cargar.yml`](.github/workflows/cargar.yml)), una hora después del archivador:

1. Lista los ZIP del archivo y les resta las fechas ya completas en la base.
2. Por cada fecha pendiente, en orden: la marca como incompleta, borra sus precios, lee el ZIP comercio por comercio y escribe banderas, precios y productos en lotes de 1.000 filas.
3. Recién al final marca la fecha como completa. Una fecha a medio cargar nunca se ve como válida y la próxima corrida la rehace entera.

Si una fecha falla, sigue con las demás y la corrida termina con error (aviso por mail de GitHub). Con una fecha como argumento (o el campo `fecha` del disparo manual) recarga solo esa. Toda la base se puede rehacer desde el archivo.

**Base:** [Turso](https://turso.tech) (libSQL, compatible con SQLite), plan gratuito. Una carga semanal escribe unas 560 mil filas de los 10 millones mensuales y ocupa unos 25 MB. El cargador habla la API HTTP de Turso con un cliente propio ([`Turso.java`](cargador/src/main/java/io/github/murabitof/cargador/Turso.java)): el driver JDBC de libSQL no implementa `executeUpdate()` ni lotes. Las tablas ([`schema.sql`](cargador/src/main/resources/schema.sql)) se crean al arrancar:

```
carga     (fecha PK, completa_en, filas)          completa_en nulo = fecha incompleta
bandera   (id_comercio, id_bandera, razon_social, nombre, fecha)
producto  (ean PK, descripcion, marca, fecha)
precio    (fecha, id_comercio, id_bandera, ean, centavos, sucursales)
```

**Infraestructura:** el workflow lee el archivo con el rol `precios-super-cargador` ([`infra/cargador.yaml`](infra/cargador.yaml)), que asume por OIDC y que solo puede leer. A Turso entra con un token limitado a la base, guardado como secreto del repo. Se arma una vez:

```sh
aws cloudformation deploy --stack-name precios-super-cargador --template-file infra/cargador.yaml \
  --capabilities CAPABILITY_NAMED_IAM --region us-east-1 --parameter-overrides BucketName=<bucket>
turso db create precios-super --location aws-us-east-1
turso db tokens create precios-super --expiration none | gh secret set TURSO_TOKEN
gh variable set TURSO_URL --body "$(turso db show precios-super --url)"
gh variable set CARGADOR_ROLE_ARN --body <RoleArn del stack>
```

Si el token se filtra: `turso db tokens invalidate precios-super` y se genera otro.

Para correrlo en local contra un servidor libSQL:

```sh
docker run -d --rm -p 8080:8080 ghcr.io/tursodatabase/libsql-server:v0.24.33
eval "$(aws configure export-credentials --format env --region us-east-1)"  # el SDK de Java no lee la sesión de `aws login`
cd cargador && ./mvnw -q -Dmaven.test.skip package
CARGA_BUCKET=<bucket> java -jar target/cargador-*.jar [AAAA-MM-DD]
```

## Tests

Corren en cada push ([`tests.yml`](.github/workflows/tests.yml)).

**Archivador:** shell plano, sin framework.

```sh
scripts/archivar_test.sh                           # unitarios: sin red ni Docker
docker run -d --rm -p 4566:4566 floci/floci:2.4.0  # emulador de AWS
scripts/archivar_integracion_test.sh               # integración: uno por escenario del spec
```

Los de integración simulan el portal con archivos locales (`file://`) y ZIP de prueba chicos, y usan [floci](https://hub.docker.com/r/floci/floci) en lugar de AWS. El único escenario sin test automático es "acceso anónimo rechazado", porque floci no aplica los permisos como AWS: se verificó a mano contra el bucket real.

**Cargador:** JUnit, con un test por escenario del spec.

```sh
cd cargador && ./mvnw verify   # requiere Docker: Testcontainers levanta libsql-server y floci
```

Los ZIP de prueba se arman en memoria desde CSV escrito en cada test. Los de integración corren contra libsql-server, el mismo servidor que usa Turso, y floci para el archivo. Los escenarios de acceso (el rol no puede escribir en el bucket, la única credencial guardada es el token) se verificaron a mano contra AWS y GitHub.

El diseño y los escenarios están en [`openspec/`](openspec/).
