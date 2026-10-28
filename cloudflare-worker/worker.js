const UPSTREAMS = {
  groq: "https://api.groq.com",
  gemini: "https://generativelanguage.googleapis.com",
  openrouter: "https://openrouter.ai",
  cerebras: "https://api.cerebras.ai",
  mistral: "https://api.mistral.ai",
  nvidia: "https://integrate.api.nvidia.com",
  ollama: "https://ollama.com",
  cloudflare: "https://api.cloudflare.com",
  sambanova: "https://api.sambanova.ai",
  github: "https://models.github.ai",
  cohere: "https://api.cohere.ai",
  huggingface: "https://router.huggingface.co",
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
  const fail = (status, msg) =>
    new Response(msg, { status, headers: { "content-type": "text/plain", "Access-Control-Allow-Origin": "*" } });
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
    const r = await fetch(target.toString(), {
      headers: {
        accept: "text/html,application/xhtml+xml,text/plain;q=0.9",
        "user-agent": "Mozilla/5.0 (Linux; Android 14; Mobile) FreeLLMChat/1.0",
      },
      redirect: "follow",
      signal: ctrl.signal,
    });
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
        "Access-Control-Allow-Origin": "*",
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
];

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Headers": "*",
          "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
        },
      });
    }
    const url = new URL(request.url);
    const parts = url.pathname.split("/").filter(Boolean);
    if (env.SECRET) {
      if (parts.shift() !== env.SECRET) return new Response("Not found", { status: 404 });
    }
    if (parts[0] === "_debug") {
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
    const id = parts.shift();
    const base = UPSTREAMS[id];
    if (!base) return new Response("Not found", { status: 404 });

    const target = base + "/" + parts.join("/") + url.search;
    const headers = new Headers(request.headers);
    STRIP.forEach((h) => headers.delete(h));

    headers.set("accept-encoding", "identity");

    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort("upstream_timeout"), 25000);
    let upstream;
    try {
      upstream = await fetch(target, {
        method: request.method,
        headers,
        body: request.method === "GET" || request.method === "HEAD" ? undefined : request.body,
        redirect: "follow",
        signal: ctrl.signal,
      });
    } catch (e) {
      clearTimeout(timer);
      const timedOut = String(e && e.message ? e.message : e).includes("upstream_timeout") || ctrl.signal.aborted;
      return new Response(
        JSON.stringify({ error: { message: timedOut ? "worker upstream timeout" : "worker upstream error: " + String(e), type: "worker_error" } }),
        {
          status: timedOut ? 504 : 502,
          headers: { "content-type": "application/json", "Access-Control-Allow-Origin": "*" },
        }
      );
    }
    clearTimeout(timer);

    const out = new Headers(upstream.headers);
    out.set("Access-Control-Allow-Origin", "*");
    out.set("Cache-Control", "no-store");
    out.set("x-worker-colo", String((request.cf && request.cf.colo) || ""));
    out.set("X-Accel-Buffering", "no");
    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: out,
    });
  },
};
