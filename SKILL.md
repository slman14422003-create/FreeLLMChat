---
name: freellmchat
description: Progress log and working notes for the FreeLLMChat Android project (com.freellm.chat). Read before continuing any work on it.
---

# Rules
- Raw code only, no comments in code, no README.md.
- Keep existing tests passing (Router, Adaptive, UsageLimiter, LocalBrain, FailureLog, LanGuard, PromptVars).
- Every user-visible string goes in values/strings.xml (Arabic) and values-en/strings.xml (English), no duplicate names.
- Every new feature has a settings toggle (SettingsStore) with a beginner-friendly default.
- Verify every import; keep dependencies minimal: OkHttp, kotlinx.serialization and mXparser (math). Add another library only if it is small, on Maven Central, and needs no reflection.
- Do not edit build.yml unless the build needs it.

# Status
- Phases 1 to 5: DONE. Not compiled locally (no Android SDK or Maven access in the sandbox); verified by static review only: imports, string names in both locales, removed APIs.

# Phase 1 map
- domain/TokenBudget.kt: TokenEstimator, MaxTokensPlanner (dynamic max_tokens), RequestFitter (fits, clampLast).
- domain/ContextPlanner.kt: split recent/older, digest fallback, summary prompt, update thresholds.
- domain/AnswerCache.kt: CacheKey (SHA-256 of normalized question + short context), CachePolicy (TTL).
- domain/DocChunker.kt: chunk and keyword-ranked selection for long attachments.
- domain/SideTasks.kt: LightPicker and SideTasks (summaries and other side jobs go to the lightest healthy model).
- domain/Concepts.kt: synonym groups used by LocalBrain.tokens for meaning-level matching.
- domain/UsageLimiter.kt: pressure() and budgetPressure().
- domain/Router.kt: order() demotes models that cannot fit the estimate or are above 90% pressure; emits RouteEvent.NearLimit at 80% of a daily or monthly budget.
- data/db: Room v4 (conversations.summary, summarizedUpTo, table answer_cache, MIGRATION_3_4).
- data/SettingsStore.kt: contextSummary, answerCache, cacheTtlHours, dynamicTokens, smartAttachments, budgetAlerts.
- ui/ChatViewModel.kt: cache lookup before any network call, windowed history plus summary line, dynamic max tokens, request clamp, background summary update, compact system prompts.
- ui/OtherScreens.kt: six new settings rows.
- Tests added: TokenBudgetTest, ContextPlannerTest, AnswerCacheTest, DocChunkerTest, LocalBrainConceptTest, BudgetRoutingTest.

# Phase 2 map
- domain/Compare.kt: CompareRequest/Result/Side, Comparer (parallel, pinned), CompareChoice, Merger.
- domain/SideTasks.kt: StrongPicker, picker and temperature params.
- ui/CompareScreen.kt, ModelSheet (PickPurpose.COMPARE), ChatViewModel compare/choose/merge. Key: compare_merge.

# Phase 3 map
- domain/Search.kt: SearchResult, SearchProvider, HtmlText, SearchParsers, SearchCacheKey, Excerpt, UrlPolicy, SourcesPrompt, SearchLimits, SearchQuota, SearchPolicy.
- domain/WebSearch.kt: QueryBuilder, WebSearchPipeline.
- data/SearchClients.kt (Google PSE, Brave, Tavily, Serper), data/PageFetcher.kt (SSRF-safe).
- Room v5 (messages.sources, table search_cache, MIGRATION_4_5). ui/SearchScreens.kt wizard. Key: web_search.

# Phase 4 map
- domain/AnswerPolicy.kt: SourceRank, AnswerPolicy, TemperaturePolicy, Verifier. Keys: answer_temperature, verify_answers (default off).
- Temperature flows ChatTransport.stream (default overload), OpenAiClient.body, RouteRequest.

# Phase 5 map
- domain/Errors.kt resource-based labels; ui/ErrorText.kt; Router emits structured FailReason, no hard-coded text.
- Theme.kt tokens Motion, Spacing, Corners applied across ui.
- ModelBadge and switched_notice in ChatScreen; try-other button on every error.
- OtherScreens: collapsible advanced settings; mode_adaptive is the smart auto default.
- data/ShareCard.kt (CardText, ShareCard): reply as PNG card via CreateDocument then share intent.

# Local search
- data/WebViewSearch.kt (hidden WebView, engines google then ddg then bing, EngineHealth cooldown, wipes cookies/cache/history after every search), data/LocalSearchParse.kt (EXTRACT_JS, unwrap, parse), domain ChainSearch (key first, local as backup). Key: local_search.
- EXTRACT_JS must stay balanced: LocalSearchParseTest.extractScriptIsBalanced guards it.

# Round 3
- OpenAiClient auto-continues up to 2 times when finish_reason is length (internal parseChunk is tested in OpenAiParseTest).
- worker.js has a /fetch?url= route (https only, private hosts refused, 700KB cap); PageFetcher tries it first when a proxy URL is set, then direct.
- Local search engines: google, ddg, bing, yahoo (NAMES parallel to engines()).
- Search settings has a Worker check button hitting <proxy>/_debug.
- Not done on purpose: new search provider, QR provider export, LaTeX/Mermaid rendering, MCP.

# Next
- Run `./gradlew testDebugUnitTest` once and fix any compile error first.
- Then a manual pass on device: compare dialog on phone and tablet, search wizard per provider, share card with RTL text.

## Round 4
- ProviderErrors.kt: maps HTTP/stream errors to ErrorKind; POLICY (OpenRouter free-model data policy) disables the provider until settings change; daily 429 waits until UTC midnight; "No endpoints found" is SERVER (transient).
- OpenAiClient: reasoning models get max_tokens >= 3000.
- ImagePreview.kt: tap a sent image to open a zoomable dialog (AttachThumb onClick).
- CodeFiles.kt + ZipExport.kt: file blocks in a reply (label line "File: path", info string, or first-line comment) are extracted; message menu "save as ZIP" appears only when files are found; CodeFiles.SYSTEM is appended to the system prompt.
- VoiceChat.kt + Speaker.say + SpeechText: hands-free loop listen -> send -> speak -> listen using the on-device recognizer and TTS (works with every provider); entry from the attach menu; needs RECORD_AUDIO.
- Tests: CodeFilesTest, ZipExportTest, SpeechTextTest, ProviderErrorsTest.

## Round 5 (tools + Whisper voice)
- Tools (domain/Tools.kt): prompt-based protocol that works with every provider. The model writes <tool_call>{json}</tool_call> or the body form <tool_call name="project_write" path="x.py">code</tool_call>; ToolRunner wraps router.stream, runs the tools, appends assistant call + <tool_result> user message and streams the next round (max 6 rounds, 8 calls per round). Raw tool markup is never shown (ToolProtocol.visibleLength withholds partial tags and dangling code fences).
- ToolPolicy.select picks tools by keywords (smart) or all enabled (always); no tools means zero overhead. Tool turns are not cached and not learned by the local brain.
- Executors live in data/ToolHost.kt: calculator (MathEngine, mXparser, 5 s timeout), convert (UnitConvert + open.er-api.com currencies), datetime (DateTools, Hijri), web_search (same provider/quota as chat search), fetch_url, wikipedia, run_js (hidden WebView, no network, JsRunner), run_code (optional Piston-compatible URL), check_syntax (json/xml/js), project_* (Workspace in filesDir/workspaces/<conversationId>, ProjectTemplates, exported through the existing ZIP menu when the message note starts with the folder emoji).
- Tool steps are stored as note lines starting with the wrench emoji (no DB migration) and shown by ToolTrace.
- Settings: Settings > Tools (master, smart/always, per-tool, runner URL, workspace) and Settings > Voice.
- Voice: MicCapture (AudioRecord 16 kHz + VoiceVad) -> GroqVoice.transcribe (whisper-large-v3-turbo, /audio/transcriptions, via worker proxy then direct) -> chat -> SpeechSplitter -> GroqVoice.speak (Orpheus English/Arabic Saudi, wav, 190 chars per request) -> PcmPlayer. Falls back to device recognizer/TTS without a Groq key or after a TTS error (terms not accepted shows a message). Speech starts while the reply is still streaming. Optional barge-in (VOICE_COMMUNICATION source + AEC), tap the orb to skip. Composer mic uses Whisper dictation (WhisperDictation).
- Tests: ToolsTest (protocol, policy, templates, ToolRunner), VoiceTest (VAD, WAV, splitter, units, dates).
- Not compiled locally (no Android SDK or Maven): run `./gradlew testDebugUnitTest` and fix compile errors first. Riskiest spots: mXparser API in MathEngine, WebView bridge in JsRunner.

## Round 6 (robustness)
- Router: hedged start. If a non-reasoning model has no first token after hedgeMs (6 s, or 3x its measured latency), the next eligible model (different provider preferred) starts in parallel; the first lane that answers wins, the other is cancelled. Toggle: Settings > fast switching (Router.hedgeMs). Reasoning models get a longer first-token and idle window (RouterConfig.reasoningFactor).
- Health: TIMEOUT and INTERRUPTED count double toward a cooldown; Health.clearTransient() reopens models cooled by generic failures. A network blip (all models cooling or NETWORK errors) triggers one automatic retry after 1 s; network callback in FreeLlmApp clears the OkHttp pool and transient cooldowns when the connection returns.
- OpenAiClient: when a target has two bases (worker proxy + direct) the first gets a 10 s response-header budget (30 s reasoning) before the second is tried; a base that timed out or failed to connect is tried last for 2 minutes (orderedBases).
- OkHttp pool keeps idle connections 20 s (stale sockets after Wi-Fi/mobile switches were a hang source).
- cloudflare-worker: request body is buffered so one automatic retry on network errors and 502/503/504/52x is possible; 25 s header timeout inside a 40 s budget; /_ping endpoint; x-worker-ms header; 30 MB request cap.
- CrashLog: last fatal or handled coroutine exception saved to files/last_crash.txt, shown in Settings with a share button; every viewModelScope launch and appScope use CrashLog.guard. Room falls back to destructive migration only on downgrade.
- Tests: RouterHedgeTest (race, failure, reasoning window, blip retry, health).

## Round 7 (continuity, voice terms, switch speed)
- ChatViewModel.generate: short follow-ups (<= 80 chars in a chat with history) are classified together with the previous user message, skip the local brain (local-first, fallback, past-answer memory) and the ambiguity LLM call; TokenSaver never uses QUICK compaction in a chat with history; CONTINUITY_PROMPT is added to the system prompt once the chat has more than one message so a newly selected or fallback model continues the same conversation.
- ContextPlanner.KEEP_RECENT 8 -> 14, digest 1100 chars / 160 per message.
- Speed: first token 12 s, idle 10 s, hedge 3 s (min 1.8 s), worker TTFB 7 s; one TIMEOUT or INTERRUPTED now cools a model down (weight 3); latency bucket is used for every adaptive request.
- Groq voice: mapHttp checks "terms" before 401/403; VoiceException carries the model; GroqVoice.termsUrl and checkTts; Settings > Voice has a terms section (open EN/AR playground page, check now); the voice screen shows the two link buttons when TERMS fails.
- Test added: GroqTermsTest.
- Not compiled locally: run `./gradlew testDebugUnitTest` first.

## Round 8 (multi-key, camera)
- Keys: up to MAX_KEYS (8) per provider, stored newline-joined in SecretStore key_<id>. ProviderManager.keysOf / removeKeyAt; testAndSave now tests only new keys and merges with saved ones. Targets for extra keys use providerId "<id>#<n>".
- Router: groupKeys keeps all keys of the same provider+model adjacent right after the first one, so a limited key hands over to the next key before any other provider; pinned models match by baseKey (provider/model without #n) and move to the next key on RATE_LIMIT, PAYMENT or AUTH; pickHedge prefers another provider (baseProviderId).
- ChatViewModel: AuthFailed with several keys shows key_rejected_notice and does not disable the provider.
- ProviderScreens: saved keys list (masked) with per-key delete, add-another-key field.
- Camera: ui/CameraCapture.kt (CameraX 1.3.4: core, camera2, lifecycle, view), multi-shot, per-photo select/deselect/delete, flash, flip; ChatScreen attach menu item "Camera" asks CAMERA permission then vm.attachImages(uris). Manifest: CAMERA + optional camera features. Attachments fallback path (API < 28) applies EXIF rotation.
- Tests: RouterKeysTest.
- Not compiled locally: run `./gradlew testDebugUnitTest assembleDebug` first.

# Phase 6 map
- ui/Markdown.kt: FileCard composable (title present → card, else CodeBlock). codeColors() and fileFormat() extracted as top-level. FileCard: 2-dp primary accent bar, filename in monospace, format badge from extension, copy icon, expand/collapse chevron, auto-collapsed when > 30 lines.
- domain/Think.kt: ThinkSplit (split, strip, steps). ThinkParts data class.
- ui/ThinkPanel.kt: ThinkChip (chip + ModalBottomSheet timeline). Steps shown on dotted vertical line.
- data/OpenAiClient.kt: streams <think>…</think> around reasoning_content/reasoning delta fields.
- domain/CodeFiles.kt: ZIP_SYSTEM prompt, zipCommand(), stripCommand(), wantsZip(), fileLabel(). ZIP_SYSTEM injected when user asks for zip.
- ui/CameraCapture.kt: BoxWithConstraints, landscape/portrait split, safeDrawing insets, adaptive shutter/thumb size.
- Tests: ThinkSplitTest (think split, zip command, wantsZip, extract-ignores-thinking).

## Round 9 (audit: security, speed, logic, dead code)
- Router: a hedge lane that loses the race is released again (consumed.remove) so it can still serve as fallback; think markup is stripped before a partial answer is handed to the next model (Router.buildMessages, tail, restart check) and in OpenAiClient auto-continue.
- ChatViewModel: start() cancels and joins the previous job; only a reply that reached RouteEvent.Done is cached or learned by the local brain (stopped partial answers no longer poison them); brain work in finally runs on Dispatchers.Default; search budget 40 s -> 20 s; CodeFiles.SYSTEM only for code or project requests; delete-all in settings goes through deleteAllChats (workspace and attachments cleaned).
- Attachments: sweep() removes files no message references (after delete conversation/message, delete all, VM init); copy limit 60 MB, docx xml limit 12 MB.
- ProviderManager: single Built holder for the targets cache, testAll runs providers in parallel (checkProvider).
- CatalogGuard (CatalogRepository.kt): a remote or cached catalog cannot move a bundled provider to another host and new providers need https (or LAN).
- worker.js: /fetch follows redirects manually and re-checks every hop; only GET and POST accepted. Set the SECRET variable and put it in the proxy URL path to close the open proxy.
- WebViewSearch only navigates to https. SecretStore.importAll(replace) keeps device-local x_ keys. BackupManager.apply tolerates malformed files. BrainStore.save is serialized by a Mutex. OkHttp pool 10 idle connections / 30 s.
- Removed: unused strings (16), unused imports, ProviderManager.move, BrainStore.exportJson, ChatViewModel.clearBrain.
- Tests added: CatalogGuardTest, RouterHedgeTest (losingHedgeLaneStaysAvailableForFallback, continuationDoesNotCarryThinkMarkup).
- Open decision: app/keystore/freellm-release.jks and its password are committed in build.gradle.kts. Rotating them breaks update-over-install, so it was left unchanged.
- Not compiled locally (no Android SDK): run `./gradlew testDebugUnitTest assembleDebug` first.

## Round 10 (security, correctness, stability, polish)
- Signing: build.gradle.kts reads KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD only; without them it signs with the debug keystore. The committed release.jks and its .gitignore exception are removed; build.yml unchanged (its secrets flow already exports those variables).
- Proxy: no BuildConfig.DEFAULT_PROXY fallback; SettingsStore.proxyUrl is "" when unset and an empty value means direct connections. Settings row can set or clear it, with a one-time notice that a proxy sees keys and prompts. Routing logic lives in data/ProxyRouting.kt.
- Tools: ToolProtocol.system declares <tool_result> and <untrusted> content as data; fetched and search text is wrapped in <untrusted>. domain/FetchGuard (ChainState per round chain): rejects long queries and copied conversation text, asks for confirmation on hosts not in the user message or previous search results, blocks re-fetching a host after project_write or run_js. toolsMode default is smart; always mode keeps project tools behind projectIntent.
- Network: cleartext off by default; domain-config permits localhost, 127.0.0.1, 10.0.2.2, .local, .lan, .home.arpa. LanGuard stays the enforcing interceptor, allows 100.64.0.0/10 and a runtime user-host list (LanGuard.registerUrl). Android cannot extend network_security_config at runtime, so plain-http LAN hosts given as raw IPs still need https or a .local/.lan name.
- Vision: needsVision comes from the last user message only; older images are kept only if every candidate supports vision, otherwise replaced by a text placeholder (HistoryImages).
- LocalBrain and cache: BrainEntry.expiresAt (30 days), BrainPolicy (follow-ups and context-dependent questions are neither learned nor served), local-first confidence 0.93 with number and quoted-token check, unified time sensitivity (QuestionClassifier.needsFresh with any 20xx year at or after the current one), CacheKey includes style, instructions, language, no cache or learning when needsFresh or the answer is poor.
- Errors: streamKind uses word-boundary rate-limit matching; 403 is AUTH only when the body says invalid key, unauthorized or permission, otherwise SERVER; 403 never calls markFailed, repeated 403 cools the provider for 10 minutes with a notice.
- UsageLimiter: UTC day by default, optional resetTz on providers in models.json (gemini uses America/Los_Angeles), refund() for NETWORK failures before a response, cancellations and losing hedge lanes.
- Router: hedging skipped for providers with rpm <= 30 or rpd <= 1000 unless the user switches fast switch on explicitly; primary that lost a hedge race is no longer marked failed; first token 25 s, idle and first token scaled from measured latency (floor 25 s when p50 > 5 s); TIMEOUT counts once toward cooldown after a success in the last 10 minutes.
- Projects: CodeFiles.wantsProject needs build verb + project noun + code word or explicit zip/project/files word; the bare compress/pressure word is gone from zip detection.
- AutoBackup: failed export deletes the created document, no autoBackupLast update, one hour back-off, empty files are not counted in KEEP. Auto-resume is skipped for NO_PROVIDER, NO_VISION, PINNED_MISSING, AUTH, POLICY (ResumePolicy).
- Attachments: data/TextImport accepts known text types, strict UTF-8 then windows-1256 then ISO-8859-1, extracts xlsx and pptx text, decodes numeric entities in docx, rejects everything else with attach_unsupported. Sweep keeps files younger than 10 minutes.
- Chat UI: auto-scroll only while near the bottom, jump-to-bottom button, vm.send returns Boolean and the input clears only when accepted, send disabled while importing, delete cancels and joins the running job, search debounced 300 ms with escaped LIKE and LIMIT 100. Draft moved into SecretStore.
- Polish: SecretStore reset flag with dialog and restore shortcut; backup can include conversations and optional attachments with an explicit list in BackupUi, Preview.providers counts key_ entries only; models.json remote update needs a matching models.json.sha256 (catalog.yml writes it); AppRoot is not composed while locked, FLAG_SECURE while appLock is on, explicit disable flow when no biometrics or screen lock exists; UI language defaults to device Arabic or English else English; local search warning and 3 s consent/captcha fail-fast; JsRunner runs once, XML check rejects DOCTYPE and entities; worker.js requires SECRET, unused upstreams removed.
- Tests: RouterRound10Test, ProviderErrorsRound10Test, LocalBrainRound10Test, AnswerCacheRound10Test, CodeFilesRound10Test, ToolGuardRound10Test, AutoBackupRound10Test, LanGuardRound10Test, UsageLimiterRound10Test, AttachmentsTypeFilterTest, Round10Test (data), Round10DomainTest.
- Open decision: keystore rotation. The old release keystore password was public in git history, so it must be treated as compromised; rotating breaks update-over-install for existing users and was not done here. Decide whether to publish under a new key or keep the old one for continuity.
- Not compiled locally (no Android SDK or Gradle): run `./gradlew testDebugUnitTest assembleDebug` first.
