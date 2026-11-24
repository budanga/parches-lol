import { test } from "node:test";
import assert from "node:assert/strict";
import { parsePatchNotes } from "../src/parse.ts";
import { buildNameIndex } from "../src/names.ts";
import { fixture, fixtureJson } from "./helpers.ts";

const names = buildNameIndex(
  fixtureJson("ddragon/16.23.1/champion.json"),
  fixtureJson("ddragon/16.23.1/item.json"),
  fixtureJson("ddragon/16.23.1/runesReforged.json"));
const c2623 = parsePatchNotes(fixture("patch-26-23.html"), names);
const c25 = parsePatchNotes(fixture("patch-25-s1-1.html"), names);

test("Azir estadística base", () => {
  const c = c2623.find(c => c.entity.id === "Azir" && c.part === "base")!;
  assert.deepEqual([c.kind, c.attribute, c.before, c.after, c.sectionId],
    ["stat", "Alcance de ataque", "525", "550", "patch-champions"]);
});

test("Azir W con spellKey", () => {
  const c = c2623.find(c => c.entity.id === "Azir" && c.part === "W")!;
  assert.deepEqual([c.partTitle, c.spellKey, c.attribute, c.before, c.after],
    ["W: ¡Levántense!", "AzirW", "Tasa de Daño de Ataque / al impacto", "50%", "67%"]);
});

test("Ezreal W y R", () => {
  const w = c2623.find(c => c.entity.id === "Ezreal" && c.part === "W")!;
  assert.equal(w.before, "80 / 135 / 190 / 245 / 300 (+100% de Daño de Ataque adicional) (+90% de Poder de Habilidad)");
  assert.equal(w.after, "80 / 135 / 190 / 245 / 300 (+115% de Daño de Ataque adicional) (+100% de Poder de Habilidad)");
  assert.ok(c2623.some(c => c.entity.id === "Ezreal" && c.part === "R" && c.attribute === "Daño a campeones"));
});

test("Objeto con h3", () => {
  const c = c2623.find(c => c.entity.id === "6699")!;
  assert.deepEqual([c.entity.kind, c.partTitle, c.before],
    ["item", "Firmamento único", "[9% || 7%] Vida actual del objetivo"]);
});

test("NUEVO: Talon en Classic", () => {
  const c = c2623.find(c => c.kind === "new" && c.entity.id === "Talon")!;
  assert.equal(c.sectionId, "patch-classic");
});

test("texto sin estructura se conserva", () => {
  assert.ok(c2623.some(c => c.kind === "text" && c.text.startsWith("El chat de voz de equipo está habilitado")));
});

test("ningún cambio de la sección de campeones queda sin entidad", () => {
  assert.deepEqual(c2623.filter(c => c.sectionId === "patch-champions" && !c.entity.id), []);
});

test("2025: hotfix dentro de la versión con h4 de campeón", () => {
  const c = c25.find(c => c.entity.id === "JarvanIV")!;
  assert.equal(c.midPatch, true);
});

test("2025: Kalista estadísticas básicas → base", () => {
  assert.ok(c25.some(c => c.entity.id === "Kalista" && c.part === "base" && c.kind === "stat"));
});

test("2025: objetos sin h3 se resuelven", () => {
  assert.ok(c25.some(c => c.sectionId === "patch-items" && c.entity.kind === "item" && c.entity.id));
});

test("2025: objeto como título en negrita dentro del hotfix", () => {
  const c = c25.find(c => c.midPatch && c.text.startsWith("Penetración de magia: 19 ⇒ 18"))!;
  assert.deepEqual([c.entity.kind, c.entity.name], ["item", "Zapatos de Lanzahechizos"]);
});

test("2025: línea de ARAM con el campeón al principio", () => {
  const c = c25.find(c => c.sectionId === "patch-aram" && c.text.startsWith("Swain:"))!;
  assert.deepEqual([c.entity.id, c.attribute, c.before, c.after], ["Swain", "Daño infligido", "90%", "95%"]);
});

test("26.9: títulos con etiqueta [NUEVO]/[REDISEÑADO] se resuelven", () => {
  const c269 = parsePatchNotes(fixture("patch-26-9.html"), names);
  const v = c269.find(c => c.text === "Letalidad: 18 ⇒ 10")!;
  assert.deepEqual([v.entity.kind, v.entity.id], ["item", "6699"]);
  const s = c269.find(c => c.text === "Resistencia Mágica: 10")!;
  assert.deepEqual([s.entity.kind, s.entity.name], ["item", "Casco de Doran"]);
});

test("25.10: objetos con h3.change-detail-title se resuelven", () => {
  const c = parsePatchNotes(fixture("patch-25-10.html"), names).find(c => c.text === "Poder de Habilidad: 60 ⇒ 65")!;
  assert.equal(c.entity.kind, "item");
  assert.ok(c.entity.id);
});

test("25.18: campeón como <strong> suelto antes de la lista (Arena)", () => {
  const c = parsePatchNotes(fixture("patch-25-18.html"), names).find(c => c.text.startsWith("Tiempo de aterrizaje: 2 seg"))!;
  assert.equal(c.entity.id, "Kled");
});


test("I2: 'Actualizaciones de mitad de versión' (id patch-midpatch-updates) es hotfix", () => {
  const html = `<h2 id="patch-midpatch-updates">Actualizaciones de mitad de versión</h2>
    <div class="patch-change-block"><h3 class="change-title">Azir</h3><ul><li><strong>Alcance de ataque</strong>: 525 ⇒ 550</li></ul></div>`;
  assert.equal(parsePatchNotes(html, names)[0].midPatch, true);
});

test("I3: título no reconocido no hereda el objeto anterior", () => {
  const c = c25.find(c => c.text === "Penetración de magia adicional: 4 ⇒ 2")!;
  assert.notEqual(c.entity.id, "3170");
  assert.equal(c.entity.name, "Botas del Hechicero Triunfante");
});

test("I3: <strong>Q: …</strong> suelto marca la habilidad", () => {
  const c = parsePatchNotes(fixture("patch-25-18.html"), names).find(c => c.text.startsWith("Enfriamiento: 10 / 9 / 8 / 7 / 6 seg"))!;
  assert.deepEqual([c.part, c.partTitle], ["Q", "Q: Onda Sónica"]);
});

test("etiquetas de los títulos ([NUEVO], [REDISEÑADO]) se conservan en cada línea", () => {
  const c269 = parsePatchNotes(fixture("patch-26-9.html"), names);
  assert.equal(c269.find(c => c.text === "Letalidad: 18 ⇒ 10")!.tag, "REDISEÑADO");
  assert.equal(c269.find(c => c.text === "Resistencia Mágica: 10")!.tag, "NUEVO");
  assert.equal(c2623.find(c => c.entity.id === "Azir" && c.part === "base")!.tag, null);
});
