import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, readdirSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { run } from "../src/main.ts";
import { readPageMeta } from "../src/riot.ts";
import { fixture } from "./helpers.ts";
import type { PatchFile, IndexFile, ChampionHistory } from "../src/types.ts";

const RIOT = "https://www.leagueoflegends.com/es-mx";
const P2623 = `${RIOT}/news/game-updates/league-of-legends-patch-26-23-notes/`;
const DD = "https://ddragon.leagueoflegends.com";
const CD = "https://raw.communitydragon.org";

function site(overrides: Record<string, string> = {}): Record<string, string> {
  const s: Record<string, string> = {
    [`${DD}/api/versions.json`]: fixture("versions.json"),
    [`${RIOT}/news/tags/patch-notes/`]: `<a href="/es-mx/news/game-updates/league-of-legends-patch-26-23-notes/">26.23</a>`,
    [`${RIOT}/news/game-updates/`]: fixture("news-dev.html"),
    [`${RIOT}/news/dev/`]: fixture("news-dev.html"),
    [P2623]: fixture("patch-26-23.html"),
  };
  for (const v of ["16.22.1", "16.23.1"]) for (const f of ["champion", "item", "runesReforged"])
    s[`${DD}/cdn/${v}/data/es_MX/${f}.json`] = fixture(`ddragon/${v}/${f}.json`);
  for (const v of ["16.22", "16.23"]) for (const c of ["azir", "ezreal"])
    s[`${CD}/${v}/game/data/characters/${c}/${c}.bin.json`] = fixture(`cdragon/${v}/${c}.bin.json`);
  return { ...s, ...overrides };
}
// Community Dragon files not in the fixtures answer with an empty bin (no spells → unchecked), unless listed as down.
const fakeFetch = (pages: Record<string, string>, failOn?: string, cdragonDown: string[] = []) => async (url: string) => {
  if (url === failOn || cdragonDown.includes(url)) throw new Error(`network down ${url}`);
  if (!(url in pages) && url.startsWith(CD)) return "{}";
  if (!(url in pages)) throw new Error(`HTTP 404 ${url}`);
  return pages[url];
};
const readJson = (dir: string, f: string) => JSON.parse(readFileSync(join(dir, f), "utf8"));
const snapshot = (dir: string): Record<string, string> => {
  const out: Record<string, string> = {};
  const walk = (d: string) => { for (const e of readdirSync(d, { withFileTypes: true })) {
    const p = join(d, e.name); e.isDirectory() ? walk(p) : (out[p] = readFileSync(p, "utf8")); } };
  walk(dir);
  return out;
};
const freshDir = () => join(mkdtempSync(join(tmpdir(), "parches-")), "data");

test("primera corrida publica parche, historial e índice", async () => {
  const dataDir = freshDir();
  const r = await run({ fetchText: fakeFetch(site()), dataDir, now: "2026-11-23T12:00:00Z", seedUrls: [] });
  assert.deepEqual(r.processed, ["26.23"]);
  const p: PatchFile = readJson(dataDir, "patches/26.23.json");
  const azir = p.changes.find(c => c.entity.id === "Azir" && c.part === "base")!;
  assert.equal(azir.verification, "verified");
  assert.equal(p.changes.find(c => c.entity.id === "Ezreal" && c.part === "W")!.verification, "verified");
  const h: ChampionHistory = readJson(dataDir, "champions/Azir.json");
  assert.equal(h.entries[0].patch, "26.23");
  const idx: IndexFile = readJson(dataDir, "index.json");
  assert.equal(idx.patches[0].patch, "26.23");
  assert.ok(readJson(dataDir, "news.json").length > 0);
});

test("segunda corrida con el mismo rev no reprocesa", async () => {
  const dataDir = freshDir();
  await run({ fetchText: fakeFetch(site()), dataDir, now: "2026-11-23T12:00:00Z", seedUrls: [] });
  const r = await run({ fetchText: fakeFetch(site()), dataDir, now: "2026-11-23T15:00:00Z", seedUrls: [] });
  assert.deepEqual(r.processed, []);
});

test("otro rev con una línea agregada → solo esa línea queda addedLater", async () => {
  const dataDir = freshDir();
  await run({ fetchText: fakeFetch(site()), dataDir, now: "2026-11-23T12:00:00Z", seedUrls: [] });
  const original = fixture("patch-26-23.html");
  const edited = original
    .replaceAll(`"rev":"${readPageMeta(original).rev}"`, '"rev":"editado"')
    .replace("<li><strong>Alcance de ataque</strong>: 525 ⇒ <strong>550</strong></li>",
      "<li><strong>Alcance de ataque</strong>: 525 ⇒ <strong>550</strong></li><li><strong>Armadura</strong>: 30 ⇒ <strong>32</strong></li>");
  assert.notEqual(edited, original);
  const r = await run({ fetchText: fakeFetch(site({ [P2623]: edited })), dataDir, now: "2026-11-23T15:00:00Z", seedUrls: [] });
  assert.deepEqual(r.processed, ["26.23"]);
  const p: PatchFile = readJson(dataDir, "patches/26.23.json");
  assert.deepEqual(p.changes.filter(c => c.addedLater).map(c => c.attribute), ["Armadura"]);
});

test("falla de red a mitad de corrida deja data/ intacto", async () => {
  const dataDir = freshDir();
  await run({ fetchText: fakeFetch(site()), dataDir, now: "2026-11-23T12:00:00Z", seedUrls: [] });
  const before = snapshot(dataDir);
  await assert.rejects(run({ fetchText: fakeFetch(site(), P2623), dataDir, now: "2026-11-23T15:00:00Z", seedUrls: [] }));
  assert.deepEqual(snapshot(dataDir), before);
});

test("URL del seed que no existe → missing, la corrida termina bien", async () => {
  const dataDir = freshDir();
  const r = await run({ fetchText: fakeFetch(site()), dataDir, now: "2026-11-23T12:00:00Z",
    seedUrls: [`${RIOT}/news/game-updates/patch-25-s1-3-notes/`] });
  assert.deepEqual(r.missing, ["25.S1.3"]);
  assert.deepEqual(readJson(dataDir, "missing.json"), ["25.S1.3"]);
  assert.ok(existsSync(join(dataDir, "patches/26.23.json")));
});

test("I4: los parches ya registrados siguen siendo conocidos aunque salgan del listado", async () => {
  const { knownPatches } = await import("../src/main.ts");
  const k = knownPatches([{ patch: "26.23", url: P2623 }], [], { parserVersion: 2, patches: { "26.4": { url: "u/26.4", rev: "r" } } });
  assert.equal(k.get("26.4"), "u/26.4");
  assert.equal(k.get("26.23"), P2623);
});

test("I5: listado vacío → la corrida falla y data/ queda intacto", async () => {
  const dataDir = freshDir();
  await run({ fetchText: fakeFetch(site()), dataDir, now: "2026-11-23T12:00:00Z", seedUrls: [] });
  const before = snapshot(dataDir);
  await assert.rejects(run({ fetchText: fakeFetch(site({ [`${RIOT}/news/tags/patch-notes/`]: "<html></html>" })), dataDir,
    now: "2026-11-23T15:00:00Z", seedUrls: [] }), /listado/);
  assert.deepEqual(snapshot(dataDir), before);
});

test("I5: noticias ilegibles → se conservan las anteriores", async () => {
  const dataDir = freshDir();
  await run({ fetchText: fakeFetch(site()), dataDir, now: "2026-11-23T12:00:00Z", seedUrls: [] });
  const news = readFileSync(join(dataDir, "news.json"), "utf8");
  await run({ fetchText: fakeFetch(site({ [`${RIOT}/news/game-updates/`]: "<html></html>", [`${RIOT}/news/dev/`]: "<html></html>" })),
    dataDir, now: "2026-11-23T15:00:00Z", seedUrls: [] });
  assert.equal(readFileSync(join(dataDir, "news.json"), "utf8"), news);
});

test("I7: si Community Dragon falla, el parche queda incompleto y se reintenta", async () => {
  const dataDir = freshDir();
  const azirUrl = `${CD}/16.23/game/data/characters/azir/azir.bin.json`;
  await run({ fetchText: fakeFetch(site(), undefined, [azirUrl]), dataDir, now: "2026-11-23T12:00:00Z", seedUrls: [] });
  assert.equal(readJson(dataDir, "state.json").patches["26.23"].incomplete, true);
  const r = await run({ fetchText: fakeFetch(site()), dataDir, now: "2026-11-23T15:00:00Z", seedUrls: [] });
  assert.deepEqual(r.processed, ["26.23"]);
  const p: PatchFile = readJson(dataDir, "patches/26.23.json");
  assert.equal(p.changes.find(c => c.entity.id === "Azir" && c.part === "W")!.verification, "verified");
  assert.equal(readJson(dataDir, "state.json").patches["26.23"].incomplete, undefined);
});

test("I9: un parche ya registrado cuya página da 404 conserva su archivo", async () => {
  const dataDir = freshDir();
  await run({ fetchText: fakeFetch(site()), dataDir, now: "2026-11-23T12:00:00Z", seedUrls: [] });
  const gone = site();
  delete gone[P2623];
  await run({ fetchText: fakeFetch(gone), dataDir, now: "2026-11-23T15:00:00Z", seedUrls: [] });
  assert.ok(existsSync(join(dataDir, "patches/26.23.json")));
});
