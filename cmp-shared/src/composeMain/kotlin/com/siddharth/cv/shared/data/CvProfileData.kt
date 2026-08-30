package com.siddharth.cv.shared.data

import com.siddharth.cv.shared.labs.labCount

/**
 * Transcribed verbatim from cv-siddharth/src/data/profile.ts, which is the source of truth. Diff
 * against it before editing anything here: this file has drifted before, and not as changed numbers
 * but as a whole superseded revision of the intro and the summary, which is much harder to spot.
 *
 * Every metric string is an audited claim. `cmp-shared/src` is a registered claim-audit surface, so
 * changing "~87%", "50k+", "50% → 95%", "80%" or "~964k" here fails the gate, not just review.
 */

val profile = Profile(
    name = "Siddharth Pandalai",
    title = "Senior Android Engineer",
    resumeTitle = "Senior Android Engineer, Mobile Architecture & Platform",
    tagline = "I take Android apps from prototype to platform.",
    location = "Pune, India",
    email = "siddharthpandalai990@gmail.com",
    phone = "+91 8848852062",
    github = "https://github.com/darkpandawarrior",
    linkedin = "https://linkedin.com/in/siddharth-pandalai",
    portfolio = "https://cv-siddharth.vercel.app",
    // No notice period and no "available immediately". Dice.tech runs to Present on the same page,
    // and in this market a notice period is assumed: the two together read as either hiding
    // unemployment or not intending to serve notice. Location and remote preference are the parts a
    // recruiter can act on. profile.ts carries the same decision and the same reasoning.
    availability = "Open to remote (worldwide / India) and hybrid in Pune / Bengaluru",
    intro = """5+ years building production Android. I own the platform behind a ~964k-LOC financial SaaS app serving 50,000+ monthly users. I joined it with zero Kotlin in the codebase. ~87% of the UI layer is Compose today. Location accuracy, crash-free sessions, architecture a team can move fast in.""",
    summary = """Senior Android Engineer, 5+ years in Kotlin. Technical owner and Product Owner of a ~964k-LOC, 50,000+ MAU financial SaaS app, inherited as Java with no Kotlin in it and now ~87% Jetpack Compose across the UI layer. Clean Architecture with MVVM/MVI, Coroutines and Flow, Hilt, Room. Hard-systems depth where it counts: staged dead-reckoning location with Kalman smoothing (GPS accuracy 50% to 95%), VAPT-grade on-device security (Android Keystore, SSL pinning), and an 80% production crash reduction at 22,000+ DAU won on the concurrency model, not defensive catches.""",
)

val education = Education(
    school = "NIT Bhopal (MANIT)",
    degree = "B.Tech, Computer Science & Engineering",
    period = "2017 - 2021",
)

val metrics = listOf(
    Metric("50k+", "monthly active users", "22k+ daily, platform owner at Dice.tech"),
    Metric("95%", "GPS accuracy", "up from 50%, by predictive dead reckoning"),
    Metric("80%", "crash reduction", "Crashlytics + structured concurrency fixes"),
    Metric("~87%", "UI-layer Compose", "455k of 523k UI LOC in a ~964k LOC app; per-screen parity verification"),
)

/** Core competency chips — shown in the résumé header and on LinkedIn. */
val competencies = listOf(
    "Kotlin & Jetpack Compose",
    "Clean Architecture (MVVM / MVI)",
    "Kotlin Coroutines & Flow",
    "Hilt Dependency Injection",
    // The audited form. "Room & Offline Storage" carried no number; claims.json bans "16+ schema
    // migrations" precisely because the real figure is 24 across 2 databases, so state it.
    "Room (2 DBs, 24 production migrations)",
    // The one-pager's "Core:" line is this array verbatim, and it had no networking token at all.
    // Retrofit/REST is a hard filter on most Android reqs.
    "Retrofit / OkHttp & REST APIs",
    "Location Engineering (Dead Reckoning, Kalman)",
    "Mobile Security (Android Keystore, SSL Pinning)",
    "CI/CD (Fastlane, Gradle)",
)

val languages = listOf("Kotlin", "Java", "Dart", "C++")

val experience = listOf(
    Experience(
        company = "Neev Consulting",
        role = "Consulting Engineer, Platform & AI",
        period = "April 2026 - Present",
        points = listOf(
            ExperiencePoint(
                label = "Agentic ERP",
                text = """Authored the LLM assistant layer of an ERPNext/Frappe consulting ERP: business-context resolution, capability discovery, and an AI capability gate that defaults OFF with a test proving it. Models client → project → PO → milestone → GST invoice → payment end to end.""",
            ),
            ExperiencePoint(
                label = "Platform",
                text = """Python/Frappe on MariaDB and Docker Compose, with a LibreChat deployment and MCP tool wiring (Atlassian, Playwright). Delivered under Jira with PR review across four repositories.""",
            ),
        ),
    ),
    Experience(
        company = "Dice.tech",
        role = "SDE-2, Android & Product Owner",
        period = "June 2023 - Present",
        points = listOf(
            ExperiencePoint(
                label = "Platform Ownership",
                text = """Own Android architecture and platform decisions across a ~964k-LOC Kotlin app serving 50,000+ MAU.""",
            ),
            ExperiencePoint(
                label = "Compose Migration",
                text = """Led legacy Java/XML to ~87% of UI-layer code in Compose migration verified per-screen against the legacy XML baseline across mission-critical workflows.""",
            ),
            ExperiencePoint(
                label = "Location Engineering",
                text = """Built a predictive dead-reckoning location engine, lifting GPS accuracy from 50% to 95% for 22,000+ DAU.""",
            ),
            ExperiencePoint(
                label = "Crash Reduction",
                text = """Cut production crashes 80% via structured-concurrency fixes and dual Crashlytics + Sentry monitoring.""",
            ),
            ExperiencePoint(
                label = "Security Hardening",
                text = """Hardened the app to VAPT/banking compliance: AES-256 Android Keystore field-level encryption, a biometric access gate, and SSL pinning across 9 domains (5 SHA-256 pins) via build flavors.""",
            ),
            ExperiencePoint(
                label = "Data Layer",
                text = """Own the Room persistence layer across two databases with 24 verified production schema migrations.""",
            ),
            ExperiencePoint(
                label = "Product Growth",
                text = """Built the in-app review prompting that moved the Play Store listing from 1.6★ across 67 reviews to 4.5★ across 27,300, the rating a prospective customer sees before they install anything.""",
            ),
            ExperiencePoint(
                label = "Travel Platform",
                text = """Shipped the Android side of Trip V2: Itinerary V2, GIN screens, and full Mixpanel instrumentation.""",
            ),
            ExperiencePoint(
                label = "UI Platform",
                text = """Designed a Dynamic Theme Engine for client branding, cutting UI development friction 60%.""",
            ),
            ExperiencePoint(
                label = "CI/CD & Automation",
                text = """Automated Fastlane builds and Play Store releases; upgraded to AGP 9 with agentic MCP workflows.""",
            ),
        ),
    ),
    Experience(
        company = "Jugnoo / Tookan / Jungleworks",
        role = "Software Engineer, Android & Vertical Owner",
        period = "January 2021 - May 2023",
        points = listOf(
            ExperiencePoint("""Owned Android development across multi-tenant SaaS platforms for customer, driver, and merchant apps."""),
            ExperiencePoint("""Built modular white-label templates, cutting delivery time 80% across 20+ clients."""),
            ExperiencePoint("""Refactored core modules and REST integrations (Retrofit, OkHttp), including secure payment gateways."""),
            ExperiencePoint("""Unified P2P Carpool and Trucking verticals into one super-app platform, simplifying user flows."""),
            ExperiencePoint("""Collaborated cross-team on roadmaps with product and backend, cutting engineering overhead 40%."""),
        ),
    ),
    Experience(
        company = "John Deere India",
        role = "GET Intern",
        period = "May 2020 - July 2020",
        points = listOf(
            ExperiencePoint("""Built a proof of concept integrating social-media sentiment analysis into financial lending systems to enhance credit-risk modeling."""),
        ),
    ),
)

val caseStudies = listOf(
    CaseStudy(
        slug = "mileway",
        title = "Mileway: offline-first mileage tracker (Android · iOS · Wear OS · watchOS · Desktop)",
        metric = "46 modules · 5 platforms · offline AI",
        summary = """An open-source app I designed and built end-to-end: mileage, travel & expense tracking that runs entirely offline across Android, iOS, Wear OS, watchOS and Compose Desktop from one shared Kotlin codebase. Zero backend, Room + DataStore only, so the whole thing is reproducible and reviewable by anyone.""",
        problem = """I wanted a clean, inspectable reference for the architecture I advocate for at scale: Compose Multiplatform, strict module isolation, MVI state, a real location engine and a real policy/reimbursement layer. Built with zero backend, so the whole thing is reproducible and reviewable by anyone.""",
        approach = listOf(
            """46-module clean architecture: 13 feature modules that never depend on each other, meeting only at the :app composition root, wired with Koin.""",
            """Shared commonMain core: design system, Room (KMP) + DataStore, and every check-in / hardware-event screen. It drives Android, iOS, Wear OS, a watchOS SwiftUI app and a Compose Desktop window from one snapshot model.""",
            """A location engine that treats GPS as a noisy signal: jitter suppression, spike detection, a four-bucket distance accumulator, IMU (accelerometer) fusion and device-tier-adaptive sampling, with a deterministic simulated-drive source so the whole engine is unit-testable without hardware.""",
            """A policy engine that computes reimbursement from configurable per-vehicle rate rules and flags policy violations on approvals. The real logic a live expense platform needs, all local.""",
            """A durable submit-outbox: a track/voucher submission is journaled locally and reconciled deterministically, so a kill mid-submit never loses or double-counts a record. The repository already looks one implementation-swap away from a real API.""",
            """An on-device AI assistant: retrieval-grounded chat over real local trip/expense/card data, Room-backed history with 5-minute session resume, chunked streaming and on-device speech I/O. No remote LLM, no server.""",
            """A super-profile & plugin-composition platform (V24, shipped, with a V25→V37 series landed on top spanning on-device intelligence, JWT auth, closeout hardening, home-screen cards/advances and a What's New feature): a single plugin registry drives four persona presets (Corporate Commuter, Super-App Consumer, Gig Driver, Minimal Guest) that reshape hubs, auth flows and tracking behaviour from one account, plus act-on-behalf session delegation, a verification centre, growth/membership surfaces and wallet/payout identity. Every tile, capability and tunable value gates through that registry, resolved by layering FORCED > USER > PRESET > DEFAULT.""",
            """Dual gms / noGms distribution (Google Play + F-Droid) with a dependency-guard that fails the build if proprietary libraries leak into the FOSS flavor; quality gated by 159 Roborazzi JVM screenshot tests (no emulator, no network), Napier logging, detekt, ktlint, Kover and CI.""",
        ),
        outcome = """All five targets build, run and pass every quality gate from one shared Kotlin codebase, with a real location engine, a policy/reimbursement layer, a durable submit-outbox, a persona-driven plugin-composition platform and an on-device AI assistant layered on the offline data model. Explore the app, architecture diagrams and all rendered screens at github.com/darkpandawarrior/Mileway.""",
        tags = listOf("Kotlin Multiplatform", "Compose Multiplatform", "Android · iOS · Wear OS · watchOS · Desktop", "46 modules", "Offline AI", "Open source"),
    ),
    CaseStudy(
        slug = "gps-accuracy",
        title = "Predictive dead reckoning for billing-grade mileage",
        metric = "50% → 95%",
        summary = """Predictive dead reckoning for a mileage-tracking app whose raw GPS was wrong half the time.""",
        problem = """Field users' trip distances were off by large margins from urban canyons, tunnels, and OEM-throttled location updates.""",
        approach = listOf(
            """Fused accelerometer and GPS data to estimate position between fixes via dead reckoning.""",
            """Rejected physically impossible fixes with spike detection, plus gap-filling for weak signal.""",
            """Ran a foreground service with a floating bubble UI to survive OEM battery restrictions.""",
        ),
        outcome = """Tracking accuracy rose from 50% to 95%, making mileage reliable enough for expense reimbursement.""",
        tags = listOf("Location", "Dead reckoning", "Kalman filtering", "Foreground services"),
    ),
    CaseStudy(
        slug = "crash-reduction",
        title = "Systematic crash triage at 50k-MAU scale",
        metric = "-80% crashes",
        summary = """Systematic triage with Crashlytics turned a noisy crash feed into a fixable backlog.""",
        problem = """A fast-growing ~964k-LOC app had a crash rate hurting its Play Store rating, driven by untraceable threading bugs.""",
        approach = listOf(
            """Clustered crashes to collapse dozens of stack traces into a handful of root bugs.""",
            """Reconstructed the user journey before each crash with structured breadcrumb instrumentation.""",
            """Hunted concurrency bugs: main-thread violations, coroutine race conditions, lifecycle leaks.""",
        ),
        outcome = """Crashes fell 80% at 22k DAU; Play Store went 1.6★/67 reviews to 4.5★/27.3K, closing 85% of the gap to a perfect 5.0, +181% rating, 407x review volume.""",
        tags = listOf("Crashlytics", "Structured concurrency", "Coroutines"),
    ),
    CaseStudy(
        slug = "compose-migration",
        // Verbatim from profile.ts. This previously read "Zero-regression migration…", which the claim-audit:allow
        // source never says and the claim-audit gate forbids outright: 31 unit-test files against
        // 3,585 source files is not evidence of zero regressions. The defensible claim, used claim-audit:allow
        // everywhere else in this record, is per-screen parity against the XML baseline.
        title = "The theme platform behind a ~964k-LOC Compose migration",
        metric = "~87% UI-layer Compose",
        summary = """Migrated a ~964k-LOC app to Jetpack Compose verified per-screen against the legacy XML baseline and built a theme engine the whole team ships on.""",
        problem = """XML views made UI changes slow and inconsistent, and design's theming requests meant touching dozens of files.""",
        approach = listOf(
            """Migrated incrementally via interop, keeping Expenses, Travel, and Invoices shipping throughout.""",
            """Standardized on a single immutable UiState per screen with StateFlow and MVI.""",
            """Built a Dynamic Theme Engine on CompositionLocal for one-place brand and token changes.""",
        ),
        outcome = """Reached ~87% UI-layer Compose coverage (455k of 523k LOC) verified per-screen against the legacy XML baseline; UI development friction dropped 60%.""",
        tags = listOf("Jetpack Compose", "MVI", "Design systems"),
    ),
    CaseStudy(
        slug = "white-label",
        title = "Configuration-driven pipeline for multi-tenant Android",
        metric = "80% faster delivery",
        summary = """A configuration-driven pipeline that turned weeks of per-client Android work into days.""",
        problem = """Every new white-label client meant manually forking, rebranding, and re-releasing the app: weeks of error-prone work.""",
        approach = listOf(
            """Built configuration-driven theming and feature flags so one codebase served every client.""",
            """Automated per-client signing, asset generation, and Play Store packaging end-to-end.""",
            """Unified brand tokens and vertical-specific flows into a single reusable app template.""",
        ),
        outcome = """Shipped 20+ client apps with delivery time cut 80% versus manual per-client builds.""",
        tags = listOf("Build systems", "Multi-tenant", "Automation"),
    ),
)

/** 4-group layout for the homepage skill cards. */
val skills = listOf(
    SkillGroup(
        group = "UI & Architecture",
        items = listOf("Jetpack Compose + Material 3", "MVVM + Clean Architecture", "MVI / single UiState", "Compose Multiplatform", "Dynamic theme engines"),
    ),
    SkillGroup(
        group = "Concurrency & Data",
        items = listOf("Kotlin Coroutines", "Flow / StateFlow / SharedFlow", "Room (SQLite, 24 migrations · 2 DBs)", "DataStore + WorkManager", "Retrofit + OkHttp"),
    ),
    SkillGroup(
        group = "Platform & Systems",
        items = listOf("Android SDK", "Location engineering (dead reckoning + Kalman)", "Foreground services", "Hilt / Dagger", "Firebase Crashlytics + Sentry + Mixpanel"),
    ),
    SkillGroup(
        group = "Security & Ops",
        items = listOf(
            "Android Keystore field-level encryption (AES-256)",
            "SSL pinning (9 domains, 5 SHA-256 pins)",
            "BiometricPrompt access gate",
            "EncryptedSharedPreferences / DataStore + Tink",
            "Fastlane CI/CD · AGP 9 · Gradle KTS",
            "Agentic workflows (Firebender, MCP)",
        ),
    ),
)

/** Granular 7-group layout for the résumé view — matches PDF structure for ATS coverage. */
val resumeSkills = listOf(
    SkillGroup(
        group = "UI",
        items = listOf("Jetpack Compose (~87% of UI-layer code)", "Material 3", "Compose-View interop", "Compose Multiplatform"),
    ),
    SkillGroup(
        group = "Architecture",
        items = listOf("Clean Architecture", "MVVM", "MVI", "Modular architecture", "Repository pattern", "Kotlin Multiplatform (KMP, building depth)"),
    ),
    SkillGroup(
        group = "Concurrency & DI",
        items = listOf("Kotlin Coroutines", "Flow", "StateFlow / SharedFlow", "Structured concurrency", "Hilt", "Dagger"),
    ),
    SkillGroup(
        group = "Data & Networking",
        items = listOf("Room (SQLite, 24 schema migrations across 2 databases)", "DataStore", "Retrofit", "OkHttp", "Ktor", "REST APIs"),
    ),
    SkillGroup(
        group = "Platform",
        items = listOf("Android SDK", "WorkManager", "Foreground Services", "Location / dead reckoning + Kalman filtering", "Firebase Crashlytics + Sentry", "Mixpanel"),
    ),
    SkillGroup(
        group = "Security",
        items = listOf("Android Keystore (AES-256)", "SSL pinning", "BiometricPrompt", "EncryptedSharedPreferences", "VAPT compliance"),
    ),
    SkillGroup(
        group = "Build, CI/CD & Tools",
        items = listOf("Gradle (Kotlin DSL)", "AGP 9", "Fastlane", "Git", "Play Store release management", "Android Studio", "Jira", "Figma", "Postman", "Firebender + MCP agentic workflows"),
    ),
)

val sharedFoundation = SharedFoundation(
    blurb = """Mileway and PaymentsLab aren't two isolated demos. They're two KMP apps sitting on a common foundation I built and maintain separately. Both pull in my own convention-plugin and MVI-base libraries as composite builds, so the build wiring and the unidirectional-state contract are written once and reused, exactly the platform discipline I bring to a codebase at scale.""",
    libs = listOf(
        SharedLib(
            name = "kmp-build-logic",
            url = "https://github.com/darkpandawarrior/kmp-build-logic",
            role = """Gradle convention plugins: one place that configures every KMP module's targets, Compose, lint and test wiring.""",
            usedBy = listOf("Mileway", "PaymentsLab"),
        ),
        SharedLib(
            name = "kmp-toolkit",
            url = "https://github.com/darkpandawarrior/kmp-toolkit",
            role = """A vendored KMP toolkit: the tiny (State, Event) → Effects mvi-core base (the reducer/store contract the payment state machine is built on), plus shared feedback/common modules.""",
            usedBy = listOf("Mileway", "PaymentsLab"),
        ),
    ),
)

/** Merged PRs to career-ops, a public OSS project (⭐60k+). */
val openSource = listOf(
    Contribution("santifer/career-ops", "feat(agent-inbox): queue requests for the next session", "https://github.com/santifer/career-ops/pull/1472", "merged", "2026-07-03"),
    Contribution("santifer/career-ops", "fix(dashboard): rewrite only the Status cell on status update", "https://github.com/santifer/career-ops/pull/1186", "merged", "2026-06-23"),
    Contribution("santifer/career-ops", "feat(providers): add Breezy HR provider", "https://github.com/santifer/career-ops/pull/1185", "merged", "2026-06-23"),
    Contribution("santifer/career-ops", "feat(providers): add BambooHR provider", "https://github.com/santifer/career-ops/pull/1141", "merged", "2026-06-20"),
)

/** Recent shipping timeline — "what I've built in the last few weeks". */
val recentGrowth = listOf(
    GrowthItem(
        date = "Jun 2026",
        title = "Kursi shipped",
        detail = """Full Kotlin Multiplatform social-deduction game across Android, iOS, desktop and web. Deterministic engine + ISMCTS AI.""",
    ),
    GrowthItem(
        date = "Jun-Jul 2026",
        title = "career-ops: public OSS contributions",
        detail = """Merged PRs to the public career-ops project (⭐60k+): ATS providers (BambooHR, Breezy HR), a dashboard status-cell fix, and an agent-inbox feature.""",
    ),
    GrowthItem(
        date = "Jun 2026",
        title = "Mileway: five platforms",
        detail = """Android, iOS, Wear OS, watchOS and Compose Desktop from one shared codebase, plus Glance/WidgetKit widgets and an iOS Live Activity. 159 Roborazzi tests green.""",
    ),
    GrowthItem(
        date = "Jul 2026",
        title = "Mileway: offline AI + policy engine",
        detail = """Retrieval-grounded chat over local data with voice I/O, a reimbursement-rate policy engine and a durable submit-outbox, still zero backend.""",
    ),
    GrowthItem(
        date = "Jul 2026",
        title = "PaymentsLab: 5 rails + 66 gateways",
        detail = """40-module KMP payments lab: payouts, mandates, card vault, marketplace Connect and a double-entry wallet ledger beyond one-shot pay-in, all MOCK_MODE-honest.""",
    ),
    GrowthItem(
        date = "Jul 2026",
        title = "Shared KMP foundation",
        detail = """Extracted kmp-build-logic (convention plugins) and kmp-toolkit (MVI base) as my own libraries, consumed by Mileway and PaymentsLab as composite builds.""",
    ),
    GrowthItem(
        date = "Jul 2026",
        title = "Mileway: super-profile & plugin platform (V24)",
        detail = """A plugin-composition registry (TILE/CAPABILITY/VALUE, FORCED>USER>PRESET>DEFAULT layering) driving four persona presets, plus delegation, verification, growth, membership and wallet/payout depth. Shipped, with a V25→V37 series (on-device intelligence, JWT auth, closeout hardening, home cards/advances, What's New) landed on top.""",
    ),
)

/**
 * The site's own interactive surfaces, in the React original's order.
 *
 * `to` is a path, not a promise: some of these rooms run in this build and some only run on the
 * React one. Nothing here says which. Callers ask the router (`routeOrNull`) instead, so a room that
 * ports later starts opening in-app on its own, and the three that already had ported stop being
 * advertised as "web only" on their own home page.
 */
val siteRooms = listOf(
    SiteRoom(
        to = "/compose",
        label = "Compose Playground",
        blurb = """Write Jetpack Compose, watch it recompose live in a phone frame: reactive state, animation, and an AI that writes it for you.""",
        tag = "live editor · AI",
    ),
    SiteRoom(
        to = "/lab",
        label = "The Lab Bench",
        // The count comes from the bench, not from prose. It used to read "Nine", which was the
        // React bench's size two ports ago; React now derives eleven and this build ships five.
        blurb = """$labCount experiments that prove the numbers: Dice.tech's production metrics plus personal builds, running in your browser.""",
        tag = "canvas · physics",
    ),
    SiteRoom(
        to = "/blueprint",
        label = "The Blueprint Room",
        blurb = """The whole portfolio as an infinite canvas: a real-time 3D fly-through, an ASCII render of the same scene, and a sketchable whiteboard.""",
        tag = "3D · WebGL",
    ),
    SiteRoom(
        to = "/map",
        label = "The 3D Storyboard",
        blurb = """The projects and the ideas that connect them, as a constellation you can orbit. Every edge is a real dependency.""",
        tag = "3D · graph",
    ),
    SiteRoom(
        to = "/forge",
        label = "The Particle Forge",
        blurb = """A few thousand particles, each spring-tied to a letter, parting around your cursor and snapping back. Physics on a canvas.""",
        tag = "canvas · interactive",
    ),
    SiteRoom(
        to = "/terminal",
        label = "The Terminal",
        blurb = """A faux shell you can actually type in: ls the site, cat a project, or hit the backtick key from anywhere.""",
        tag = "text · easter egg",
    ),
    // No game count in this blurb, deliberately: the corpus grows every time he plays, and this
    // string feeds the prerendered head tags as well as the tile.
    SiteRoom(
        to = "/chess",
        label = "The Board",
        blurb = """Seven years of games across lichess and chess.com, mined: the rating arc in 3D, where games end, a shifting repertoire, and a bot that plays like me.""",
        tag = "3d · engine",
    ),
    SiteRoom(
        to = "/weeb",
        label = "Weeb Central",
        blurb = """Years of anime and manga kept by hand, read as evidence: a status column with no word for quitting, a score scale whose bottom half is unused, and the seasons that aired while the list wasn't looking.""",
        tag = "corpus · data",
    ),
)
