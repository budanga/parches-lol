import { normalizeName } from "./names.ts";
import { sectionFor } from "./classify.ts";
import type { RawChange, Verification } from "./types.ts";

type GameData = { champion: any; item: any };
type Getter = (data: GameData, id: string) => number | undefined;

const champ = (key: string): Getter => (d, id) => d.champion?.data?.[id]?.stats?.[key];
const itemStat = (key: string, scale = 1): Getter => (d, id) => {
  const v = d.item?.data?.[id]?.stats?.[key];
  return v === undefined ? undefined : v * scale;
};

/** Keys are normalizeName(attribute) with the word "base" removed. */
const CHAMPION: Record<string, Getter> = {};
const addChamp = (key: string, ...names: string[]) => { for (const n of names) CHAMPION[n] = champ(key); };
addChamp("hp", "vida"); addChamp("hpperlevel", "vidapornivel", "crecimientodevida");
addChamp("mp", "mana"); addChamp("mpperlevel", "manapornivel", "crecimientodemana");
addChamp("armor", "armadura"); addChamp("armorperlevel", "armadurapornivel", "crecimientodearmadura");
addChamp("spellblock", "resistenciamagica");
addChamp("spellblockperlevel", "resistenciamagicapornivel", "crecimientoderesistenciamagica");
addChamp("movespeed", "velocidaddemovimiento");
addChamp("attackrange", "alcancedeataque");
addChamp("attackdamage", "danodeataque");
addChamp("attackdamageperlevel", "danodeataquepornivel", "crecimientodedanodeataque");
addChamp("attackspeed", "velocidaddeataque");
addChamp("attackspeedperlevel", "velocidaddeataquepornivel", "crecimientodevelocidaddeataque");
addChamp("hpregen", "regeneraciondevida"); addChamp("hpregenperlevel", "regeneraciondevidapornivel");

const goldTotal: Getter = (d, id) => d.item?.data?.[id]?.gold?.total;
const goldBase: Getter = (d, id) => d.item?.data?.[id]?.gold?.base;
/** "Costo" can be the total price or the upgrade (combine) cost: either matching counts. */
const COST: Getter[] = [goldTotal, goldBase];
const ITEM: Record<string, Getter | Getter[]> = {
  costo: COST,
  costototal: goldTotal,
  precio: COST,
  danodeataque: itemStat("FlatPhysicalDamageMod"),
  velocidaddeataque: itemStat("PercentAttackSpeedMod", 100),
  probabilidaddeimpactocritico: itemStat("FlatCritChanceMod", 100),
  vida: itemStat("FlatHPPoolMod"),
  armadura: itemStat("FlatArmorMod"),
  resistenciamagica: itemStat("FlatSpellBlockMod"),
  poderdehabilidad: itemStat("FlatMagicDamageMod"),
};

export const NUM_RE = /-?\d+(?:[.,]\d+)?/g;
/** A number as written in the notes. */
export interface NoteNum { v: number }
export const parseNoteNum = (s: string): NoteNum => ({ v: Number(s.replace(",", ".")) });
/**
 * The game value must be the note's number, allowing only float32 storage noise
 * (0.6700000166 = 67%): 550 ≠ 552, 0.67 ≠ 67.3%, 6.25 ≠ 6.
 */
export const matchesNote = (n: NoteNum, game: number): boolean => Math.abs(game - n.v) <= 1e-5 * Math.max(1, Math.abs(n.v));
const singleNumber = (s: string | null): NoteNum | null => {
  const m = s?.match(NUM_RE);
  return m && m.length === 1 ? parseNoteNum(m[0]) : null;
};
export const approx = (a: number, b: number): boolean => Math.abs(a - b) <= 0.005 * Math.max(1, Math.abs(b));
const fmt = (v: number): string => String(Number(v.toFixed(3)));

/** Base champion stats and item stats against Data Dragon. null = not a relatable change. */
export function verifyStat(raw: RawChange, prev: GameData, next: GameData):
  { verification: Verification; gameData: { before: string; after: string } | null } | null {
  if (raw.kind !== "stat" || !raw.attribute || !raw.entity.id || sectionFor(raw) === "modes") return null;
  let key = normalizeName(raw.attribute).replace(/base/g, "");
  // "Armadura: +5.2 por nivel ⇒ +5.4 por nivel" is the per-level growth.
  if (/por nivel/i.test(`${raw.before} ${raw.after}`) && !key.includes("pornivel") && !key.startsWith("crecimiento"))
    key += "pornivel";
  let found: Getter | Getter[] | undefined;
  if (raw.entity.kind === "champion" && (raw.part === "base" || raw.part === null)) found = CHAMPION[key];
  if (raw.entity.kind === "item" && (raw.part === null || /estad[ií]sticas/i.test(raw.part))) found = ITEM[key];
  if (!found) return null;

  const before = singleNumber(raw.before), after = singleNumber(raw.after);
  if (before === null || after === null) return null;
  const pairs: [number, number][] = [];
  for (const get of Array.isArray(found) ? found : [found]) {
    const old = get(prev, raw.entity.id), now = get(next, raw.entity.id);
    // Data Dragon zeroes some fields (attackdamageperlevel since 2026): no data, not a contradiction.
    if (old === undefined || now === undefined || (old === 0 && now === 0)) continue;
    if (matchesNote(before, old) && matchesNote(after, now)) return { verification: "verified", gameData: null };
    pairs.push([old, now]);
  }
  if (pairs.length === 0) return null;

  const [old, now] = pairs[0];
  // The notes say it changed but Data Dragon did not: stale data or a field we can't map, not a contradiction.
  if (approx(now, old)) return { verification: "unchecked", gameData: null };
  if (raw.midPatch) return { verification: "unchecked", gameData: null };
  return { verification: "mismatch", gameData: { before: fmt(old), after: fmt(now) } };
}
