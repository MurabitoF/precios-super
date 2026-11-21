Regla para todo el cambio: los tests a crear son los listados en `design.md` (U1 a U3, I1 a I24) y ninguno se escribe antes de que Franco apruebe esa lista. Crear otro test, o modificar uno existente o sus datos de prueba, requiere su ok.

## 1. Aprobaciones previas (las hace Franco)

- [x] 1.1 Aprobar la lista de tests de `design.md`, o pedir cambios
- [x] 1.2 Aprobar la canasta por defecto: se le propone una lista de unos 25 productos con EAN y nombre, según el criterio de `design.md`
- [x] 1.3 Instalar el SAM CLI

## 2. Índice por producto

- [x] 2.1 Agregar `precio_ean (ean, fecha)` al `schema.sql` del cargador y confirmar que sus tests siguen en verde
- [x] 2.2 Commitear para que la carga programada cree el índice en Turso, y confirmar que existe

## 3. Proyecto de la API

- [x] 3.1 Crear el proyecto Maven en `api/` (Spring Boot 4 con Spring MVC, Java 25, wrapper, adaptador de Lambda, Jackson, SDK de SSM, CRaC; Testcontainers para tests)
- [x] 3.2 Escribir el cliente `Turso` de solo lectura, que recrea su cliente HTTP después de una restauración de SnapStart
- [x] 3.3 Leer el token desde SSM al iniciar, o desde una propiedad en local y en los tests
- [x] 3.4 Escribir `canasta.json` con la lista aprobada en la tarea 1.2

## 4. Consultas y cálculo

- [x] 4.1 Escribir la caché de fechas completas y del catálogo en memoria, con la renovación cuando cambia la última fecha completa (como mucho una consulta cada 10 minutos)
- [x] 4.2 Escribir la búsqueda en el catálogo: texto normalizado, EAN exacto, páginas de a 20
- [x] 4.3 Escribir la consulta de la serie de un producto y la ficha a partir de ella
- [x] 4.4 Escribir el cálculo del índice de una canasta (dos niveles de media geométrica sobre la muestra pareada)
- [x] 4.5 Escribir los tests unitarios U1 a U3 y dejarlos en verde corrigiendo el código, no los tests

## 5. Endpoints

- [x] 5.1 Escribir los controladores de `/fechas`, `/productos`, `/productos/{ean}`, `/productos/{ean}/serie`, `/canasta` y `/canasta/indice`, con las validaciones del spec y errores `application/problem+json`
- [x] 5.2 Configurar CORS para `GET` desde cualquier origen y `Cache-Control` en las respuestas exitosas
- [x] 5.3 Escribir el handler de Lambda con el adaptador de Spring Boot
- [x] 5.4 Escribir los tests de integración I1 a I24 contra libsql-server, con el `schema.sql` del cargador como recurso de prueba
- [x] 5.5 Correrlos y dejarlos en verde corrigiendo el código, no los tests

## 6. Tests en GitHub Actions

- [x] 6.1 Agregar a `.github/workflows/tests.yml` un job que corra `./mvnw verify` en `api/`
- [x] 6.2 Confirmar que el job pasa en un push

## 7. Despliegue (solo con los grupos 2 a 6 en verde)

- [x] 7.1 Crear el token de solo lectura de Turso y guardarlo en SSM como `/precios-super/turso-lectura`, sin que pase por la terminal ni por el repo
- [x] 7.2 Escribir `infra/api.yaml` (SAM): función `java25` en `arm64` con SnapStart, alias `vivo`, Function URL sin autenticación y rol con lectura solo de ese parámetro
- [x] 7.3 Desplegar con `sam build && sam deploy` y anotar la URL
- [x] 7.4 Confirmar que SnapStart está activo en la versión publicada y medir el tiempo de respuesta en frío y en caliente

## 8. Validación con datos reales

- [x] 8.1 Comparar la serie de la Coca-Cola de 2,25 L (EAN 7790895000997) que devuelve la API contra la base, y el índice de la canasta por defecto entre las dos primeras fechas contra un cálculo independiente en shell. Mostrarle los números a Franco
- [x] 8.2 Leer de Turso las filas leídas por cada tipo de pedido y confirmar que ninguno pasa de unos miles
- [x] 8.3 Verificar a mano que el token de solo lectura no puede escribir en la base y que no está en el código ni en el repo

## 9. Cierre

- [x] 9.1 Actualizar `README.md`: la URL pública, ejemplos de cada endpoint con `curl`, cómo se calcula el índice y cómo desplegar
- [x] 9.2 Actualizar `CLAUDE.md` con las decisiones (Lambda con SnapStart, catálogo en memoria, índice de Jevons pareado, token en SSM) y los gotchas encontrados
