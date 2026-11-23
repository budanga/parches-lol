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

test("C1: un número después de un paréntesis forma su propio grupo y, si no coincide con el juego, la línea queda unchecked", () => {
  const raw = { ...ezrealW, before: ezrealW.before + ", dura 3 seg", after: ezrealW.after + ", dura 9 seg" };
  assert.equal(verifyAbility(raw, ez2622, ez2623), "unchecked");
});
test("I1: 67.3% no coincide con 0.67 (se compara con la precisión de la nota)", () => {
  assert.equal(verifyAbility({ ...azirW, after: "67.3%" }, azir2622, azir2623), "unchecked");
});

const kal2424 = fixtureJson("cdragon/14.24/kalista.bin.json"), kal2501 = fixtureJson("cdragon/15.1/kalista.bin.json");
const { c25 } = await import("./shared.ts");
const kalistaQ = find(c25, c => c.entity.id === "Kalista" && c.part === "Q" && c.attribute === "Daño");

test("ratio sin paréntesis ('+ 105% de Daño de Ataque total') se verifica (Kalista Q 25.S1.1)", () => {
  assert.equal(verifyAbility(kalistaQ, kal2424, kal2501), "verified");
});
test("ratio sin paréntesis alterado → unchecked", () => {
  const bad = { ...kalistaQ, after: kalistaQ.after!.replace("+ 105%", "+ 110%") };
  assert.equal(verifyAbility(bad, kal2424, kal2501), "unchecked");
});

test("formato viejo de Community Dragon (mDataValues / mName / mValues)", () => {
  const bin = (vals: number[]) => ({ "Characters/X/Spells/XQAbility/XQ": { mSpell: { mDataValues: [{ mName: "BaseDamage", mValues: vals }] } } });
  const raw = { ...azirW, spellKey: "XQ", before: "10/20", after: "15/25" };
  assert.equal(verifyAbility(raw, bin([0, 10, 20]), bin([0, 15, 25])), "verified");
});
