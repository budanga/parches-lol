import { patchFromSlug } from "./patches.ts";

const ORIGIN = "https://www.leagueoflegends.com";

export function readNextData(html: string): any {
  const m = /<script id="__NEXT_DATA__"[^>]*>(.*?)<\/script>/s.exec(html);
  if (!m) throw new Error("La página no tiene __NEXT_DATA__");
  return JSON.parse(m[1]);
}

export function readPageMeta(html: string): { title: string; publishedAt: string; rev: string } {
  const page = readNextData(html)?.props?.pageProps?.page;
  const title = page?.title, publishedAt = page?.analytics?.publishDate, rev = page?.analytics?.rev;
  if (!title || !publishedAt || !rev) throw new Error("Faltan metadatos de la página (title/publishDate/rev)");
  return { title: String(title).trim(), publishedAt, rev };
}

export function readPatchListing(html: string): { patch: string; url: string }[] {
  const out = new Map<string, string>();
  for (const m of html.matchAll(/\/es-mx\/news\/game-updates\/([a-z0-9-]+)\/?/g)) {
    const patch = patchFromSlug(m[1]);
    if (patch && !out.has(patch)) out.set(patch, `${ORIGIN}/es-mx/news/game-updates/${m[1]}/`);
  }
  return [...out].map(([patch, url]) => ({ patch, url }));
}
