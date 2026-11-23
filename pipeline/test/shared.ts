import { parsePatchNotes } from "../src/parse.ts";
import { buildNameIndex } from "../src/names.ts";
import { fixture, fixtureJson } from "./helpers.ts";
import type { RawChange } from "../src/types.ts";

export const champ2622 = fixtureJson("ddragon/16.22.1/champion.json");
export const champ2623 = fixtureJson("ddragon/16.23.1/champion.json");
export const item2622 = fixtureJson("ddragon/16.22.1/item.json");
export const item2623 = fixtureJson("ddragon/16.23.1/item.json");
export const names2623 = buildNameIndex(champ2623, item2623, fixtureJson("ddragon/16.23.1/runesReforged.json"));
export const c2623 = parsePatchNotes(fixture("patch-26-23.html"), names2623);
export const c25 = parsePatchNotes(fixture("patch-25-s1-1.html"), names2623);
export const find = (list: RawChange[], pred: (c: RawChange) => boolean): RawChange => {
  const c = list.find(pred);
  if (!c) throw new Error("fixture change not found");
  return c;
};
export const azirBase = find(c2623, c => c.entity.id === "Azir" && c.part === "base");
export const azirW = find(c2623, c => c.entity.id === "Azir" && c.part === "W");
export const ezrealW = find(c2623, c => c.entity.id === "Ezreal" && c.part === "W");
export const voltaicPassive = find(c2623, c => c.entity.id === "6699");
