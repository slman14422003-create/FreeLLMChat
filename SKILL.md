---
name: freellmchat
description: Progress log and working notes for the FreeLLMChat Android project (com.freellm.chat). Read before continuing any work on it.
---

# Rules
- Raw code only, no comments in code, no README.md.
- Keep existing tests passing (Router, Adaptive, UsageLimiter, LocalBrain, FailureLog, LanGuard, PromptVars).
- Every user-visible string goes in values/strings.xml (Arabic) and values-en/strings.xml (English), no duplicate names.
- Every new feature has a settings toggle (SettingsStore) with a beginner-friendly default.
- Verify every import; no heavy libraries (OkHttp and kotlinx.serialization only).
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
