import type { Catalog } from "./types.ts";

const byName = (a: { name: string }, b: { name: string }) => a.name.localeCompare(b.name, "es");

/** Every champion, and the purchasable Summoner's Rift items (one per name), for the app's pickers. */
export function buildCatalog(ddragonVersion: string, championJson: any, itemJson: any): Catalog {
  const champions = Object.values<any>(championJson.data)
    .map(c => ({ id: c.id as string, name: c.name as string, tags: [...(c.tags ?? [])] as string[] }))
    .sort(byName);
  const byItemName = new Map<string, { id: string; name: string; tags: string[] }>();
  for (const [id, it] of Object.entries<any>(itemJson.data)) {
    if (!it.maps?.["11"] || it.gold?.purchasable === false || !(it.gold?.total > 0)) continue;
    const prev = byItemName.get(it.name);
    if (!prev || Number(id) < Number(prev.id)) byItemName.set(it.name, { id, name: it.name, tags: [...(it.tags ?? [])] });
  }
  return { schemaVersion: 1, ddragonVersion, champions, items: [...byItemName.values()].sort(byName) };
}
