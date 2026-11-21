## Why

La API ya responde las preguntas del proyecto, pero un JSON no muestra la inflación de un vistazo ni se puede probar desde el celular. El dashboard es la cara pública del portfolio: tiene que dejar ver en segundos cuánto subió la canasta, cómo cambió el precio de un producto y en qué cadena sale menos.

## What Changes

- Dashboard web público en React con TypeScript, construido con Vite y publicado como sitio estático en S3 privado con CloudFront delante (HTTPS y caché), definido en CloudFormation.
- Inicio: la canasta por defecto, con su variación en la última semana, su variación acumulada desde la primera fecha, un gráfico de la variación semana a semana y el detalle por producto, con los productos que no se pudieron comparar.
- Búsqueda de productos por texto o EAN, con más resultados a pedido.
- Página de producto: su último precio en cada cadena, de menor a mayor, y un gráfico de su precio semanal por cadena.
- Precios en pesos argentinos, variaciones con signo, URLs que se pueden compartir y abrir directo, uso cómodo en el celular y errores de la API visibles con opción de reintentar.
- Fuente de los datos y método del índice explicados en el propio sitio.
- API: endpoint nuevo `GET /canasta/serie` con la variación de la canasta entre cada par de fechas consecutivas y la variación acumulada encadenada. Se calcula en el servidor y se guarda en memoria hasta que llega una carga nueva, para que el inicio sea un solo pedido.

Queda afuera: canastas armadas por el usuario, dominio propio, cuentas de usuario, comparación entre productos y despliegue automático.

## Capabilities

### New Capabilities

- `dashboard`: sitio web público que muestra la canasta, la búsqueda y la página de cada producto a partir de la API.

### Modified Capabilities

- `api-precios`: se agrega el requisito de la serie del índice de una canasta (`GET /canasta/serie`). Los requisitos existentes no cambian.

## Impact

- Repositorio: proyecto nuevo `web/` (React, TypeScript, Vite, Vitest), un endpoint y sus tests en `api/`, una plantilla nueva `infra/web.yaml` y un job nuevo en el workflow de tests.
- AWS: un bucket S3 privado y una distribución de CloudFront con Origin Access Control; redespliegue de la API.
- Turso: la serie de la canasta lee unas 900 filas por par de fechas, una vez por carga nueva y por instancia de la API.
- Costo: dentro de la capa gratuita de CloudFront y S3.
- Herramientas: Node 22, ya instalado.
