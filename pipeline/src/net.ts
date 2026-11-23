import type { FetchText } from "./types.ts";

const USER_AGENT = "parches-lol-personal/1.0";
const sleep = (ms: number) => new Promise(r => setTimeout(r, ms));

/** fetch with a minimum spacing per host and retries with growing backoff. */
export function createFetchText(opts: { minIntervalMs?: number; retries?: number } = {}): FetchText {
  const minIntervalMs = opts.minIntervalMs ?? 1000;
  const retries = opts.retries ?? 3;
  const lastByHost = new Map<string, number>();
  const queueByHost = new Map<string, Promise<void>>();

  /** One queue per host: each request starts at least minIntervalMs after the previous one actually started. */
  const takeTurn = (host: string): Promise<void> => {
    const turn = (queueByHost.get(host) ?? Promise.resolve()).then(async () => {
      const wait = (lastByHost.get(host) ?? -Infinity) + minIntervalMs - Date.now();
      if (wait > 0) await sleep(wait);
      lastByHost.set(host, Date.now());
    });
    queueByHost.set(host, turn);
    return turn;
  };

  return async (url: string) => {
    const host = new URL(url).host;
    for (let attempt = 0; ; attempt++) {
      await takeTurn(host);
      try {
        const res = await fetch(url, { headers: { "User-Agent": USER_AGENT } });
        if (res.status !== 200) throw new Error(`HTTP ${res.status} ${url}`);
        return await res.text();
      } catch (err) {
        if (attempt >= retries || /HTTP 404/.test(String(err))) throw err;
        await sleep(2000 * (attempt + 1));
      }
    }
  };
}
