import { normalizeName } from "./names.ts";
import type { RawChange, Section } from "./types.ts";

const MODE_TITLES = ["aram", "arena", "urf", "swarm", "nexusblitz", "brawl", "mayhem"];
const MODE_IDS = ["aram", "arena", "classic", "urf", "swarm", "nexus-blitz", "one-for-all", "brawl", "mayhem", "doom"];

export function sectionFor(raw: RawChange): Section {
  const id = raw.sectionId.toLowerCase();
  const title = normalizeName(raw.sectionTitle);
  if (MODE_IDS.some(m => id.includes(m)) || MODE_TITLES.some(m => title.includes(m))) return "modes";
  if (id.includes("skins") || id.includes("chromas")) return "cosmetics";
  const generic = raw.part !== null && !["base", "P", "Q", "W", "E", "R"].includes(raw.part);
  if (generic && /\b(aram|arena|urf)\b/i.test(raw.partTitle ?? "")) return "modes";
  if (raw.entity.kind === "champion") return "champions";
  if (raw.entity.kind === "item") return "items";
  if (raw.entity.kind === "rune") return "runes";
  if (id === "patch-champions") return "champions";
  if (id === "patch-items") return "items";
  if (id === "patch-runes") return "runes";
  return "systems";
}

export function tagsFor(raw: RawChange, championJson: any, itemJson: any): string[] {
  const { kind, id } = raw.entity;
  if (!id) return [];
  if (kind === "champion") return [...(championJson.data[id]?.tags ?? [])];
  if (kind === "item") return [...(itemJson.data[id]?.tags ?? [])];
  return [];
}
