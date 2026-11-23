import { test } from "node:test";
import assert from "node:assert/strict";
import { readNews, mergeNews } from "../src/news.ts";
import { fixture } from "./helpers.ts";
import type { NewsItem } from "../src/types.ts";

test("readNews lee la noticia /dev real", () => {
  const n = readNews(fixture("news-dev.html")).find(n => n.title === "RESUMEN: Actualización /dev de la Temporada 1 2027")!;
  assert.equal(n.category, "dev");
  assert.equal(n.publishedAt, "2026-11-16T16:00:00.000Z");
  assert.equal(n.url, "https://www.leagueoflegends.com/es-mx/news/dev/tldw-season-1-2027-dev-update---lol-website-article-lollol27-s1225r001");
  assert.equal(n.description, "Un breve resumen de los cambios que anunciamos en la actualización /dev de hoy.");
  assert.match(n.imageUrl!, /^https:\/\/cmsassets\.rgpub\.io\//);
});

test("readNews solo trae game-updates y dev", () => {
  assert.ok(readNews(fixture("news-dev.html")).every(n => n.category === "dev" || n.category === "game-updates"));
});

test("mergeNews elimina duplicados y ordena", () => {
  const a: NewsItem = { title: "A", publishedAt: "2026-01-01", url: "u1", imageUrl: null, description: null, category: "dev" };
  const b: NewsItem = { ...a, title: "B", publishedAt: "2026-02-01", url: "u2" };
  assert.deepEqual(mergeNews([[a, b], [a]]).map(n => n.title), ["B", "A"]);
  assert.equal(mergeNews([[a, b]], 1).length, 1);
});
