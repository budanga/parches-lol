import { readNextData } from "./riot.ts";
import type { NewsItem } from "./types.ts";

const ORIGIN = "https://www.leagueoflegends.com";
const stripTags = (s: string) => s.replace(/<[^>]*>/g, "").replace(/\s+/g, " ").trim();

export function readNews(html: string): NewsItem[] {
  const out: NewsItem[] = [];
  const walk = (o: any) => {
    if (!o || typeof o !== "object") return;
    const cat = o.category?.machineName;
    const url = o.action?.payload?.url;
    if (o.title && typeof url === "string" && o.publishedAt && (cat === "game-updates" || cat === "dev")) {
      out.push({
        title: String(o.title).trim(),
        publishedAt: o.publishedAt,
        url: url.startsWith("/") ? ORIGIN + url : url,
        imageUrl: o.media?.url ?? null,
        description: typeof o.description?.body === "string" ? stripTags(o.description.body) || null : null,
        category: cat,
      });
      return;
    }
    for (const v of Object.values(o)) walk(v);
  };
  walk(readNextData(html));
  return out;
}

export function mergeNews(lists: NewsItem[][], limit = 30): NewsItem[] {
  const byUrl = new Map<string, NewsItem>();
  for (const n of lists.flat()) if (!byUrl.has(n.url)) byUrl.set(n.url, n);
  return [...byUrl.values()].sort((a, b) => b.publishedAt.localeCompare(a.publishedAt)).slice(0, limit);
}
