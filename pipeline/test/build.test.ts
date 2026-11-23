import { test } from "node:test";
import assert from "node:assert/strict";
import { changeId, markAddedLater, qualityCheck, buildIndex, buildChampionHistories, toChange } from "../src/build.ts";
import type { Change, PatchFile } from "../src/types.ts";
import { c2623, azirBase, azirW, ezrealW, champ2623 } from "./shared.ts";

const ch = (raw = azirBase, extra: Partial<Change> = {}): Change =>
  ({ ...toChange(raw, "champions", ["Mage"], "verified", null), ...extra });
const patch = (p: string, publishedAt: string, changes: Change[]): PatchFile =>
  ({ schemaVersion: 1, patch: p, title: `Notas ${p}`, url: `u/${p}`, publishedAt, ddragonVersion: "x", changes });

test("changeId estable y distinto por cambio", () => {
  assert.equal(changeId(azirBase), changeId({ ...azirBase }));
  assert.notEqual(changeId(azirBase), changeId(azirW));
  assert.match(changeId(azirBase), /^[0-9a-f]{12}$/);
});

test("markAddedLater", () => {
  const a = ch(azirBase), w = ch(azirW), e = ch(ezrealW);
  assert.deepEqual(markAddedLater([a, w], null).map(c => c.addedLater), [false, false]);
  const second = markAddedLater([a, w, e], [a, w]);
  assert.deepEqual(second.map(c => c.addedLater), [false, false, true]);
  const third = markAddedLater([a, w, e], second);
  assert.deepEqual(third.map(c => c.addedLater), [false, false, true]);
});

test("qualityCheck", () => {
  assert.equal(qualityCheck([]).ok, false);
  const ok = Array.from({ length: 7 }, () => ch());
  const bad = Array.from({ length: 3 }, () => ch(azirBase, { entity: { kind: "none", id: null, name: "?" } }));
  assert.equal(qualityCheck([...ok, ...bad]).ok, false);
  assert.equal(qualityCheck(Array.from({ length: 6 }, () => ch(azirBase, { kind: "text" }))).ok, false);
  assert.equal(qualityCheck(c2623.map(r => ch(r, { section: r.sectionId === "patch-champions" ? "champions" : "systems" }))).ok, true);
});

test("buildIndex ordena y cuenta", () => {
  const idx = buildIndex([
    patch("26.22", "2026-11-03T00:00:00Z", [ch()]),
    patch("26.23", "2026-11-17T00:00:00Z", [ch(), ch(azirW, { verification: "unchecked" }), ch(ezrealW, { verification: "mismatch" })]),
  ], "2026-11-23T00:00:00Z");
  assert.deepEqual(idx.patches.map(p => p.patch), ["26.23", "26.22"]);
  assert.deepEqual(idx.patches[0].counts, { total: 3, verified: 1, unchecked: 1, mismatch: 1 });
  assert.equal(idx.updatedAt, "2026-11-23T00:00:00Z");
});

test("buildChampionHistories: más nuevo primero y solo campeones con cambios", () => {
  const hs = buildChampionHistories([
    patch("26.22", "2026-11-03T00:00:00Z", [ch(azirW)]),
    patch("26.23", "2026-11-17T00:00:00Z", [ch(azirBase), ch(ezrealW)]),
  ], champ2623);
  const azir = hs.find(h => h.id === "Azir")!;
  assert.deepEqual(azir.entries.map(e => e.patch), ["26.23", "26.22"]);
  assert.equal(azir.name, "Azir");
  assert.ok(hs.find(h => h.id === "Ezreal")!.tags.includes("Marksman"));
  assert.equal(hs.find(h => h.id === "Annie"), undefined);
});

test("I8: assignIds da ids únicos a líneas iguales y es estable", async () => {
  const { assignIds } = await import("../src/build.ts");
  const a = ch(azirBase), arena = ch({ ...azirBase, sectionId: "patch-arena" }), again = ch(azirBase);
  const ids = assignIds([a, arena, again]).map(c => c.id);
  assert.equal(new Set(ids).size, 3);
  assert.deepEqual(assignIds([a, arena, again]).map(c => c.id), ids);
});
