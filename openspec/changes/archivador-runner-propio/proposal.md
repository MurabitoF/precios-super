## Why

Desde el 2026-11-17 el portal de datos responde 403 a toda IP de la nube: la API CKAN y las descargas fallan desde los runners de GitHub (Azure) y desde AWS (CloudShell en `us-east-1`), con cualquier `User-Agent`. Desde una conexión hogareña en Argentina responden 200. El archivador falla todos los días desde entonces; el miércoles 2026-11-18 se rescató a mano y cada miércoles que no se archive en 7 días se pierde para siempre.

## What Changes

- El archivador pasa a correr en un runner propio de GitHub Actions (self-hosted) instalado en la máquina de Franco, cuya IP argentina el portal acepta.
- El runner y el workflow del archivador viven en un repo privado aparte, `precios-super-archivador`. Ese workflow hace checkout de `precios-super` y corre su `scripts/archivar.sh`: el script sigue siendo uno solo, con sus tests, en el repo del proyecto.
- `precios-super` queda sin runners propios y se puede publicar: un fork no tiene dónde correr código en la máquina de Franco.
- El rol del archivador pasa a confiar en el repo privado en lugar del público. La autenticación sigue siendo por OIDC, sin claves guardadas en la máquina ni en ningún repo.
- **BREAKING (operativo):** `archivar.yml` sale de `precios-super`; el historial de corridas del archivador queda en el repo privado.
- Revisión del historial de `precios-super` y publicación del repo.
- Documentación del bloqueo, del runner y de cómo reinstalarlo.

No cambia: `scripts/archivar.sh`, el bucket, el cargador (lee del bucket, no del portal, y sigue en runners de GitHub en el repo público) ni los tests.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Ningún requisito de `archivo-sepa` dice dónde corre la tarea: se sigue archivando el miércoles, todos los días se reintenta y el acceso al bucket sigue sin credenciales de larga duración guardadas. El cambio declara `skip_specs: true`.

## Impact

- `precios-super`: se borra `.github/workflows/archivar.yml` y la variable `ROLE_ARN`; `BUCKET` queda porque la usa `cargar.yml`. El repo pasa a público.
- Repo nuevo `precios-super-archivador` (privado): el workflow del archivador y las variables `BUCKET` y `ROLE_ARN`.
- AWS: redeploy de `infra/archivo.yaml` con el `sub` de OIDC del repo privado (parámetro `GitHubRepo`).
- Máquina de Franco: runner de GitHub Actions como servicio de systemd, con un usuario propio; usa `curl`, `jq`, `unzip` y la CLI de AWS, ya instalados.
- Disponibilidad: el archivo depende de que la máquina esté prendida algún día de cada semana. Un job programado espera hasta 24 horas a que aparezca el runner y el portal conserva cada miércoles 7 días.
- Costo: ninguno; los minutos de Actions de un repo público son gratis.
