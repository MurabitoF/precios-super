## Context

Motivación en `proposal.md`. El archivador es `scripts/archivar.sh`, corrido por `.github/workflows/archivar.yml` todos los días a las 19:00 UTC en `ubuntu-latest`; asume el rol `precios-super-archivador` por OIDC y sube el ZIP con escritura condicional. El diseño de `archivo-semanal-sepa` ya preveía este riesgo ("si el portal bloquea los runners, el mismo script corre en una máquina propia").

Lo medido el 2026-11-21:

- Desde el runner de GitHub (`20.127.247.33`, Azure): 403 en la API y en la descarga del ZIP, con `curl` por defecto, con `User-Agent` de navegador y con encabezados `Accept`. El bloqueo es por IP, no por cliente.
- Desde AWS CloudShell (`us-east-1`): 403.
- Desde la máquina de Franco (conexión hogareña en Argentina): 200; `archivar.sh` archivó el 2026-11-18 en 3 minutos.

La máquina de Franco corre Linux x86_64 (CachyOS) y ya tiene `curl`, `jq`, `unzip` y la CLI de AWS. `precios-super` es privado hoy y se va a publicar como portfolio; mientras es privado, los workflows de forks están deshabilitados.

## Goals / Non-Goals

**Goals:**

- Que el archivo semanal vuelva a funcionar solo, sin intervención en la semana.
- Mantener el acceso a AWS sin claves guardadas, ni en el repo ni en la máquina.
- Que el runner no pueda usarse para correr código que no sea de Franco, aunque `precios-super` sea público.

**Non-Goals:**

- Mover el cargador: lee del bucket, no del portal, y sigue en runners de GitHub.
- Alta disponibilidad del runner. Con 7 días de margen por miércoles alcanza con una máquina que se prenda algún día de la semana.
- Detectar si el portal levanta el bloqueo. Se revisa a mano si alguna vez conviene volver a `ubuntu-latest`.

## Decisions

### Runner propio de GitHub Actions en la máquina de Franco

El job corre en un runner self-hosted con IP argentina. El script, el bucket y la autenticación por OIDC quedan iguales: el runner pide el token de OIDC a GitHub igual que uno hospedado, y las credenciales temporales viven solo durante el job.

Un job programado espera hasta 24 horas a que aparezca un runner con la etiqueta pedida. Con el cron diario y 7 días de publicación por miércoles, la máquina tiene que estar prendida al menos un día entre el miércoles y el martes siguiente. Si un día no aparece, el job falla y GitHub avisa por mail, como hoy.

Alternativas:
- VPS en Argentina (USD 3 a 5 por mes): más confiable que una PC hogareña, pero con costo y otro proveedor para un problema que una máquina propia ya resuelve.
- EC2 en la Local Zone de Buenos Aires (`us-east-1-bue-1`): bastante más caro, y no está probado que el filtro del portal la deje pasar.
- Cron local que corra `archivar.sh` sin GitHub: obligaría a guardar claves de AWS de larga duración en la máquina y pierde el historial y los avisos de Actions.
- Proxy residencial: costo, y una dependencia opaca para un proyecto que tiene que explicarse.

### El runner en un repo privado aparte

Un runner propio en un repo público es riesgoso: un pull request desde un fork puede agregar un workflow que corra en ese runner, y GitHub desaconseja esa combinación. Por eso el runner se registra en un repo privado nuevo, `precios-super-archivador`, que solo tiene `archivar.yml`. Ese workflow:

- corre en `runs-on: [self-hosted, sepa-ar]`, todos los días a las 19:00 UTC y por disparo manual;
- hace checkout de `MurabitoF/precios-super` (rama `main`) y corre su `scripts/archivar.sh`, así el script y sus tests siguen en un solo lugar;
- asume el rol del archivador por OIDC con el `sub` del repo privado.

`precios-super` queda sin runners propios y puede ser público: lo que llegue por un fork corre, como mucho, en un runner hospedado de GitHub sin secretos. El repo privado tiene a Franco como único colaborador y los workflows de forks deshabilitados.

El rol del archivador (`infra/archivo.yaml`) pasa a confiar solo en el repo privado, con el formato inmutable del `sub` (`repo:MurabitoF@<id>/precios-super-archivador@<id>:ref:refs/heads/main`). El parámetro `GitHubRepo` ya existe; el cambio es un redeploy pasándolo con `--parameter-overrides`.

Defensas adicionales en la máquina:
- Usuario del sistema propio (`gh-runner`), sin `sudo` y sin acceso al home de Franco. Un job comprometido queda dentro de ese usuario.
- Servicio de systemd (`svc.sh install gh-runner`), así arranca con la máquina sin sesión abierta.

Alternativas:
- Runner en `precios-super` público con "Require approval for all external contributors": un solo repo, pero la seguridad depende de no aprobar nunca por error un PR malicioso.
- Dejar `precios-super` privado y publicar solo el front: resuelve el riesgo, pero esconde el código, que es lo que el portfolio tiene que mostrar.
- Runner efímero (`--ephemeral`): necesita un token de registro renovable guardado en la máquina, que es justo la clase de credencial que se quiere evitar.

### Publicación de `precios-super`

Antes de publicar se revisa el historial completo: que no haya tokens, claves, IDs de cuenta de AWS ni datos que no deban ser públicos. Los secretos (`TURSO_TOKEN`) y las variables del repo no se exponen al publicarlo, pero los logs de Actions sí: los del cargador no imprimen credenciales ni el bucket.

### Verificación

Primero un disparo manual del workflow del repo privado para confirmar que corre en el runner propio, asume el rol con el nuevo `sub` y llega al portal; como el 2026-11-18 ya está archivado, debe terminar sin descargar. Recién entonces se borra `archivar.yml` del repo público. Después, la corrida programada del miércoles 2026-11-25 tiene que archivar ese ZIP sola.

## Risks / Trade-offs

- [La máquina queda apagada más de 7 días seguidos] → Se pierde ese miércoles. Si se sabe de antemano (un viaje), se puede rescatar desde cualquier conexión argentina con `BUCKET=... scripts/archivar.sh` y credenciales de AWS. Si no, se acepta el hueco: el cargador y la serie lo toleran como una semana sin datos.
- [El portal también bloquea IP hogareñas, o la de Franco] → El job falla con aviso por mail. La salida es un VPS argentino, con el mismo runner.
- [Un fork intenta correr código en el runner] → El runner está en un repo privado sin otros colaboradores; `precios-super` público no tiene runners propios. Además el runner corre con un usuario sin privilegios.
- [Entre el redeploy del rol y el borrado de `archivar.yml` del repo público, el workflow viejo falla] → Ya falla todos los días por el 403; el redeploy se hace justo antes del disparo manual del repo privado.
- [El repo privado corre lo que haya en `main` de `precios-super`] → Solo Franco empuja a `main`; es el mismo código que hoy corre el workflow del repo público.
- [Una actualización del sistema rompe una herramienta del script] → El job falla con aviso; el script usa herramientas comunes (`curl`, `jq`, `unzip`, `aws`).
- [El despliegue depende de una máquina hogareña, lo que en un portfolio puede leerse como un atajo] → Se documenta en el README como decisión forzada por el bloqueo del portal, con lo medido y las alternativas.

## Migration Plan

1. Crear el repo privado con su workflow y sus variables.
2. Crear el usuario `gh-runner`, instalar el runner en el repo privado con la etiqueta `sepa-ar` y dejarlo como servicio.
3. Revisar el historial de `precios-super` y publicarlo. Va antes del paso siguiente: el workflow del repo privado hace checkout de `precios-super`, y su `GITHUB_TOKEN` no puede leer otro repo privado.
4. Redeploy de `infra/archivo.yaml` con el `sub` del repo privado y disparo manual de su workflow.
5. Borrar `archivar.yml` y `ROLE_ARN` del repo público.
6. Esperar la corrida programada que archive el 2026-11-25.

Vuelta atrás: redeploy de `infra/archivo.yaml` con el `sub` del repo público y restaurar `archivar.yml` desde git. Mientras tanto, el rescate a mano sigue disponible hasta el martes 2026-12-01 para el miércoles 2026-11-25.
