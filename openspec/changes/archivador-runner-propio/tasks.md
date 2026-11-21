Este cambio no agrega funcionalidad ni modifica requisitos (`skip_specs: true`): no lleva tests nuevos. Se verifica corriendo el workflow real. Las tareas que necesitan `sudo` en la máquina las corre Franco, con los comandos que se le pasan.

## 1. Repo privado del archivador

- [x] 1.1 Crear el repo privado `precios-super-archivador` y confirmar que los workflows de forks están deshabilitados
- [x] 1.2 Escribir su `.github/workflows/archivar.yml`: cron diario 19:00 UTC y disparo manual, `runs-on: [self-hosted, sepa-ar]`, OIDC, checkout de `precios-super` y `scripts/archivar.sh`
- [x] 1.3 Cargar las variables `BUCKET` y `ROLE_ARN` en el repo privado

## 2. Runner en la máquina de Franco

- [x] 2.1 Crear el usuario del sistema `gh-runner`, sin `sudo`, y confirmar que no puede leer el home de Franco
- [x] 2.2 Descargar el runner de GitHub Actions y registrarlo en el repo privado con la etiqueta `sepa-ar`, con un token de registro de un solo uso
- [x] 2.3 Instalarlo como servicio de systemd que corre como `gh-runner` y confirmar que GitHub lo muestra en línea
- [x] 2.4 Confirmar que, como `gh-runner`, `curl`, `jq`, `unzip` y `aws` están en el `PATH` y la API del portal responde 200

## 3. Publicación de `precios-super`

Va antes del paso del archivador: el workflow del repo privado hace checkout de `precios-super`, y su `GITHUB_TOKEN` no puede leer otro repo privado.

- [x] 3.1 Revisar el historial completo: sin tokens, claves, IDs de cuenta de AWS ni datos que no deban ser públicos
- [x] 3.2 Confirmar que no tiene runners propios ni workflows que se disparen con `pull_request`
- [x] 3.3 Hacerlo público, con el ok de Franco

## 4. Paso del archivador al repo privado

- [x] 4.1 Redeploy de `infra/archivo.yaml` con `GitHubRepo` del repo privado, en formato inmutable, y confirmar la condición de confianza del rol
- [x] 4.2 Disparar el workflow del repo privado a mano y confirmar que corre en el runner propio, asume el rol y termina bien sin descargar (el 2026-11-18 ya está archivado)
- [x] 4.3 Borrar `archivar.yml` y la variable `ROLE_ARN` de `precios-super`, y actualizar el comentario de despliegue de `infra/archivo.yaml`
- [ ] 4.4 Confirmar que una corrida programada archiva el 2026-11-25. Si no pasa a tiempo, rescatarlo a mano antes del 2026-12-01

## 5. Cierre

- [x] 5.1 Actualizar `README.md`: el bloqueo del portal, por qué el archivador corre en un runner propio desde un repo privado y cómo reinstalarlo
- [x] 5.2 Actualizar `CLAUDE.md`: dónde corre cada tarea programada, los dos repos y el gotcha del bloqueo por IP
- [x] 5.3 Tachar la 7.4 de `archivo-semanal-sepa` (la primera corrida programada terminó bien el 2026-11-12) y archivar ese cambio con sync de su spec
