import { test } from "node:test";
import assert from "node:assert/strict";
import { sectionFor, tagsFor } from "../src/classify.ts";
import { c2623, c25, find, azirBase, voltaicPassive, ezrealW, champ2623, item2623 } from "./shared.ts";

test("sectionFor", () => {
  assert.equal(sectionFor(azirBase), "champions");
  assert.equal(sectionFor(voltaicPassive), "items");
  assert.equal(sectionFor(find(c2623, c => c.kind === "new" && c.entity.id === "Talon")), "modes");
  assert.equal(sectionFor(find(c2623, c => c.text.startsWith("El chat de voz de equipo está habilitado"))), "systems");
  assert.equal(sectionFor(find(c25, c => c.entity.id === "JarvanIV")), "champions");
  assert.equal(sectionFor(find(c25, c => c.sectionId === "patch-aram" && c.text.startsWith("Swain:"))), "modes");
  assert.equal(sectionFor(find(c2623, c => c.sectionId.startsWith("patch-upcoming-skins"))), "cosmetics");
});

test("tagsFor", () => {
  assert.ok(tagsFor(ezrealW, champ2623, item2623).includes("Marksman"));
  assert.deepEqual(tagsFor({ ...voltaicPassive, entity: { kind: "item", id: "3031", name: "Filo del Infinito" } }, champ2623, item2623),
    ["CriticalStrike", "Damage"]);
  assert.deepEqual(tagsFor(find(c2623, c => c.text.startsWith("El chat de voz")), champ2623, item2623), []);
});

test("sección 'Campeones (Arena)' con id patch-champions → modes", () => {
  const raw = { ...azirBase, sectionId: "patch-champions", sectionTitle: "Campeones (Arena)" };
  assert.equal(sectionFor(raw), "modes");
});

test("M4: subsección de ARAM dentro de un hotfix → modes, pero no una habilidad llamada 'Arenas…'", () => {
  assert.equal(sectionFor({ ...azirBase, entity: { kind: "none", id: null, name: "" }, sectionId: "patch-midpatch-updates",
    sectionTitle: "Actualizaciones de mitad de versión", part: "Actualizaciones para ARAM y ARAM: Caos",
    partTitle: "Actualizaciones para ARAM y ARAM: Caos" }), "modes");
  assert.equal(sectionFor({ ...azirBase, part: "E", partTitle: "E: Arenas Movedizas" }), "champions");
});
