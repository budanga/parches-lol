# Programa de datos (parte 1 de 2) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un programa Node/TypeScript que descarga las notas de parche oficiales de Riot (es-mx), las convierte en cambios estructurados, los verifica contra Data Dragon y Community Dragon, y publica archivos JSON (parches, historial por campeón, noticias) en `data/` del repositorio, ejecutado cada 3 h por GitHub Actions.

**Architecture:** Funciones puras por responsabilidad (parsear, clasificar, verificar, construir salidas) y un orquestador (`main.ts`) que hace toda la red a través de una función `fetchText` inyectable, lo que permite probarlo sin red. El estado (`data/state.json`) recuerda qué parches ya se procesaron y con qué revisión de página. La app de Android (parte 2, plan aparte) lee los JSON desde `https://raw.githubusercontent.com/<usuario>/<repo>/main/data/...`.

**Tech Stack:** Node 24 (ejecuta `.ts` directo, sin compilar), TypeScript solo para chequear tipos (`tsc --noEmit`), `cheerio` para el HTML, `node:test` para las pruebas, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-23-parches-lol-design.md`

## Global Constraints

- Notas en español latino: `https://www.leagueoflegends.com/es-mx/news/game-updates/<slug>/`.
- Nunca descartar contenido: lo que no se pueda estructurar se guarda como `kind: "text"` con el texto oficial.
- No escribir texto propio: `text`, `attribute`, `before` y `after` son literales de la página (espacios normalizados).
- `verification` solo puede ser `"verified"`, `"unchecked"` o `"mismatch"`. Las habilidades nunca producen `"mismatch"`, solo `"verified"` o `"unchecked"`, porque la relación con los datos de Community Dragon es aproximada.
- Historial desde el parche 25.S1.1 (enero de 2025) hasta hoy.
- Corrida programada cada 3 h; entre pedidos HTTP al mismo host, al menos 1 s; User-Agent `parches-lol-personal/1.0` (sin datos personales).
- Si la corrida falla o no pasa el control de calidad, `data/` queda exactamente como estaba.
- Ejecución: `node src/main.ts` con imports que terminan en `.ts`; `package.json` con `"type": "module"`; `tsconfig` con `noEmit`, `allowImportingTsExtensions`, `erasableSyntaxOnly`, `verbatimModuleSyntax`, `strict`.
- Git: regla del usuario. **Nunca hacer stage, commit ni push sin su confirmación explícita.** Los pasos "Commit" de este plan significan: proponer el commit (archivos por nombre y mensaje) y esperar el sí.

## Review Focus

1. **Hotfixes dentro del parche** (sección "Actualizaciones dentro de la versión"): Data Dragon no se actualiza con ellos, así que un desacuerdo ahí debe dar `unchecked`, nunca `mismatch`. Prueba en la Tarea 5.
2. **Una página con un formato nuevo que el lector no entiende**: el contenido sigue apareciendo como texto, y si casi nada se pudo estructurar, la corrida aborta sin publicar. Prueba en la Tarea 7.
3. **Riot edita una nota ya publicada** (cambia `rev`): solo las líneas nuevas llevan `addedLater: true` y las anteriores conservan sus datos. Prueba en la Tarea 7.
4. **Nombres con caracteres especiales** (Kai'Sa, Bel'Veth, Nunu y Willump, Wukong → `MonkeyKing`): se resuelven al ID correcto. Se prefiere la imagen del encabezado y se recurre al nombre normalizado solo si no hay imagen. Prueba en la Tarea 3.
5. **La red falla a mitad de la corrida**: `data/` no queda a medio escribir (se escribe en una carpeta temporal y luego se reemplaza). Prueba en la Tarea 9.

---

## Estructura de archivos

```
.gitignore
pipeline/
  package.json, tsconfig.json
  patches.seed.json          URLs de parches viejos que ya no están en el listado
  src/
    types.ts                 contrato de datos (lo lee también la app)
    patches.ts               número de parche ↔ slug ↔ versión de Data Dragon
    net.ts                   fetchText con pausa y reintentos
    riot.ts                  metadatos de la página (título, fecha, rev) y listado
    names.ts                 índice de nombres es_MX → ID (campeones, ítems, runas)
    parse.ts                 HTML de la nota → RawChange[]
    classify.ts              sección y tags
    verify-stats.ts          estadísticas base e ítems contra Data Dragon
    verify-abilities.ts      habilidades contra Community Dragon
    build.ts                 PatchFile, ChampionHistory, IndexFile, addedLater, control de calidad
    news.ts                  noticias
    main.ts                  orquestador
  test/
    fixtures/                páginas y datos reales guardados (ver Tarea 1)
    *.test.ts
data/                        salida publicada (la genera el programa)
.github/workflows/update-data.yml
```

## Contrato de datos (`pipeline/src/types.ts`)

Lo produce la Tarea 1; todas las demás tareas lo usan tal cual.

```ts
export type Verification = "verified" | "unchecked" | "mismatch";
export type Section = "champions" | "items" | "runes" | "systems" | "modes" | "cosmetics";
export type EntityKind = "champion" | "item" | "rune" | "none";

export interface RawChange {
  sectionTitle: string;              // texto del h2
  sectionId: string;                 // id del h2, ej. "patch-champions"
  midPatch: boolean;                 // dentro de "Actualizaciones dentro de la versión"
  entity: { kind: EntityKind; id: string | null; name: string };
  part: string | null;               // "base" | "P" | "Q" | "W" | "E" | "R" | texto del h4
  partTitle: string | null;          // texto completo del h4, ej. "W: ¡Levántense!"
  spellKey: string | null;           // de la imagen del h4, ej. "AzirW"
  kind: "stat" | "new" | "removed" | "text";
  attribute: string | null;
  before: string | null;
  after: string | null;
  text: string;                      // línea oficial completa en texto plano
}

export interface Change extends RawChange {
  id: string;                        // sha1(entity.id|part|attribute|text) primeros 12 hex
  section: Section;
  tags: string[];                    // clases del campeón o tags del ítem (Data Dragon)
  verification: Verification;
  gameData: { before: string; after: string } | null;   // solo si mismatch
  addedLater: boolean;
}

export interface PatchFile {
  schemaVersion: 1; patch: string; title: string; url: string;
  publishedAt: string; ddragonVersion: string; changes: Change[];
}
export interface PatchSummary {
  patch: string; title: string; url: string; publishedAt: string;
  counts: { total: number; verified: number; unchecked: number; mismatch: number };
}
export interface IndexFile { schemaVersion: 1; updatedAt: string; patches: PatchSummary[] }   // más nuevo primero
export interface ChampionHistory {
  schemaVersion: 1; id: string; name: string; tags: string[];
  entries: { patch: string; publishedAt: string; changes: Change[] }[];                      // más nuevo primero
}
export interface NewsItem {
  title: string; publishedAt: string; url: string;
  imageUrl: string | null; description: string | null; category: "game-updates" | "dev";
}
export interface State {
  parserVersion: number;
  patches: Record<string, { url: string; rev: string }>;
}
export type FetchText = (url: string) => Promise<string>;
```

Archivos publicados: `data/index.json`, `data/patches/<patch>.json`, `data/champions/<id>.json`, `data/news.json`, `data/state.json`, `data/missing.json` (`string[]` de parches sin URL encontrada).

---

### Task 1: Proyecto, contrato y números de parche

**Files:**
- Create: `.gitignore` (`node_modules/`), `pipeline/package.json`, `pipeline/tsconfig.json`, `pipeline/src/types.ts`, `pipeline/src/patches.ts`
- Create fixtures (descargados una vez con curl y guardados tal cual):
  - `pipeline/test/fixtures/patch-26-23.html` ← `https://www.leagueoflegends.com/es-mx/news/game-updates/league-of-legends-patch-26-23-notes/`
  - `pipeline/test/fixtures/patch-25-s1-1.html` ← `.../game-updates/patch-25-s1-1-notes/`
  - `pipeline/test/fixtures/versions.json` ← `https://ddragon.leagueoflegends.com/api/versions.json`
- Test: `pipeline/test/patches.test.ts`

**Interfaces:**
- Produces: todos los tipos de `types.ts`;
  `patchFromSlug(slug: string): string | null`;
  `ddragonVersionFor(patch: string, versions: string[]): string | null`;
  `previousVersion(version: string, versions: string[]): string | null`;
  `cdragonVersion(ddragonVersion: string): string` (`"16.23.1"` → `"16.23"`).

- [ ] **Step 1: `git init` en `C:\Proyectos\parches-lol`; crear `package.json` (scripts: `"test": "node --test \"test/**/*.test.ts\""`, `"typecheck": "tsc --noEmit"`, `"start": "node src/main.ts"`), `tsconfig.json`; `npm install cheerio` y `npm install -D typescript @types/node`; descargar las fixtures.**
- [ ] **Step 2: Escribir las pruebas que fallan**

```ts
test("patchFromSlug", () => {
  assert.equal(patchFromSlug("league-of-legends-patch-26-23-notes"), "26.23");
  assert.equal(patchFromSlug("patch-25-s1-1-notes"), "25.S1.1");
  assert.equal(patchFromSlug("patch-2025-s1-3-notes"), "25.S1.3");
  assert.equal(patchFromSlug("patch-25-04-notes"), "25.4");
  assert.equal(patchFromSlug("tldw-season-1-2027-dev-update"), null);
});
test("ddragonVersionFor / previousVersion", () => {
  assert.equal(ddragonVersionFor("26.23", versions), "16.23.1");
  assert.equal(ddragonVersionFor("25.S1.2", versions), "15.2.1");
  assert.equal(ddragonVersionFor("25.4", versions), "15.4.1");
  assert.equal(previousVersion("16.23.1", versions), "16.22.1");
  assert.equal(previousVersion("16.1.1", versions), "15.24.1");
  assert.equal(cdragonVersion("16.23.1"), "16.23");
});
```

- [ ] **Step 3: `npm test` → falla (módulo inexistente)**
- [ ] **Step 4: Implementar `types.ts` (copiar el contrato) y `patches.ts`.** Regla: año de dos dígitos `yy`, número `n`; `S1.x` usa `x` como número. La versión de Data Dragon es `${yy-10}.${n}.1`, y solo si existe en `versions`. La versión anterior es la siguiente en `versions` (ordenado de más nuevo a más viejo).
- [ ] **Step 5: `npm test` y `npm run typecheck` → PASS**
- [ ] **Step 6: Commit** (pedir confirmación) — `chore: proyecto del programa de datos y números de parche`

### Task 2: Red, metadatos de Riot y nombres

**Files:**
- Create: `pipeline/src/net.ts`, `pipeline/src/riot.ts`, `pipeline/src/names.ts`
- Create fixtures: `pipeline/test/fixtures/listing.html` ← `https://www.leagueoflegends.com/es-mx/news/tags/patch-notes/`; `ddragon/16.22.1/champion.json`, `ddragon/16.23.1/champion.json`, `ddragon/16.22.1/item.json`, `ddragon/16.23.1/item.json`, `ddragon/16.23.1/runesReforged.json` (todos `es_MX`, desde `https://ddragon.leagueoflegends.com/cdn/<v>/data/es_MX/<archivo>`)
- Test: `pipeline/test/riot.test.ts`, `pipeline/test/names.test.ts`

**Interfaces:**
- Consumes: `FetchText`, `patchFromSlug`.
- Produces:
  `createFetchText(opts?: { minIntervalMs?: number; retries?: number }): FetchText` (pausa mínima de 1000 ms por host, 3 reintentos con espera creciente, error si el estado HTTP no es 200);
  `readPageMeta(html: string): { title: string; publishedAt: string; rev: string }` (de `__NEXT_DATA__` → `props.pageProps.page`: `title`, `analytics.publishDate`, `analytics.rev`);
  `readPatchListing(html: string): { patch: string; url: string }[]` (todos los links `/es-mx/news/game-updates/<slug>/` cuyo slug da un parche, convertidos a URL absoluta y sin duplicados);
  `interface NameIndex { champion(nameOrImg: string): {id: string; name: string} | null; item(...): {id: string; name: string} | null; rune(name: string): {id: string; name: string} | null }`;
  `buildNameIndex(championJson: unknown, itemJson: unknown, runesJson: unknown): NameIndex`;
  `normalizeName(s: string): string` (minúsculas, sin acentos, sin apóstrofos, puntos ni espacios).

- [ ] **Step 1: Escribir las pruebas que fallan**

```ts
test("readPageMeta", () => {
  const m = readPageMeta(fixture("patch-26-23.html"));
  assert.equal(m.title, "Notas de la versión 26.23 de League of Legends");
  assert.equal(m.publishedAt, "2026-11-17T19:00:00.000Z");
  assert.ok(m.rev.length > 0);
});
test("readPatchListing incluye 26.23 y 26.4", () => {
  const l = readPatchListing(fixture("listing.html"));
  assert.deepEqual(l.find(p => p.patch === "26.23")?.url,
    "https://www.leagueoflegends.com/es-mx/news/game-updates/league-of-legends-patch-26-23-notes/");
  assert.ok(l.some(p => p.patch === "26.4"));
});
test("nombres", () => {
  const n = buildNameIndex(champ2623, item2623, runes2623);
  assert.equal(n.champion("Wukong")?.id, "MonkeyKing");
  assert.equal(n.champion("Kai'Sa")?.id, "Kaisa");
  assert.equal(n.champion("Nunu y Willump")?.id, "Nunu");
  assert.equal(n.champion("https://ddragon.leagueoflegends.com/cdn/16.22.1/img/champion/Azir.png")?.id, "Azir");
  assert.equal(n.item("Cicloespada Voltaica")?.id, "6699");
  assert.equal(n.item(".../cdn/16.22.1/img/item/6699.png")?.id, "6699");
  assert.equal(n.champion("Cicloespada Voltaica"), null);
});
```

- [ ] **Step 2: `npm test` → FAIL**
- [ ] **Step 3: Implementar `net.ts`, `riot.ts`, `names.ts`.** `champion()` / `item()` reconocen primero una URL de imagen (`/img/champion/<Id>.png`, `/img/item/<id>.png`; la URL de imagen de Riot envuelve la de Data Dragon en `?f=`) y si no, buscan por `normalizeName`. Para `createFetchText` no hay prueba con red; se ejercita en la Tarea 9 con una función falsa.
- [ ] **Step 4: `npm test` y `npm run typecheck` → PASS**
- [ ] **Step 5: Commit** (pedir confirmación) — `feat: metadatos de Riot e índice de nombres`

### Task 3: Lector de notas (`parse.ts`)

**Files:**
- Create: `pipeline/src/parse.ts`
- Test: `pipeline/test/parse.test.ts`

**Interfaces:**
- Consumes: `NameIndex`, `RawChange`.
- Produces: `parsePatchNotes(html: string, names: NameIndex): RawChange[]` (en orden de aparición).

Estructura real de las notas, comprobada en 26.23 y 25.S1.1:
- Secciones: `<h2 id="patch-...">Título</h2>`.
- Bloque de campeón u objeto: `div.patch-change-block` con `h3.change-title` (nombre) y una imagen previa de Data Dragon (`img/champion/Azir.png` o `img/item/6699.png`).
- Subtítulo: `h4.change-detail-title` ("Estadísticas base" o "Estadísticas básicas"), o `h4.change-detail-title.ability-title` con `img/spell/AzirW.png` y texto "W: ¡Levántense!".
- Cambio: `<li><strong>Atributo</strong>: antes ⇒ <strong>después</strong></li>`.
- Nuevo/eliminado: `<p><span style=…><strong>&nbsp; NUEVO &nbsp;</strong></span><strong>&nbsp;: Talon</strong></p>` (o `ELIMINADO`).
- En 2025, la sección "Actualizaciones dentro de la versión" y la de Objetos **no** usan `h3`: el campeón u objeto aparece como `h4.ability-title` con su nombre o su imagen.

Algoritmo, recorriendo el artículo en orden:
1. `h2` → nueva sección. Reinicia entidad y parte. `midPatch` = el título normalizado contiene "actualizaciones dentro de la version".
2. `h3.change-title` → la entidad sale de la imagen del bloque o, si no hay, del nombre (campeón → ítem → runa). Reinicia la parte.
3. `h4` → primero se intenta como entidad (imagen de campeón/ítem o nombre exacto). Si lo es, la entidad cambia y la parte se reinicia. Si no, es una parte:
   - "Estadísticas base|básicas" → `"base"`.
   - `/^(Pasiva|Q|W|E|R)\s*:/` → `"P"`, `"Q"`, `"W"`, `"E"` o `"R"`, con `spellKey` tomado de `img/spell/<Key>.png`.
   - Cualquier otro texto → ese mismo texto.
4. `li` (solo su texto propio, sin las sublistas anidadas, que se emiten aparte con la misma entidad y parte) y `p` con marca NUEVO/ELIMINADO:
   - Contiene "⇒" → `kind "stat"`; `attribute` = el `strong` inicial si existe (si no, `null`); `before` y `after` = los lados del "⇒".
   - Marca NUEVO/ELIMINADO → `kind "new"` / `"removed"`, y la entidad es la nombrada después de los ":" si se resuelve.
   - Cualquier otro caso → `kind "text"`.
5. Las secciones sin `li` ni `p` relevantes (imágenes de "Versión en breve", aspectos) no generan cambios.

- [ ] **Step 1: Escribir las pruebas que fallan**

```ts
const c2623 = parsePatchNotes(fixture("patch-26-23.html"), names2623);
test("Azir estadística base", () => {
  const c = c2623.find(c => c.entity.id === "Azir" && c.part === "base")!;
  assert.deepEqual([c.kind, c.attribute, c.before, c.after, c.sectionId],
    ["stat", "Alcance de ataque", "525", "550", "patch-champions"]);
});
test("Azir W con spellKey", () => {
  const c = c2623.find(c => c.entity.id === "Azir" && c.part === "W")!;
  assert.deepEqual([c.partTitle, c.spellKey, c.attribute, c.before, c.after],
    ["W: ¡Levántense!", "AzirW", "Tasa de Daño de Ataque / al impacto", "50%", "67%"]);
});
test("Ezreal W y R", () => {
  const w = c2623.find(c => c.entity.id === "Ezreal" && c.part === "W")!;
  assert.equal(w.before, "80 / 135 / 190 / 245 / 300 (+100% de Daño de Ataque adicional) (+90% de Poder de Habilidad)");
  assert.equal(w.after,  "80 / 135 / 190 / 245 / 300 (+115% de Daño de Ataque adicional) (+100% de Poder de Habilidad)");
  assert.ok(c2623.some(c => c.entity.id === "Ezreal" && c.part === "R" && c.attribute === "Daño a campeones"));
});
test("Objeto con h3", () => {
  const c = c2623.find(c => c.entity.id === "6699")!;
  assert.deepEqual([c.entity.kind, c.partTitle, c.before],
    ["item", "Firmamento único", "[9% || 7%] Vida actual del objetivo"]);
});
test("NUEVO: Talon en Classic", () => {
  const c = c2623.find(c => c.kind === "new" && c.entity.id === "Talon")!;
  assert.equal(c.sectionId, "patch-classic");
});
test("texto sin estructura se conserva", () => {
  assert.ok(c2623.some(c => c.kind === "text" && c.text.startsWith("El chat de voz de equipo está habilitado")));
});
const c25 = parsePatchNotes(fixture("patch-25-s1-1.html"), names2623);
test("2025: hotfix dentro de la versión con h4 de campeón", () => {
  const c = c25.find(c => c.entity.id === "JarvanIV")!;
  assert.equal(c.midPatch, true);
});
test("2025: Kalista estadísticas básicas → base", () => {
  assert.ok(c25.some(c => c.entity.id === "Kalista" && c.part === "base" && c.kind === "stat"));
});
test("2025: objetos sin h3 se resuelven", () => {
  assert.ok(c25.some(c => c.sectionId === "patch-items" && c.entity.kind === "item" && c.entity.id));
});
```

- [ ] **Step 2: `npm test` → FAIL**
- [ ] **Step 3: Implementar `parsePatchNotes` con cheerio** siguiendo el algoritmo. Normalizar el texto: `&nbsp;` → espacio, espacios repetidos → uno solo, sin espacios al principio ni al final.
- [ ] **Step 4: `npm test` → PASS.** Si algún valor esperado no coincide con la página guardada, la referencia es la página: corregir la prueba solo después de mirar el HTML y anotar por qué.
- [ ] **Step 5: Commit** (pedir confirmación) — `feat: lector de notas de parche`

### Task 4: Secciones y tags (`classify.ts`)

**Files:**
- Create: `pipeline/src/classify.ts`
- Test: `pipeline/test/classify.test.ts`

**Interfaces:**
- Produces: `sectionFor(raw: RawChange): Section`; `tagsFor(raw: RawChange, championJson: unknown, itemJson: unknown): string[]`.

Reglas de `sectionFor`, en este orden:
1. `sectionId` que contiene alguno de `aram`, `arena`, `classic`, `urf`, `swarm`, `nexus-blitz`, `one-for-all`, `brawl`, `mayhem`, `doom` → `"modes"`.
2. `sectionId` que contiene `skins` o `chromas` → `"cosmetics"`.
3. Entidad campeón → `"champions"`; ítem → `"items"`; runa → `"runes"`.
4. `sectionId` igual a `patch-champions`, `patch-items` o `patch-runes` → su sección.
5. Cualquier otro caso → `"systems"`.

La regla 1 va antes que la 3 para que los cambios de ARAM a un campeón no aparezcan como cambios de la Grieta. Los hotfixes de campeones caen en la regla 3.

- [ ] **Step 1: Pruebas que fallan:** Azir base → `"champions"`; 6699 → `"items"`; Talon NUEVO en `patch-classic` → `"modes"`; chat de voz → `"systems"`; JarvanIV en hotfix 2025 → `"champions"`; `tagsFor` de Ezreal incluye `"Marksman"`; `tagsFor` de 3031 = `["CriticalStrike","Damage"]`.
- [ ] **Step 2: FAIL → Step 3: implementar → Step 4: PASS**
- [ ] **Step 5: Commit** (pedir confirmación) — `feat: secciones y tags`

### Task 5: Verificación de estadísticas base e ítems (`verify-stats.ts`)

**Files:**
- Create: `pipeline/src/verify-stats.ts`
- Create fixtures: `pipeline/test/fixtures/items-cost-prev.json`, `items-cost-new.json` (copia mínima con la forma de `item.json` de Data Dragon: `{"data":{"6699":{"gold":{"total":3100},"stats":{"FlatPhysicalDamageMod":60}}}}` y `{"data":{"6699":{"gold":{"total":3000},"stats":{"FlatPhysicalDamageMod":55}}}}`)
- Test: `pipeline/test/verify-stats.test.ts`

**Interfaces:**
- Produces: `verifyStat(raw: RawChange, prev: {champion: unknown; item: unknown}, next: {champion: unknown; item: unknown}): { verification: Verification; gameData: {before: string; after: string} | null } | null` (devuelve `null` si el cambio no es una estadística base de campeón o una estadística de ítem que se pueda relacionar).

Tabla de atributos (clave = `normalizeName` del atributo, sin la palabra "base"):

| Campeón (`stats.*`) | Ítem |
|---|---|
| vida→`hp`, vidapornivel / crecimientodevida→`hpperlevel` | costo / costototal / precio→`gold.total` |
| mana→`mp`, manapornivel / crecimientodemana→`mpperlevel` | danodeataque→`stats.FlatPhysicalDamageMod` |
| armadura→`armor`, armadurapornivel / crecimientodearmadura→`armorperlevel` | velocidaddeataque→`stats.PercentAttackSpeedMod`×100 |
| resistenciamagica→`spellblock`, …pornivel / crecimientode…→`spellblockperlevel` | probabilidaddeimpactocritico→`stats.FlatCritChanceMod`×100 |
| velocidaddemovimiento→`movespeed` | vida→`stats.FlatHPPoolMod` |
| alcancedeataque→`attackrange` | armadura→`stats.FlatArmorMod` |
| danodeataque→`attackdamage`, …pornivel / crecimientode…→`attackdamageperlevel` | resistenciamagica→`stats.FlatSpellBlockMod` |
| velocidaddeataque→`attackspeed`, …pornivel / crecimientode…→`attackspeedperlevel` | poderdehabilidad→`stats.FlatMagicDamageMod` |
| regeneraciondevida→`hpregen`, …pornivel→`hpregenperlevel` | |

Comparación: primer número de `before`/`after` (acepta `,` o `.` decimal; ignora `%`), con tolerancia `|a−b| ≤ 0.005·max(1,|b|)`.
- `verified`: before ≈ dato anterior y after ≈ dato nuevo.
- Si no coincide: si `raw.midPatch` → `unchecked` sin `gameData`; si no → `mismatch` con `gameData` = valores de Data Dragon como texto.

- [ ] **Step 1: Pruebas que fallan**

```ts
test("Azir alcance verificado", () => {
  assert.deepEqual(verifyStat(azirBase, d2622, d2623), { verification: "verified", gameData: null });
});
test("número equivocado → mismatch con datos del juego", () => {
  assert.deepEqual(verifyStat({ ...azirBase, before: "500" }, d2622, d2623),
    { verification: "mismatch", gameData: { before: "525", after: "550" } });
});
test("hotfix que no coincide → unchecked", () => {
  assert.deepEqual(verifyStat({ ...azirBase, before: "500", midPatch: true }, d2622, d2623),
    { verification: "unchecked", gameData: null });
});
test("costo de ítem", () => {
  const raw = { ...item6699, part: null, attribute: "Costo total", before: "3100", after: "3000" };
  assert.equal(verifyStat(raw, costPrev, costNew)?.verification, "verified");
});
test("efecto de ítem no relacionable → null", () => {
  assert.equal(verifyStat(voltaicPassive, d2622, d2623), null);
});
test("habilidad → null", () => { assert.equal(verifyStat(azirW, d2622, d2623), null); });
```

- [ ] **Step 2: FAIL → Step 3: implementar → Step 4: PASS**
- [ ] **Step 5: Commit** (pedir confirmación) — `feat: verificación de estadísticas base e ítems`

### Task 6: Verificación de habilidades (`verify-abilities.ts`)

**Files:**
- Create: `pipeline/src/verify-abilities.ts`
- Create fixtures: `pipeline/test/fixtures/cdragon/16.22/azir.bin.json`, `16.23/azir.bin.json`, `16.22/ezreal.bin.json`, `16.23/ezreal.bin.json` ← `https://raw.communitydragon.org/<v>/game/data/characters/<id>/<id>.bin.json`
- Test: `pipeline/test/verify-abilities.test.ts`

**Interfaces:**
- Produces: `verifyAbility(raw: RawChange, prevBin: unknown, nextBin: unknown): Verification | null` (`null` si no es una habilidad con `spellKey`); `cdragonChampionUrl(cdVersion: string, championId: string): string` (id en minúsculas).

Comprobado con datos reales: Azir W `OnHitMultiplier` pasa de 0.5 a 0.67; Ezreal W `BaseDamage` 80/135/190…, `APRatio` 0.9 → 1, y el ratio de AD adicional está en `mSpellCalculations` como `mCoefficient` 1 → 1.15.

Algoritmo:
1. Hechizo = la entrada del `.bin.json` cuya clave termina en `/<spellKey>` y que tiene `mSpell`.
2. Candidatos = cada `DataValues[i]` (por `name`, valores por nivel), `cooldownTime` y `mana`, y cada `mCoefficient` dentro de `mSpellCalculations` (clave = ruta JSON).
3. Grupos numéricos de un texto = la lista inicial separada por " / " antes del primer "(", más los números de cada paréntesis. Si un número lleva "%", vale `n/100` o `n`.
4. Un grupo se cumple si existe una clave K con `prev[K] ≈ grupo_before` y `next[K] ≈ grupo_after`, misma tolerancia que la Tarea 5. Para listas por nivel se comparan con `values[1..n]` (y, si no coincide, con `values[0..n-1]`). Un número suelto coincide con un escalar o con una lista de valores todos iguales.
5. `verified` solo si before y after tienen la misma cantidad de grupos y **todos** se cumplen. En cualquier otro caso → `unchecked`. Los cambios en la sección `"modes"` no se verifican y quedan `unchecked`.

- [ ] **Step 1: Pruebas que fallan**

```ts
test("Azir W 50% ⇒ 67% verificado", () => {
  assert.equal(verifyAbility(azirW, azir2622, azir2623), "verified");
});
test("Ezreal W verificado incluyendo ratio de AD en mSpellCalculations", () => {
  assert.equal(verifyAbility(ezrealW, ez2622, ez2623), "verified");
});
test("un grupo que no coincide → unchecked (nunca verificado a medias)", () => {
  const bad = { ...ezrealW, after: ezrealW.after!.replace("+115%", "+130%") };
  assert.equal(verifyAbility(bad, ez2622, ez2623), "unchecked");
});
test("hechizo inexistente → unchecked", () => {
  assert.equal(verifyAbility({ ...azirW, spellKey: "AzirZ" }, azir2622, azir2623), "unchecked");
});
test("no es habilidad → null", () => { assert.equal(verifyAbility(azirBase, azir2622, azir2623), null); });
```

- [ ] **Step 2: FAIL → Step 3: implementar → Step 4: PASS**
- [ ] **Step 5: Commit** (pedir confirmación) — `feat: verificación de habilidades con Community Dragon`

### Task 7: Construcción de salidas y control de calidad (`build.ts`)

**Files:**
- Create: `pipeline/src/build.ts`
- Test: `pipeline/test/build.test.ts`

**Interfaces:**
- Consumes: `Change`, `PatchFile`, `IndexFile`, `ChampionHistory`.
- Produces:
  `changeId(raw: RawChange): string`;
  `markAddedLater(next: Change[], previous: Change[] | null): Change[]` (si `previous` es `null`, todos quedan `addedLater: false`; si no, un cambio es `addedLater: true` cuando su `id` no estaba antes, y conserva `true` si ya lo tenía);
  `qualityCheck(changes: Change[]): { ok: true } | { ok: false; reason: string }`;
  `buildIndex(patches: PatchFile[], now: string): IndexFile`;
  `buildChampionHistories(patches: PatchFile[], championJson: unknown): ChampionHistory[]`.

Control de calidad: falla si `changes.length === 0`, o si en las secciones `champions` + `items` hay al menos 5 cambios y más del 20 % tiene `entity.id === null`, o si en esas secciones ninguno es `kind "stat"`.

- [ ] **Step 1: Pruebas que fallan:**
  - `markAddedLater`: con `previous` `null` → todo `false`; un cambio nuevo en una segunda versión → `true`, los viejos `false`; un cambio que ya era `true` sigue `true`.
  - `qualityCheck`: lista vacía → `ok: false`; 10 cambios de campeones con 3 sin `entity.id` → `ok: false`; los cambios reales de 26.23 → `ok: true`.
  - `buildIndex`: ordena por `publishedAt`, más nuevo primero, y las cuentas suman `total`.
  - `buildChampionHistories`: dados dos parches con cambios de Azir, `entries[0].patch` es el más nuevo; un campeón sin cambios no genera archivo.
  - `changeId`: estable entre corridas e igual para el mismo cambio.
- [ ] **Step 2: FAIL → Step 3: implementar → Step 4: PASS**
- [ ] **Step 5: Commit** (pedir confirmación) — `feat: construcción de salidas y control de calidad`

### Task 8: Noticias (`news.ts`)

**Files:**
- Create: `pipeline/src/news.ts`
- Create fixture: `pipeline/test/fixtures/news-dev.html` ← `https://www.leagueoflegends.com/es-mx/news/dev/`
- Test: `pipeline/test/news.test.ts`

**Interfaces:**
- Produces: `readNews(html: string): NewsItem[]`; `mergeNews(lists: NewsItem[][], limit = 30): NewsItem[]` (sin duplicados por `url`, más nuevo primero).

Fuente: `__NEXT_DATA__`. Se recorre todo y se toma cada objeto que tenga `title`, `action.payload.url` y `publishedAt`, y cuyo `category.machineName` sea `game-updates` o `dev`. La URL relativa se completa con `https://www.leagueoflegends.com`. `imageUrl` = `media.url`. `description` = `description.body` sin etiquetas HTML.

- [ ] **Step 1: Pruebas que fallan:** `readNews(news-dev)` contiene `{ title: "RESUMEN: Actualización /dev de la Temporada 1 2027", category: "dev", publishedAt: "2026-11-16T16:00:00.000Z", url: "https://www.leagueoflegends.com/es-mx/news/dev/tldw-season-1-2027-dev-update---lol-website-article-lollol27-s1225r001", description: "Un breve resumen de los cambios que anunciamos en la actualización /dev de hoy." }`; `mergeNews` elimina duplicados y ordena.
- [ ] **Step 2: FAIL → Step 3: implementar → Step 4: PASS**
- [ ] **Step 5: Commit** (pedir confirmación) — `feat: noticias`

### Task 9: Orquestador (`main.ts`) y lista de parches viejos

**Files:**
- Create: `pipeline/src/main.ts`, `pipeline/patches.seed.json`
- Test: `pipeline/test/main.test.ts`

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: `run(opts: { fetchText: FetchText; dataDir: string; now: string; seedUrls: string[] }): Promise<{ processed: string[]; missing: string[] }>`. El final del archivo llama a `run` con `createFetchText()`, `dataDir = ../data`, la hora actual y el seed, y ante cualquier error sale con código 1.
- `const PARSER_VERSION = 1` (subirlo cuando cambie el lector obliga a reprocesar todo).

Flujo de `run`:
1. Leer `state.json` (si no existe, el estado está vacío) y las salidas actuales.
2. Descargar `versions.json`, el listado de notas y las noticias (`/es-mx/news/game-updates/` y `/es-mx/news/dev/`).
3. Parches conocidos = listado ∪ seed. Procesar un parche si:
   - no está en el estado, o
   - es uno de los 2 más nuevos y su `rev` cambió, o
   - `PARSER_VERSION` cambió.
   Para los 2 más nuevos siempre se descarga la página, para comparar `rev`.
4. Por parche: descargar la página, `readPageMeta`, Data Dragon (actual y anterior: `champion.json`, `item.json`, `runesReforged.json`), `parsePatchNotes`, clasificar, verificar (estadísticas → habilidades, descargando de Community Dragon solo los campeones con habilidades cambiadas), `markAddedLater` contra el archivo anterior y `qualityCheck` (si falla, lanzar un error con el parche y el motivo).
5. Reconstruir los historiales de campeones y el índice con **todos** los parches (los nuevos más los ya publicados).
6. Escribir todo en `dataDir + ".tmp"` y, solo si todo terminó bien, reemplazar `dataDir` por esa carpeta.

`patches.seed.json`: URLs es-mx desde 25.S1.1 hasta 26.3. Ya comprobadas: `patch-25-s1-1-notes`, `patch-25-s1-2-notes`, `patch-25-04-notes`, `patch-25-05-notes`, `patch-25-06-notes`, `patch-25-10-notes`, `patch-25-24-notes`, `patch-26-1-notes`, `patch-26-2-notes`, `patch-26-3-notes`. Para 25.S1.3 el slug en en-us es `patch-2025-s1-3-notes`, y en es-mx hay que comprobarlo. Las demás (25.07–25.23) se completan probando `patch-25-NN-notes`, `patch-2025-NN-notes` y `league-of-legends-patch-25-NN-notes`, con una prueba manual de HTTP 200 por URL. Lo que no se encuentre va a `missing.json` y se informa: nunca se omite en silencio.

- [ ] **Step 1: Pruebas que fallan** (con una `fetchText` falsa que sirve las fixtures por URL):
  - Corrida con el parche 26.23 → `data/patches/26.23.json` existe con Azir alcance `verified`; `data/champions/Azir.json` existe; `index.json` lo lista.
  - Segunda corrida con el mismo `rev` → `processed` vacío.
  - Segunda corrida con otro `rev` y una línea agregada → solo esa línea tiene `addedLater: true`.
  - La `fetchText` falsa falla a mitad de la corrida → la promesa se rechaza y `data/` queda idéntico byte por byte.
  - Un URL del seed que responde 404 → aparece en `missing` y la corrida termina bien.
- [ ] **Step 2: FAIL → Step 3: implementar → Step 4: PASS** (`npm test` y `npm run typecheck`)
- [ ] **Step 5: Completar `patches.seed.json`** probando los slugs; anotar en el commit los que quedaron en `missing`.
- [ ] **Step 6: Corrida real: `npm start`** (tarda varios minutos por la pausa de 1 s). Se espera: `data/index.json` con unos 45 parches, `missing` vacío o explicado, código de salida 0.
- [ ] **Step 7: Control manual de exactitud.** Abrir en el navegador las notas oficiales de 26.23 y de un parche de 2025 y comparar 10 cambios al azar (5 de ADC: Ezreal, Ashe, Kalista, Twitch, Varus) contra `data/patches/*.json`. Los 10 deben coincidir textualmente. Además, revisar cada `mismatch` del total: si alguno es un error del lector, corregirlo con una prueba nueva antes de seguir. Informar al usuario cuántos cambios quedaron `verified` / `unchecked` / `mismatch`.
- [ ] **Step 8: Commit** (pedir confirmación) — `feat: orquestador y datos iniciales` (incluye `data/`)

### Task 10: GitHub Actions y publicación

**Files:**
- Create: `.github/workflows/update-data.yml`, `README.md` (cómo correrlo y las URLs de los datos)

**Interfaces:**
- Produces: el workflow `update-data`. URL base de los datos para la app: `https://raw.githubusercontent.com/<usuario>/<repo>/main/data/`.

Workflow:
- `on: schedule: - cron: "17 */3 * * *"` y `workflow_dispatch`.
- `permissions: contents: write`.
- Pasos: `actions/checkout@v4` → `actions/setup-node@v4` con `node-version: 24` → `npm ci` (en `pipeline/`) → `npm test` → `npm start`.
- Si `git status --porcelain data` no está vacío: commit `data: actualización automática` con el usuario `github-actions[bot]` y push.
- Si cualquier paso falla, el workflow falla y GitHub manda un mail; no hay commit.

- [ ] **Step 1: Escribir el workflow y el README**
- [ ] **Step 2: Pedir al usuario que cree el repositorio público en GitHub** (su cuenta) **y confirme el push** del código. Esto es acción del usuario o requiere su sí explícito.
- [ ] **Step 3: Lanzar el workflow a mano** (`gh workflow run update-data`) y comprobar con `gh run watch` que termina en verde y que `https://raw.githubusercontent.com/<usuario>/<repo>/main/data/index.json` responde con el JSON.
- [ ] **Step 4: Commit** (pedir confirmación) — `ci: actualización automática cada 3 h`

---

La **parte 2 (app de Android)** tiene su propio plan, que se escribe cuando esta parte esté publicada y el formato de `data/` esté probado con datos reales.
