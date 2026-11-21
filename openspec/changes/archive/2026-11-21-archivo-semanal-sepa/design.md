## Context

Motivación en `proposal.md`. Lo observado en el portal el 2026-11-11:

- `package_show?id=sepa-precios` devuelve 7 recursos ZIP, uno por día de la semana, con `name` igual al día (`Miércoles`) y la fecha al final de `description` (`Precios SEPA Minoristas miercoles, 2026-11-04`).
- Cada ZIP pesa entre 231 MB (domingo) y 332 MB. Los de miércoles vistos rondan los 320 MB.
- Se publican alrededor de las 16:19 UTC (13:19 de Argentina) con fecha del mismo día.
- El servidor ignora los pedidos por rango: siempre devuelve el archivo completo.
- Dentro hay un ZIP por comercio; alguno puede venir vacío (0 bytes) todas las semanas. Eso es normal y no invalida el archivo.

Decisiones ya tomadas para el proyecto completo: medición semanal, pipeline y API en Java con Spring Boot, despliegue en AWS, tareas programadas en GitHub Actions.

## Goals / Non-Goals

**Goals:**

- Empezar a archivar esta misma semana, con lo mínimo que funcione.
- Que una corrida fallida no requiera intervención: la siguiente la compensa.
- Que el archivo sea la única fuente de verdad: todo lo posterior se puede rehacer desde los ZIP.

**Non-Goals:**

- Archivar los otros seis días de la semana.
- Reemplazo por un día vecino cuando el miércoles viene mal. Si el ZIP del miércoles no pasa la validación en toda su semana, esa semana se pierde.
- Recomprimir o transformar los ZIP.
- Alertas propias: alcanza con el aviso por mail de GitHub cuando falla un workflow.

## Decisions

### El archivador es un script de shell, no Java

El trabajo es: consultar CKAN, leer una fecha, ver si ya existe, descargar, validar y subir. Con `curl`, `jq`, `unzip` y la CLI de AWS (todo preinstalado en los runners) son unas 30 líneas en `scripts/archivar.sh`.

Alternativa: un programa Java con el SDK de AWS. Suma build, dependencias y tiempo de arranque para algo que no tiene lógica de negocio, y demora el inicio del archivo. Java entra en el paso 2, que es donde está el procesamiento.

### Cron diario para una foto semanal

El workflow corre todos los días a las 19:00 UTC, bien después de la publicación. Cada corrida mira solo el recurso `Miércoles`: si su fecha ya está archivada, termina. Así el portal da hasta 7 corridas para archivar cada miércoles, sin lógica de reintentos.

Alternativa: cron semanal con reintentos explícitos. Más código para el mismo resultado.

### Nombre de objeto por fecha de los datos

`s3://<bucket>/sepa/AAAA-MM-DD.zip`, con la fecha de la descripción. La existencia del objeto es el único estado del sistema: se consulta con `head-object` antes de descargar y la subida usa escritura condicional (`--if-none-match '*'`) para no pisar nunca un objeto.

### Validación

Antes de subir: la fecha cumple `AAAA-MM-DD` y es miércoles, el archivo pesa al menos 100 MB (variable `MIN_BYTES` en el script; los miércoles reales rondan 320 MB) y `unzip -tq` pasa sobre el ZIP externo. No se validan los ZIP internos, porque uno vacío es normal.

### Infraestructura: una plantilla CloudFormation

`infra/archivo.yaml` con tres recursos: bucket S3 privado (acceso público bloqueado), proveedor OIDC de GitHub y rol IAM que solo el workflow de este repo en `main` puede asumir, con permiso de listar, leer y escribir bajo `sepa/`. Se despliega a mano una vez con `aws cloudformation deploy`. Región `us-east-1`.

Alternativas: CDK (demasiado para tres recursos) y claves de acceso en secretos de GitHub (credenciales de larga duración, lo que el spec prohíbe). SAM se suma cuando haya Lambda, en el paso 3.

Sin reglas de ciclo de vida: 16 GB por año en la clase estándar cuestan centavos y los ZIP se van a releer al reprocesar.

### Pruebas: dos niveles, y AWS recién al final

Nada se despliega en AWS hasta que los dos niveles pasen y la validación con datos reales coincida.

El script se escribe como funciones chicas que el archivo de tests carga con `source`. Los tests son shell plano con comparaciones simples, sin framework. Los dos niveles corren en cada push.

**Regla de autorización.** Las dos listas de abajo son los tests que Franco aprobó crear. Cualquier test fuera de estas listas, o cualquier cambio a uno existente o a sus datos de prueba, necesita su ok antes de escribirse. Si un test falla se corrige el código; si el que parece estar mal es el test, se le avisa y se espera.

**Unitarios** (`scripts/archivar_test.sh`, sin red ni Docker):

| # | Caso | Resultado esperado |
|---|---|---|
| U1 | Descripción `...miercoles, 2026-11-04` | fecha `2026-11-04` |
| U2 | Descripción sin fecha | error |
| U3 | Fecha con formato inválido (`04-11-2026`, `2026-13-45`) | error |
| U4 | Día de la semana | `2026-11-04` es miércoles; `2026-11-10` no |
| U5 | Nombre del objeto para `2026-11-04` | `sepa/2026-11-04.zip` |
| U6 | Respuesta de CKAN guardada, con recurso `Miércoles` | devuelve su URL y descripción |
| U7 | Respuesta de CKAN sin recurso `Miércoles` | error |
| U8 | Tamaño mínimo | por debajo se rechaza; igual o por encima se acepta |

**Integración** (`scripts/archivar_integracion_test.sh`, contra floci en Docker), uno por escenario del spec:

| # | Escenario del spec | Verifica |
|---|---|---|
| I1 | Miércoles publicado y todavía no archivado | el objeto existe y su SHA-256 es el del ZIP publicado |
| I2 | Archivo identificado por la fecha de los datos | la clave contiene la fecha de la descripción |
| I3 | Descripción sin fecha reconocible | la corrida falla, bucket vacío |
| I4 | Fecha que no es miércoles | la corrida falla, bucket vacío |
| I5 | Fecha ya archivada | termina bien, sin descargar ni escribir |
| I6 | Un archivo guardado no se pisa | el objeto conserva su SHA-256 original |
| I7 | Falla el día de publicación | una corrida falla, la siguiente archiva |
| I8 | Portal caído | la corrida falla, bucket vacío |
| I9 | Descarga truncada o corrupta | la corrida falla, bucket vacío |
| I10 | Archivo demasiado chico | la corrida falla, bucket vacío |

Cómo se arma sin tocar el script: la CLI de AWS apunta a floci con la variable `AWS_ENDPOINT_URL`; el portal se simula con archivos locales, porque `curl` lee `file://` (la URL de CKAN es una variable); los ZIP de prueba son chicos y `MIN_BYTES` se baja para los tests.

El escenario "Acceso anónimo" no tiene test automático: no está verificado que floci aplique los permisos como AWS, y un test que pasa sin probar nada es peor que no tenerlo. Se verifica a mano contra el bucket real.

**Validación con datos reales.** Antes de desplegar, se corre el script contra floci con el ZIP real del 2026-11-04 (`~/desarrollo/precios-super-data/2026-11-04.zip`) y se compara lo guardado con el original: tamaño en bytes, SHA-256 y cantidad de ZIP internos.

Alternativa descartada: probar directo contra AWS. Es más lento, deja basura en el bucket real y no se puede correr en cada push.

### Primer punto de la serie

El ZIP del 2026-11-04, descargado a mano durante la exploración, se sube con el mismo nombre de objeto que usaría la tarea, después de pasar la misma validación.

## Risks / Trade-offs

- [El portal puede bloquear o cortar descargas desde los runners de GitHub, que están fuera de Argentina] → Es lo primero que se prueba, con una corrida manual del workflow. Si falla, el mismo script corre por cron en una máquina propia.
- [floci no fue probado en este proyecto y puede no soportar CloudFormation o la escritura condicional de S3] → Es la primera tarea de los tests de integración. Si falta CloudFormation, el bucket de prueba se crea con la CLI y la plantilla se valida recién en AWS. Si falta la escritura condicional, se le avisa a Franco antes de cambiar el test I6.
- [GitHub desactiva los workflows programados tras 60 días sin actividad en el repo] → El proyecto va a tener commits frecuentes mientras se construye; revisar cuando quede estable.
- [Si el ZIP del miércoles viene inválido los 7 días, se pierde la semana] → Aceptado. Se vería como una semana de corridas fallidas; ampliar a día vecino si llega a pasar.
- [El portal cambia el nombre del recurso o el formato de la descripción] → El script falla sin guardar nada y GitHub avisa por mail; hay 7 días para corregirlo.
- [Un comercio falta un miércoles puntual] → No es problema del archivo; el cálculo de variaciones del paso 2 ya contempla que el panel cambia.
