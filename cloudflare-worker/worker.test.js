import test from "node:test";
import assert from "node:assert/strict";
import worker, { authorize, safeEqual } from "./worker.js";

const env = { SECRET: "s3cret" };
const req = (path, headers, method) => new Request("https://w.test" + path, { method: method || "GET", headers: headers || {} });
const split = (p) => p.split("/").filter(Boolean);

test("safeEqual", () => {
  assert.equal(safeEqual("a", "a"), true);
  assert.equal(safeEqual("a", "b"), false);
  assert.equal(safeEqual("a", "ab"), false);
  assert.equal(safeEqual("", undefined), true);
});

test("authorize: header", () => {
  const r = authorize(req("/groq/v1", { "x-app-secret": "s3cret" }), env, split("/groq/v1"));
  assert.equal(r.ok, true);
  assert.deepEqual(r.parts, ["groq", "v1"]);
});

test("authorize: wrong header is rejected even with valid path", () => {
  const r = authorize(req("/s3cret/groq", { "x-app-secret": "bad" }), env, split("/s3cret/groq"));
  assert.equal(r.ok, false);
});

test("authorize: legacy path", () => {
  const r = authorize(req("/s3cret/groq/v1"), env, split("/s3cret/groq/v1"));
  assert.equal(r.ok, true);
  assert.deepEqual(r.parts, ["groq", "v1"]);
});

test("authorize: header plus legacy path segment", () => {
  const r = authorize(req("/s3cret/groq", { "x-app-secret": "s3cret" }), env, split("/s3cret/groq"));
  assert.equal(r.ok, true);
  assert.deepEqual(r.parts, ["groq"]);
});

test("authorize: missing secret and missing env", () => {
  assert.equal(authorize(req("/groq"), env, ["groq"]).ok, false);
  assert.equal(authorize(req("/s3cret/groq"), {}, ["s3cret", "groq"]).ok, false);
});

test("fetch: unauthenticated is 404", async () => {
  const res = await worker.fetch(req("/groq/x"), env);
  assert.equal(res.status, 404);
});

test("fetch: ping with header", async () => {
  const res = await worker.fetch(req("/_ping", { "x-app-secret": "s3cret" }), env);
  assert.equal(res.status, 200);
});

test("fetch: debug requires DEBUG=1 and secret", async () => {
  assert.equal((await worker.fetch(req("/_debug", { "x-app-secret": "s3cret" }), env)).status, 404);
  assert.equal((await worker.fetch(req("/_debug"), { SECRET: "s3cret", DEBUG: "1" })).status, 404);
});

test("fetch: oversized body rejected using env limit", async () => {
  const res = await worker.fetch(
    new Request("https://w.test/groq/x", { method: "POST", headers: { "x-app-secret": "s3cret", "content-length": "2000" }, body: "x" }),
    { SECRET: "s3cret", MAX_REQUEST_BYTES: "1000" }
  );
  assert.equal(res.status, 413);
});

test("fetch: rate limit from env", async () => {
  const e = { SECRET: "s3cret", RATE_LIMIT_PER_MINUTE: "2" };
  const h = { "x-app-secret": "s3cret", "cf-connecting-ip": "9.9.9.9" };
  const codes = [];
  for (let i = 0; i < 4; i++) codes.push((await worker.fetch(req("/_ping", h), e)).status);
  assert.deepEqual(codes, [200, 200, 503, 503]);
});
