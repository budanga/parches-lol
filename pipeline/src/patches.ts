/** "league-of-legends-patch-26-23-notes" → "26.23"; "patch-25-s1-1-notes" → "25.S1.1". */
export function patchFromSlug(slug: string): string | null {
  const m = /patch-(?:20)?(\d{2})-(?:s(\d+)-)?(\d+)-notes/i.exec(slug);
  if (!m) return null;
  const [, yy, season, n] = m;
  if (Number(yy) < 25) return null; // history starts in 2025; older slugs use 14.x numbering
  return season ? `${yy}.S${season}.${Number(n)}` : `${yy}.${Number(n)}`;
}

/** Riot patch → Data Dragon version: yy-10 . n . 1 (S1.x uses x). */
export function ddragonVersionFor(patch: string, versions: string[]): string | null {
  const m = /^(\d{2})\.(?:S\d+\.)?(\d+)$/.exec(patch);
  if (!m) return null;
  const v = `${Number(m[1]) - 10}.${Number(m[2])}.1`;
  return versions.includes(v) ? v : null;
}

/** versions.json is newest first, so the previous version is the next entry. */
export function previousVersion(version: string, versions: string[]): string | null {
  const i = versions.indexOf(version);
  return i >= 0 && i + 1 < versions.length ? versions[i + 1] : null;
}

export function cdragonVersion(ddragonVersion: string): string {
  return ddragonVersion.split(".").slice(0, 2).join(".");
}
