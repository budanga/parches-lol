# App de Android (parte 2 de 2) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App nativa de Android (Kotlin + Jetpack Compose) que lee los JSON publicados en `data/`, muestra parches, historial por campeón y noticias con el diseño "The Quiet Archive · Registro", funciona sin conexión y avisa cuando sale un parche nuevo.

**Architecture:** La lógica que decide qué se muestra es Kotlin puro con pruebas JVM: modelo, resaltado de números, filtro "Para ADC", tiempos relativos, cuadros de animación y decisión de avisos. Sobre esa capa van un repositorio (red + caché en archivos), la UI en Compose y un `CoroutineWorker` de WorkManager. La UI se comprueba en el celular real, por depuración inalámbrica, con capturas de `adb`.

**Tech Stack:** Gradle 8.14 (wrapper generado con la distribución ya cacheada en `~/.gradle/wrapper/dists/gradle-8.14-bin`), Android Gradle Plugin 8.9.1, Kotlin 2.2.20 (plugins `org.jetbrains.kotlin.plugin.compose` y `org.jetbrains.kotlin.plugin.serialization` 2.2.20), Compose BOM 2025.06.00 + Material3, activity-compose 1.10.1, navigation-compose 2.9.0, lifecycle-viewmodel-compose 2.9.1, kotlinx-serialization-json 1.8.1, kotlinx-coroutines-android 1.10.2, OkHttp 4.12.0, coil-compose 2.7.0, work-runtime-ktx 2.10.1, datastore-preferences 1.1.7. Pruebas: junit 4.13.2, kotlinx-coroutines-test 1.10.2, mockwebserver 4.12.0.

**Spec:** `docs/superpowers/specs/2026-11-23-parches-lol-design.md` (secciones 6.3 y 6.4)

## Global Constraints

- Proyecto en `android/`; paquete y applicationId `com.budanga.parcheslol`; nombre visible "Parches LoL".
- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`. SDK en `C:\AndroidSDK` vía `android/local.properties` (`sdk.dir=C\:\\AndroidSDK`), que va en `.gitignore`.
- Si una versión fijada no resuelve, se usa la más cercana que resuelva, se anota como Ruling en el ledger y se sigue.
- URL base de datos: `https://raw.githubusercontent.com/budanga/parches-lol/main/data/`.
- **Nunca se reescribe texto oficial:** `text`, `attribute`, `before` y `after` se muestran literales. El diseño solo agrega color a los números que cambiaron.
- Colores, tipografías, formas y animaciones: exactamente los de la sección 6.4 del spec. Fuentes incluidas en `res/font` (OFL): Newsreader (variable `opsz,wght`), Plus Jakarta Sans (variable `wght`) e IBM Plex Mono (Regular y Medium).
- Textos de la UI: voz calma y breve, sin signos de exclamación. Todos los textos fijos viven en `res/values/strings.xml`.
- Verificación en pantalla: `verified` → ✓ `#3A604A`; `mismatch` → ⚠ `#D97706` y debajo "En el juego figura {gameData.after}" cuando `gameData.before == before`, o "En el juego figura {gameData.before} ⇒ {gameData.after}" cuando difieren; `unchecked` → sin ícono. (`fun mismatchText(change: Change): String?` en `domain/Highlight.kt`, probada en la Tarea 3: Ezreal 26.3 → "En el juego figura 3.75"; Gwen 25.21 "Crecimiento de Vida" → "En el juego figura 110 ⇒ 115".)
- `addedLater` y `midPatch` no se muestran en la v1: el spec 6.4 no los incluye y el usuario pidió evitar etiquetas.
- Reducir movimiento = `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`. En ese caso ninguna animación corre y el contenido aparece directo.
- Git: regla del usuario. **Nunca hacer stage, commit ni push sin su confirmación explícita.** Los pasos "Commit" significan: proponer el commit (archivos por nombre y mensaje) y esperar el sí.
- Comandos de compilación, desde `android/` en Git Bash: `./gradlew testDebugUnitTest` (pruebas) y `./gradlew assembleDebug` (APK en `app/build/outputs/apk/debug/app-debug.apk`).

## Review Focus

1. **Primera apertura sin internet:** sin caché se muestra "Sin conexión. Probá de nuevo cuando haya internet." con un botón "Reintentar"; con caché se muestran los datos guardados sin ningún error. Prueba en la Tarea 5.
2. **Parche sin cambios para tus ADC:** "Para ADC" muestra "Sin cambios para tus ADC en este parche." y no una pantalla vacía. Prueba en la Tarea 4.
3. **Avisos repetidos:** si el worker corre varias veces con el mismo parche nuevo, avisa una sola vez. Y en la primera instalación no avisa de los 47 parches existentes. Prueba en la Tarea 9.
4. **El JSON trae campos nuevos** (el programa de datos puede crecer): la app los ignora y no se cae. Prueba en la Tarea 2.
5. **Líneas oficiales muy largas** (pasiva de Kalista en 25.S1.1): se cortan en varias líneas sin truncar ni superponerse con el ✓. Se comprueba en el celular en la Tarea 10.

---

## Estructura de archivos

```
pipeline/src/catalog.ts                    (Tarea 1) catálogo de campeones e ítems
data/catalog.json                          (Tarea 1, generado)
android/
  settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew*, gradle/wrapper/*
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/res/font/*.ttf, res/values/strings.xml, res/drawable/ic_notification.xml
  app/src/main/java/com/budanga/parcheslol/
    MainActivity.kt                         navegación y arranque
    data/Model.kt                           clases serializables (espejo de pipeline/src/types.ts)
    data/Repository.kt                      red + caché en archivos
    data/Prefs.kt                           DataStore: mis ADC, mis ítems, avisos, parches vistos
    domain/Highlight.kt                     números que cambiaron entre antes y después
    domain/AdcView.kt                       semillas, filtro "Para ADC", agrupado por entidad
    domain/TimeText.kt                      "Actualizado hace X"
    domain/Notify.kt                        decisión de avisos
    anim/Glyphs.kt                          cuadros de descifrado ASCII y matriz Bayer
    anim/Effects.kt                         composables de animación
    ui/Theme.kt                             colores, tipografías, formas
    ui/Components.kt                        ChangeLine, EntityHeader, PatchRow, NavBar
    ui/HomeScreen.kt, PatchScreen.kt, ChampionsScreen.kt, ChampionScreen.kt, NewsScreen.kt, SettingsScreen.kt
    work/UpdateWorker.kt                    revisión cada 3 h y notificaciones
  app/src/test/java/com/budanga/parcheslol/*Test.kt
  app/src/test/resources/                   copias de data/index.json, data/patches/26.23.json, data/patches/25.S1.1.json, data/catalog.json
```

---

### Task 1: Catálogo de campeones e ítems (programa de datos)

La app necesita la lista de todos los campeones (buscador y "mis ADC") y de los ítems de la Grieta ("mis ítems"). Hoy el programa de datos no la publica.

**Files:**
- Create: `pipeline/src/catalog.ts`, `pipeline/test/catalog.test.ts`
- Modify: `pipeline/src/main.ts` (escribir `catalog.json` junto a `index.json`), `pipeline/src/types.ts`

**Interfaces:**
- Produces: `interface Catalog { schemaVersion: 1; ddragonVersion: string; champions: { id: string; name: string; tags: string[] }[]; items: { id: string; name: string; tags: string[] }[] }` y `buildCatalog(ddragonVersion: string, championJson: any, itemJson: any): Catalog`. Campeones ordenados por `name`. Ítems: solo los que tienen `maps["11"] === true`, `gold.purchasable !== false` y `gold.total > 0`, sin duplicados por nombre (gana el id menor) y ordenados por `name`.

- [ ] **Step 1: Pruebas que fallan** (fixtures `ddragon/16.23.1`): `buildCatalog("16.23.1", champ, item)` incluye `{ id: "MonkeyKing", name: "Wukong" }`; incluye el ítem `"3031"` con tags `["CriticalStrike","Damage"]`; no incluye `"223152"` (Arena); `champions.length === 173`.
- [ ] **Step 2: `npm test` → FAIL**
- [ ] **Step 3: Implementar `buildCatalog` y llamarlo en `run()`** con `versions[0]`, escribiendo `catalog.json` en la carpeta temporal antes del reemplazo. Agregar a la prueba de `main` que `catalog.json` existe tras la primera corrida.
- [ ] **Step 4: `npm test` y `npm run typecheck` → PASS**
- [ ] **Step 5: Commit** (pedir confirmación) — `feat: catálogo de campeones e ítems`

### Task 2: Proyecto Android y modelo de datos

**Files:**
- Create: todo `android/` excepto la UI; `android/app/src/main/java/com/budanga/parcheslol/data/Model.kt`; `android/app/src/test/java/com/budanga/parcheslol/ModelTest.kt`; recursos de prueba (copias de los JSON listados en la estructura).
- Modify: `.gitignore` (agregar `android/local.properties`, `android/.gradle/`, `android/build/`, `android/app/build/`).

**Interfaces:**
- Produces (todas `@Serializable`, mismos nombres de campo que `pipeline/src/types.ts`):
  `Entity(kind: String, id: String?, name: String)`,
  `GameData(before: String, after: String)`,
  `Change(id, sectionTitle, sectionId, midPatch: Boolean, entity: Entity, part: String?, partTitle: String?, spellKey: String?, kind: String, attribute: String?, before: String?, after: String?, text: String, section: String, tags: List<String>, verification: String, gameData: GameData?, addedLater: Boolean)`,
  `PatchFile(schemaVersion, patch, title, url, publishedAt, ddragonVersion, changes: List<Change>)`,
  `Counts(total, verified, unchecked, mismatch)`, `PatchSummary(patch, title, url, publishedAt, counts)`, `IndexFile(schemaVersion, updatedAt, patches)`,
  `HistoryEntry(patch, publishedAt, changes)`, `ChampionHistory(schemaVersion, id, name, tags, entries)`,
  `NewsItem(title, publishedAt, url, imageUrl: String?, description: String?, category)`,
  `CatalogEntry(id, name, tags)`, `Catalog(schemaVersion, ddragonVersion, champions, items)`;
  `val AppJson: Json = Json { ignoreUnknownKeys = true }`.

- [ ] **Step 1: Generar el wrapper** con la distribución cacheada: `~/.gradle/wrapper/dists/gradle-8.14-bin/*/gradle-8.14/bin/gradle wrapper --gradle-version 8.14`, ejecutado en `android/`. Escribir `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts` (versiones del Tech Stack), `gradle.properties` (`android.useAndroidX=true`) y `local.properties`. Manifest con permiso `INTERNET` y una `MainActivity` mínima.
- [ ] **Step 2: Pruebas que fallan** en `ModelTest`:
  - `index.json` real → `patches.first().patch == "26.23"` y `patches.size == 47`.
  - `patches/26.23.json` → el cambio de Azir con `part == "base"` tiene `before == "525"`, `after == "550"` y `verification == "verified"`.
  - Un JSON con un campo extra (`"nuevoCampo": 1` dentro de un `Change`) se decodifica sin error (Review Focus 4).
  - `catalog.json` → contiene `MonkeyKing`.
- [ ] **Step 3: `./gradlew testDebugUnitTest` → FAIL** (no compila: faltan las clases).
- [ ] **Step 4: Implementar `Model.kt`**
- [ ] **Step 5: `./gradlew testDebugUnitTest` y `./gradlew assembleDebug` → BUILD SUCCESSFUL**
- [ ] **Step 6: Commit** (pedir confirmación) — `feat(android): proyecto y modelo de datos`

### Task 3: Lógica pura de texto y animación

**Files:**
- Create: `domain/Highlight.kt`, `domain/TimeText.kt`, `anim/Glyphs.kt`; pruebas `HighlightTest.kt`, `TimeTextTest.kt`, `GlyphsTest.kt` (con un helper de prueba `change(...)` que arma un `Change` con valores por defecto)

**Interfaces:**
- Produces:
  `fun mismatchText(change: Change): String?` (regla en las Global Constraints; `null` si no es `mismatch`);
  `data class Segment(val text: String, val changed: Boolean)`;
  `fun highlightAfter(before: String, after: String): List<Segment>`. Los segmentos concatenados reproducen `after` exacto. Los números son tokens `-?\d+(?:[.,]\d+)?%?`. Si las dos líneas tienen la misma cantidad de números, se marca el número i de `after` cuando difiere del i de `before`; si la cantidad difiere, se marcan todos los números de `after`.
  `fun updatedText(updatedAt: Instant, now: Instant): String`. Devuelve "Actualizado hace N min" si es menos de 60 min (mínimo 1), "Actualizado hace N h" si es menos de 24 h, y en otro caso "Actualizado hace 1 día" o "Actualizado hace N días".
  `const val GLYPHS = "░▒▓#%&*+=:<>/\\"`;
  `fun decipherFrame(final: String, frame: Int, totalFrames: Int, random: Random): String`. Los primeros `floor(frame/totalFrames*len)` caracteres son los reales; los espacios, `/`, `.`, `,` y `%` siempre son reales; el resto sale de `GLYPHS`.
  `val BAYER4: Array<IntArray>` (la matriz 4×4 estándar 0..15);
  `fun ditherOn(x: Int, y: Int, progress: Float): Boolean` = `progress > (BAYER4[y%4][x%4] + 0.5f) / 16f`.

- [ ] **Step 1: Pruebas que fallan**

```kotlin
@Test fun ezrealW() {
  val s = highlightAfter(
    "80 / 135 / 190 / 245 / 300 (+100% de Daño de Ataque adicional) (+90% de Poder de Habilidad)",
    "80 / 135 / 190 / 245 / 300 (+115% de Daño de Ataque adicional) (+100% de Poder de Habilidad)")
  assertEquals(listOf("115%", "100%"), s.filter { it.changed }.map { it.text })
  assertEquals("80 / 135 / 190 / 245 / 300 (+115% de Daño de Ataque adicional) (+100% de Poder de Habilidad)", s.joinToString("") { it.text })
}
@Test fun ezrealR() = assertEquals(listOf("600", "850", "110%", "120%"),
  highlightAfter("350 / 550 / 750 (+100% de Daño de Ataque adicional) (+110% de Poder de Habilidad)",
    "350 / 600 / 850 (+110% de Daño de Ataque adicional) (+120% de Poder de Habilidad)").filter { it.changed }.map { it.text })
@Test fun sinCambios() = assertTrue(highlightAfter("525", "525").none { it.changed })
@Test fun textoMismatch() {
  val base = change(before = "3.25", after = "3.5", verification = "mismatch", gameData = GameData("3.25", "3.75"))
  assertEquals("En el juego figura 3.75", mismatchText(base))
  assertEquals("En el juego figura 110 ⇒ 115", mismatchText(base.copy(before = "100", after = "125", gameData = GameData("110", "115"))))
  assertNull(mismatchText(base.copy(verification = "verified", gameData = null)))
}
@Test fun distintaCantidad() = assertEquals(listOf("3", "4"), highlightAfter("1", "3 / 4").filter { it.changed }.map { it.text })
@Test fun tiempos() {
  val now = Instant.parse("2026-11-23T12:00:00Z")
  assertEquals("Actualizado hace 2 h", updatedText(Instant.parse("2026-11-23T10:00:00Z"), now))
  assertEquals("Actualizado hace 1 min", updatedText(now, now))
  assertEquals("Actualizado hace 1 día", updatedText(Instant.parse("2026-11-22T11:00:00Z"), now))
}
@Test fun descifrado() {
  val r = Random(1)
  assertEquals("115%", decipherFrame("115%", 10, 10, r))
  val f0 = decipherFrame("26.23", 0, 10, r)
  assertEquals(5, f0.length); assertEquals('.', f0[2]); assertTrue(f0.filterIndexed { i, _ -> i != 2 }.all { it in GLYPHS })
}
@Test fun bayer() {
  assertTrue((0 until 4).all { y -> (0 until 4).none { x -> ditherOn(x, y, 0f) } })
  assertTrue((0 until 4).all { y -> (0 until 4).all { x -> ditherOn(x, y, 1f) } })
}
```

- [ ] **Step 2: FAIL → Step 3: implementar → Step 4: `./gradlew testDebugUnitTest` PASS**
- [ ] **Step 5: Commit** (pedir confirmación) — `feat(android): resaltado de números, tiempos y cuadros de animación`

### Task 4: Vista "Para ADC" y "Todo"

**Files:**
- Create: `domain/AdcView.kt`, `AdcViewTest.kt`

**Interfaces:**
- Produces:
  `val DEFAULT_ADCS: Set<String> = setOf("Aphelios","Ashe","Caitlyn","Corki","Draven","Ezreal","Jhin","Jinx","Kaisa","Kalista","KogMaw","Lucian","MissFortune","Nilah","Samira","Senna","Sivir","Smolder","Tristana","Twitch","Varus","Vayne","Xayah","Yunara","Zeri")`;
  `fun isDefaultAdcItem(tags: List<String>): Boolean` = contiene `CriticalStrike` o `AttackSpeed`, o contiene a la vez `ArmorPenetration` y `Damage`;
  `data class Group(val key: String, val title: String, val entity: Entity?, val changes: List<Change>, val collapsed: Boolean)`;
  `fun adcGroups(patch: PatchFile, myAdcs: Set<String>, myItems: Set<String>?): List<Group>`. `myItems == null` significa que se usa `isDefaultAdcItem`. Orden: grupos de campeones de `myAdcs` (en orden de aparición), grupos de ítems ADC, y un grupo `"global"` con título "Global" que junta todo `section == "systems"`. Sin `modes` ni `cosmetics`.
  `fun allGroups(patch: PatchFile): List<Group>`. Orden: champions, items, runes y systems, un grupo por entidad (o por `sectionTitle` cuando la entidad es `none`); al final, un grupo "Otros modos" (`modes`) y otro "Cosméticos" (`cosmetics`), los dos con `collapsed = true`.

- [ ] **Step 1: Pruebas que fallan** (con `patches/26.23.json` real):
  - `adcGroups(p, DEFAULT_ADCS, null).first().entity?.id == "Ezreal"` y ese grupo tiene 2 cambios.
  - Ningún grupo de `adcGroups` contiene `Azir` ni un cambio con `section == "modes"`.
  - Existe el grupo `"global"` y contiene la línea que empieza con "El chat de voz de equipo está habilitado".
  - `adcGroups(p, setOf("Teemo"), emptySet())` → solo el grupo global (Review Focus 2: la pantalla muestra "Sin cambios para tus ADC en este parche." cuando no hay grupos de campeones ni ítems; la prueba verifica que no hay grupos de entidad).
  - `allGroups(p).last().collapsed == true` y los dos últimos títulos son "Otros modos" y "Cosméticos".
  - `isDefaultAdcItem(listOf("CriticalStrike","Damage"))` es true e `isDefaultAdcItem(listOf("SpellDamage"))` es false.
- [ ] **Step 2: FAIL → Step 3: implementar → Step 4: PASS**
- [ ] **Step 5: Commit** (pedir confirmación) — `feat(android): vistas Para ADC y Todo`

### Task 5: Repositorio (red + caché)

**Files:**
- Create: `data/Repository.kt`, `RepositoryTest.kt`

**Interfaces:**
- Produces:
  `sealed interface Loaded<out T> { data class Fresh<T>(val value: T): Loaded<T>; data class Cached<T>(val value: T): Loaded<T>; data object Offline: Loaded<Nothing> }`;
  `class Repository(private val baseUrl: String, private val cacheDir: File, private val client: OkHttpClient = OkHttpClient())` con `suspend fun index(): Loaded<IndexFile>`, `suspend fun patch(id: String): Loaded<PatchFile>`, `suspend fun champion(id: String): Loaded<ChampionHistory>`, `suspend fun news(): Loaded<List<NewsItem>>` y `suspend fun catalog(): Loaded<Catalog>`.
  Cada uno pide `baseUrl + ruta` (`index.json`, `patches/$id.json`, `champions/$id.json`, `news.json`, `catalog.json`). Si responde bien → guarda el texto en `cacheDir/ruta` y devuelve `Fresh`. Si falla la red o el código no es 200 → si hay caché devuelve `Cached`, si no `Offline`. Un JSON corrupto en la red cuenta como falla. Usa `Dispatchers.IO`.

- [ ] **Step 1: Pruebas que fallan** con `MockWebServer` y una carpeta temporal:
  - El servidor responde `index.json` → `Fresh` y el archivo queda en la caché.
  - Servidor apagado y caché previa → `Cached` con el mismo contenido (Review Focus 1, con caché).
  - Servidor apagado y sin caché → `Offline` (Review Focus 1, sin caché).
  - El servidor responde 500 y hay caché → `Cached`.
  - El servidor responde `"{roto"` → `Cached`/`Offline` según haya caché, y la caché no se pisa.
- [ ] **Step 2: FAIL → Step 3: implementar → Step 4: PASS**
- [ ] **Step 5: Commit** (pedir confirmación) — `feat(android): repositorio con caché sin conexión`

### Task 6: Tema, componentes y pantalla de parche

**Files:**
- Create: `ui/Theme.kt`, `ui/Components.kt`, `ui/PatchScreen.kt`, `data/Prefs.kt`, `res/font/*`, `res/values/strings.xml`
- Fuentes (descargadas del repositorio oficial `github.com/google/fonts`, carpeta `ofl/`): `newsreader.ttf` ← `ofl/newsreader/Newsreader[opsz,wght].ttf`; `plus_jakarta_sans.ttf` ← `ofl/plusjakartasans/PlusJakartaSans[wght].ttf`; `ibm_plex_mono_regular.ttf` y `ibm_plex_mono_medium.ttf` ← `ofl/ibmplexmono/`.

**Interfaces:**
- Consumes: `PatchFile`, `adcGroups`, `allGroups`, `highlightAfter`, `Repository`.
- Produces:
  `object Ink { Paper=#F5F2EB, Surface2=#FAF8F3, Card=#FFFFFF, Input=#EFECE4, Accent=#BA5A3A, AccentSoft=#D47758, SelectTint=#F3E5E0, Text=#22201D, Text2=#767169, Faint=#A5A097, Line=#DDD9D0, Success=#3A604A, Warning=#D97706, Error=#C0392B }`; `val Serif`, `val Sans`, `val Mono: FontFamily` (Newsreader con `FontVariation.weight(500)` y `600`); `@Composable fun ArchiveTheme(content)`.
  `class Prefs(context)` sobre DataStore con `myAdcs: Flow<Set<String>>` (por defecto `DEFAULT_ADCS`), `myItems: Flow<Set<String>?>` (`null` = regla por defecto), `newsAlerts: Flow<Boolean>` (por defecto false), `openedPatches: Flow<Set<String>>`, `revealedPatches: Flow<Set<String>>`, `notifiedPatches: Flow<Set<String>>`, `lastNewsUrl: Flow<String?>`, y los `suspend fun set…`/`add…` correspondientes.
  `@Composable fun ChangeLine(change: Change, animateNumbers: Boolean)`:
  - Columna izquierda de 18 dp: letra `part` (`P`/`Q`/`W`/`E`/`R`) en Mono terracota, o el ícono de barras si `part == "base"`, o vacía.
  - Atributo en Sans 500.
  - Para `kind == "stat"`: `"− " + before` en Mono `Faint` y `"+ " + after` en Mono `Text`, con los segmentos marcados de `highlightAfter` en `Accent`. Luego el ícono de verificación según las Global Constraints.
  - Para otros `kind`: `text` en Sans `Text2`.
  `@Composable fun EntityHeader(entity: Entity?, title: String, ddragonVersion: String)`: retrato circular de 24 dp (Coil: `https://ddragon.leagueoflegends.com/cdn/$v/img/champion/$id.png` o `/img/item/$id.png`; si falla, la inicial en Serif sobre `Input`), el título en Serif 18 y una línea de tinta (`Text`) de 1 dp debajo.
  `@Composable fun PatchScreen(patchId: String, repo: Repository, prefs: Prefs, onBack: () -> Unit)`: barra (volver, número en Serif 28, fecha en Mono `Faint`), selector "Para ADC" / "Todo" (radio 10, activo `SelectTint` + `Accent`), y una lista perezosa de grupos. Los grupos plegados se abren al tocarlos. Si en "Para ADC" no hay grupos de entidad, se muestra `R.string.no_adc_changes` = "Sin cambios para tus ADC en este parche.". Al abrir, si `patchId` no está en `openedPatches`, `animateNumbers = true` y se agrega.

- [ ] **Step 1: Descargar las fuentes, escribir el tema, `Prefs`, los componentes y `PatchScreen`.** En `MainActivity`, mostrar temporalmente `PatchScreen("26.23")`.
- [ ] **Step 2: `./gradlew testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL** (las pruebas de las Tareas 2 a 5 siguen en verde).
- [ ] **Step 3: Revisión en el celular:** la app lee los datos de GitHub, así que antes hay que hacer los pasos 1 a 3 de la Tarea 10 (publicar, conectar e instalar). Abrir el parche 26.23 y sacar una captura con `adb exec-out screencap -p > scratch/patch.png`. Comparar con el boceto B: Ezreal con W y R, `−`/`+`, 115% y 100% en terracota, ✓ verde y el grupo Global. Mandar la captura al usuario.
- [ ] **Step 4: Commit** (pedir confirmación) — `feat(android): tema y pantalla de parche`

### Task 7: Inicio, campeones, historial, noticias, ajustes y navegación

**Files:**
- Create: `ui/HomeScreen.kt`, `ui/ChampionsScreen.kt`, `ui/ChampionScreen.kt`, `ui/NewsScreen.kt`, `ui/SettingsScreen.kt`
- Modify: `MainActivity.kt` (NavHost con rutas `home`, `patch/{id}`, `champions`, `champion/{id}`, `news` y `settings`; barra inferior con Parches, Campeones y Noticias, ícono activo en `Accent`)

**Interfaces:**
- Consumes: todo lo anterior.
- Produces, por pantalla:
  - **Inicio:**
    - Barra con "Archivo de parches" (Serif 20) y engranaje que lleva a `settings`.
    - Etiqueta "Último parche"; número en Serif 40 y fecha en Mono.
    - Retratos de tus ADC con cambios en ese parche (de `adcGroups`, solo grupos de campeones).
    - `updatedText(index.updatedAt, now)`.
    - Lista de los demás parches: número en Serif 17 y fecha en Mono a la derecha, filas con borde de 1 dp.
    - "Noticias" con las 3 más recientes.
    - Con `Loaded.Offline`, el mensaje "Sin conexión. Probá de nuevo cuando haya internet." y el botón "Reintentar".
  - **Campeones:** buscador (input `Input`, borde terracota al enfocar). Tus ADC primero y después el resto del catálogo, por nombre. Al tocar uno se va a `champion/{id}`; si el campeón no tiene archivo de historial (404 y sin caché) se muestra "Sin cambios registrados desde 2025.".
  - **Campeón:** retrato de 34 dp y nombre en Serif 24. Por cada entrada: encabezado con el parche en Serif 18 y la fecha en Mono, más la línea de tinta, y luego `ChangeLine` (sin animación).
  - **Noticias:** título, fecha en Mono y descripción. Al tocar, se abre `url` en el navegador (`Intent.ACTION_VIEW`).
  - **Ajustes:**
    - "Mis ADC": catálogo de campeones con check; se guarda en `Prefs.myAdcs`.
    - "Mis ítems": catálogo de ítems con check, marcado inicialmente según `isDefaultAdcItem`; al tocar uno se guarda el conjunto explícito.
    - "Avisos de noticias" (switch).
    - "Restablecer" vuelve a los valores por defecto.
  - Fechas en formato "17 nov", con locale `es`, mes abreviado y sin punto.

- [ ] **Step 1: Implementar las pantallas y la navegación**
- [ ] **Step 2: `./gradlew testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL**
- [ ] **Step 3: Revisión en el celular:** capturas de Inicio, Campeones (buscando "ez"), Ezreal (historial con el ⚠ de 26.3, "En el juego figura 3.75"), Noticias y Ajustes. Mandarlas al usuario.
- [ ] **Step 4: Commit** (pedir confirmación) — `feat(android): inicio, campeones, noticias y ajustes`

### Task 8: Animaciones

**Files:**
- Create: `anim/Effects.kt`
- Modify: `HomeScreen.kt`, `PatchScreen.kt`, `MainActivity.kt`

**Interfaces:**
- Consumes: `decipherFrame`, `GLYPHS`, `ditherOn`, `Prefs`.
- Produces:
  `@Composable fun rememberReduceMotion(): Boolean`;
  `@Composable fun DecipherText(text: String, style: TextStyle, color: Color, play: Boolean, frames: Int = 10)`, que corre a 12 fps (`delay(83)`) con `decipherFrame`; si `play` es false o hay reducir movimiento, muestra `text` directo;
  `@Composable fun DitherShimmer(modifier)`, celdas de 4 dp con `Ink.Input` sobre `Ink.Paper` cuya opacidad sigue `ditherOn` con un desplazamiento que avanza a 12 fps;
  `@Composable fun DitherCover(progress: Float, modifier)`, que pinta celdas de 8 dp en `Ink.Accent` donde `ditherOn(x, y, progress)`;
  `@Composable fun DitherReveal(play: Boolean, content)`, que revela el contenido con celdas de papel que se retiran en ~500 ms.

Dónde va cada animación (numeración del spec 6.4):
1. "Archivo de parches" en el Inicio con `DecipherText(play = primera composición de este proceso)`, en menos de 1 s (10 cuadros).
2. En `PatchScreen`, los segmentos marcados se dibujan con `DecipherText(play = animateNumbers)`.
3. Si la primera carga del índice no tiene caché, mientras espera se muestran 6 filas `DitherShimmer` en lugar de la lista.
4. Al navegar de Inicio a `patch/{id}`: `DitherCover` de 0 a 1 en 250 ms, navegar, y de 1 a 0 en 250 ms sobre la pantalla nueva. Las demás rutas usan `fadeIn`/`fadeOut` de 180 ms con easeOutCubic al entrar y easeInCubic al salir.
5. En el Inicio, cada parche que no está en `revealedPatches` se envuelve en `DitherReveal(play = true)` y se agrega al conjunto. **En la primera instalación** (`revealedPatches` vacío y sin caché previa) se agregan todos los parches actuales sin animar.

- [ ] **Step 1: Implementar los efectos y engancharlos**
- [ ] **Step 2: `./gradlew testDebugUnitTest assembleDebug` → BUILD SUCCESSFUL**
- [ ] **Step 3: Revisión en el celular:**
  - Grabar un video corto con `adb shell screenrecord --time-limit 8 /sdcard/anim.mp4` y bajarlo con `adb pull`: apertura (1), entrada a un parche (4 y 2).
  - Con "Quitar animaciones" activado en el celular, comprobar que todo aparece directo.
  - Mandar el video al usuario.
- [ ] **Step 4: Commit** (pedir confirmación) — `feat(android): animaciones ASCII y dither`

### Task 9: Avisos cada 3 horas

**Files:**
- Create: `domain/Notify.kt`, `work/UpdateWorker.kt`, `NotifyTest.kt`, `res/drawable/ic_notification.xml`
- Modify: `AndroidManifest.xml` (`POST_NOTIFICATIONS`), `MainActivity.kt` (pedir el permiso en Android 13+ la primera vez; encolar el trabajo)

**Interfaces:**
- Produces:
  `data class PatchAlert(val patch: String, val adcChanges: Int)`;
  `fun patchesToNotify(index: IndexFile, notified: Set<String>, firstRun: Boolean): List<String>`. Si `firstRun` es true, no avisa de nada (el worker igual marca todos como avisados). Si no, devuelve los parches del índice que no están en `notified`, del más viejo al más nuevo.
  `fun alertText(alert: PatchAlert): Pair<String, String?>` = `"Salió el parche ${patch}"` con `"${n} cambios a tus ADC"` (o `"1 cambio a tus ADC"`), o `null` si `n == 0`;
  `fun newsToNotify(news: List<NewsItem>, lastUrl: String?): NewsItem?`, la más reciente si su `url != lastUrl` y `lastUrl != null` (en la primera corrida solo se guarda).
  `UpdateWorker : CoroutineWorker`, encolado con `PeriodicWorkRequest` de 3 h, `NetworkType.CONNECTED` y `ExistingPeriodicWorkPolicy.KEEP`, con el nombre único `"update"`. Lee el índice con `Repository`; para cada parche que avisar, baja el parche, cuenta los cambios de los grupos de entidad de `adcGroups` y notifica (canal "Parches"). Las noticias se notifican solo si `newsAlerts` está activo (canal "Noticias"). Después actualiza `notifiedPatches` y `lastNewsUrl`.

- [ ] **Step 1: Pruebas que fallan:**
  - `patchesToNotify` con `firstRun = true` → vacío.
  - Con `notified` = todos menos "26.23" → `["26.23"]`; llamado de nuevo con "26.23" agregado → vacío (Review Focus 3).
  - `alertText(PatchAlert("26.24", 4)) == "Salió el parche 26.24" to "4 cambios a tus ADC"`; con 1 → `"1 cambio a tus ADC"`; con 0 → segundo valor `null`.
  - `newsToNotify(news, null) == null`; con `lastUrl` distinto de la más reciente → la más reciente.
- [ ] **Step 2: FAIL → Step 3: implementar → Step 4: PASS y `assembleDebug`**
- [ ] **Step 5: Revisión en el celular:** forzar una corrida con `adb shell cmd jobscheduler run -f com.budanga.parcheslol 0`, o con un botón temporal de depuración que se borra antes del commit. Con `notifiedPatches` sin "26.23" (modificado para la prueba), comprobar que llega "Salió el parche 26.23 · 2 cambios a tus ADC" y que una segunda corrida no repite el aviso.
- [ ] **Step 6: Commit** (pedir confirmación) — `feat(android): avisos de parches y noticias`

### Task 10: Publicar los datos e instalar en el celular

- [ ] **Step 1: Publicar.** Con confirmación del usuario: commit de los cambios pendientes del programa de datos y `git push -u origin main` a `budanga/parches-lol`. Comprobar que `https://raw.githubusercontent.com/budanga/parches-lol/main/data/index.json` responde 200 con 47 parches. Lanzar el workflow (`gh workflow run update-data`) y verificar con `gh run watch` que termina en verde.
- [ ] **Step 2: Conectar el celular.** Pedirle al usuario que active Depuración inalámbrica, toque "Vincular dispositivo con código" y corra él mismo `adb pair IP:PUERTO` con el código. Luego pedirle la IP:puerto que se ve en la pantalla de Depuración inalámbrica (es distinta de la de vinculación) y correr `adb connect IP:PUERTO`. Esperado: `adb devices` lista el celular como `device`.
- [ ] **Step 3: Instalar:** `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` → `Success`.
- [ ] **Step 4: Lista de comprobación en el celular**, cada punto con su captura:
  1. El Inicio muestra 26.23 con el retrato de Ezreal y "Actualizado hace …".
  2. En 26.23 "Para ADC", la vista coincide con el boceto aprobado.
  3. En 25.S1.1 "Todo", la pasiva de Kalista (líneas largas) se corta en varias líneas sin superponerse con los íconos (Review Focus 5).
  4. El historial de Ezreal está ordenado del más nuevo al más viejo.
  5. En modo avión, la app muestra los datos guardados (Review Focus 1).
  6. En Ajustes, al quitar a Ezreal de Mis ADC, 26.23 "Para ADC" muestra "Sin cambios para tus ADC en este parche." (Review Focus 2).
  7. Las noticias abren el navegador.
- [ ] **Step 5: Commit final** (pedir confirmación) — `docs: instrucciones de instalación de la app` (README: cómo compilar, vincular e instalar).
