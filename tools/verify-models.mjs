import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const catalogPath = resolve(here, "../app/src/main/assets/models.json");
const healthPath = resolve(here, "health.json");
const proxy = (process.env.PROXY || "").trim().replace(/\/+$/, "");
const account = process.env.CF_ACCOUNT_ID || "";
const deadAfter = 3;

const catalog = JSON.parse(readFileSync(catalogPath, "utf8"));
const health = existsSync(healthPath) ? JSON.parse(readFileSync(healthPath, "utf8")) : { runs: [], streaks: {}, dead: [] };
const today = new Date().toISOString().slice(0, 10);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const pathOf = (u) => "/" + u.replace(/^[a-z]+:\/\//i, "").split("/").slice(1).join("/").replace(/\/+$/, "");

function endpointFor(p) {
  let direct = p.baseUrl.replace("{ACCOUNT_ID}", account).replace(/\/+$/, "");
  if (proxy) {
    const path = pathOf(direct);
    direct = proxy + "/" + p.id + (path === "/" ? "" : path);
  }
  return direct + "/chat/completions";
}

function classify(status, body) {
  const lower = String(body).toLowerCase();
  const missing = lower.includes("model") && /not found|does not exist|not_found|no endpoints found|decommissioned/.test(lower);
  if (status === 200) return "PASS";
  if (status === 429) return "LIMITED";
  if (status === 401 || status === 403) return "AUTH";
  if (status === 404 || missing) return "DEAD";
  return "UNKNOWN";
}

async function probe(p, m, key) {
  const headers = { "content-type": "application/json", accept: "application/json", ...(p.headers || {}) };
  if (key) headers.authorization = "Bearer " + key;
  if (p.id === "gemini" && key) headers["x-goog-api-key"] = key;
  const started = Date.now();
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), 25000);
  try {
    const r = await fetch(endpointFor(p), {
      method: "POST",
      headers,
      body: JSON.stringify({ model: m.id, messages: [{ role: "user", content: "hi" }], max_tokens: 5 }),
      signal: ctrl.signal
    });
    const text = await r.text();
    return { verdict: classify(r.status, text), status: r.status, ms: Date.now() - started };
  } catch (e) {
    return { verdict: "NETWORK", status: 0 };
  } finally {
    clearTimeout(timer);
  }
}

const results = [];
for (const p of catalog.providers) {
  if (p.available === false) continue;
  const key = process.env["KEY_" + p.id.toUpperCase()] || "";
  if (!key && !p.keyless) continue;
  if (p.needsAccountId && !account) continue;
  const list = catalog.models.filter((m) => m.provider === p.id);
  for (const m of list) {
    const r = await probe(p, m, key);
    const k = p.id + "/" + m.id;
    results.push({ key: k, ...r });
    if (r.verdict === "DEAD") health.streaks[k] = (health.streaks[k] || 0) + 1;
    else if (r.verdict === "PASS" || r.verdict === "LIMITED") delete health.streaks[k];
    await sleep(1200);
  }
}

health.latency = health.latency || {};
for (const r of results) {
  if (r.verdict === "PASS") health.latency[r.key] = Math.round(((health.latency[r.key] || r.ms) * 2 + r.ms) / 3);
  else if (r.verdict === "DEAD") delete health.latency[r.key];
}

health.dead = Object.entries(health.streaks).filter(([, n]) => n >= deadAfter).map(([k]) => k);
health.runs = [...(health.runs || []), today].slice(-14);
writeFileSync(healthPath, JSON.stringify(health, null, 2) + "\n");

const tally = {};
for (const r of results) tally[r.verdict] = (tally[r.verdict] || 0) + 1;
console.log(JSON.stringify({ checked: results.length, tally, dead: health.dead }, null, 2));
for (const r of results) if (r.verdict === "DEAD") console.log("DEAD", r.key, r.status);
