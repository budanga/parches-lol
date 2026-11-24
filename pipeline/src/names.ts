export interface Named { id: string; name: string }
export interface NameIndex {
  champion(nameOrImg: string): Named | null;
  item(nameOrImg: string): Named | null;
  rune(name: string): Named | null;
}

/**
 * Items the patch notes call by another name than Data Dragon (es_MX). Checked against the English
 * names: Symbiotic Soles is 3010 "Botas Simbióticas", Chainlaced Crushers is 3173 "Trituradoras Aceradas".
 */
const ITEM_ALIASES: Record<string, string> = {
  suelassimbioticas: "Botas Simbióticas",
  trituradorasdemalla: "Trituradoras Aceradas",
};

export function normalizeName(s: string): string {
  return s.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[\s'’.\-&]/g, "");
}

export function buildNameIndex(championJson: any, itemJson: any, runesJson: any): NameIndex {
  const champs = new Map<string, Named>(), champById = new Map<string, Named>();
  for (const c of Object.values<any>(championJson.data)) {
    const n = { id: c.id, name: c.name };
    champs.set(normalizeName(c.name), n);
    champById.set(c.id, n);
  }
  const items = new Map<string, Named>(), itemById = new Map<string, Named>(), riftIds = new Set<string>();
  // Several item ids share a name (Arena and other mode variants): prefer the Summoner's Rift one.
  const rank = (id: string) => (riftIds.has(id) ? 0 : 1e9) + Number(id);
  for (const [id, it] of Object.entries<any>(itemJson.data)) {
    itemById.set(id, { id, name: it.name });
    if (it.maps?.["11"] && it.gold?.purchasable !== false) riftIds.add(id);
  }
  for (const n of itemById.values()) {
    const key = normalizeName(n.name);
    if (!items.has(key) || rank(n.id) < rank(items.get(key)!.id)) items.set(key, n);
  }
  const runes = new Map<string, Named>();
  for (const tree of runesJson) for (const slot of tree.slots) for (const r of slot.runes)
    runes.set(normalizeName(r.name), { id: String(r.id), name: r.name });

  return {
    champion(s) {
      const img = /\/img\/champion\/([A-Za-z0-9]+)\.png/.exec(s);
      return img ? champById.get(img[1]) ?? null : champs.get(normalizeName(s)) ?? null;
    },
    item(s) {
      const img = /\/img\/item\/(\d+)\.png/.exec(s);
      if (!img) {
        const key = normalizeName(s);
        return items.get(key) ?? (ITEM_ALIASES[key] ? items.get(normalizeName(ITEM_ALIASES[key])) ?? null : null);
      }
      const n = itemById.get(img[1]);
      return n && !riftIds.has(n.id) ? items.get(normalizeName(n.name)) ?? n : n ?? null;
    },
    rune: s => runes.get(normalizeName(s)) ?? null,
  };
}
