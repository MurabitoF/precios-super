## Context

Motivación en `proposal.md`; comportamiento en `specs/dashboard/spec.md` y en el delta de `specs/api-precios/spec.md`. Estado de partida, al 2026-11-21:

- La API pública está en una Function URL de Lambda (`infra/api.yaml`), con CORS abierto para `GET`, `Cache-Control` de una hora y errores `application/problem+json`. Responde `/fechas`, `/productos`, `/productos/{ean}`, `/productos/{ean}/serie`, `/canasta` y `/canasta/indice`.
- El índice de una canasta entre dos fechas ya está en `Indice.calcular` (Jevons en dos niveles sobre muestra pareada). `Datos` guarda en memoria el catálogo y las fechas, y los renueva cuando cambia el último `completa_en` (la "versión" de los datos).
- Hay tres fechas cargadas o en camino; la serie crece una fecha por semana.
- Franco apunta a puestos fullstack Java: el front se elige por lo que piden esos avisos (React con TypeScript).
- Node 22 está instalado en la máquina de Franco.

## Goals / Non-Goals

**Goals:**

- Que el inicio cargue con dos pedidos a la API (serie de la canasta y detalle de la última semana) y se vea bien en un celular.
- Un sitio estático: sin servidor propio, dentro de la capa gratuita y fácil de volver a publicar.
- Tests de cada escenario del spec sin depender de la API real.

**Non-Goals:**

- Canastas armadas por el usuario, dominio propio, cuentas.
- Renderizado en el servidor o SEO: es una herramienta para probar el proyecto, no un sitio de contenido.
- Despliegue automático: se publica a mano con tres comandos, como la API.
- Caché de la API en CloudFront: la API ya responde rápido en caliente y sus respuestas se cachean en el navegador.

## Decisions

### Serie de la canasta en la API, calculada una vez por versión de datos

`GET /canasta/serie` recorre los pares de fechas consecutivas y, para cada uno, reutiliza la consulta de pares y `Indice.calcular` del índice actual. La acumulada es el producto de los relativos semanales (`Π(1 + v) − 1`), con los relativos sin redondear; recién la respuesta redondea a dos decimales. Un par sin productos comparables va con `variacion: null` y no entra en el producto.

La serie de la canasta por defecto se guarda en memoria junto con la versión de `Datos`: se calcula la primera vez que alguien la pide después de una carga nueva (unas 900 filas leídas por par de fechas) y después sale de memoria. Las canastas propias se calculan en cada pedido, acotadas por el límite de 50 EAN.

Respuesta:

```
GET /canasta/serie   {acumulada, desde, hasta, semanas: [{desde, hasta, variacion}]}
```

Encadenar semanas es lo que hace el INDEC entre meses: cada semana compara solo lo que está en sus dos fechas, así que una cadena que falta una semana no ensucia el resto de la serie.

Alternativas: que el navegador pida `/canasta/indice` por cada par (una llamada por semana en cada visita, más de 50 al año); el índice directo entre la primera y la última fecha (pierde los productos y cadenas que no están en las dos puntas, y deja de ser comparable a medida que pasa el tiempo).

### React con TypeScript y Vite, en `web/`

SPA con tres rutas de React Router:

```
/                    inicio: canasta
/buscar?q=texto      búsqueda
/producto/:ean       página de producto
```

El acceso a la API es un módulo chico (`api.ts`) con una función por endpoint, tipada, y un hook (`useApi`) que expone carga, error y reintento. La URL base de la API se fija al compilar con `VITE_API_URL`.

Formatos en un módulo `formato.ts` con `Intl.NumberFormat("es-AR")`: pesos con dos decimales, variaciones con signo y fechas `dd/mm/aaaa` armadas a partir del texto `AAAA-MM-DD`, sin pasar por `Date` para no correrlas por la zona horaria.

Estilos en CSS propio con variables para colores, claro y oscuro según el sistema, sin biblioteca de componentes. Las tablas anchas van dentro de un contenedor con desplazamiento propio.

Alternativas: TanStack Query para el acceso a datos (caché y reintentos listos, pero una dependencia más para tres pantallas que piden pocas cosas); Next.js (renderizado en servidor que no hace falta y que complica publicar en S3); una biblioteca de componentes (más peso y menos control del diseño en celular).

### Gráficos con Recharts

Recharts, el que más se usa con React. Dos gráficos:

- Inicio: barras con la variación de cada semana, con un color para subas y otro para bajas.
- Producto: líneas del precio semanal por cadena. Una semana sin dato va como `null` y la línea queda cortada (`connectNulls={false}`), así una ausencia no se dibuja como precio. El gráfico dibuja como mucho las 8 cadenas más baratas (los colores categóricos que se distinguen entre sí); la tabla muestra todas.

La paleta y la forma de cada gráfico siguen la guía de visualización del proyecto, con colores validados para claro y oscuro.

Alternativa: Chart.js (sin componentes de React, hay que envolverlo).

### Publicación en S3 privado con CloudFront

`infra/web.yaml`:

- Bucket privado (acceso público bloqueado, `BucketOwnerEnforced`).
- Distribución de CloudFront con Origin Access Control: solo CloudFront lee el bucket.
- `index.html` como raíz y como respuesta a 403 y 404 con estado 200, para que las rutas de React (`/producto/...`) se puedan abrir directo.
- Política de caché administrada `CachingOptimized`, HTTPS obligatorio, `PriceClass_100` (América del Norte y Europa; Sudamérica se sirve desde ahí sin costo extra en la capa gratuita).

Publicar:

```sh
cd web && VITE_API_URL=<url de la API> npm run build
aws s3 sync dist/assets s3://<bucket>/assets --cache-control "public, max-age=31536000, immutable"
aws s3 cp dist/index.html s3://<bucket>/index.html --cache-control "no-cache"
aws cloudfront create-invalidation --distribution-id <id> --paths /index.html
```

Los archivos de `assets/` llevan un hash en el nombre, así que se cachean para siempre; `index.html` no se cachea y es lo único que se invalida.

Alternativas: GitHub Pages (más simple, pero fuera de AWS); hosting de Amplify (resuelve todo, pero esconde la infraestructura que el portfolio quiere mostrar); bucket público con sitio web de S3 (sin HTTPS).

### Pruebas

**Regla de autorización.** Las listas de abajo son los tests que se proponen crear. Ninguno se escribe hasta que Franco apruebe esta lista. Cualquier test fuera de ella, o cualquier cambio a uno existente o a sus datos de prueba, necesita su ok.

**API** (MockMvc contra libsql-server, como I1 a I24), uno por escenario del requisito nuevo:

| # | Escenario |
|---|---|
| I25 | Dos semanas encadenadas |
| I26 | Semana sin productos comparables |
| I27 | Una sola fecha disponible |
| I28 | Carga nueva |

**Web, unitarios** (Vitest, sin navegador):

| # | Caso | Resultado esperado |
|---|---|---|
| W1 | `610000` centavos | `$ 6.100,00` |
| W2 | Variaciones `3`, `-1.5` y `0` | `+3,00 %`, `-1,50 %` y `0,00 %` |
| W3 | Fecha `2026-11-04` | `04/11/2026` |

**Web, pantallas** (Vitest con Testing Library y jsdom, con la API simulada en cada test), uno por escenario del spec del dashboard:

| # | Escenario del spec |
|---|---|
| W4 | Canasta con varias semanas |
| W5 | Detalle por producto |
| W6 | Producto excluido |
| W7 | Resultados |
| W8 | Más resultados |
| W9 | Sin resultados |
| W10 | Texto corto |
| W11 | Producto en varias cadenas |
| W12 | Precio de una semana anterior |
| W13 | Semana faltante (los datos del gráfico llevan `null` en esa semana) |
| W14 | Producto inexistente |
| W15 | Abrir la página de un producto |
| W16 | Abrir una búsqueda |
| W17 | API caída |
| W18 | Reintento |
| W19 | Explicación del índice |

Los escenarios de formato (`Precio`, `Variación negativa`) los cubren W1 y W2. "Pantalla angosta" no tiene test automático: jsdom no calcula el diseño. Se verifica a mano a 360 px en el navegador, en las tres pantallas.

**Validación final.** Con el sitio publicado: las tres pantallas en un celular, abrir directo una dirección de producto y de búsqueda, y comparar las variaciones del inicio contra la API.

## Risks / Trade-offs

- [La serie de la canasta tarda en el primer pedido después de una carga] → Son unas 900 filas por par de fechas; con un año de datos, unas 50 consultas en el primer pedido de cada instancia. Si molesta, se calcula al renovar la caché en lugar de en el primer pedido.
- [Las rutas de React devuelven 200 con `index.html` aunque la dirección no exista] → La página de producto pide el EAN a la API y muestra "no encontrado"; cualquier otra ruta muestra el inicio. Es el comportamiento habitual de una SPA.
- [Con muchas cadenas, el gráfico del producto se llena de líneas] → El gráfico se limita a las 8 cadenas más baratas; la tabla de precios queda como lectura principal.
- [La URL de la API queda fija en el build] → Si la API cambia de URL, hay que volver a publicar el sitio. Aceptado: cambia solo si se recrea el stack.

## Migration Plan

1. Endpoint de la serie en la API, con sus tests, y redespliegue.
2. Sitio y tests en verde en local y en GitHub Actions.
3. `infra/web.yaml` desplegado, primera publicación del sitio y validación final.
4. README con la dirección del sitio.

Vuelta atrás: borrar el stack `precios-super-web`. El endpoint nuevo de la API no cambia los existentes.
