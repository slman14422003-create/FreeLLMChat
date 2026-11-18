const UPSTREAMS = {
  groq: "https://api.groq.com",
  gemini: "https://generativelanguage.googleapis.com",
  openrouter: "https://openrouter.ai",
  ollama: "https://ollama.com",
  cloudflare: "https://api.cloudflare.com",
  github: "https://models.github.ai",
  cohere: "https://api.cohere.ai",
  zai: "https://api.z.ai",
  kilo: "https://api.kilo.ai",
  ovh: "https://oai.endpoints.kepler.ai.cloud.ovh.net",
  llm7: "https://api.llm7.io",
  tavily: "https://api.tavily.com",
  brave: "https://api.search.brave.com",
  serper: "https://google.serper.dev",
  google: "https://www.googleapis.com",
};

const MAX_PAGE_BYTES = 700000;
const MAX_REQUEST_BYTES = 30 * 1024 * 1024;
const HEADER_TIMEOUT_MS = 12000;
const TOTAL_BUDGET_MS = 25000;
const MAX_HEADER_TIMEOUT_MS = 60000;
const RATE_LIMIT_PER_MINUTE = 60;
const RATE_WINDOW_MS = 60000;
const RATE_MAX_ENTRIES = 5000;
const RETRY_STATUS = new Set([502, 503, 504, 520, 521, 522, 523, 524]);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const hits = new Map();

function rateLimited(ip, now) {
  if (hits.size > RATE_MAX_ENTRIES) {
    for (const [k, v] of hits) if (now - v.start >= RATE_WINDOW_MS) hits.delete(k);
    if (hits.size > RATE_MAX_ENTRIES) hits.clear();
  }
  const cur = hits.get(ip);
  if (!cur || now - cur.start >= RATE_WINDOW_MS) {
    hits.set(ip, { start: now, count: 1 });
    return false;
  }
  cur.count++;
  return cur.count > RATE_LIMIT_PER_MINUTE;
}

function allowedOrigin(request, env) {
  const origin = request.headers.get("origin") || "";
  const list = String(env.ALLOWED_ORIGINS || "").split(",").map((x) => x.trim()).filter(Boolean);
  return origin && list.includes(origin) ? origin : "";
}

function withCors(headers, origin) {
  if (origin) {
    headers.set("Access-Control-Allow-Origin", origin);
    headers.set("Vary", "Origin");
  } else {
    headers.delete("Access-Control-Allow-Origin");
  }
  return headers;
}

function errorResponse(status, message, extra) {
  return new Response(JSON.stringify({ error: { message, type: "worker_error" } }), {
    status,
    headers: Object.assign({ "content-type": "application/json" }, extra || {}),
  });
}

function privateHost(h) {
  h = h.toLowerCase();
  if (h === "localhost" || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".lan") || !h.includes(".")) return true;
  if (h.includes(":")) return true;
  const m = h.match(/^(\d+)\.(\d+)\.(\d+)\.(\d+)$/);
  if (m) {
    const a = +m[1], b = +m[2];
    return a === 10 || a === 127 || a === 0 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) || a >= 224;
  }
  return false;
}

async function fetchPage(raw) {
  const fail = (status, msg) => new Response(msg, { status, headers: { "content-type": "text/plain" } });
  let target;
  try {
    target = new URL(raw || "");
  } catch (e) {
    return fail(400, "bad url");
  }
  if (target.protocol !== "https:" || privateHost(target.hostname)) return fail(400, "blocked url");
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort("upstream_timeout"), 9000);
  try {
    let r = null;
    for (let hop = 0; hop < 4; hop++) {
      r = await fetch(target.toString(), {
        headers: {
          accept: "text/html,application/xhtml+xml,text/plain;q=0.9",
          "user-agent": "Mozilla/5.0 (Linux; Android 14; Mobile) FreeLLMChat/1.0",
        },
        redirect: "manual",
        signal: ctrl.signal,
      });
      const loc = r.status >= 300 && r.status < 400 ? r.headers.get("location") : null;
      if (!loc) break;
      let next;
      try {
        next = new URL(loc, target);
      } catch (e) {
        return fail(502, "bad redirect");
      }
      if (next.protocol !== "https:" || privateHost(next.hostname)) return fail(400, "blocked url");
      try {
        await r.body?.cancel();
      } catch (e) {}
      target = next;
      r = null;
    }
    if (!r) return fail(502, "too many redirects");
    const type = (r.headers.get("content-type") || "").toLowerCase();
    if (type && !type.includes("html") && !type.includes("xml") && !type.startsWith("text/plain")) {
      return fail(415, "unsupported type");
    }
    const reader = r.body.getReader();
    const chunks = [];
    let size = 0;
    while (size < MAX_PAGE_BYTES) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(value);
      size += value.length;
    }
    reader.cancel().catch(() => {});
    const buf = new Uint8Array(Math.min(size, MAX_PAGE_BYTES));
    let off = 0;
    for (const c of chunks) {
      const room = buf.length - off;
      if (room <= 0) break;
      buf.set(c.length > room ? c.subarray(0, room) : c, off);
      off += Math.min(c.length, room);
    }
    return new Response(buf, {
      status: r.status,
      headers: {
        "content-type": type || "text/html; charset=utf-8",
        "Cache-Control": "no-store",
      },
    });
  } catch (e) {
    return fail(502, "fetch failed");
  } finally {
    clearTimeout(timer);
  }
}

const STRIP = [
  "x-forwarded-host",
  "cf-connecting-ipv6",
  "cf-ew-via",
  "cf-pseudo-ipv4",
  "true-client-ip",
  "forwarded",
  "via",
  "origin",
  "referer",
  "host",
  "cf-connecting-ip",
  "cf-ipcountry",
  "cf-ray",
  "cf-visitor",
  "cf-worker",
  "x-forwarded-for",
  "x-forwarded-proto",
  "x-real-ip",
  "x-freellm-ttfb",
];

export default {
  async fetch(request, env) {
    const origin = allowedOrigin(request, env);
    if (request.method === "OPTIONS") {
      const h = withCors(new Headers(), origin);
      if (origin) {
        h.set("Access-Control-Allow-Headers", "authorization,content-type");
        h.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
      }
      return new Response(null, { status: 204, headers: h });
    }
    if (request.method !== "GET" && request.method !== "POST") return errorResponse(405, "method not allowed");
    const url = new URL(request.url);
    const parts = url.pathname.split("/").filter(Boolean);
    if (!env.SECRET || parts.shift() !== env.SECRET) return new Response("Not found", { status: 404 });
    const ip = request.headers.get("cf-connecting-ip") || "unknown";
    if (rateLimited(ip, Date.now())) {
      return errorResponse(429, "rate limit exceeded", { "retry-after": "60" });
    }
    if (parts[0] === "_debug" && env.DEBUG === "1") {
      const probe = async (u) => {
        try {
          const r = await fetch(u, { headers: { "user-agent": "freellm-debug" } });
          return { status: r.status, body: (await r.text()).slice(0, 220) };
        } catch (e) {
          return { error: String(e) };
        }
      };
      const info = {
        colo: request.cf && request.cf.colo,
        country: request.cf && request.cf.country,
        placement: request.headers.get("cf-placement"),
        groq: await probe("https://api.groq.com/openai/v1/models"),
        gemini: await probe("https://generativelanguage.googleapis.com/v1beta/openai/models"),
      };
      return new Response(JSON.stringify(info, null, 2), {
        headers: { "content-type": "application/json; charset=utf-8" },
      });
    }
    if (parts[0] === "fetch") {
      return await fetchPage(url.searchParams.get("url"));
    }
    if (parts[0] === "_ping") {
      return new Response(JSON.stringify({ ok: true, colo: (request.cf && request.cf.colo) || "", t: Date.now() }), {
        headers: { "content-type": "application/json", "Cache-Control": "no-store" },
      });
    }
    const id = parts.shift();
    const base = UPSTREAMS[id];
    if (!base) return new Response("Not found", { status: 404 });

    const target = base + "/" + parts.join("/") + url.search;
    const headers = new Headers(request.headers);
    STRIP.forEach((h) => headers.delete(h));
    headers.set("accept-encoding", "identity");

    const hasBody = request.method !== "GET" && request.method !== "HEAD";
    let body;
    if (hasBody) {
      const len = Number(request.headers.get("content-length") || 0);
      if (len > MAX_REQUEST_BYTES) return errorResponse(413, "request too large");
      try {
        body = await request.arrayBuffer();
      } catch (e) {
        return errorResponse(400, "bad request body");
      }
    }

    const started = Date.now();
    const hint = Number(request.headers.get("x-freellm-ttfb") || 0);
    const headerMs = hint > 0 ? Math.min(Math.max(HEADER_TIMEOUT_MS, hint + 2000), MAX_HEADER_TIMEOUT_MS) : HEADER_TIMEOUT_MS;
    const budgetMs = Math.max(TOTAL_BUDGET_MS, headerMs + 8000);
    let upstream = null;
    let lastError = null;
    let timedOut = false;
    for (let attempt = 0; attempt < 2; attempt++) {
      const left = budgetMs - (Date.now() - started);
      if (left < 4000) break;
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort("upstream_timeout"), Math.min(headerMs, left));
      try {
        const r = await fetch(target, {
          method: request.method,
          headers,
          body,
          redirect: "follow",
          signal: ctrl.signal,
        });
        clearTimeout(timer);
        if (attempt === 0 && RETRY_STATUS.has(r.status)) {
          lastError = "upstream " + r.status;
          try {
            await r.body?.cancel();
          } catch (e) {}
          await sleep(250);
          continue;
        }
        upstream = r;
        break;
      } catch (e) {
        clearTimeout(timer);
        const msg = String(e && e.message ? e.message : e);
        timedOut = msg.includes("upstream_timeout") || ctrl.signal.aborted;
        lastError = msg;
        if (timedOut) break;
        await sleep(250);
      }
    }
    if (!upstream) {
      return new Response(
        JSON.stringify({ error: { message: timedOut ? "worker upstream timeout" : "worker upstream error: " + lastError, type: "worker_error" } }),
        {
          status: timedOut ? 504 : 502,
          headers: withCors(new Headers({ "content-type": "application/json", "x-worker-ms": String(Date.now() - started) }), origin),
        }
      );
    }

    const out = new Headers(upstream.headers);
    withCors(out, origin);
    out.set("Cache-Control", "no-store");
    out.set("x-worker-colo", String((request.cf && request.cf.colo) || ""));
    out.set("x-worker-ms", String(Date.now() - started));
    out.set("X-Accel-Buffering", "no");
    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: out,
    });
  },
};
