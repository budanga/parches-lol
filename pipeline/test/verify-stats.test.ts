import { test } from "node:test";
import assert from "node:assert/strict";
import { verifyStat } from "../src/verify-stats.ts";
import { fixtureJson } from "./helpers.ts";
import { azirBase, azirW, voltaicPassive, champ2622, champ2623, item2622, item2623 } from "./shared.ts";

const d2622 = { champion: champ2622, item: item2622 };
const d2623 = { champion: champ2623, item: item2623 };
const costPrev = { champion: champ2622, item: fixtureJson("items-cost-prev.json") };
const costNew = { champion: champ2623, item: fixtureJson("items-cost-new.json") };

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
  const raw = { ...voltaicPassive, part: null, partTitle: null, attribute: "Costo total", before: "3100", after: "3000" };
  assert.equal(verifyStat(raw, costPrev, costNew)?.verification, "verified");
});
test("daño de ataque de ítem", () => {
  const raw = { ...voltaicPassive, part: null, partTitle: null, attribute: "Daño de Ataque", before: "60", after: "55" };
  assert.equal(verifyStat(raw, costPrev, costNew)?.verification, "verified");
});
test("efecto de ítem no relacionable → null", () => {
  assert.equal(verifyStat(voltaicPassive, d2622, d2623), null);
});
test("habilidad → null", () => { assert.equal(verifyStat(azirW, d2622, d2623), null); });

const champ = (stats: Record<string, number>) => ({ champion: { data: { Azir: { stats } } }, item: { data: {} } });
const boots = (total: number, base: number) => ({ champion: { data: {} }, item: { data: { "6699": { gold: { total, base }, stats: {} } } } });

test("Data Dragon sin cambios aunque las notas digan que cambió → unchecked (datos desactualizados)", () => {
  assert.deepEqual(verifyStat(azirBase, d2622, d2622), { verification: "unchecked", gameData: null });
});
test("campo en 0 en ambas versiones (dato roto en Data Dragon) → null", () => {
  const raw = { ...azirBase, attribute: "Crecimiento de Daño de Ataque", before: "3", after: "3.5" };
  assert.equal(verifyStat(raw, champ({ attackdamageperlevel: 0 }), champ({ attackdamageperlevel: 0 })), null);
});
test("'por nivel' en el valor → se compara con el crecimiento", () => {
  const raw = { ...azirBase, attribute: "Armadura", before: "+5.2 por nivel", after: "+5.4 por nivel" };
  assert.equal(verifyStat(raw, champ({ armor: 21, armorperlevel: 5.2 }), champ({ armor: 21, armorperlevel: 5.4 }))?.verification, "verified");
});
test("costo de mejora (gold.base) también vale", () => {
  const raw = { ...voltaicPassive, part: null, partTitle: null, attribute: "Costo", before: "750 de oro", after: "500 de oro" };
  assert.equal(verifyStat(raw, boots(1950, 750), boots(1700, 500))?.verification, "verified");
});

test("I1: 552 no se verifica contra 550", () => {
  assert.equal(verifyStat({ ...azirBase, after: "552" }, d2622, d2623)?.verification, "mismatch");
});
test("I1: costo 3000 no se verifica contra 3015", () => {
  const raw = { ...voltaicPassive, part: null, partTitle: null, attribute: "Costo total", before: "3100", after: "3000" };
  assert.equal(verifyStat(raw, boots(3100, 0), boots(3015, 0))?.verification, "mismatch");
});
test("M9: el juego no cambió y 'antes' tampoco coincide → unchecked (no ⚠️)", () => {
  const raw = { ...voltaicPassive, part: null, partTitle: null, attribute: "Costo total", before: "35", after: "45" };
  assert.equal(verifyStat(raw, boots(40, 0), boots(40, 0))?.verification, "unchecked");
});

test("I1: la nota '6' no verifica un 6.25 del juego (Naafiri 26.4)", () => {
  const raw = { ...azirBase, attribute: "Regeneración de Vida Base", before: "7.5", after: "6" };
  assert.equal(verifyStat(raw, champ({ hpregen: 7.5 }), champ({ hpregen: 6.25 }))?.verification, "mismatch");
});
