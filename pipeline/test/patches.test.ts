import { test } from "node:test";
import assert from "node:assert/strict";
import { patchFromSlug, ddragonVersionFor, previousVersion, cdragonVersion } from "../src/patches.ts";
import { fixtureJson } from "./helpers.ts";

const versions: string[] = fixtureJson("versions.json");

test("patchFromSlug", () => {
  assert.equal(patchFromSlug("league-of-legends-patch-26-23-notes"), "26.23");
  assert.equal(patchFromSlug("patch-25-s1-1-notes"), "25.S1.1");
  assert.equal(patchFromSlug("patch-2025-s1-3-notes"), "25.S1.3");
  assert.equal(patchFromSlug("patch-25-04-notes"), "25.4");
  assert.equal(patchFromSlug("tldw-season-1-2027-dev-update"), null);
});

test("ddragonVersionFor / previousVersion", () => {
  assert.equal(ddragonVersionFor("26.23", versions), "16.23.1");
  assert.equal(ddragonVersionFor("25.S1.2", versions), "15.2.1");
  assert.equal(ddragonVersionFor("25.4", versions), "15.4.1");
  assert.equal(previousVersion("16.23.1", versions), "16.22.1");
  assert.equal(previousVersion("16.1.1", versions), "15.24.1");
  assert.equal(cdragonVersion("16.23.1"), "16.23");
});

test("los parches de 2024 (14.x) se ignoran", () => {
  assert.equal(patchFromSlug("patch-14-24-notes"), null);
  assert.equal(patchFromSlug("league-of-legends-patch-14-21-notes"), null);
});
