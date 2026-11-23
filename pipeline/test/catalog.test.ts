import { test } from "node:test";
import assert from "node:assert/strict";
import { buildCatalog } from "../src/catalog.ts";
import { champ2623, item2623 } from "./shared.ts";

const cat = buildCatalog("16.23.1", champ2623, item2623);

test("catálogo: campeones completos y ordenados por nombre", () => {
  assert.equal(cat.champions.length, 173);
  assert.deepEqual(cat.champions.find(c => c.id === "MonkeyKing"), { id: "MonkeyKing", name: "Wukong", tags: ["Fighter", "Tank"] });
  const names = cat.champions.map(c => c.name);
  assert.deepEqual(names, [...names].sort((a, b) => a.localeCompare(b, "es")));
});

test("catálogo: ítems de la Grieta, sin variantes de Arena", () => {
  assert.deepEqual(cat.items.find(i => i.id === "3031")?.tags, ["CriticalStrike", "Damage"]);
  assert.equal(cat.items.find(i => i.id === "223152"), undefined);
  assert.equal(new Set(cat.items.map(i => i.name)).size, cat.items.length);
  assert.equal(cat.ddragonVersion, "16.23.1");
});
