import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "..");
const catalogPath = resolve(root, "app/src/main/assets/models.json");
const configPath = resolve(here, "catalog.config.json");
const healthPath = resolve(here, "health.json");
const awesomePath = process.env.AWESOME_DATA || resolve(here, "awesome.json");
const dryRun = process.argv.includes("--dry-run");

const read = (p) => JSON.parse(readFileSync(p, "utf8"));
const cfg = read(configPath);
const base = read(catalogPath);
const awesome = existsSync(awesomePath) ? read(awesomePath) : null;
const health = existsSync(healthPath) ? read(healthPath) : { dead: [] };

if (!awesome) {
  console.error("missing awesome data: " + awesomePath);
  process.exit(1);
}

const keyOf = (provider, id) => provider + "/" + id;

function parseContext(s) {
  if (!s) return 0;
  const m = String(s).trim().match(/^([\d.]+)\s*([KM])?$/i);
  if (!m) return 0;
  const n = parseFloat(m[1]);
  const unit = (m[2] || "").toUpperCase();
  if (unit === "M") return Math.round(n * 1_000_000);
  if (unit === "K") return Math.round(n * 1000);
  return Math.round(n);
}

function num(s) {
  return parseFloat(String(s).replace(/,/g, ""));
}

function parseLimits(text) {
  const out = {};
  if (!text) return out;
  const t = String(text).toLowerCase();
  let m;
  if ((m = t.match(/([\d,.]+)\s*rpm/))) out.rpm = Math.round(num(m[1]));
  if (!out.rpm && (m = t.match(/([\d,.]+)\s*(?:rps|requests?\s*\/\s*s(?:econd)?)/))) out.rpm = Math.round(num(m[1]) * 60);
  if ((m = t.match(/([\d,.]+)\s*(?:req|requests)\s*(?:\/|per\s*)\s*(?:hr|hour)/))) out.rph = Math.round(num(m[1]));
  if ((m = t.match(/([\d,.]+)\s*rpd/)) || (m = t.match(/([\d,.]+)\s*(?:req|requests)\s*\/\s*day/))) out.rpd = Math.round(num(m[1]));
  if ((m = t.match(/([\d,.]+)\s*(?:api\s*calls|requests|calls)\s*(?:\/|per\s*)\s*month/))) out.rpmo = Math.round(num(m[1]));
  return out;
}

function guessModes(id, name, modality) {
  const s = (id + " " + name).toLowerCase();
  const modes = [];
  if (/flash|lite|mini|nano|small|lightning|[^0-9.](3|7|8|9|14|20)b\b/.test(s)) modes.push("fast");
  if (/code|coder|codestral|laguna|flint/.test(s)) modes.push("code");
  if (!modes.includes("fast") || /large|ultra|pro|medium|reason/.test(s) || /reason/i.test(modality || "")) modes.push("smart");
  return modes;
}

const providers = new Map(base.providers.map((p) => [p.id, p]));
for (const p of cfg.providers) providers.set(p.id, { ...(providers.get(p.id) || {}), ...p });

const models = new Map(base.models.map((m) => [keyOf(m.provider, m.id), m]));
for (const m of cfg.seedModels) {
  const k = keyOf(m.provider, m.id);
  if (!models.has(k)) models.set(k, { ...m });
}

for (const id of cfg.remove || []) {
  providers.delete(id);
  for (const [k, m] of [...models]) if (m.provider === id) models.delete(k);
}

const deadKeys = new Set(health.dead || []);
for (const k of deadKeys) models.delete(k);

const report = { dropped: [], added: [], missingInAwesome: [] };
const byAwesomeProvider = new Map();
for (const p of awesome.providers) {
  const id = cfg.awesomeNames[p.name];
  if (id) byAwesomeProvider.set(id, p);
}

const keep = new Set(cfg.keep || []);
const pruneSet = new Set(cfg.prune || []);
const trainSet = new Set(cfg.trainsOnData || []);

for (const [pid, ap] of byAwesomeProvider) {
  if (!providers.has(pid)) continue;
  const listed = new Map(ap.models.filter((x) => x.id).map((x) => [x.id, x]));
  const descTrains = /may be used|train/i.test(ap.description || "");
  const provLimits = parseLimits(ap.description || "");

  for (const [k, m] of [...models]) {
    if (m.provider !== pid) continue;
    const am = listed.get(m.id);
    if (!am) {
      if (pruneSet.has(pid) && !keep.has(k)) {
        models.delete(k);
        report.dropped.push(k);
      } else if (!keep.has(k)) {
        report.missingInAwesome.push(k);
      }
      continue;
    }
    const ctx = parseContext(am.context);
    if (ctx > 0) m.contextWindow = ctx;
    const modality = String(am.modality || "");
    if (/image|vision|multimodal/i.test(modality)) m.vision = true;
    if (/reasoning/i.test(modality)) m.reasoning = true;
    if (descTrains || trainSet.has(pid)) m.trainsOnData = true;
    const lim = { ...provLimits, ...parseLimits(am.rateLimit) };
    for (const f of ["rpm", "rph", "rpd", "rpmo"]) if (lim[f]) m[f] = lim[f];
  }

  const autoNow = [...models.values()].filter((x) => x.provider === pid && x.priority >= 200);
  let room = ((cfg.autoAdopt || {})[pid] ?? 0) - autoNow.length;
  let next = autoNow.reduce((a, x) => Math.max(a, x.priority), 199) + 1;
  for (const am of ap.models) {
    if (room <= 0) break;
    if (!am.id) continue;
    const k = keyOf(pid, am.id);
    if (models.has(k) || deadKeys.has(k)) continue;
    const text = (am.id + " " + (am.name || "")).toLowerCase();
    if ((cfg.skipIds || []).some((w) => text.includes(w))) continue;
    if (/retire|deprecat/i.test(am.name || "")) continue;
    const modality = String(am.modality || "");
    const lim = { ...provLimits, ...parseLimits(am.rateLimit) };
    const entry = {
      id: am.id,
      provider: pid,
      displayName: String(am.name || am.id).replace(/\s*\(.*\)\s*$/, ""),
      priority: next++,
      contextWindow: parseContext(am.context) || 8000,
      modes: guessModes(am.id, am.name || "", modality),
      vision: /image|vision|multimodal/i.test(modality),
      reasoning: /reasoning/i.test(modality),
      trainsOnData: descTrains || trainSet.has(pid)
    };
    for (const f of ["rpm", "rph", "rpd", "rpmo"]) if (lim[f]) entry[f] = lim[f];
    models.set(k, entry);
    report.added.push(k);
    room--;
  }
}

for (const m of models.values()) {
  const defaults = (cfg.limits || {})[m.provider] || {};
  for (const f of ["rpm", "rph", "rpd", "rpmo"]) if (!m[f] && defaults[f]) m[f] = defaults[f];
  for (const f of (cfg.dropLimits || {})[m.provider] || []) delete m[f];
  if (trainSet.has(m.provider) && !m.trainsOnData) m.trainsOnData = true;
  const exp = (cfg.expires || {})[keyOf(m.provider, m.id)];
  if (exp) m.expiresAt = exp;
}

const arabicRe = /gemini|qwen|gpt|llama-?3\.[1-9]|llama-?4|deepseek|glm|command|mistral|claude|gemma/i;
function capsOf(m) {
  const c = [];
  const modes = m.modes || [];
  if (modes.includes("code")) c.push("code");
  if (modes.includes("fast")) c.push("fast");
  if (m.reasoning) c.push("reasoning");
  if ((m.contextWindow || 0) >= 200000) c.push("long_context");
  if (arabicRe.test(m.id + " " + (m.displayName || ""))) c.push("arabic");
  return c;
}
for (const m of models.values()) m.caps = capsOf(m);

const out = {
  version: base.version,
  providers: [...providers.values()],
  models: [...models.values()].sort((a, b) => a.priority - b.priority || a.id.localeCompare(b.id))
};

const strip = (o) => JSON.stringify({ providers: o.providers, models: o.models });
if (strip(out) !== strip(base)) out.version = base.version + 1;

const text = JSON.stringify(out, null, 2) + "\n";
if (!dryRun) writeFileSync(catalogPath, text);

console.log(JSON.stringify({ version: out.version, providers: out.providers.length, models: out.models.length, ...report }, null, 2));
