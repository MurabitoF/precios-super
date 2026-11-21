Regla para todo el cambio: los tests a crear son los listados en `design.md` (I25 a I28 y W1 a W19) y ninguno se escribe antes de que Franco apruebe esa lista. Crear otro test, o modificar uno existente o sus datos de prueba, requiere su ok.

## 1. Aprobaciones previas (las hace Franco)

- [x] 1.1 Aprobar la lista de tests de `design.md`, o pedir cambios

## 2. Serie de la canasta en la API

- [x] 2.1 Escribir el cálculo de la serie: un índice por par de fechas consecutivas y la acumulada encadenada sin redondeos intermedios
- [x] 2.2 Guardar en memoria la serie de la canasta por defecto junto con la versión de los datos de `Datos`
- [x] 2.3 Escribir el endpoint `GET /canasta/serie` con las validaciones de las canastas propias y el error 422 con menos de dos fechas
- [x] 2.4 Escribir los tests I25 a I28 y dejar en verde toda la suite de la API, corrigiendo el código y no los tests
- [ ] 2.5 Redesplegar la API con `sam build && sam deploy` y confirmar el endpoint con los datos reales

## 3. Proyecto web

- [x] 3.1 Crear el proyecto en `web/` con Vite (React y TypeScript), React Router, Recharts, Vitest, Testing Library y jsdom
- [x] 3.2 Escribir `api.ts` (una función tipada por endpoint, URL base desde `VITE_API_URL`) y el hook `useApi` con carga, error y reintento
- [x] 3.3 Escribir `formato.ts`: pesos, variaciones con signo y fechas `dd/mm/aaaa`
- [x] 3.4 Escribir los estilos base: variables de color para claro y oscuro, diseño para 360 px y tablas con desplazamiento propio
- [x] 3.5 Escribir los tests W1 a W3 y dejarlos en verde corrigiendo el código, no los tests

## 4. Pantallas

- [x] 4.1 Escribir el inicio: última semana, acumulada con su período, gráfico de barras semanal, detalle por producto ordenado, excluidos y explicación del método con enlace al repositorio
- [x] 4.2 Escribir la búsqueda: formulario, mínimo de 3 caracteres, resultados enlazados, más resultados y aviso sin resultados; el texto vive en la dirección (`/buscar?q=`)
- [x] 4.3 Escribir la página de producto: tabla de precios de menor a mayor, con la fecha cuando no es la última, gráfico de líneas por cadena con huecos y aviso de no encontrado
- [x] 4.4 Escribir el aviso de error con reintento, común a las tres pantallas
- [x] 4.5 Escribir los tests W4 a W19 y dejarlos en verde corrigiendo el código, no los tests

## 5. Tests en GitHub Actions

- [x] 5.1 Agregar a `.github/workflows/tests.yml` un job que en `web/` corra `npm ci`, los tests y `npm run build`
- [ ] 5.2 Confirmar que el job pasa en un push

## 6. Publicación (solo con los grupos 2 a 5 en verde)

- [x] 6.1 Escribir `infra/web.yaml`: bucket privado, CloudFront con Origin Access Control, `index.html` como raíz y como respuesta a 403 y 404, HTTPS obligatorio
- [ ] 6.2 Desplegar el stack `precios-super-web` y anotar el bucket, el ID de la distribución y la dirección del sitio
- [ ] 6.3 Compilar con la URL de la API, subir `assets/` con caché larga e `index.html` sin caché, e invalidar `index.html`

## 7. Validación con el sitio publicado

- [ ] 7.1 Recorrer las tres pantallas a 360 px de ancho y confirmar que no hay desplazamiento horizontal de la página
- [ ] 7.2 Abrir directo la dirección de un producto y la de una búsqueda
- [ ] 7.3 Comparar las variaciones del inicio contra `/canasta/serie` y `/canasta/indice` de la API. Mostrarle los números y capturas a Franco

## 8. Cierre

- [ ] 8.1 Actualizar `README.md`: dirección del sitio, qué muestra cada pantalla y cómo publicarlo
- [ ] 8.2 Actualizar `CLAUDE.md` con las decisiones (React con Vite, S3 con CloudFront, serie encadenada de la canasta) y los gotchas encontrados
