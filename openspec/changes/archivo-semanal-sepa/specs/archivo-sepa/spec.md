## Purpose

Conservar una foto semanal de los ZIP crudos de la base SEPA de Precios Claros, que el portal borra a los 7 días, para poder construir y reprocesar series históricas de precios.

## ADDED Requirements

### Requirement: Foto semanal del miércoles
El sistema SHALL archivar, una vez por semana, el ZIP de SEPA correspondiente al miércoles, sin modificar su contenido.

#### Scenario: Miércoles publicado y todavía no archivado
- **WHEN** la tarea corre y el portal publica un ZIP de un miércoles cuya fecha no está en el archivo
- **THEN** el ZIP queda guardado en el archivo, byte a byte igual al publicado

#### Scenario: Archivo identificado por la fecha de los datos
- **WHEN** se archiva el ZIP cuya descripción en el portal termina en `2026-11-11`
- **THEN** el objeto queda guardado bajo un nombre que contiene `2026-11-11`, sin importar el día en que corrió la tarea

### Requirement: Fecha tomada del portal
El sistema SHALL obtener la fecha de los datos de la descripción del recurso en el portal, y MUST NOT archivar nada si esa fecha no se puede leer o no corresponde a un miércoles.

#### Scenario: Descripción sin fecha reconocible
- **WHEN** la descripción del recurso del miércoles no termina en una fecha con formato `AAAA-MM-DD`
- **THEN** la corrida falla y no se guarda nada

#### Scenario: Fecha que no es miércoles
- **WHEN** la fecha de la descripción del recurso del miércoles cae en otro día de la semana
- **THEN** la corrida falla y no se guarda nada

### Requirement: Corridas repetibles
El sistema SHALL poder ejecutarse cualquier cantidad de veces sin duplicar ni alterar lo ya archivado.

#### Scenario: Fecha ya archivada
- **WHEN** la tarea corre y la fecha del miércoles publicado ya está en el archivo
- **THEN** la corrida termina bien sin descargar el ZIP ni escribir en el archivo

#### Scenario: Un archivo guardado no se pisa
- **WHEN** existe un objeto para una fecha y el portal publica un ZIP distinto con esa misma fecha
- **THEN** el objeto existente queda intacto

### Requirement: Recuperación automática
El sistema SHALL correr todos los días, de modo que un miércoles que no se pudo archivar se archive en una corrida posterior mientras el portal lo siga publicando.

#### Scenario: Falla el día de publicación
- **WHEN** la corrida del miércoles falla y el jueves el portal sigue publicando el mismo ZIP
- **THEN** la corrida del jueves lo archiva sin intervención manual

#### Scenario: Portal caído
- **WHEN** el portal no responde o devuelve un error
- **THEN** la corrida falla de forma visible y no se guarda nada

### Requirement: Validación antes de guardar
El sistema MUST verificar que lo descargado es un ZIP íntegro y de tamaño plausible antes de guardarlo, y MUST NOT guardar un archivo que no pase esa verificación.

#### Scenario: Descarga truncada o corrupta
- **WHEN** el archivo descargado no pasa la prueba de integridad de ZIP
- **THEN** la corrida falla y no se guarda nada

#### Scenario: Archivo demasiado chico
- **WHEN** el archivo descargado pesa menos que el mínimo configurado
- **THEN** la corrida falla y no se guarda nada

### Requirement: Archivo privado
El archivo MUST NOT ser legible ni escribible públicamente, y la tarea programada MUST acceder a él sin credenciales de larga duración guardadas en el repositorio.

#### Scenario: Acceso anónimo
- **WHEN** alguien sin credenciales intenta leer o escribir un objeto del archivo
- **THEN** el acceso es rechazado
