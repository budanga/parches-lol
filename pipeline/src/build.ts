import { createHash } from "node:crypto";
import type { Catalog, Change, ChampionHistory, PatchSummary, IndexFile, PatchFile, RawChange, Section, Verification } from "./types.ts";

export function changeId(raw: RawChange): string {
  const key = [raw.sectionId, raw.entity.id ?? raw.entity.name, raw.part ?? "", raw.attribute ?? "", raw.text].join("|");
  return createHash("sha1").update(key).digest("hex").slice(0, 12);
}

export function toChange(raw: RawChange, section: Section, tags: string[], verification: Verification,
  gameData: Change["gameData"]): Change {
  return { ...raw, id: changeId(raw), section, tags, verification, gameData, addedLater: false };
}

/** Identical lines (same section, entity and text) get an occurrence suffix so ids are unique and stable. */
export function assignIds(changes: Change[]): Change[] {
  const seen = new Map<string, number>();
  return changes.map(c => {
    const base = changeId(c);
    const n = seen.get(base) ?? 0;
    seen.set(base, n + 1);
    return { ...c, id: n === 0 ? base : createHash("sha1").update(`${base}#${n}`).digest("hex").slice(0, 12) };
  });
}

/** Lines that appear after a patch page was first published are flagged; flags are never lost. */
export function markAddedLater(next: Change[], previous: Change[] | null): Change[] {
  if (!previous) return next.map(c => ({ ...c, addedLater: false }));
  const before = new Map(previous.map(c => [c.id, c.addedLater]));
  return next.map(c => ({ ...c, addedLater: before.has(c.id) ? before.get(c.id)! : true }));
}

export function qualityCheck(changes: Change[]): { ok: true } | { ok: false; reason: string } {
  if (changes.length === 0) return { ok: false, reason: "no se extrajo ningún cambio" };
  const core = changes.filter(c => c.section === "champions" || c.section === "items");
  if (core.length === 0) return { ok: false, reason: "no hay cambios de campeones ni de objetos" };
  const orphan = core.filter(c => c.entity.id === null).length;
  if (core.length >= 5 && orphan / core.length > 0.2)
    return { ok: false, reason: `${orphan} de ${core.length} cambios de campeones/objetos sin entidad` };
  if (!core.some(c => c.kind === "stat")) return { ok: false, reason: "ningún cambio de campeones/objetos con formato antes ⇒ después" };
  return { ok: true };
}

const newestFirst = (a: { publishedAt: string }, b: { publishedAt: string }) => b.publishedAt.localeCompare(a.publishedAt);

/** Distinct champions and items changed in the champions/items sections, in page order. */
function changedEntities(changes: Change[]): PatchSummary["changed"] {
  const seen = new Map<string, PatchSummary["changed"][number]>();
  for (const c of changes) {
    const kind = c.entity.kind;
    if (!c.entity.id || !((kind === "champion" && c.section === "champions") || (kind === "item" && c.section === "items"))) continue;
    const key = kind + ":" + c.entity.id;
    if (!seen.has(key)) seen.set(key, { kind, id: c.entity.id, name: c.entity.name, tags: [...c.tags] });
  }
  return [...seen.values()];
}

export function buildIndex(patches: PatchFile[], now: string): IndexFile {
  const count = (cs: Change[], v: Verification) => cs.filter(c => c.verification === v).length;
  return {
    schemaVersion: 1,
    updatedAt: now,
    patches: [...patches].sort(newestFirst).map(p => ({
      patch: p.patch, title: p.title, url: p.url, publishedAt: p.publishedAt,
      counts: { total: p.changes.length, verified: count(p.changes, "verified"),
        unchecked: count(p.changes, "unchecked"), mismatch: count(p.changes, "mismatch") },
      changed: changedEntities(p.changes),
    })),
  };
}

export function buildChampionHistories(patches: PatchFile[], championJson: any): ChampionHistory[] {
  const byChamp = new Map<string, ChampionHistory>();
  for (const p of [...patches].sort(newestFirst)) {
    const perChamp = new Map<string, Change[]>();
    for (const c of p.changes) {
      if (c.entity.kind !== "champion" || !c.entity.id) continue;
      perChamp.set(c.entity.id, [...(perChamp.get(c.entity.id) ?? []), c]);
    }
    for (const [id, changes] of perChamp) {
      if (!byChamp.has(id)) {
        const info = championJson.data[id];
        byChamp.set(id, { schemaVersion: 1, id, name: info?.name ?? changes[0].entity.name, tags: [...(info?.tags ?? [])], entries: [] });
      }
      byChamp.get(id)!.entries.push({ patch: p.patch, publishedAt: p.publishedAt, changes });
    }
  }
  return [...byChamp.values()];
}

/**
 * Item histories, newest first. Riot sometimes gives an item a new id between versions, so changes are
 * filed under the current catalog id of the same name (falling back to their own id).
 */
export function buildItemHistories(patches: PatchFile[], catalog: Catalog): ChampionHistory[] {
  const currentId = new Map(catalog.items.map(i => [i.name, i.id]));
  const info = new Map(catalog.items.map(i => [i.id, i]));
  const byItem = new Map<string, ChampionHistory>();
  for (const p of [...patches].sort(newestFirst)) {
    const perItem = new Map<string, Change[]>();
    for (const c of p.changes) {
      if (c.entity.kind !== "item" || !c.entity.id) continue;
      const id = currentId.get(c.entity.name) ?? c.entity.id;
      perItem.set(id, [...(perItem.get(id) ?? []), c]);
    }
    for (const [id, changes] of perItem) {
      if (!byItem.has(id)) {
        const it = info.get(id);
        byItem.set(id, { schemaVersion: 1, id, name: it?.name ?? changes[0].entity.name, tags: [...(it?.tags ?? changes[0].tags)], entries: [] });
      }
      byItem.get(id)!.entries.push({ patch: p.patch, publishedAt: p.publishedAt, changes });
    }
  }
  return [...byItem.values()];
}
