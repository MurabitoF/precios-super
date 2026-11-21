## Why

El portal de Precios Claros solo conserva los últimos 7 días de la base SEPA: cada recurso se pisa a la semana y el histórico no se recupera. Sin un archivo propio no hay serie histórica, que es la base de todo el proyecto, y cada semana sin archivar es un punto perdido para siempre.

## What Changes

- Tarea programada que guarda una foto semanal de la base SEPA (el ZIP del miércoles) en almacenamiento propio.
- La tarea corre todos los días y se pone al día sola: si el miércoles de la semana todavía no está archivado y el portal lo sigue publicando, lo archiva. Una corrida fallida se recupera en la siguiente.
- Validación del archivo antes de guardarlo (fecha, tamaño, integridad del ZIP). Un archivo ya guardado nunca se pisa.
- Infraestructura mínima en AWS: un bucket privado y un rol que GitHub Actions asume sin claves guardadas.
- Carga manual del ZIP del 2026-11-04, ya descargado, como primer punto de la serie.
- Tests de toda la funcionalidad: unitarios para las funciones del script y de integración, uno por escenario del spec, contra un emulador local de AWS (floci).
- Validación local con el ZIP real antes de desplegar nada en AWS: lo guardado tiene que coincidir con lo descargado en tamaño, SHA-256 y cantidad de ZIP internos.

Queda afuera: leer el contenido de los ZIP, la base de datos, la API y el dashboard (pasos 2 a 4).

## Capabilities

### New Capabilities

- `archivo-sepa`: conservación semanal de los ZIP crudos de SEPA, con qué se archiva, cuándo, bajo qué nombre y qué pasa cuando el portal falla o publica algo inválido.

### Modified Capabilities

Ninguna.

## Impact

- Repositorio: primer código del proyecto (script de archivo, tests, workflows de GitHub Actions, plantilla de infraestructura).
- Herramientas locales: Docker (para floci) y la CLI de AWS.
- Sistemas externos: cuenta de AWS (S3, IAM) y repositorio público en GitHub; lectura diaria de la API CKAN de `datos.produccion.gob.ar`.
- Costo: unos 16 GB por año en S3, por debajo de USD 0,50 por mes al final del primer año.
