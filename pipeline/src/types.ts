export type Verification = "verified" | "unchecked" | "mismatch";
export type Section = "champions" | "items" | "runes" | "systems" | "modes" | "cosmetics";
export type EntityKind = "champion" | "item" | "rune" | "none";

export interface RawChange {
  sectionTitle: string;
  sectionId: string;
  midPatch: boolean;
  entity: { kind: EntityKind; id: string | null; name: string };
  part: string | null;
  partTitle: string | null;
  spellKey: string | null;
  kind: "stat" | "new" | "removed" | "text";
  attribute: string | null;
  before: string | null;
  after: string | null;
  text: string;
}

export interface Change extends RawChange {
  id: string;
  section: Section;
  tags: string[];
  verification: Verification;
  gameData: { before: string; after: string } | null;
  addedLater: boolean;
}

export interface PatchFile {
  schemaVersion: 1; patch: string; title: string; url: string;
  publishedAt: string; ddragonVersion: string; changes: Change[];
}
export interface PatchSummary {
  patch: string; title: string; url: string; publishedAt: string;
  counts: { total: number; verified: number; unchecked: number; mismatch: number };
  /** Champions and items changed on the Rift in this patch (for the app's favourites row). */
  changed: { kind: "champion" | "item"; id: string; name: string; tags: string[] }[];
}
export interface IndexFile { schemaVersion: 1; updatedAt: string; patches: PatchSummary[] }
export interface ChampionHistory {
  schemaVersion: 1; id: string; name: string; tags: string[];
  entries: { patch: string; publishedAt: string; changes: Change[] }[];
}
export interface NewsItem {
  title: string; publishedAt: string; url: string;
  imageUrl: string | null; description: string | null; category: "game-updates" | "dev";
}
export interface State {
  parserVersion: number;
  patches: Record<string, { url: string; rev: string; incomplete?: boolean }>;
}
export type FetchText = (url: string) => Promise<string>;
export interface Catalog {
  schemaVersion: 1; ddragonVersion: string;
  champions: { id: string; name: string; tags: string[] }[];
  items: { id: string; name: string; tags: string[] }[];
}
