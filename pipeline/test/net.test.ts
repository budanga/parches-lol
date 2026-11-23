import { test } from "node:test";
import assert from "node:assert/strict";
import { createFetchText } from "../src/net.ts";

test("404 no se reintenta y el error dice HTTP 404", async () => {
  let calls = 0;
  const original = globalThis.fetch;
  globalThis.fetch = (async () => { calls++; return new Response("no", { status: 404 }); }) as typeof fetch;
  try {
    await assert.rejects(createFetchText({ minIntervalMs: 0 })("https://example.test/x"), /HTTP 404/);
    assert.equal(calls, 1);
  } finally { globalThis.fetch = original; }
});

test("I6: pedidos en paralelo al mismo host respetan la pausa mínima", async () => {
  const times: number[] = [];
  const original = globalThis.fetch;
  globalThis.fetch = (async () => { times.push(Date.now()); return new Response("ok"); }) as typeof fetch;
  try {
    const f = createFetchText({ minIntervalMs: 60 });
    await Promise.all([1, 2, 3, 4].map(i => f(`https://example.test/${i}`)));
    times.sort((a, b) => a - b);
    for (let i = 1; i < times.length; i++) assert.ok(times[i] - times[i - 1] >= 55, `gap ${times[i] - times[i - 1]}ms`);
  } finally { globalThis.fetch = original; }
});
