import { test } from "node:test";
import assert from "node:assert/strict";
import { readPageMeta, readPatchListing } from "../src/riot.ts";
import { fixture } from "./helpers.ts";

test("readPageMeta", () => {
  const m = readPageMeta(fixture("patch-26-23.html"));
  assert.equal(m.title, "Notas de la versión 26.23 de League of Legends");
  assert.equal(m.publishedAt, "2026-11-17T19:00:00.000Z");
  assert.ok(m.rev.length > 0);
});

test("readPatchListing incluye 26.23 y 26.4", () => {
  const l = readPatchListing(fixture("listing.html"));
  assert.equal(l.find(p => p.patch === "26.23")?.url,
    "https://www.leagueoflegends.com/es-mx/news/game-updates/league-of-legends-patch-26-23-notes/");
  assert.ok(l.some(p => p.patch === "26.4"));
  assert.equal(new Set(l.map(p => p.patch)).size, l.length);
});
