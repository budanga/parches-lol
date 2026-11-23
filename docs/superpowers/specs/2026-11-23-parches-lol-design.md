# Parches LoL — Diseño

Fecha: 2026-11-23
Estado: borrador, pendiente de aprobación

## 1. Intención

App de Android **personal** (un solo usuario, no se publica en la Play Store) para seguir los parches y noticias de League of Legends con información **concisa, verificada y correcta**.

El usuario juega solo ADC. Lo que más le importa:
- enterarse cuando sale un parche,
- ver cada cambio en una línea exacta ("Miss Fortune — AD base: 50 ⇒ 60"),
- ver primero lo que afecta a ADCs, a ítems de ADC y al juego en general,
- ver el historial completo de cambios de cada campeón, del parche más nuevo al más viejo.

## 2. Objetivos, no-objetivos y anti-objetivos

**Objetivos**
- Aviso en el celular cuando sale un parche nuevo.
- Vista de cada parche con todos los cambios en formato `antes ⇒ después`, tomados de las notas oficiales de Riot.
- Vista "Para ADC" por defecto: mis ADCs, ítems de ADC, cambios globales (sistemas, mapa, objetivos) y cliente.
- Historial por campeón, más reciente primero.
- Lista de noticias oficiales (actualizaciones del juego y de desarrolladores).
- Cada cambio muestra si se pudo comprobar contra los datos oficiales del juego.

**No-objetivos (v1)**
- IA de cualquier tipo. Queda como idea futura (sección 9).
- Esports, TFT, Wild Rift.
- Publicar la app o soportar iPhone.
- Historial de ítems (fácil de agregar después con los mismos datos).

**Anti-objetivos** (aunque la app funcione, esto sería un fracaso)
- Mostrar un número incorrecto sin advertirlo.
- Perder un cambio en silencio: si una parte de las notas no se pudo leer en detalle, se muestra igual, como texto oficial y marcada.
- Resúmenes o redacción propia que no estén en las notas oficiales.

## 3. Restricciones

- Android solamente. La PC del usuario es Windows.
- Gratis: sin servidores pagos.
- En la PC ya hay Node 24, Java 17 y Git. **No hay Python ni el SDK de Android**: hay que instalar Android Studio para compilar la app.
- Uso personal y sin fines comerciales de datos públicos de Riot. Las descargas son pocas (cada 3 h) para no cargar sus servidores.

## 4. Enfoques considerados

| Enfoque | Qué es | A favor | En contra |
|---|---|---|---|
| A. Todo en el celular | La app descarga y lee las notas sola | Una sola pieza, sin servidor | Si Riot cambia el formato, hay que reinstalar la app; los errores se descubren recién en el teléfono |
| **B. Programa aparte + app simple** ✅ | Un programa en la nube (GitHub Actions) lee y verifica las notas y publica archivos de datos; la app los muestra | La verificación está separada y tiene pruebas; un arreglo no exige tocar la app | Dos piezas; requiere cuenta de GitHub |
| C. Como B + Firebase y push instantáneo | Servidor propio con notificaciones al instante | El aviso llega en el momento | Más complejo; poco valor con parches cada 2 semanas |

**Elegido: B** (lo eligió el usuario). Es el único que garantiza que los datos se verifiquen antes de llegar al celular.

## 5. Fuentes (comprobadas el 2026-11-23)

- **Notas de parche oficiales en español latino:**
  `https://www.leagueoflegends.com/es-mx/news/game-updates/league-of-legends-patch-26-23-notes/`
  Listado: `https://www.leagueoflegends.com/es-mx/news/tags/patch-notes/`.
  Los cambios ya vienen en formato `valor anterior ⇒ valor nuevo`.
- **Data Dragon** (datos oficiales del juego por versión): `https://ddragon.leagueoflegends.com/api/versions.json`. La versión actual es `16.23.1`, que corresponde al parche 26.23.
  - Trae las **estadísticas base** de los campeones (`stats.attackdamage`, etc.), las **clases** (`tags`, por ejemplo `["Marksman","Mage"]`) y los ítems (costo, estadísticas, tags).
  - **No** trae valores de habilidades: `datavalues` viene vacío.
- **Community Dragon** (`raw.communitydragon.org`): datos extraídos del juego por parche, **incluidos los valores de habilidades**. No es oficial de Riot, pero se extrae directamente de los archivos del juego.

## 6. Diseño

### 6.1 Programa de datos (en GitHub)

Repositorio público en GitHub, programa en **Node/TypeScript** (Node ya está instalado). Corre con GitHub Actions cada 3 horas.

Pasos de cada corrida:
1. **Descubrir parches:** leer el listado de notas y detectar los que faltan. También volver a descargar los **2 últimos parches** para captar cambios agregados después (hotfixes, micro-parches). Si una página cambió, se reprocesa; las líneas nuevas se marcan como "agregado después".
2. **Leer cada página** y convertirla en cambios estructurados:
   - `parche`, `sección` (campeones, ítems, runas, sistemas, mapa/objetivos, cliente, otros modos, cosméticos),
   - `entidad` (campeón, ítem o runa, con su ID de Data Dragon),
   - `parte` (estadísticas base, pasiva, Q, W, E, R),
   - `atributo`, `antes`, `después`, o un tipo `nuevo` / `eliminado` / `texto`.
   - Los nombres en español se traducen a ID usando el `champion.json` / `item.json` en `es_MX` de Data Dragon.
   - **Todo bloque que no se pueda leer en detalle se guarda como texto oficial** con la marca "no leído en detalle". Nunca se descarta.
3. **Verificar:**
   - Estadísticas base de campeones e ítems (costo, estadísticas): `antes` debe coincidir con Data Dragon de la versión anterior y `después` con la versión del parche.
   - Habilidades: intentar comprobarlas con Community Dragon (versión anterior contra versión nueva). Si no se puede relacionar el valor, queda como "texto oficial, sin comprobar".
   - Resultado por cambio: `verificado` ✅, `sin comprobar` (texto oficial) o `no coincide` ⚠️. En ese último caso se muestran el texto oficial y el dato del juego.
4. **Clasificar:** cada cambio lleva las clases del campeón y los tags del ítem, para que la app pueda filtrar.
5. **Publicar** en GitHub Pages:
   - `index.json`: lista de parches (número, fecha, cantidad de cambios), última actualización.
   - `patches/<parche>.json`: todos los cambios de ese parche.
   - `champions/<id>.json`: historial del campeón, más reciente primero.
   - `news.json`: noticias.
6. **Si algo falla gravemente** (la página cambió de formato y casi nada se pudo leer, o un error del programa), **no se publica**: los datos anteriores quedan como estaban y la corrida falla. GitHub avisa por mail.

**Historial inicial:** desde el comienzo de 2025 hasta hoy (unos 2 años). Hay que contemplar que en 2025 Riot cambió la forma de numerar los parches. Más adelante se puede ir más atrás si el lector aguanta los formatos viejos.

**Pruebas:** páginas reales de notas guardadas como ejemplos, con el resultado esperado escrito a mano para un puñado de cambios (por ejemplo, los de Miss Fortune). Las pruebas corren antes de publicar.

### 6.2 Noticias

Del sitio de Riot en español latino, solo las categorías **actualizaciones del juego** y **desarrolladores**. Se guardan título, fecha, imagen, la descripción corta que pone Riot y el link. Al tocar una noticia se abre en el navegador. No hay texto propio.

### 6.3 App de Android

**Kotlin + Jetpack Compose**, nativa. Se compila en Android Studio y se instala en el celular por cable (o pasando el APK).

Pantallas:
```
Inicio        → último parche (tarjeta) + noticias recientes
Parche        → [Para ADC | Todo]
                 Para ADC: Mis ADCs · Ítems de ADC · Cambios globales · Cliente
                 Todo: además los otros campeones, runas, otros modos y cosméticos (plegados)
                 Línea: "Miss Fortune — Q: daño 20/45/70 ⇒ 30/55/80  ✅"
Campeones     → buscador, mis ADCs arriba → historial por parche, más nuevo primero
Noticias      → lista, abre en el navegador
Ajustes       → mis ADCs, mis ítems de ADC, avisos de noticias on/off
```

- **Mis ADCs:** lista editable. Arranca con los campeones de clase Marksman que se juegan en bot. No alcanza con filtrar por la clase, porque Data Dragon también marca como Marksman a campeones que no son de bot.
- **Ítems de ADC:** lista editable. Arranca con los ítems de Data Dragon que tienen tags de crítico, velocidad de ataque o letalidad con daño de ataque.
- **Sin conexión:** guarda los últimos datos descargados y funciona sin internet.
- **Avisos:** un trabajo en segundo plano (WorkManager) revisa `index.json` cada ~3 h. Cuando hay parche nuevo avisa "Salió el parche 26.24 — 4 cambios a tus ADCs". Las noticias avisan solo si está activado.

## 7. Qué decidió el usuario

- App solo para él, en Android.
- Sin IA que resuma. Como mucho, más adelante, algo enfocado en ADC.
- Foco en ADCs, ítems de ADC, cambios globales y cliente.
- Historial por campeón, más reciente primero.
- Enfoque B.

## 8. Decisiones mías (corregibles)

- Notas en **español latino** (`es-mx`).
- Filtro de ADC con listas editables, sin IA.
- Historial desde 2025.
- Verificación: estadísticas base e ítems contra Data Dragon; habilidades contra Community Dragon cuando se pueda, y si no, marcadas como "sin comprobar".
- Revisión cada 3 h; volver a descargar los 2 últimos parches por hotfixes.
- Node/TypeScript para el programa de datos; Kotlin + Compose para la app.
- Otros modos (ARAM, Arena) y cosméticos visibles solo en "Todo", plegados.
- Repositorio público (GitHub Pages es gratis así; no hay datos privados ni claves).

## 9. Idea futura: IA

Pregunta opcional del tipo "¿cómo me afecta este parche como ADC?". La IA respondería **solo** con los cambios verificados de la app y citaría cada línea de la que saca algo. Tendría costo por uso de API. No entra en la v1.

## 10. Queda para quien lo construya

- El formato exacto de los JSON y la lógica para leer el HTML.
- Cómo relacionar los valores de habilidades con los datos de Community Dragon.
- La lista inicial exacta de ADCs e ítems de ADC.
- El estilo visual de la app (oscuro, simple).
