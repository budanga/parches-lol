# Parches LoL

App personal de Android para seguir los parches de League of Legends con información verificada.

- Diseño: `docs/superpowers/specs/2026-11-23-parches-lol-design.md`
- Parte 1, programa de datos: `pipeline/`
- Parte 2, app de Android: pendiente

## Programa de datos

Lee las notas oficiales en español latino, extrae cada cambio y lo compara con Data Dragon (estadísticas base e ítems) y con Community Dragon (habilidades). Escribe los resultados en `data/`.

Cada cambio lleva `verification`:
- `verified`: los números de las notas coinciden con los datos del juego.
- `unchecked`: es texto oficial que no se pudo comparar.
- `mismatch`: las notas y los datos del juego no coinciden; `gameData` trae los valores del juego.

```
cd pipeline
npm ci
npm test        # pruebas con páginas reales guardadas
npm start       # actualiza ../data (varios minutos la primera vez)
```

GitHub Actions (`.github/workflows/update-data.yml`) lo corre cada 3 horas y commitea `data/` si algo cambió. Si una corrida falla, `data/` no se toca y GitHub avisa por mail.

### Datos publicados

Base: `https://raw.githubusercontent.com/<usuario>/<repo>/main/data/`

| Archivo | Contenido |
|---|---|
| `index.json` | parches, del más nuevo al más viejo, con la cantidad de cambios por verificación |
| `patches/<parche>.json` | todos los cambios de un parche |
| `champions/<Id>.json` | historial de un campeón, del más nuevo al más viejo |
| `news.json` | noticias oficiales (actualizaciones del juego y /dev) |
| `missing.json` | parches cuya página no se encontró |

El contrato de datos está en `pipeline/src/types.ts`.
