import { test } from "node:test";
import assert from "node:assert/strict";
import { verifyAbility, cdragonChampionUrl } from "../src/verify-abilities.ts";
import { fixtureJson } from "./helpers.ts";
import { azirBase, azirW, ezrealW, c2623, find } from "./shared.ts";

const azir2622 = fixtureJson("cdragon/16.22/azir.bin.json"), azir2623 = fixtureJson("cdragon/16.23/azir.bin.json");
const ez2622 = fixtureJson("cdragon/16.22/ezreal.bin.json"), ez2623 = fixtureJson("cdragon/16.23/ezreal.bin.json");

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
test("en modos de juego no se verifica", () => {
  assert.equal(verifyAbility({ ...azirW, sectionId: "patch-aram" }, azir2622, azir2623), "unchecked");
});
test("no es habilidad → null", () => { assert.equal(verifyAbility(azirBase, azir2622, azir2623), null); });
test("url de Community Dragon", () => {
  assert.equal(cdragonChampionUrl("16.23", "MonkeyKing"),
    "https://raw.communitydragon.org/16.23/game/data/characters/monkeyking/monkeyking.bin.json");
});
test("Ezreal R: informe del resultado real", () => {
  const r = find(c2623, c => c.entity.id === "Ezreal" && c.part === "R");
  assert.ok(["verified", "unchecked"].includes(verifyAbility(r, ez2622, ez2623)!));
});

test("C1: un número fuera de los grupos (después de un paréntesis) → unchecked", () => {
  const raw = { ...ezrealW, before: ezrealW.before + ", dura 3 seg", after: ezrealW.after + ", dura 9 seg" };
  assert.equal(verifyAbility(raw, ez2622, ez2623), "unchecked");
});
test("I1: 67.3% no coincide con 0.67 (se compara con la precisión de la nota)", () => {
  assert.equal(verifyAbility({ ...azirW, after: "67.3%" }, azir2622, azir2623), "unchecked");
});
