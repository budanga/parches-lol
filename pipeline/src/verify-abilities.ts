import { sectionFor } from "./classify.ts";
import { matchesNote, NUM_RE, parseNoteNum, type NoteNum } from "./verify-stats.ts";
import type { RawChange, Verification } from "./types.ts";

interface Group { nums: NoteNum[]; pct: boolean }

export function cdragonChampionUrl(cdVersion: string, championId: string): string {
  const id = championId.toLowerCase();
  return `https://raw.communitydragon.org/${cdVersion}/game/data/characters/${id}/${id}.bin.json`;
}

/**
 * "80 / 135 (+100% de AD) (+90% de AP)" → [80,135], [100%], [90%].
 * "20/85/150 + 105% de AD" → [20,85,150], [105%]: outside parentheses, "+" also separates groups.
 */
export function numericGroups(text: string): Group[] {
  const parse = (s: string): Group => {
    const nums: NoteNum[] = [];
    let pct = false;
    for (const m of s.matchAll(/(-?\d+(?:[.,]\d+)?)\s*(%)?/g)) {
      nums.push(parseNoteNum(m[1]));
      if (m[2]) pct = true;
    }
    return { nums, pct };
  };
  const outside = text.replace(/\([^)]*\)/g, "\u0000").split(/[+\u0000]/);
  const groups = outside.map(parse);
  for (const m of text.matchAll(/\(([^)]*)\)/g)) groups.push(parse(m[1]));
  return groups.filter(g => g.nums.length > 0);
}

function findSpell(bin: any, spellKey: string): any | null {
  for (const [k, v] of Object.entries<any>(bin ?? {})) if (k.endsWith(`/${spellKey}`) && v?.mSpell) return v.mSpell;
  return null;
}

/** Every number list in a spell that a patch note line could be talking about. */
function candidates(spell: any): Map<string, number[]> {
  const out = new Map<string, number[]>();
  for (const dv of spell.DataValues ?? []) if (dv?.name && Array.isArray(dv.values)) out.set(`dv:${dv.name}`, dv.values);
  // Older game files (2025) use mDataValues / mName / mValues.
  for (const dv of spell.mDataValues ?? []) if (dv?.mName && Array.isArray(dv.mValues)) out.set(`dv:${dv.mName}`, dv.mValues);
  if (Array.isArray(spell.cooldownTime)) out.set("cooldown", spell.cooldownTime);
  if (Array.isArray(spell.mana)) out.set("mana", spell.mana);
  const walk = (o: any, path: string) => {
    if (!o || typeof o !== "object") return;
    if (typeof o.mCoefficient === "number") out.set(`coef:${path}`, [o.mCoefficient]);
    for (const [k, v] of Object.entries(o)) walk(v, `${path}/${k}`);
  };
  walk(spell.mSpellCalculations, "");
  return out;
}

function matches(values: number[] | undefined, g: Group): boolean {
  if (!values) return false;
  // "67%" may be stored as 0.67 or as 67.
  const toNote = g.pct ? [(v: number) => v, (v: number) => v * 100] : [(v: number) => v];
  return toNote.some(conv => {
    const ok = (v: number | undefined, n: NoteNum) => v !== undefined && matchesNote(n, conv(v));
    if (g.nums.length === 1) {
      if (values.length === 1) return ok(values[0], g.nums[0]);
      return [values.slice(1, 6), values.slice(0, 5)].some(r => r.length > 0 && r.every(v => ok(v, g.nums[0])));
    }
    return [1, 0].some(off => g.nums.every((n, i) => ok(values[i + off], n)));
  });
}

/** True when the groups account for every number written in the text. */
const coversAll = (text: string, groups: Group[]) =>
  (text.match(NUM_RE)?.length ?? 0) === groups.reduce((s, g) => s + g.nums.length, 0);

/** Ability lines against Community Dragon. Only "verified" when every number group checks out. */
export function verifyAbility(raw: RawChange, prevBin: unknown, nextBin: unknown): Verification | null {
  if (raw.kind !== "stat" || !raw.spellKey) return null;
  if (sectionFor(raw) === "modes") return "unchecked";
  const prevSpell = findSpell(prevBin, raw.spellKey), nextSpell = findSpell(nextBin, raw.spellKey);
  if (!prevSpell || !nextSpell) return "unchecked";

  const before = numericGroups(raw.before ?? ""), after = numericGroups(raw.after ?? "");
  if (before.length === 0 || before.length !== after.length) return "unchecked";
  // A number outside the groups (e.g. after a parenthesis) would go unchecked: never call that verified.
  if (!coversAll(raw.before ?? "", before) || !coversAll(raw.after ?? "", after)) return "unchecked";

  const prev = candidates(prevSpell), next = candidates(nextSpell);
  const allMatch = before.every((g, i) =>
    [...prev.keys()].some(k => matches(prev.get(k), g) && matches(next.get(k), after[i])));
  return allMatch ? "verified" : "unchecked";
}
