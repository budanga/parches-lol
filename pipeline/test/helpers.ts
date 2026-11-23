import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join, dirname } from "node:path";

const dir = join(dirname(fileURLToPath(import.meta.url)), "fixtures");
export const fixturePath = (name: string): string => join(dir, name);
export const fixture = (name: string): string => readFileSync(fixturePath(name), "utf8");
export const fixtureJson = (name: string): any => JSON.parse(fixture(name));
