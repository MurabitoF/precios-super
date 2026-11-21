# precios-super

Seguimiento de precios de supermercados argentinos a partir de la base pública SEPA (Precios Claros): archivo semanal, series históricas, API y dashboard de inflación por producto.

Estado: el archivo semanal, la carga a la base, la API pública y el dashboard están andando.

**Probalo:** https://d1qcidvnny7mze.cloudfront.net

## Archivo semanal

El portal de Precios Claros publica un ZIP por día de la semana y lo pisa a los 7 días. Sin un archivo propio no hay serie histórica, así que el primer paso es guardar una foto por semana: el ZIP del miércoles, sin tocarlo.

[`scripts/archivar.sh`](scripts/archivar.sh) corre todos los días a las 19:00 UTC en GitHub Actions, en un runner propio con IP argentina (ver [Dónde corre el archivador](#dónde-corre-el-archivador)):

1. Consulta la API CKAN del portal y toma el recurso `Miércoles`.
2. Lee la fecha del final de la descripción y verifica que sea un miércoles.
3. Si `s3://<bucket>/sepa/AAAA-MM-DD.zip` ya existe, termina sin descargar nada.
4. Si no, descarga el ZIP, verifica tamaño mínimo e integridad (`unzip -t`) y lo sube con escritura condicional, que nunca pisa un objeto.

Como corre a diario, una corrida fallida se compensa sola en la siguiente: el portal da 7 días para archivar cada miércoles. Si falla, GitHub avisa por mail.

**Infraestructura** ([`infra/archivo.yaml`](infra/archivo.yaml), CloudFormation): un bucket S3 privado y un rol IAM que solo el workflow del archivador en `main` puede asumir por OIDC, sin claves guardadas. Se despliega una vez:

```sh
aws cloudformation deploy --stack-name precios-super-archivo --template-file infra/archivo.yaml \
  --capabilities CAPABILITY_NAMED_IAM --region us-east-1
```

### Dónde corre el archivador

Desde el 2026-11-17 el portal de datos responde 403 a toda IP de la nube: falla igual desde los runners de GitHub (Azure) y desde AWS, con cualquier `User-Agent`, y responde 200 desde una conexión hogareña en Argentina. Por eso el archivador corre en un **runner propio de GitHub Actions** en una máquina de Franco.

Un runner propio en un repo público permitiría que un pull request desde un fork corra código en esa máquina. Para evitarlo, el runner y el workflow viven en un **repo privado aparte** (`precios-super-archivador`), cuyo workflow hace checkout de este repo y corre `scripts/archivar.sh`. Este repo no tiene runners propios. El rol del archivador confía solo en ese repo privado, y el runner corre como servicio de systemd con un usuario sin privilegios.

Alcanza con que la máquina esté prendida algún día de la semana: un job programado espera hasta 24 horas a que aparezca el runner, y el portal conserva cada miércoles 7 días. Si hace falta rescatar un miércoles a mano, desde cualquier conexión argentina:

```sh
BUCKET=<bucket> scripts/archivar.sh   # con credenciales de AWS que puedan escribir en sepa/
```

Para reinstalar el runner (en el repo privado, Settings → Actions → Runners → New self-hosted runner, con la etiqueta `sepa-ar`):

```sh
sudo useradd --system --create-home --home-dir /var/lib/gh-runner --shell /usr/bin/nologin gh-runner
# descargar el runner en /var/lib/gh-runner/runner, verificar su SHA-256 y darle la carpeta a gh-runner
sudo -u gh-runner ./config.sh --unattended --url https://github.com/MurabitoF/precios-super-archivador \
  --token <token de registro> --name <nombre> --labels sepa-ar
sudo ./svc.sh install gh-runner && sudo ./svc.sh start
```

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

## API pública

API REST de solo lectura, sin credenciales: **https://3zkxuhrrk2no6b7wlqneqh63ay0vcgoq.lambda-url.us-east-1.on.aws**

```sh
curl https://3zkxuhrrk2no6b7wlqneqh63ay0vcgoq.lambda-url.us-east-1.on.aws/fechas                              # fechas disponibles
curl "https://3zkxuhrrk2no6b7wlqneqh63ay0vcgoq.lambda-url.us-east-1.on.aws/productos?q=yerba&pagina=1"        # búsqueda por descripción, marca o EAN (de a 20)
curl https://3zkxuhrrk2no6b7wlqneqh63ay0vcgoq.lambda-url.us-east-1.on.aws/productos/7790895000997             # ficha: último precio de cada bandera
curl https://3zkxuhrrk2no6b7wlqneqh63ay0vcgoq.lambda-url.us-east-1.on.aws/productos/7790895000997/serie       # serie semanal por bandera
curl https://3zkxuhrrk2no6b7wlqneqh63ay0vcgoq.lambda-url.us-east-1.on.aws/canasta                             # canasta por defecto (24 productos)
curl https://3zkxuhrrk2no6b7wlqneqh63ay0vcgoq.lambda-url.us-east-1.on.aws/canasta/indice                      # variación de la canasta entre las dos últimas fechas
curl https://3zkxuhrrk2no6b7wlqneqh63ay0vcgoq.lambda-url.us-east-1.on.aws/canasta/serie                       # variación de cada semana y acumulada encadenada
curl "https://3zkxuhrrk2no6b7wlqneqh63ay0vcgoq.lambda-url.us-east-1.on.aws/canasta/indice?desde=2026-11-04&hasta=2026-11-11&ean=7790895000997&ean=7792710000182"
```

Los precios van en centavos enteros y las fechas en `AAAA-MM-DD`. Solo aparecen fechas que la carga marcó como completas. Los errores vienen como `application/problem+json`.

**Índice de la canasta.** Para cada producto se toman solo las banderas que lo publicaron en las dos fechas (muestra pareada: una cadena que falta una semana no se confunde con un cambio de precio) y se calcula la media geométrica de sus variaciones. El índice es la media geométrica de los productos, con igual peso cada uno (método de Jevons, el del primer nivel del IPC del INDEC). La acumulada de `/canasta/serie` encadena las variaciones semanales (cada semana compara solo lo que está en sus dos fechas), como hace el INDEC entre meses. La canasta por defecto ([`canasta.json`](api/src/main/resources/canasta.json)) tiene 24 productos de consumo masivo que publican al menos 9 banderas; no incluye leche, arroz ni azúcar porque cada cadena vende su propia marca.

**Cómo funciona.** Spring Boot 4 en AWS Lambda ([`api/`](api/)) con SnapStart: la función se inicializa al publicar cada versión y cada arranque en frío restaura esa foto, en alrededor de un segundo en lugar de once. El catálogo de 77 mil productos vive en memoria para buscar sin leer la base, y se renueva cuando aparece una carga nueva. La serie de un producto lee unas 40 filas de Turso y el índice de la canasta, unas 900. Lee la base con un token de Turso de solo lectura guardado en SSM Parameter Store, que solo el rol de la función puede leer.

**Despliegue** ([`infra/api.yaml`](infra/api.yaml), SAM), una vez y después de cada cambio:

```sh
turso db tokens create precios-super --read-only --expiration never | tr -d '\n' > /tmp/t && \
  aws ssm put-parameter --name /precios-super/turso-lectura --type SecureString --value file:///tmp/t; rm /tmp/t   # solo la primera vez
sam build --template-file infra/api.yaml
sam deploy --stack-name precios-super-api --capabilities CAPABILITY_IAM --resolve-s3 --region us-east-1
```

Rotar el token: actualizar el parámetro y volver a desplegar, que publica una versión nueva con una foto nueva.

## Dashboard

**https://d1qcidvnny7mze.cloudfront.net**: sitio estático en React con TypeScript ([`web/`](web/)), construido con Vite, que lee la API pública desde el navegador.

- **Inicio:** variación de la canasta en la última semana y acumulada, gráfico semana a semana y detalle por producto, con los que no se pudieron comparar.
- **Búsqueda** (`/buscar?q=yerba`): por descripción, marca o EAN.
- **Producto** (`/producto/7790895000997`): último precio en cada cadena, de menor a mayor, y gráfico del precio semanal de las 8 más baratas; una semana sin precio queda como hueco en la línea.

Se sirve desde un bucket S3 privado con CloudFront delante ([`infra/web.yaml`](infra/web.yaml)): solo CloudFront puede leer el bucket (Origin Access Control) y los 403/404 devuelven `index.html`, así las direcciones de búsqueda y de producto se pueden abrir directo.

**Publicar**, desde la raíz del repo (el stack se crea una vez; bucket e ID de la distribución salen de sus outputs):

```sh
aws cloudformation deploy --template-file infra/web.yaml --stack-name precios-super-web --region us-east-1   # solo la primera vez
cd web && npm ci && VITE_API_URL=https://3zkxuhrrk2no6b7wlqneqh63ay0vcgoq.lambda-url.us-east-1.on.aws npm run build
aws s3 sync dist/assets s3://<bucket>/assets --cache-control "public, max-age=31536000, immutable"
aws s3 cp dist/index.html s3://<bucket>/index.html --cache-control "no-cache"
aws cloudfront create-invalidation --distribution-id <id> --paths /index.html
```

Los archivos de `assets/` llevan un hash en el nombre y se cachean para siempre; `index.html` no se cachea y es lo único que se invalida.

## Tests

Corren en cada push ([`tests.yml`](.github/workflows/tests.yml)).

**Archivador:** shell plano, sin framework.

```sh
scripts/archivar_test.sh                           # unitarios: sin red ni Docker
docker run -d --rm -p 4566:4566 floci/floci:2.4.0  # emulador de AWS
scripts/archivar_integracion_test.sh               # integración: uno por escenario del spec
```

Los de integración simulan el portal con archivos locales (`file://`) y ZIP de prueba chicos, y usan [floci](https://hub.docker.com/r/floci/floci) en lugar de AWS. El único escenario sin test automático es "acceso anónimo rechazado", porque floci no aplica los permisos como AWS: se verificó a mano contra el bucket real.

**Cargador y API:** JUnit, con un test por escenario del spec.

```sh
cd cargador && ./mvnw verify   # requiere Docker: Testcontainers levanta libsql-server y floci
cd api && ./mvnw verify        # requiere Docker: libsql-server; los endpoints se prueban con MockMvc
```

Los ZIP de prueba se arman en memoria desde CSV escrito en cada test. Los de integración corren contra libsql-server, el mismo servidor que usa Turso, y floci para el archivo. Los escenarios de acceso (el rol no puede escribir en el bucket, la única credencial guardada es el token) se verificaron a mano contra AWS y GitHub.

**Dashboard:** Vitest con Testing Library y jsdom, con la API simulada en cada test.

```sh
cd web && npm ci && npm test
```

El escenario "pantalla angosta" (360 px sin desplazamiento horizontal) no tiene test automático, porque jsdom no calcula el diseño: se verificó en Chromium emulando un celular.

El diseño y los escenarios están en [`openspec/`](openspec/).
