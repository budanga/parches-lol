import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { assignIds, buildChampionHistories, buildIndex, markAddedLater, qualityCheck, toChange } from "./build.ts";
import { sectionFor, tagsFor } from "./classify.ts";
import { buildNameIndex } from "./names.ts";
import { createFetchText } from "./net.ts";
import { mergeNews, readNews } from "./news.ts";
import { parsePatchNotes } from "./parse.ts";
import { cdragonVersion, ddragonVersionFor, patchFromSlug, previousVersion } from "./patches.ts";
import { readPageMeta, readPatchListing } from "./riot.ts";
import type { Change, FetchText, IndexFile, NewsItem, PatchFile, State } from "./types.ts";
import { cdragonChampionUrl, verifyAbility } from "./verify-abilities.ts";
import { verifyStat } from "./verify-stats.ts";

export const PARSER_VERSION = 4;
const RIOT = "https://www.leagueoflegends.com/es-mx";
const DD = "https://ddragon.leagueoflegends.com";
const is404 = (err: unknown) => err instanceof Error && /HTTP 404/.test(err.message);

/** "26.23" > "26.4" > "25.S1.3": year, then number. */
const patchOrder = (p: string) => {
  const m = /^(\d{2})\.(?:S\d+\.)?(\d+)$/.exec(p);
  return m ? Number(m[1]) * 1000 + Number(m[2]) : 0;
};

function readJsonIfExists<T>(file: string): T | null {
  return existsSync(file) ? JSON.parse(readFileSync(file, "utf8")) as T : null;
}

/** Listing ∪ seed ∪ already recorded patches, so a patch that drops off the listing is still reprocessed. */
export function knownPatches(listing: { patch: string; url: string }[], seedUrls: string[], state: State): Map<string, string> {
  const known = new Map(listing.map(l => [l.patch, l.url]));
  for (const url of seedUrls) {
    const p = patchFromSlug(url);
    if (p && !known.has(p)) known.set(p, url);
  }
  for (const [p, rec] of Object.entries(state.patches)) if (!known.has(p)) known.set(p, rec.url);
  return known;
}

export async function run(opts: { fetchText: FetchText; dataDir: string; now: string; seedUrls: string[] }):
  Promise<{ processed: string[]; missing: string[] }> {
  const { fetchText, dataDir, now, seedUrls } = opts;
  const state: State = readJsonIfExists<State>(join(dataDir, "state.json")) ?? { parserVersion: 0, patches: {} };
  const reprocessAll = state.parserVersion !== PARSER_VERSION;
  const existing = new Map<string, PatchFile>();
  if (existsSync(join(dataDir, "patches")))
    for (const f of readdirSync(join(dataDir, "patches"))) {
      const p = readJsonIfExists<PatchFile>(join(dataDir, "patches", f));
      if (p) existing.set(p.patch, p);
    }

  const json = new Map<string, Promise<any>>();
  const getJson = (url: string) => {
    if (!json.has(url)) json.set(url, fetchText(url).then(t => JSON.parse(t)));
    return json.get(url)!;
  };
  const dd = (v: string, file: string) => getJson(`${DD}/cdn/${v}/data/es_MX/${file}.json`);
  /** Community Dragon is best effort: unavailable data only means "unchecked". */
  const bin = (v: string, champ: string) => getJson(cdragonChampionUrl(cdragonVersion(v), champ)).catch(() => null);

  const versions: string[] = await getJson(`${DD}/api/versions.json`);
  const listing = readPatchListing(await fetchText(`${RIOT}/news/tags/patch-notes/`));
  // A listing Riot reshaped would silently stop new patches: fail loudly instead.
  if (listing.length === 0) throw new Error("El listado de notas no tiene parches (¿cambió el formato de la página?)");
  const newestRecorded = Object.keys(state.patches).sort((a, b) => patchOrder(b) - patchOrder(a))[0];
  if (newestRecorded && !listing.some(l => l.patch === newestRecorded))
    throw new Error(`El listado de notas no incluye el parche más nuevo registrado (${newestRecorded})`);

  // News are secondary: a broken news page keeps the previous news instead of stopping the run.
  const oldNews = readJsonIfExists<NewsItem[]>(join(dataDir, "news.json"));
  const readNewsSafe = async (url: string) => {
    try { return readNews(await fetchText(url)); } catch (err) { console.warn(`Noticias: ${err}`); return []; }
  };
  const freshNews = mergeNews([await readNewsSafe(`${RIOT}/news/game-updates/`), await readNewsSafe(`${RIOT}/news/dev/`)]);
  const news = freshNews.length === 0 && oldNews ? oldNews : freshNews;

  const known = knownPatches(listing, seedUrls, state);
  const ordered = [...known.keys()].sort((a, b) => patchOrder(b) - patchOrder(a));
  const newest = new Set(ordered.slice(0, 2));

  const processed: string[] = [], missing: string[] = [];
  const patches = new Map(existing);
  const nextState: State = { parserVersion: PARSER_VERSION, patches: { ...state.patches } };

  for (const patch of ordered) {
    const url = known.get(patch)!;
    const recorded = state.patches[patch];
    const need = !recorded || reprocessAll || newest.has(patch) || !existing.has(patch);
    if (!need) continue;

    let html: string;
    try {
      html = await fetchText(url);
    } catch (err) {
      if (is404(err) && existing.has(patch)) { console.warn(`Parche ${patch}: la página dio 404, se conserva lo publicado`); continue; }
      if (is404(err)) { missing.push(patch); continue; }
      throw err;
    }
    const meta = readPageMeta(html);
    if (recorded && recorded.rev === meta.rev && !reprocessAll && existing.has(patch) && !recorded.incomplete) continue;

    const version = ddragonVersionFor(patch, versions);
    const prevVersion = version ? previousVersion(version, versions) : null;
    if (!version || !prevVersion) continue; // Data Dragon not published yet: try again next run.

    const [champNow, itemNow, runesNow, champPrev, itemPrev] = await Promise.all([
      dd(version, "champion"), dd(version, "item"), dd(version, "runesReforged"),
      dd(prevVersion, "champion"), dd(prevVersion, "item"),
    ]);
    const raws = parsePatchNotes(html, buildNameIndex(champNow, itemNow, runesNow));

    let changes: Change[] = [];
    let incomplete = false;
    for (const raw of raws) {
      const section = sectionFor(raw);
      const tags = tagsFor(raw, champNow, itemNow);
      const stat = verifyStat(raw, { champion: champPrev, item: itemPrev }, { champion: champNow, item: itemNow });
      if (stat) { changes.push(toChange(raw, section, tags, stat.verification, stat.gameData)); continue; }
      let verification: Change["verification"] = "unchecked";
      if (raw.spellKey && raw.entity.kind === "champion" && raw.entity.id) {
        const [b0, b1] = await Promise.all([bin(prevVersion, raw.entity.id), bin(version, raw.entity.id)]);
        if (!b0 || !b1) incomplete = true;
        verification = (b0 && b1 ? verifyAbility(raw, b0, b1) : null) ?? "unchecked";
      }
      changes.push(toChange(raw, section, tags, verification, null));
    }

    const previous = existing.get(patch)?.changes ?? null;
    changes = markAddedLater(assignIds(changes), reprocessAll ? null : previous);
    if (reprocessAll && previous) {
      const flagged = new Set(previous.filter(c => c.addedLater).map(c => c.id));
      changes = changes.map(c => ({ ...c, addedLater: flagged.has(c.id) }));
    }
    const qc = qualityCheck(changes);
    if (!qc.ok) throw new Error(`Parche ${patch}: ${qc.reason}`);

    patches.set(patch, { schemaVersion: 1, patch, title: meta.title, url, publishedAt: meta.publishedAt,
      ddragonVersion: version, changes });
    // Missing Community Dragon data is retried while the patch is one of the newest two.
    nextState.patches[patch] = incomplete ? { url, rev: meta.rev, incomplete: true } : { url, rev: meta.rev };
    processed.push(patch);
  }

  const all = [...patches.values()];
  const histories = buildChampionHistories(all, await dd(versions[0], "champion"));
  const oldIndex = readJsonIfExists<IndexFile>(join(dataDir, "index.json"));
  const unchanged = processed.length === 0 && JSON.stringify(oldNews) === JSON.stringify(news);
  const index = buildIndex(all, unchanged && oldIndex ? oldIndex.updatedAt : now);

  // Write everything next to data/ and swap only once it is complete.
  const tmp = dataDir + ".tmp";
  rmSync(tmp, { recursive: true, force: true });
  mkdirSync(join(tmp, "patches"), { recursive: true });
  mkdirSync(join(tmp, "champions"), { recursive: true });
  const write = (f: string, v: unknown) => writeFileSync(join(tmp, f), JSON.stringify(v, null, 1) + "\n");
  for (const p of all) write(`patches/${p.patch}.json`, p);
  for (const h of histories) write(`champions/${h.id}.json`, h);
  write("index.json", index);
  write("news.json", news);
  write("state.json", nextState);
  write("missing.json", missing.sort((a, b) => patchOrder(a) - patchOrder(b)));
  rmSync(dataDir, { recursive: true, force: true });
  renameSync(tmp, dataDir);

  return { processed, missing };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const here = dirname(fileURLToPath(import.meta.url));
  const seedUrls: string[] = JSON.parse(readFileSync(join(here, "..", "patches.seed.json"), "utf8"));
  run({ fetchText: createFetchText(), dataDir: join(here, "..", "..", "data"), now: new Date().toISOString(), seedUrls })
    .then(r => console.log(`Procesados: ${r.processed.join(", ") || "ninguno"}\nSin encontrar: ${r.missing.join(", ") || "ninguno"}`))
    .catch(err => { console.error(err); process.exit(1); });
}
