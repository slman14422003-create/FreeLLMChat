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
};

const STRIP = [
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
    const id = parts.shift();
    const base = UPSTREAMS[id];
    if (!base) return new Response("Not found", { status: 404 });

    const target = base + "/" + parts.join("/") + url.search;
    const headers = new Headers(request.headers);
    STRIP.forEach((h) => headers.delete(h));

    const upstream = await fetch(target, {
      method: request.method,
      headers,
      body: request.method === "GET" || request.method === "HEAD" ? undefined : request.body,
      redirect: "follow",
    });

    const out = new Headers(upstream.headers);
    out.set("Access-Control-Allow-Origin", "*");
    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: out,
    });
  },
};
