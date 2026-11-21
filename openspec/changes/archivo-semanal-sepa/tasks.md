Regla para todo el cambio: los tests a crear son los listados en `design.md` (U1 a U8, I1 a I10). Crear otro test, o modificar uno existente o sus datos de prueba, requiere el ok de Franco.

## 1. Requisitos previos (los hace Franco)

- [x] 1.1 Crear el repositorio público en GitHub y subir `main`
- [x] 1.2 Instalar la CLI de AWS en la máquina local (Docker ya está)
- [x] 1.3 Tener una cuenta de AWS con la CLI configurada (hace falta recién para el grupo 6)
- [x] 1.4 Mover el ZIP del 2026-11-04 a una carpeta estable (`~/desarrollo/precios-super-data/2026-11-04.zip`)

## 2. Script de archivo

- [x] 2.1 Escribir `scripts/archivar.sh` como funciones chicas: elegir el recurso `Miércoles` de la respuesta de CKAN, extraer la fecha, validar que sea miércoles, armar el nombre del objeto, validar tamaño
- [x] 2.2 Agregar el chequeo de existencia (`head-object`) que termina bien sin descargar si la fecha ya está archivada
- [x] 2.3 Agregar descarga, validación (tamaño mínimo y `unzip -tq`) y subida condicional que no pisa objetos
- [x] 2.4 Dejar configurables por variable de entorno la URL de CKAN, el bucket y `MIN_BYTES`

## 3. Tests unitarios

- [x] 3.1 Escribir `scripts/archivar_test.sh` con los casos U1 a U8 de `design.md` y sus datos de prueba (respuestas de CKAN guardadas)
- [x] 3.2 Correrlos y dejarlos en verde corrigiendo el script, no los tests

## 4. Tests de integración contra floci

- [x] 4.1 Levantar floci con Docker y comprobar qué soporta: S3, escritura condicional y CloudFormation. Si falta algo que afecte un test, avisar a Franco antes de seguir
- [x] 4.2 Escribir `scripts/archivar_integracion_test.sh` con los casos I1 a I10 de `design.md`, con portal simulado por archivos locales y ZIP de prueba chicos
- [x] 4.3 Correrlos y dejarlos en verde corrigiendo el script, no los tests

## 5. Validación local con datos reales

- [x] 5.1 Correr el script contra floci con el ZIP real del 2026-11-04
- [x] 5.2 Comparar lo guardado con el original: tamaño en bytes, SHA-256 y cantidad de ZIP internos. Mostrarle los números a Franco
- [x] 5.3 Confirmar que una segunda corrida no descarga ni escribe

## 6. Infraestructura en AWS (solo con los grupos 3 a 5 en verde)

- [x] 6.1 Escribir `infra/archivo.yaml`: bucket privado, proveedor OIDC de GitHub y rol limitado a este repo en `main` con acceso a `sepa/`
- [x] 6.2 Desplegar la plantilla con `aws cloudformation deploy` y anotar el nombre del bucket y el ARN del rol
- [x] 6.3 Verificar a mano que un pedido anónimo a un objeto del bucket es rechazado

## 7. Workflows

- [x] 7.1 Escribir `.github/workflows/tests.yml`: en cada push corre los unitarios y los de integración con floci
- [x] 7.2 Escribir `.github/workflows/archivar.yml`: cron diario 19:00 UTC, disparo manual, permisos OIDC, asume el rol y corre el script
- [x] 7.3 Disparar el archivado a mano y confirmar que la descarga desde el runner funciona (riesgo principal del diseño)
- [ ] 7.4 Confirmar que la primera corrida programada termina bien

## 8. Primer punto y cierre

- [x] 8.1 Subir el ZIP del 2026-11-04 como `sepa/2026-11-04.zip` tras pasar la misma validación, y comparar tamaño y SHA-256 contra el original
- [x] 8.2 Actualizar `README.md` y `CLAUDE.md` con las decisiones tomadas (Java y Spring Boot, AWS, medición semanal) y cómo funciona el archivo y sus tests
