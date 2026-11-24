import * as cheerio from "cheerio";
import type { Element } from "domhandler";
import type { NameIndex, Named } from "./names.ts";
import { normalizeName } from "./names.ts";
import type { EntityKind, RawChange } from "./types.ts";

type Entity = RawChange["entity"];
const NO_ENTITY: Entity = { kind: "none", id: null, name: "" };
const PART_RE = /^(Pasiva|Q|W|E|R)\s*:/i;
/** "[REDISEÑADO] Daga de Statikk" → "Daga de Statikk" for name lookups. */
const stripTag = (s: string): string => s.replace(/^\s*\[[^\]]*\]\s*/, "");
/** "[REDISEÑADO] Daga de Statikk" → "REDISEÑADO" (the official tag, upper case), else null. */
const tagOf = (s: string): string | null => /^\s*\[([^\]]+)\]/.exec(s)?.[1].trim().toUpperCase() ?? null;
const MARKER_RE = /^(NUEVO|ELIMINADO)\s*:?\s*(.*)$/i;

export const clean = (s: string): string => s.replace(/ /g, " ").replace(/\s+/g, " ").trim();

/** Turns the HTML of an official patch notes page into structured changes, in page order. */
export function parsePatchNotes(html: string, names: NameIndex): RawChange[] {
  const $ = cheerio.load(html);
  const out: RawChange[] = [];

  let started = false;
  let sectionTitle = "", sectionId = "", midPatch = false;
  let entity: Entity = NO_ENTITY;
  let entityBlock: Element | null = null;
  let entityFromH3 = false;
  let entityTag: string | null = null;
  let part: string | null = null, partTitle: string | null = null, spellKey: string | null = null;

  const resetPart = () => { part = null; partTitle = null; spellKey = null; };
  /** [heading] is the visible title, kept only for its "[NUEVO]"-style tag. */
  const setEntity = (e: Entity, block: Element | null, fromH3: boolean, heading?: string) => {
    entityTag = heading ? tagOf(heading) : null;
    entity = e; entityBlock = block; entityFromH3 = fromH3; resetPart();
  };
  const setPart = (title: string, el: Element | null) => {
    const letter = PART_RE.exec(title)![1];
    part = /^pasiva$/i.test(letter) ? "P" : letter.toUpperCase();
    partTitle = title;
    spellKey = el ? /\/img\/spell\/([A-Za-z0-9]+)\.png/.exec(imgSrc(el))?.[1] ?? null : null;
  };
  const blockOf = (el: Element): Element | null => ($(el).closest(".patch-change-block").get(0) as Element | undefined) ?? null;
  const imgSrc = (el: Element): string => $(el).find("img").attr("src") ?? "";

  /** Further items of a shared heading ("Grebas Glotonas / Camino Inmortal"): each line is copied to them. */
  const alsoItems = new WeakMap<Entity, Entity[]>();
  const resolve = (nameOrImg: string, kinds: EntityKind[]): Entity | null => {
    nameOrImg = stripTag(nameOrImg);
    const one = resolveOne(nameOrImg, kinds);
    if (one || !kinds.includes("item")) return one;
    // Only when every part is an item, so a name that merely contains "y" is never split.
    const parts = nameOrImg.split(/\s+(?:\/|y)\s+/);
    if (parts.length < 2) return null;
    const found = parts.map(p => names.item(p));
    if (found.some(n => !n)) return null;
    const [first, ...rest] = found.map(n => ({ kind: "item" as const, id: n!.id, name: n!.name }));
    alsoItems.set(first, rest);
    return first;
  };
  const resolveOne = (nameOrImg: string, kinds: EntityKind[]): Entity | null => {
    for (const kind of kinds) {
      let n: Named | null = null;
      if (kind === "champion") n = names.champion(nameOrImg);
      if (kind === "item") n = names.item(nameOrImg);
      if (kind === "rune") n = names.rune(nameOrImg);
      if (n) return { kind, id: n.id, name: n.name };
    }
    return null;
  };
  /** Image first (unambiguous), then the visible name. */
  const resolveHeading = (el: Element, allowName: boolean): Entity | null => {
    const src = imgSrc(el);
    if (/\/img\/(champion|item)\//.test(src)) return resolve(src, ["champion", "item"]);
    return allowName ? resolve(clean($(el).text()), ["champion", "item", "rune"]) : null;
  };

  const emit = (fields: Pick<RawChange, "kind" | "attribute" | "before" | "after" | "text">, ent: Entity = entity) => {
    for (const e of [ent, ...(alsoItems.get(ent) ?? [])])
      out.push({ sectionTitle, sectionId, midPatch, entity: e, part, partTitle, spellKey, tag: entityTag, ...fields });
  };

  $("h2, h3, h4, li, p, strong").each((_, el) => {
    const $el = $(el);
    if ($el.closest("nav, footer, #related-articles, blockquote").length) return;

    if (el.tagName === "h2") {
      const id = $el.attr("id") ?? "";
      if (!id.startsWith("patch-")) return;
      started = true;
      sectionTitle = clean($el.text());
      sectionId = id;
      midPatch = id.toLowerCase().includes("midpatch")
        || /actualizaci[oó]n(es)?\s+(dentro|de mitad)\s+de\s+(la\s+)?versi[oó]n/i.test(sectionTitle);
      setEntity(NO_ENTITY, null, false);
      return;
    }
    if (!started) return;

    // Leaving the block an entity was declared in ends that entity.
    const block = blockOf(el);
    if (entityBlock && block !== entityBlock) setEntity(NO_ENTITY, null, false);

    // <strong>Kled</strong><ul>…</ul> directly in a block (2025 Arena): a heading.
    if (el.tagName === "strong") {
      const parent = el.parent && "tagName" in el.parent ? (el.parent as Element).tagName : "";
      if (parent !== "div") return;
      const t = clean($el.text());
      if (!t) return;
      const e = resolve(t, ["champion", "item"]);
      if (e) setEntity(e, block, false, t);
      else if (PART_RE.test(t)) setPart(t, null);
      else if (entityFromH3) { part = t; partTitle = t; spellKey = null; }
      // An unknown heading must not leave the previous champion/item in charge of the lines below.
      else setEntity({ kind: "none", id: null, name: stripTag(t) }, block, false, t);
      return;
    }

    if (el.tagName === "h3" && $el.hasClass("skin-title")) return;
    if (el.tagName === "h3" && $el.hasClass("change-title")) {
      const fromImg = block ? resolveHeading($(block).find("p").first().get(0) ?? el, false) : null;
      const e = fromImg ?? resolve(clean($el.text()), ["champion", "item", "rune"])
        ?? { kind: "none" as const, id: null, name: stripTag(clean($el.text())) };
      setEntity(e, block, true, clean($el.text()));
      return;
    }

    // h4, or an h3 styled as a subtitle (25.10 uses h3.change-detail-title for items).
    if (el.tagName === "h4" || el.tagName === "h3") {
      const title = clean($el.text());
      const asEntity = resolveHeading(el, !entityFromH3);
      if (asEntity) { setEntity(asEntity, block, false, title); return; }
      if (/^Estad[ií]sticas b[aá]s(e|icas)$/i.test(title)) {
        part = "base"; partTitle = title; spellKey = null;
      } else if (PART_RE.test(title)) {
        setPart(title, el);
      } else {
        // A generic subtitle ("Corrección de errores") ends an entity that came from an h4.
        if (!entityFromH3) setEntity(NO_ENTITY, null, false);
        part = title; partTitle = title; spellKey = null;
      }
      return;
    }

    // li: only its own text; nested lists are visited on their own.
    if (el.tagName === "p" && $el.closest("li").length) return;
    const own = $el.clone();
    own.find("ul, ol").remove();
    const text = clean(own.text());
    if (!text) return;

    // <p><strong>Item name</strong></p> works as a heading for the lines that follow (2025 hotfixes).
    const kids = own.children();
    if (el.tagName === "p" && kids.length === 1 && kids.first().is("strong") && clean(kids.first().text()) === text) {
      const e = resolve(text, ["champion", "item"]);
      if (e) { setEntity(e, block, false, text); return; }
      if (!entityFromH3) setEntity({ kind: "none", id: null, name: stripTag(text) }, block, false, text);
    }

    const marker = MARKER_RE.exec(text);
    if (marker) {
      const named = clean(marker[2].replace(/^:\s*/, "").split(":")[0]);
      const ent = resolve(named, ["champion", "item", "rune"]) ?? entity;
      emit({ kind: marker[1].toUpperCase() === "NUEVO" ? "new" : "removed", attribute: null, before: null, after: null, text }, ent);
      return;
    }

    if (text.includes("⇒")) {
      const first = own.children().first();
      const startsWithStrong = first.is("strong") && text.startsWith(clean(first.text()));
      let attribute: string | null = null;
      let rest = text;
      if (startsWithStrong) {
        const name = clean(first.text()).replace(/:$/, "");
        const after = clean(text.slice(clean(first.text()).length)).replace(/^:\s*/, "");
        if (after !== text) { attribute = name; rest = after; }
      }
      // "Swain: Daño infligido: 90% ⇒ 95%" (mode sections): the line names its own champion.
      let lineEntity = entity;
      if (entity.kind === "none") {
        const lead = attribute ?? /^([^:⇒]+):/.exec(rest)?.[1] ?? "";
        const named = lead ? resolve(clean(lead), ["champion", "item"]) : null;
        if (named) {
          lineEntity = named;
          if (attribute === null) rest = clean(rest.slice(lead.length + 1));
          const sub = /^([^:⇒]+):s*(.*)$/.exec(rest);
          attribute = sub ? clean(sub[1]) : null;
          if (sub) rest = sub[2];
        }
      }
      const i = rest.indexOf("⇒");
      emit({ kind: "stat", attribute, before: clean(rest.slice(0, i)), after: clean(rest.slice(i + 1)), text }, lineEntity);
      return;
    }

    emit({ kind: "text", attribute: null, before: null, after: null, text });
  });

  return out;
}
