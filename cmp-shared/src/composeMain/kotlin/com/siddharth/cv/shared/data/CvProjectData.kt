package com.siddharth.cv.shared.data

/**
 * Six projects, transcribed verbatim from cv-siddharth/src/data/profile.ts:369-1183.
 *
 * Deliberate inconsistency preserved: Mileway's `status` says 46 modules while its
 * detail metric says 36 — 36 local + 10 composed. Both are correct; do not reconcile.
 */

val projects = listOf(
    Project(
        slug = "kursi",
        name = "Kursi",
        tagline = """A Hinglish social-deduction bluffing game of power, satire & second chances — Kursi ke liye kuch bhi karega.""",
        description = """Deterministic Kotlin Multiplatform social-deduction game with ISMCTS bot AI, shipped across Android, iOS, Desktop, and Web.""",
        stack = listOf("Kotlin Multiplatform", "Compose Multiplatform", "Android", "iOS", "Desktop", "Web (Wasm)"),
        highlights = listOf(
            """Pure (GameState, Intent) → GameState reducer drives the AI, UI, and a future server.""",
            """ISMCTS AI with 10 bot personas plus a DARBAR social layer for bluffing and alliances.""",
        ),
        links = listOf(NamedLink("GitHub", "https://github.com/darkpandawarrior/Kursi")),
        status = "13 modules · 4 platforms · 10 bot personas",
        badges = listOf("Kotlin Multiplatform", "Game engine", "ISMCTS AI"),
        theme = ProjectTheme(
            accent = "#E8C874",
            accentDim = "#C99A3B",
            ink = "#1E1008",
            surface = "#291a12",
            card = "#33241c",
            line = "#4a3724",
        ),
        targets = listOf(
            ProjectTarget("Android", 6, """Rendered at phone dimensions from the shared Compose UI."""),
            ProjectTarget("iOS", 4, """Compose Multiplatform renders pixel-identical UI on iOS — the same composables at phone size."""),
            ProjectTarget("Desktop", 2, """Same engine, windowed — Compose Desktop (JVM) build."""),
            ProjectTarget("Web", 1, """Live — the real Compose/Wasm build, playable right here. One codebase, running in your browser."""),
        ),
        detail = ProjectDetailData(
            overview = """Kursi is a Hinglish social-deduction bluffing game set in a satirical India corporate-political underworld where six archetypes scheme for an empty chair — the Gaddi — and everyone is lying about what they hold. The Neta makes promises he'll forget tomorrow, the Bhai owns silence, the Babu approves nothing, the Jugaadu knows a shortcut, the Vakil has read every exception. Satire targets the archetype, never the person. Under the deadpan Hinglish voice ("सब मिले हुए हैं") sits a serious engineering exercise: one deterministic Kotlin engine that runs identically on Android, iOS, desktop and the web, and powers the AI, the UI and a server-authoritative backend from the same code.""",
            sections = listOf(
                ProjectDetailSection(
                    heading = "Deterministic engine",
                    body = """The whole game is a pure function: (GameState, Intent) → GameState, with the RNG seed living inside the state. The same module drives single-player, the bots and a future server — and any match can be replayed byte-for-byte from its seed and intent log.""",
                ),
                ProjectDetailSection(
                    heading = "Same game, three depths (launch overhaul)",
                    body = """The board reveals itself in three density layers so a first-timer isn't handed an expert's dashboard. FOCUS shows only whose turn it is, one plain-language line of what just happened, your hand and your legal moves; GUIDED adds gentle coaching; ANALYST is the full instrument panel (suspicion pips, odds, teleprinter log). Players graduate FOCUS → GUIDED → ANALYST by playing. Paired with a tap-to-continue beat gate so the round never resolves faster than you can read it, and a tutorial-first onboarding funnel that teaches one mechanic at a time.""",
                ),
                ProjectDetailSection(
                    heading = "AI Munshi narrator",
                    body = """A diegetic court-scribe turns raw engine events into one calm in-character line — grounded strictly on the redacted PlayerView so it narrates the beat without ever leaking a hidden card or inventing the board. It renders the deterministic templated line instantly and upgrades in place if an LLM is available (on-device Gemini Nano / Apple FoundationModels / BYOK cloud), never enters the intent log, and leaves byte-for-byte replay untouched.""",
                ),
                ProjectDetailSection(
                    heading = "ISMCTS expert AI + DARBAR social layer",
                    body = """Bots use Information Set Monte Carlo Tree Search (1.5k–16k iterations depending on difficulty tier) with an optional cloud-LLM upgrade (Anthropic / OpenAI / Gemini). Ten personas each have a personality profile driving targeting and bluff frequency. The DARBAR layer lets bots form alliances, hold grudges and trade Hinglish table-talk across four story arcs — social manipulation that never breaks engine determinism.""",
                ),
                ProjectDetailSection(
                    heading = "Secrecy boundary",
                    body = """A hidden-information game needs strict secrecy: redact(state, viewer) → PlayerView guarantees a client only ever sees what its player should. Two independent narrative RNG streams keep flavour separate from game logic.""",
                ),
                ProjectDetailSection(
                    heading = "“Sarkari Noir” visual system",
                    body = """A bespoke lamplit visual language — teak/brass/cream palette, Rozha One display type, Canvas-drawn intaglio role glyphs and stamped-instrument motifs — pushed to an AAA bar in the launch overhaul: every screen dissolved from bordered boxes into one continuous lit table (depth via shadow, never outline), a shared component vocabulary, and an AGSL/Skia runtime-shader material layer (film grain + warm bloom on the felt) with a graceful no-shader fallback. All behind a full Fastlane + CI pipeline with headless screenshot rendering.""",
                ),
                ProjectDetailSection(
                    heading = "Game modes",
                    body = """New Game (1v1–1v9, Easy→Grandmaster), a KISSA story campaign, GAUNTLET (Tarakki ki Seedhi — a 5-rung ladder ending in 6-player Grandmaster), TAMASHA (spectate ten AI personas scheme and betray), Team Khel (faction play with un-targetable allies), a Tutorial you can't leave until you catch a bluff, local pass-and-play with a handoff screen guard, and online + LAN multiplayer.""",
                ),
                ProjectDetailSection(
                    heading = "DARBAR — four live story arcs",
                    body = """Four narrative arcs run at once, fuelled or suppressed by your chat suggestions: GATHBANDHAN (a quiet coalition — watch who breaks first), AFWAAH (a rumour the table acts on even when false), STING (a leaked claim that forces a read), and BADLA (a vendetta that outlives the round). They run on a separate deterministic narrative RNG that never touches card state and resumes byte-for-byte.""",
                ),
                ProjectDetailSection(
                    heading = "Built for everyone",
                    body = """All six roles use the Okabe-Ito colourblind-safe palette plus a unique engraved bezel pattern (ring, hatch, dots, weave, double-rule, ticks) so identity reads without colour. Reduced-motion mode swaps every beat for a bespoke static end-frame (GHOTALA = held stamp, SUPARI = tipped chair) — accessibility never flattens the narrative.""",
                ),
                ProjectDetailSection(
                    heading = "Provider-agnostic AI",
                    body = """An AiProvider interface abstracts Anthropic, OpenAI, Gemini, on-device Gemini Nano (Android) and Apple FoundationModels (iOS 26); ISMCTS is the always-available offline fallback. Bring-your-own-key, stored in each platform's encrypted storage.""",
                ),
                ProjectDetailSection(
                    heading = "Server-authoritative online",
                    body = """Online and LAN play (private room codes, quick-match, Bonjour/mDNS discovery) run on a Ktor/Netty server that holds all state; clients receive only their redacted PlayerView, so another player's face-down roles can't appear on the wire by construction.""",
                ),
                ProjectDetailSection(
                    heading = "Seven toggle variants",
                    body = """Seven additive rule variants (Bail Pe Bahar, Bali Khel, Hawala, Adhyadesh, Khazana Raj, Mehengai, Tangi) combine freely and default off — the engine is byte-for-byte unchanged when they're disabled, expanding the surface without touching core logic.""",
                ),
            ),
            roles = listOf(
                ProjectRole("Netaji Vachan", """The Politician — Tax +3 (GHOTALA); blocks Foreign Aid""", "#0072B2"),
                ProjectRole("Bhai Teja", """The Don — Assassinate −3 (SUPARI); unblockable except by the Vakil""", "#D55E00"),
                ProjectRole("Babu Filewala", """The Bureaucrat — Steal 2 (VASOOLI); blocks Steal""", "#E69F00"),
                ProjectRole("Jugaadu Chhotu", """The Fixer — Exchange cards (SETTING); blocks Steal""", "#56B4E9"),
                ProjectRole("Vakil Loophole", """The Lawyer — no action; blocks Assassinate only (power through procedure)""", "#CC79A7"),
                ProjectRole("Patrakaar", """The Journalist — Investigate a card (JAANCH); unblockable""", "#009E73"),
            ),
            metrics = listOf(
                LabeledValue("4", "platforms · one engine"),
                LabeledValue("10", "AI bot personas"),
                LabeledValue("6", "roles · 4 story arcs"),
                LabeledValue("7", "toggle rule variants"),
            ),
            techStack = listOf(
                SkillGroup("Language & UI", listOf("Kotlin 2.4.20-Beta1", "Compose Multiplatform 1.12", "Canvas + AGSL/Skia runtime shaders")),
                SkillGroup("Engine", listOf("Deterministic (GameState, Intent) → GameState", "RNG-in-state", "replay from (seed, intentLog)")),
                SkillGroup("AI", listOf("ISMCTS (offline)", "Anthropic / OpenAI / Gemini", "on-device Gemini Nano · Apple FoundationModels", "BYOK (encrypted)")),
                SkillGroup("Online", listOf("Ktor / Netty server", "server-authoritative", "Bonjour/mDNS LAN")),
                SkillGroup("Platforms", listOf("Android", "iOS (arm64)", "Desktop (JVM)", "Web (Wasm)")),
                SkillGroup("Build & quality", listOf("Koin", "Fastlane", "CI")),
            ),
            extraLinks = listOf(
                NamedLink("README (full rules)", "https://github.com/darkpandawarrior/Kursi#readme"),
            ),
            diagrams = listOf(
                Diagram(
                    title = "Deterministic engine — one pure function",
                    code = """graph LR
  s["GameState"] -->|"+ Intent"| r["reduce()<br/>pure · RNG in state"] --> s2["GameState'"]
  s2 -.->|"byte-for-byte replay"| s""",
                ),
                Diagram(
                    title = "Secrecy boundary — redact per viewer",
                    code = """graph TD
  full["Full GameState<br/>(authoritative)"] -->|"redact(state, viewer)"| pv1["PlayerView — seat 1"]
  full -->|"redact(state, viewer)"| pv2["PlayerView — seat 2"]
  full -->|"redact(state, viewer)"| pv3["PlayerView — seat N"]""",
                ),
            ),
        ),
    ),
    Project(
        slug = "mileway",
        name = "Mileway",
        tagline = """Offline-first mileage, travel & expense tracker — one Kotlin codebase across Android, iOS, Wear OS, watchOS & Desktop.""",
        description = """Offline-first mileage, travel, and expense tracker spanning five platforms from one Kotlin codebase, zero backend.""",
        stack = listOf("Kotlin Multiplatform", "Compose Multiplatform", "Android", "iOS", "Wear OS", "watchOS", "Desktop", "Room (KMP)", "Koin"),
        highlights = listOf(
            """46-module clean architecture: 13 feature modules meeting only at the composition root.""",
            """Real location engine, reimbursement policy engine, durable submit-outbox, and an on-device AI assistant.""",
        ),
        links = listOf(
            NamedLink("GitHub", "https://github.com/darkpandawarrior/Mileway"),
            NamedLink("Case study", "#work"),
            NamedLink("PaymentsLab (sibling KMP app)", "#project/paymentslab"),
        ),
        status = "46 modules · 5 platforms · 159 tests",
        badges = listOf("Kotlin Multiplatform", "46 modules", "5 platforms", "Open source"),
        // Telemetry-cyan — the site's own "depth" accent, reused rather than invented.
        theme = ProjectTheme(
            accent = "#5ee6ff",
            accentDim = "#2fb8d6",
            ink = "#05070a",
            surface = "#0a1016",
            card = "#0f1720",
            line = "#1c2733",
        ),
        targets = listOf(
            ProjectTarget("Android", 5),
            ProjectTarget("iOS", 4, """Home-screen widget, Lock Screen widget and a Live Activity / Dynamic Island — genuine iOS surfaces, shown at their real widget shape."""),
            ProjectTarget("Wear OS", 2),
            ProjectTarget("watchOS", 1, """Native SwiftUI app, same shared snapshot model."""),
            ProjectTarget("Desktop", 1),
            ProjectTarget("Web", 1, """Live — a Compose/Wasm preview shell: dashboard, live simulated tracking and the expense log, running the real design system and location math in your browser."""),
        ),
        detail = ProjectDetailData(
            overview = """Mileway is an original, fully-offline mileage / travel / expense tracker I designed and built end-to-end in Kotlin & Compose Multiplatform — running on Android, iOS, Wear OS, watchOS and Compose Desktop from one shared codebase, with zero backend so the whole thing is reproducible and reviewable. It's my reference implementation for the architecture I advocate at scale: strict module isolation, a real location engine, a policy/reimbursement layer and a durable submit-outbox, all over local data.""",
            sections = listOf(
                ProjectDetailSection(
                    heading = "46-module clean architecture (36 local + 10 composed)",
                    body = """Thirteen feature modules that never depend on each other, meeting only at the :app composition root and wired with Koin. A shared commonMain core holds the design system, Room (KMP) + DataStore, and every check-in / hardware-event screen, with platform services behind expect/actual. Convention plugins from my own kmp-build-logic keep every module's build consistent.""",
                ),
                ProjectDetailSection(
                    heading = "Location engine",
                    body = """GPS is treated as a noisy signal: jitter suppression, spike detection to reject impossible fixes, a four-bucket distance accumulator, IMU (accelerometer) fusion and device-tier-adaptive sampling that trades battery against precision by hardware class. A deterministic simulated-drive source makes the whole engine unit-testable without hardware.""",
                ),
                ProjectDetailSection(
                    heading = "Policy & reimbursement engine",
                    body = """A reimbursement-rate engine computes a payout from configurable per-vehicle rate rules, and the approvals flow flags policy violations against those rules — the real expense-platform logic a live product needs, implemented entirely against local data rather than stubbed with a snackbar.""",
                ),
                ProjectDetailSection(
                    heading = "Durable submit-outbox",
                    body = """Submitting a track or voucher journals the intent locally and reconciles it deterministically, so a process kill mid-submit never loses a record or double-counts one. Repositories are written to look one implementation-swap away from a real API — the backend is deferred, not designed out.""",
                ),
                ProjectDetailSection(
                    heading = "Five targets, one snapshot model",
                    body = """Beyond Android and iOS phones, the same shared SurfaceSnapshot drives a Wear OS app, a watchOS SwiftUI app and a Compose Desktop window, plus Android Glance + iOS WidgetKit home-screen widgets and an iOS Live Activity / Dynamic Island for an in-progress trip. Each surface has its own design-system skinning but reads the identical shared state.""",
                ),
                ProjectDetailSection(
                    heading = "Offline AI assistant",
                    body = """A chat assistant grounded entirely in local Room data — trips, expenses, cards — with real chunked streaming (not a fake typing animation), persistent history with a 5-minute session-resume window, on-device speech-to-text/text-to-speech, and local usage analytics. No remote LLM, no server, same offline guarantee as the rest of the app.""",
                ),
                ProjectDetailSection(
                    heading = "Super-profile & plugin-composition platform (V24, shipped)",
                    body = """The newest depth wave: a single plugin registry is the app's composition mechanism — TILE / CAPABILITY / VALUE plugins resolved by layering FORCED > USER > PRESET > DEFAULT, editable live from a Master Plugin page with source chips. Four persona presets (Corporate Commuter, Super-App Consumer, Gig Driver, Minimal Guest) reshape hubs, auth flows, tracking behaviour and tunables from one account. Built on top: act-on-behalf session delegation with an app-wide "Acting as" banner, a verification centre with corporate-email/OTP + card KYC, growth surfaces (referral, coupons, scratch rewards), membership (club, subscriptions, incentives), external wallet linking via OTP, and payout identity (masked bank + editable UPI handle + QR) — shipped, with a V25→V37 series landed on top (on-device intelligence, JWT auth, closeout hardening, home cards/advances, What's New), still zero backend.""",
                ),
                ProjectDetailSection(
                    heading = "FOSS-safe distribution & quality gates",
                    body = """Dual gms / noGms builds (Google Play + F-Droid) with a dependency-prefix guard that fails the build if proprietary libraries leak into the FOSS flavor. 159 Roborazzi JVM screenshot tests (no emulator, no network) covering phone, watch and desktop, plus Napier logging, detekt, ktlint, Kover and CI.""",
                ),
            ),
            metrics = listOf(
                LabeledValue("46", "Gradle modules (36 local + 10 composed)"),
                LabeledValue("13", "isolated feature modules"),
                LabeledValue("5", "platforms · one codebase"),
                LabeledValue("0", "backend calls"),
            ),
            techStack = listOf(
                SkillGroup("Language & UI", listOf("Kotlin", "Compose Multiplatform", "Material 3", "SwiftUI (watchOS)")),
                SkillGroup("Data", listOf("Room (KMP)", "DataStore", "Coroutines + Flow", "Durable submit-outbox")),
                SkillGroup("Domain", listOf("Location engine (jitter · spike · IMU fusion)", "Reimbursement-rate policy engine")),
                SkillGroup("DI & build", listOf("Koin", "kmp-build-logic convention plugins", "AGP", "Gradle KTS")),
                SkillGroup("Maps & platform", listOf("MapLibre (F-Droid)", "KrossMap (Play)", "Glance + WidgetKit widgets", "Live Activity / Dynamic Island")),
                SkillGroup("Quality", listOf("Roborazzi (159 JVM screenshot tests)", "detekt", "ktlint", "Kover", "CI")),
            ),
            extraLinks = listOf(
                NamedLink("Feature modules", "https://github.com/darkpandawarrior/Mileway/tree/main/feature"),
                NamedLink("kmp-build-logic (shared)", "https://github.com/darkpandawarrior/kmp-build-logic"),
                NamedLink("README", "https://github.com/darkpandawarrior/Mileway#readme"),
            ),
            diagrams = listOf(
                Diagram(
                    title = "46-module architecture — features meet only at :app",
                    code = """graph TD
  app[":app composition root"]
  t["feature: tracking"]
  s["feature: logging"]
  tr["feature: travel"]
  ap["feature: approvals"]
  pa["feature: payables"]
  ag["feature: agent"]
  core["core: common · data · ui · network · security · maps<br/>design system · Room(KMP) · DataStore"]
  app --> t & s & tr & ap & pa & ag
  t & s & tr & ap & pa & ag --> core""",
                ),
                Diagram(
                    title = "Location pipeline — GPS treated as a noisy signal",
                    code = """graph LR
  gps["Raw GPS"] --> jit["Jitter<br/>suppression"] --> spk["Spike<br/>detection"] --> fus["IMU<br/>fusion"] --> tier["Device-tier<br/>sampling"] --> acc["Four-bucket<br/>distance"] --> out["Clean track"]""",
                ),
                Diagram(
                    title = "One shared snapshot → five targets",
                    code = """graph TD
  snap["commonMain<br/>SurfaceSnapshot"]
  snap --> a["Android phone"]
  snap --> i["iOS phone"]
  snap --> w["Wear OS"]
  snap --> wo["watchOS (SwiftUI)"]
  snap --> d["Compose Desktop"]""",
                ),
            ),
        ),
    ),
    Project(
        slug = "paymentslab",
        name = "PaymentsLab",
        tagline = """An Integration Lab for the Android payments ecosystem — every gateway behind one abstraction, with a live look at what actually happens on each transaction.""",
        description = """A Kotlin Multiplatform systems showcase: real payment flows across dozens of providers, all behind a single PaymentGateway abstraction, backed by a Ktor server that owns order creation, signature verification and webhook reconciliation.""",
        stack = listOf("Kotlin Multiplatform", "Compose Multiplatform", "Ktor", "Android", "iOS", "Room"),
        highlights = listOf(
            """40-module registry (15 local + 25 composed) spans 66 cataloged payment gateways.""",
            """Five money-movement rails plus split payments, all idempotency-keyed and MOCK_MODE-honest.""",
        ),
        links = listOf(
            NamedLink("GitHub", "https://github.com/darkpandawarrior/PaymentsLab"),
            NamedLink("Mileway (sibling KMP app)", "#project/mileway"),
        ),
        status = "40 modules · 66 gateways · 5 rails",
        badges = listOf("Kotlin Multiplatform", "40 modules", "66 gateways", "Open source"),
        theme = ProjectTheme(
            accent = "#A78BFA",
            accentDim = "#7C3AED",
            ink = "#120A1F",
            surface = "#1B1130",
            card = "#241844",
            line = "#3F2B66",
        ),
        targets = listOf(
            ProjectTarget("Android", 5),
            ProjectTarget("iOS", 3, """Native Stripe iOS SDK alongside the shared KMP gateway contract."""),
            ProjectTarget("Web", 0, """Live — a Compose/Wasm preview shell running the gateway catalog and the explained-checkout demo in your browser, in MOCK_MODE: the real orchestrator FSM and hosted-webview archetype, in-memory fakes for the server."""),
        ),
        detail = ProjectDetailData(
            overview = """Payments is the hardest integration surface on Android: every gateway ships a different SDK, most of them are Activity-callback-era, the client can lie about the outcome, and the interesting logic (signatures, webhooks, idempotency, recovery) lives on the server. PaymentsLab runs — and step-by-step visualizes — real payment flows across a 66-gateway catalog behind a single PaymentGateway abstraction, backed by a Ktor server that does the order creation, signature verification and webhook reconciliation a real integration requires — and, beyond one-shot pay-in, models five money-movement rails.""",
            sections = listOf(
                ProjectDetailSection(
                    heading = "The one idea worth stealing",
                    body = """A client-side Success is a hint, never proof. Only the server — after signature verification and webhook reconciliation — decides the true state. A server that owns price and truth, a client that always confirms before trusting, a journal written to Room before the SDK launches so a process death mid-payment is always recoverable, and a redaction layer so no secret or PII ever renders or logs.""",
                ),
                ProjectDetailSection(
                    heading = "40 modules, 66 gateways",
                    body = """One Gradle module per native-SDK provider is contributed into a registry via Koin's getAll<PaymentGateway>(), so adding gateway N+1 touches no existing code — 15 local modules plus 25 composed from kmp-toolkit (19 of them standalone provider gateway modules). The in-app catalog spans 66 registered gateways: 7 native-SDK integrations, 47 hosted-webview gateways behind one archetype, 8 mobile-money flows and 4 catalog-only / KYC-gated entries — each with its own status badge and region.""",
                ),
                ProjectDetailSection(
                    heading = "Five money-movement rails + split payments",
                    body = """Beyond one-shot checkout the server models payouts (/payouts — money out to a beneficiary), mandates & subscriptions (/mandates + scheduled debits and cancel), a card vault (/vault — tokenize once, charge later by id), marketplace Connect onboarding (/connect — sub-merchant KYC + split payouts) and an internal double-entry wallet ledger (/wallet — seed / debit / refund against a real running balance) — plus split payments, a two-leg orchestration that compensates if one leg fails. Ten provider modules ride these rails (Paystack, Flutterwave, Paytm, Xendit, M-Pesa, Peach, NMI, Stripe Connect, plus wallet and a record-only cash gateway), every one MOCK_MODE-honest until real sandbox keys are set.""",
                ),
                ProjectDetailSection(
                    heading = "One contract, real SDKs",
                    body = """Razorpay, Cashfree, Stripe (+ Google Pay), Square, Omise and a raw UPI intent flow all implement the same tiny PaymentGateway interface. The Activity-callback SDKs are bridged into suspending coroutines by a PaymentHost that never leaks an Activity upward. A generic hosted-webview archetype covers the whole class of gateways with no native SDK behind the same contract — env-backed credentials auto-degrade from SANDBOX_READY to MOCK_MODE honestly instead of silently pretending to work.""",
                ),
                ProjectDetailSection(
                    heading = "Pure, replayable state machine",
                    body = """The lifecycle is a pure (State, Event) → Effects reducer — zero coroutines/DI/IO — with the orchestrator just executing its effects. A payment's path is a recorded event log that replays byte-for-byte identically, the auditing property money movement wants. The MVI base comes from my own kmp-toolkit library, shared with other apps.""",
                ),
                ProjectDetailSection(
                    heading = "VAPT-grade security",
                    body = """core:security — real Android Keystore AES-256-GCM at-rest encryption, FLAG_SECURE + recursive tapjacking protection, device-integrity checks (root, emulator, debugger, Frida/Xposed hook detection, SSL-pinning-bypass detection), and a certificate-pinning config, with detection kept deliberately separate from enforcement policy.""",
                ),
            ),
            metrics = listOf(
                LabeledValue("40", "Gradle modules (15 local + 25 composed)"),
                LabeledValue("66", "gateways cataloged"),
                LabeledValue("5", "money-movement rails"),
                LabeledValue("1", "PaymentGateway contract"),
            ),
            techStack = listOf(
                SkillGroup("Architecture", listOf("Kotlin Multiplatform", "Compose Multiplatform", "40 Gradle modules (15 + 25 composed)", "Koin registry (getAll)", "kmp-toolkit (shared MVI base)")),
                SkillGroup("Backend & rails", listOf("Ktor server", "HMAC-SHA256 signatures", "Webhook reconciliation", "Payouts · mandates · vault · connect · wallet ledger")),
                SkillGroup("Data & Security", listOf("Room (process-death journal)", "Android Keystore AES-256-GCM", "Certificate pinning", "Device-integrity checks")),
                SkillGroup("Build & quality", listOf("kmp-build-logic convention plugins", "Roborazzi screenshot tests", "ktlint", "detekt", "GitHub Actions CI")),
            ),
            extraLinks = listOf(
                NamedLink("kmp-toolkit (shared)", "https://github.com/darkpandawarrior/kmp-toolkit"),
                NamedLink("kmp-build-logic (shared)", "https://github.com/darkpandawarrior/kmp-build-logic"),
                NamedLink("README", "https://github.com/darkpandawarrior/PaymentsLab#readme"),
            ),
            diagrams = listOf(
                Diagram(
                    title = "Gateway registry — adding provider N+1 touches no existing code",
                    code = """graph TD
  reg["PaymentGateway registry<br/>Koin getAll()"]
  p1["provider: razorpay"] --> reg
  p2["provider: stripe"] --> reg
  p3["provider: cashfree"] --> reg
  p4["provider: hosted-webview<br/>(covers 44 gateways)"] --> reg
  pn["provider: N+1"] --> reg
  reg --> orch["PaymentOrchestrator"]""",
                ),
                Diagram(
                    title = "Client Success is a hint — the server decides truth",
                    code = """graph LR
  cl["Client SDK<br/>callback"] -->|"hint only"| orch["Orchestrator"]
  orch -->|"confirm"| srv["Ktor server"]
  srv -->|"HMAC verify"| wh["Webhook<br/>reconcile"]
  wh -->|"true state"| orch""",
                ),
                Diagram(
                    title = "Five rails beyond one-shot pay-in",
                    code = """graph TD
  srv["Ktor server<br/>(idempotency-keyed)"]
  srv --> pay["Pay-in /orders"]
  srv --> out["Payouts /payouts"]
  srv --> man["Mandates /mandates"]
  srv --> vlt["Card vault /vault"]
  srv --> con["Connect /connect"]
  srv --> wal["Wallet ledger /wallet"]""",
                ),
            ),
        ),
    ),
    Project(
        slug = "hiresignal",
        name = "HireSignal",
        tagline = """A native, multiplatform AI career-intelligence engine — and the open-source project it's built on.""",
        description = """A local-first job-search engine — resume onboarding, reverse-ATS discovery, evidence-based fit scoring and tailored résumés — rebuilt from scratch in Kotlin Multiplatform, with its scoring engine ported and verified against the open-source career-ops project I actively contribute to upstream.""",
        stack = listOf("Kotlin Multiplatform", "Compose Multiplatform", "Spring Boot 4", "Room (KMP)", "Ktor", "62 ATS/board providers"),
        highlights = listOf(
            """25-module Kotlin Multiplatform clean architecture — 12 feature + 6 core modules — targeting Android, iOS, Desktop, Web and a Spring Boot 4 server from one shared engine.""",
            """core:engine is a no-IO module: A–F fit scoring, ATS search, SimHash fingerprinting, and funnel math ported 1:1 from career-ops and verified against its own test vectors.""",
            """62 ATS & job-board provider integrations and a zero-token scan path (direct Greenhouse/Ashby/Lever APIs, no LLM cost) inherited from the open-source engine it's built on.""",
            """4 merged PRs to the public career-ops project (⭐60k+) — two new ATS providers (BambooHR, Breezy HR), a dashboard status-cell fix, and an agent-inbox feature — real, verifiable upstream contributions.""",
        ),
        links = listOf(
            NamedLink("My career-ops fork", "https://github.com/darkpandawarrior/career-ops"),
            NamedLink("Upstream (career-ops, ⭐60k+)", "https://github.com/santifer/career-ops"),
        ),
        status = "Active · 4 PRs merged upstream",
        badges = listOf("Kotlin Multiplatform", "25 modules", "Open-source contributor"),
        theme = ProjectTheme(
            accent = "#3B82F6",
            accentDim = "#1D4ED8",
            ink = "#0A1120",
            surface = "#0F1B2E",
            card = "#16233A",
            line = "#28405E",
        ),
        targets = listOf(
            ProjectTarget("Android", 3, """Real Roborazzi captures — first screenshots off the actual Compose UI, not mockups."""),
        ),
        detail = ProjectDetailData(
            overview = """HireSignal is a local-first AI career-intelligence engine: resume onboarding, reverse-ATS discovery, evidence-based fit scoring and tailored résumés, in one pipeline. The product idea and scoring model started on career-ops, an open-source Node.js job-search engine (⭐60k+) that I actively contribute to upstream. The native app is a from-scratch Kotlin Multiplatform rebuild — the same A–F fit-scoring engine, ported and verified line-for-line against the original, now running identically on Android, iOS, Desktop, Web and a Spring Boot server instead of a single Node process.""",
            sections = listOf(
                ProjectDetailSection(
                    heading = "One engine, five targets",
                    body = """A 25-module clean-architecture split — 12 feature modules and 6 core modules — targets Android, iOS, Desktop, Web (wasmJs) and a Spring Boot 4 server from one shared Kotlin codebase: 543 files, ~45,000 lines. core:designsystem, core:protocol, core:engine, core:data, core:network and core:ai sit underneath feature modules for dashboard, pipeline, explore, intel, ops, profile, auth, assistant and more.""",
                ),
                ProjectDetailSection(
                    heading = "A no-IO engine, ported and verified",
                    body = """core:engine holds the A–F fit-scoring rubric, ATS search, SimHash fingerprinting for duplicate-listing detection, a liveness classifier, and the funnel math — none of it touches the network or disk. It's ported 1:1 from career-ops's original JavaScript implementation and checked against that implementation's own test vectors, so the scoring behaves identically whether it's running on Android, in a browser tab, or on the server.""",
                ),
                ProjectDetailSection(
                    heading = "Offline-first, agent-reachable",
                    body = """Room (KMP) plus DataStore caches everything locally over a Ktor REST + NDJSON/SSE sync layer, so the dashboard stays usable offline and catches up when connectivity returns. An agent-interop surface — Android AppFunctions, iOS App Intents/Shortcuts, hiresignal:// deep links, and a documented OpenAPI contract — lets other agents (and the OS itself) drive the app without going through the UI.""",
                ),
                ProjectDetailSection(
                    heading = "On-device AI, with a fallback that always works",
                    body = """Where an LLM adds real value, it runs on-device first: ML Kit GenAI / Gemini Nano on Android, Apple Foundation Models on iOS. Every AI-assisted step has a deterministic-heuristic fallback, so fit scoring and résumé tailoring keep working with zero model available — the same discipline career-ops applies with its zero-token scan path.""",
                ),
                ProjectDetailSection(
                    heading = "Zero tokens until an LLM is actually needed",
                    body = """The engine's scan path hits Greenhouse, Ashby and Lever APIs plus per-company local parsers directly, at zero LLM cost, falling back to an agent-driven search only for companies with no structured source. Every scanned posting passes through one shared trust-validator that scores and flags it before it reaches the tracker — 62 ATS & job-board provider modules plug into that one contract instead of reinventing trust scoring each time.""",
                ),
                ProjectDetailSection(
                    heading = "One engine, many candidates",
                    body = """career-ops's multi-profile architecture — a profiles.yml registry mapping each candidate to a private data root while sharing one engine install — is the same shape the native app's per-candidate routing follows: one server, N profiles, a strict User/System data contract between them.""",
                ),
                ProjectDetailSection(
                    heading = "Genuine upstream contribution, not a personal fork",
                    body = """Four merged pull requests against the public career-ops repository (⭐60k+, independently verifiable): two new ATS providers (BambooHR, Breezy HR), a dashboard rendering fix — rewriting only the changed Status cell instead of the whole row — and an agent-inbox feature for queuing requests across sessions.""",
                ),
            ),
            metrics = listOf(
                LabeledValue("25", "KMP modules · 5 targets"),
                LabeledValue("45k", "lines of Kotlin · 543 files"),
                LabeledValue("62", "ATS & job-board providers"),
                LabeledValue("4", "PRs merged upstream"),
            ),
            techStack = listOf(
                SkillGroup("Native app", listOf("Kotlin Multiplatform", "Compose Multiplatform", "Spring Boot 4 server", "Room (KMP) + DataStore", "Ktor REST + NDJSON/SSE")),
                SkillGroup("On-device AI", listOf("ML Kit GenAI / Gemini Nano (Android)", "Apple Foundation Models (iOS)", "deterministic-heuristic fallback")),
                SkillGroup("Agent interop", listOf("Android AppFunctions", "iOS App Intents / Shortcuts", "hiresignal:// deep links", "OpenAPI contract")),
                SkillGroup("Open-source engine (career-ops)", listOf("Node.js", "62 ATS/job-board providers", "zero-token Greenhouse/Ashby/Lever scanning", "A–F fit rubric")),
            ),
            extraLinks = listOf(
                NamedLink("PR: agent-inbox feature", "https://github.com/santifer/career-ops/pull/1472"),
                NamedLink("PR: dashboard Status-cell fix", "https://github.com/santifer/career-ops/pull/1186"),
                NamedLink("PR: Breezy HR provider", "https://github.com/santifer/career-ops/pull/1185"),
                NamedLink("PR: BambooHR provider", "https://github.com/santifer/career-ops/pull/1141"),
            ),
            diagrams = listOf(
                Diagram(
                    title = "One engine, five targets",
                    code = """graph LR
  eng["core:engine<br/>ported + verified vs career-ops test vectors"] --> and["Android"]
  eng --> ios["iOS"]
  eng --> desk["Desktop"]
  eng --> web["Web (wasmJs)"]
  eng --> srv["Spring Boot 4 server"]
  eng -.->|"no IO — pure scoring"| rules["A-F fit rubric · SimHash · funnel math"]""",
                ),
                Diagram(
                    title = "Zero tokens until an LLM is actually needed",
                    code = """graph LR
  scan["scan"] --> apis["Greenhouse / Ashby / Lever APIs<br/>+ local parsers — zero LLM cost"]
  apis --> trust["shared trust-validator"]
  trust --> tracker["tracker"]
  scan -.->|"no structured source"| agent["agent-driven search — fallback only"] --> trust""",
                ),
            ),
        ),
    ),
    Project(
        slug = "portfolio",
        name = """This portfolio + “Panda”, my AI assistant""",
        tagline = """The site you're reading — and Panda, a provider-agnostic LLM assistant that answers for me, grounded in my real CV.""",
        description = """An interactive résumé with a built-in AI assistant. React 19 + Vite + Tailwind on Vercel Edge, with a provider-agnostic chat backend (Groq / Gemini / Claude) and prompt-injection guards.""",
        stack = listOf("React 19", "Vite 7", "Tailwind v4", "Vercel Edge", "Multi-provider LLM"),
        highlights = listOf(
            """3D scroll-driven hero, printable résumé view, and case studies with real production metrics.""",
            """Provider-agnostic chat backend — the assistant is grounded in this same source-of-truth profile data.""",
        ),
        links = listOf(
            NamedLink("Live", "https://cv-siddharth.vercel.app"),
            NamedLink("GitHub", "https://github.com/darkpandawarrior/cv-siddharth"),
        ),
        status = "Live",
        badges = listOf("React 19", "Vercel", "LLM chat"),
    ),
    Project(
        slug = "deadlock",
        name = "DEADLOCK",
        tagline = """A first-person time-loop game about a moment someone could not let end.""",
        description = """Godot 4.7 in GDScript. A deterministic echo-replay spine — recorded input intent replays through the same physics step — powers cooperative echoes, ghosts, and boss desync from one system. Built solo as an AI-orchestrated dev crew.""",
        stack = listOf("Godot 4.7", "GDScript", "Deterministic fixed-timestep sim", "gdUnit4", "AI-orchestrated content pipeline"),
        highlights = listOf(
            """One deterministic (state, InputFrame) → state step reused five ways: cooperative Echoes, ghosts, leaderboard replays, the Hunter, and boss desync.""",
            """A bit-exact determinism gate guards every change to the time systems, wired into a hook that reruns it automatically on every edit.""",
            """Design-first build: a 4,300+ line, 7-document codex and 24 animated SVG design boards, generated by a checked-in AI dev-crew script — 39 agents, 0 failures, one session.""",
        ),
        // Repo is private — early solo build. The case study is verified against the source.
        links = emptyList(),
        status = "In development · private repo, public case study",
        badges = listOf("Godot 4.7", "GDScript", "Time-loop", "Solo + AI dev crew"),
        // Brightened from the original #B3223C pick — that failed WCAG AA against
        // ink/surface/card (2.6-3.0:1). These clear AA everywhere (5.1-7.1:1).
        theme = ProjectTheme(
            accent = "#FF5C7A",
            accentDim = "#EE5577",
            ink = "#140A0C",
            surface = "#1F0F13",
            card = "#2A151A",
            line = "#4A2530",
        ),
        detail = ProjectDetailData(
            overview = """DEADLOCK is a first-person time-loop game about a moment someone could not let end — a grieving mind's mathematics, rendered as a room that lies about its own floor. Under the mood sits one deterministic engine: every action is recorded as intent, never position, and replayed through the exact same physics step. That one idea — record intent, replay deterministically — is reused, unmodified, five different ways across the game's core systems.""",
            sections = listOf(
                ProjectDetailSection(
                    heading = "Record intent, never position",
                    body = """The determinism contract in one line: an InputFrame stores a move vector, jump, and dash — never a position. Motion.step(state, frame) replays it through the same fixed-timestep physics tick every time, so the same state plus the same frame always produces the same state out. Positions are outputs, never inputs, which is what makes an Echo standing on a pressure pad, a ghost racing a past run, and the Hunter's prediction the same handful of lines wearing three different narrative masks.""",
                ),
                ProjectDetailSection(
                    heading = "One spine, five faces",
                    body = """Recorder is a ring buffer of InputFrames; Echo replays a slice of it tick-for-tick, either incrementally (once per physics tick, for a live cooperating Echo holding a pressure pad open) or in one shot (for ghosts, tests, and the Hunter's prediction). Cooperative Echoes hold a bridge open, ghosts race a past run, the leaderboard replays a full match, and the Hunter — the thing hunting you — predicts your position off the same replay math. No branch of that list touches a second system.""",
                ),
                ProjectDetailSection(
                    heading = "The gate that can't be skipped",
                    body = """tests/test_determinism.gd asserts bit-exact field equality with no tolerance, plus a perturbation check that fails if a changed input ever produces an identical output — the test that would catch a gate that silently stopped testing anything. A PostToolUse hook reruns it automatically on any edit to the time or player systems, so drift surfaces the moment it's introduced, not at playtest.""",
                ),
                ProjectDetailSection(
                    heading = "The Hunter, built on the same replay math",
                    body = """The Hunter wakes once the player's attention score crosses a threshold, can be frozen by the Stutter ability, and catches the player by proximity — a CharacterBody3D whose prediction runs on the exact same recorded-intent pipeline as the cooperative-Echo and boss-desync mechanics. It isn't a second AI system bolted on; it's the same fifteen lines of replay code with a different narrative job.""",
                ),
                ProjectDetailSection(
                    heading = "Design bible before geometry",
                    body = """52 logged iterations, 10 entity dossiers, 24 hand-authored animated-SVG design boards, and a 4,300+ line, seven-document codex — written before most of the game's rooms exist. The frame test for every addition is one question: would a grieving mind hold this?""",
                ),
                ProjectDetailSection(
                    heading = "An AI dev crew, checked in, not described",
                    body = """The codex wasn't hand-written — it was generated by a workflow script checked into the repo: three readers distill source material, seven documents generate in a pipeline where critique starts the moment each one finishes its own draft, the four widest creative documents run dual-lens ensembles merged by a judge pass, and every draft clears adversarial critics for frame, fairness, originality, and voice before a reviser is allowed to touch the file. One session: 39 agents, zero failures, ~4.8M tokens.""",
                ),
                ProjectDetailSection(
                    heading = "Honesty as a design constraint",
                    body = """The project's own README states plainly which systems are playable versus designed-but-unbuilt, and backs every specific number with a literal command a reader could run against the source. The in-fiction lesson — an unreliable room lies about the floor; sending an Echo reveals the truth — is asked of the documentation too.""",
                ),
            ),
            metrics = listOf(
                LabeledValue("5", "systems · one deterministic spine"),
                LabeledValue("2,026", "lines of GDScript · 36 files"),
                LabeledValue("39", "AI agents · one dev-crew session"),
                LabeledValue("0", "tolerance in the determinism gate"),
            ),
            techStack = listOf(
                SkillGroup("Engine", listOf("Godot 4.7 (Forward+)", "GDScript", "fixed-timestep _physics_process")),
                SkillGroup("Determinism core", listOf("InputFrame (intent, not position)", "Recorder ring buffer", "Echo (incremental + one-shot replay)")),
                SkillGroup("Testing", listOf("gdUnit4", "bit-exact determinism gate", "PostToolUse re-run hook")),
                SkillGroup("Build", listOf("Single-threaded WASM web export", "Git LFS for binary assets")),
                SkillGroup("Content pipeline", listOf("Checked-in AI dev-crew workflow script", "voice/dash deterministic lints", "pre-commit enforced")),
            ),
            diagrams = listOf(
                Diagram(
                    title = "One deterministic step, five uses",
                    code = """graph LR
  s["state"] -->|"InputFrame (intent)"| step["Motion.step()<br/>pure · fixed timestep"] --> s2["state'"]
  step -.-> echo["Echo — cooperative"]
  step -.-> ghost["Ghost replay"]
  step -.-> board["Leaderboard replay"]
  step -.-> hunter["The Hunter — prediction"]
  step -.-> boss["Boss desync"]""",
                ),
                Diagram(
                    title = "The gate that can't be skipped",
                    code = """graph TD
  edit["Edit to core/time/ or core/player/"] --> hook["PostToolUse hook"]
  hook --> gate["tests/test_determinism.gd<br/>bit-exact · zero tolerance"]
  gate -->|"pass"| ok["Change accepted"]
  gate -->|"fail"| block["Drift caught before playtest"]
  gate --> perturb["Perturbation check<br/>changed input -> must change output"]""",
                ),
            ),
        ),
    ),
)

/** Reading order for the "next build" pager. Deliberately omits `portfolio`. */
val projectOrder = listOf("mileway", "kursi", "paymentslab", "hiresignal", "deadlock")

/**
 * The one place a slug becomes a project. Every externally-supplied slug — the
 * terminal's `/open <slug>`, a chat directive — routes through here, so an
 * invented or injected slug resolves to null rather than each caller
 * re-implementing the check.
 */
fun projectBySlug(slug: String): Project? = projects.firstOrNull { it.slug == slug }

/** The next project in [projectOrder], wrapping. Null if [slug] isn't in the pager. */
fun nextProject(slug: String): Project? {
    val i = projectOrder.indexOf(slug)
    if (i < 0) return null
    return projectBySlug(projectOrder[(i + 1) % projectOrder.size])
}
