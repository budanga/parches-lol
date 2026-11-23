import { test } from "node:test";
import assert from "node:assert/strict";
import { buildNameIndex } from "../src/names.ts";
import { fixtureJson } from "./helpers.ts";

const n = buildNameIndex(
  fixtureJson("ddragon/16.23.1/champion.json"),
  fixtureJson("ddragon/16.23.1/item.json"),
  fixtureJson("ddragon/16.23.1/runesReforged.json"));

test("nombres de campeones con caracteres especiales", () => {
  assert.equal(n.champion("Wukong")?.id, "MonkeyKing");
  assert.equal(n.champion("Kai'Sa")?.id, "Kaisa");
  assert.equal(n.champion("Bel'Veth")?.id, "Belveth");
  assert.equal(n.champion("Nunu y Willump")?.id, "Nunu");
});
test("campeón e ítem por URL de imagen", () => {
  assert.equal(n.champion("https://am-a.akamaihd.net/image?f=https://ddragon.leagueoflegends.com/cdn/16.22.1/img/champion/Azir.png")?.id, "Azir");
  assert.equal(n.item("https://am-a.akamaihd.net/image?f=https://ddragon.leagueoflegends.com/cdn/16.22.1/img/item/6699.png")?.id, "6699");
});
test("ítem por nombre y sin confusión entre tipos", () => {
  assert.equal(n.item("Cicloespada Voltaica")?.id, "6699");
  assert.equal(n.champion("Cicloespada Voltaica"), null);
});
test("runa por nombre", () => {
  assert.equal(n.rune("Cadencia Letal")?.id, "8008");
});

test("imagen de un objeto de otro modo → el objeto de la Grieta con el mismo nombre", () => {
  assert.equal(n.item("https://ddragon.leagueoflegends.com/cdn/15.9.1/img/item/223152.png")?.id, "3152");
  assert.equal(n.item("Cinturón Cohete Hextech")?.id, "3152");
});
