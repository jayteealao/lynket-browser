# Lynket Browser Rewrite: Modernization Plan

## 1. Executive Summary

This document outlines a strategic plan to rewrite the Lynket Android application. The current codebase, a mix of Java and Kotlin with legacy libraries (RxJava, Dagger 2, XML views), presents significant maintenance and feature development challenges. This rewrite will modernize the entire stack to be Kotlin-first, leveraging Jetpack Compose for the UI, Hilt for dependency injection, and Coroutines for concurrency. The primary goals are to improve performance, enhance maintainability, ensure compliance with the latest Google Play policies (targeting SDK 35), and establish a solid architectural foundation for future growth. The migration will follow a Strangler Fig pattern, incrementally replacing legacy features with new, modularized implementations behind feature flags to ensure a stable user experience, culminating in a full cutover and decommissioning of the old codebase.

### Acceptance Criteria
- [ ] Stakeholders approve the strategic direction and resource allocation.
- [ ] The final plan is published and accessible to the development team.
- [ ] Key performance indicators (KPIs) for success are defined and agreed upon.

## 2. Scope vs. Out-of-Scope

### In-Scope
- **Feature Parity:**
  - **Web Heads:** Re-implement the floating bubbles for background loading.
  - **Provider Model:** Retain compatibility with any Custom Tabs provider.
  - **Article Mode:** Preserve the local article extraction feature (via Crux or a modern equivalent).
  - **Core UI:** Minimize/Tabs screen, per-app settings, and AMP-handling logic.
- **Technical Modernization:**
  - 100% Kotlin codebase.
  - Complete UI rewrite using Jetpack Compose and Material 3.
  - Architectural migration to a multi-module, unidirectional data flow (UDF) pattern.
  - Full migration from RxJava to Kotlin Coroutines and Flow.
  - Dependency injection migration from Dagger 2 to Hilt.
  - Update all libraries to modern, maintained equivalents.
- **Compliance & Quality:**
  - Target Android SDK 35.
  - Fulfill all Google Play privacy and security requirements.
  - Achieve defined non-functional goals for performance, stability, and accessibility.
  - Retain the GPLv3 license.

### Out-of-Scope
- **New Major Features:** No new, large-scale user-facing features will be developed until the rewrite is complete.
- **Platform Expansion:** No cross-platform (e.g., iOS, Desktop) or alternative distribution ports (e.g., F-Droid) are planned.
- **Backend Changes:** This plan assumes no changes to any external services or APIs that Lynket currently relies on.
- **Tablet-Specific UI:** A dedicated, optimized tablet layout is not part of the initial rewrite scope but can be considered post-launch.

### Acceptance Criteria
- [ ] The final feature set for rewrite parity is documented and signed off.
- [ ] The out-of-scope list is reviewed and confirmed by project leads.
- [ ] The target Android SDK and compliance requirements are explicitly documented.

## 3. Architecture & Tech Decisions (ADR-lite)

| Decision Area | Chosen Tool/Pattern | Status | Rationale | Trade-offs |
| :--- | :--- | :--- | :--- | :--- |
| **UI Toolkit** | Jetpack Compose | **Final** | Declarative, modern, better performance, and aligns with industry best practices. Simplifies complex UI development. | Requires learning curve for team members unfamiliar with Compose. Min SDK 21 requirement is a factor. |
| **Architecture** | Multi-module, MVVM/UDF | **Final** | Enforces separation of concerns, improves build times, enables parallel development, and facilitates code reuse. | Initial setup is more complex than a monolithic approach. |
| **Dependency Injection**| Hilt | **Final** | Simplifies Dagger 2 setup, reduces boilerplate, and is the recommended DI framework for modern Android development. | Less flexible than pure Dagger; adds a small amount of annotation processing overhead. |
| **Concurrency** | Kotlin Coroutines & Flow | **Final** | Native Kotlin solution for asynchronous programming, simpler than RxJava, and integrates seamlessly with Jetpack libraries. | Requires careful management of CoroutineScopes and structured concurrency to prevent leaks. |
| **Navigation** | Navigation-Compose | **Final** | Type-safe, integrated solution for Compose-based apps that handles lifecycle and state automatically. | Can be complex for deeply nested or dynamic navigation graphs. |
| **Persistence** | Room | **Recommended** | Provides a robust, compile-time checked abstraction over SQLite. Integrates well with Coroutines/Flow. | SQLDelight is a valid alternative if more control over SQL is needed, but Room is sufficient for current needs. |
| **Networking** | Retrofit | **Recommended** | Industry-standard, feature-rich, and stable HTTP client for Android. | Ktor is a strong Kotlin-first alternative, but Retrofit has a larger community and more extensive documentation. |
| **Build System** | Gradle KTS | **Final** | Offers improved type safety and IDE support compared to Groovy, aligning with the Kotlin-first approach. | Can have a steeper learning curve and slightly slower configuration times than Groovy scripts. |

### Acceptance Criteria
- [ ] All major technology choices are documented with rationale.
- [ ] The chosen architecture is diagrammed and reviewed with the team.
- [ ] A sample "golden path" PR is created to demonstrate the new stack in action.

## 4. Incremental Migration Strategy

The rewrite will employ a **Strangler Fig** pattern. The legacy application will remain fully functional while new features are built in parallel within new modules. A feature flag system will be used to route users between the legacy implementation and the new Compose-based implementation on a per-feature basis.

1.  **Parallel Modules:** New features will be developed in separate, independent modules (e.g., `:feature-webheads`, `:feature-tabs`).
2.  **Feature Flags:** A remote or local feature flag mechanism (e.g., Firebase Remote Config, or a simple DataStore-based solution) will control which implementation (legacy or modern) is visible to the user.
3.  **Legacy Entry Points:** The existing Activities and Fragments will be preserved as entry points. They will act as routers, delegating to either the old View-based UI or a new `ComposeView`/Compose Activity based on the feature flag status.
4.  **Telemetry:** Robust analytics will be added to both legacy and modern paths to monitor crash rates, performance, and user behavior, ensuring the new implementation meets or exceeds the quality of the old one before a full switch.

### Acceptance Criteria
- [ ] The feature flag mechanism is implemented and tested.
- [ ] A clear plan for routing users between legacy and modern code is defined.
- [ ] Telemetry for comparing the two implementations is in place.

## 5. Wave-by-Wave Task Checklists

### Wave 0 – Foundations
*Goal: Establish the new architecture, build system, and core modules.*
- [ ] Convert all `build.gradle` files from Groovy to Gradle KTS (`*.gradle.kts`).
- [ ] Configure `build-logic` with convention plugins for Android application, library, and Compose modules.
- [ ] Set up the new multi-module structure: `app`, `core:data`, `core:domain`, `core:design-system`.
- [ ] Configure the root `build.gradle.kts` with the Compose BOM and version catalog (`libs.versions.toml`).
- [ ] Set up GitHub Actions for CI: run linter (detekt/ktlint), unit tests, and `assembleRelease`.
- [ ] Add Hilt for dependency injection and create a sample injection path in the `app` module.
- [ ] Create the `design-system` module with Material 3 theming, typography, colors, and sample components.
- [ ] Create the `data` module with skeletons for Retrofit/Ktor, Room, and DataStore.
- [ ] Configure build variants (`dev`/`prod`) with distinct application IDs and secrets handling (e.g., using `secrets-gradle-plugin`).

### Wave 1 – Domain & Data
*Goal: Migrate data sources and business logic to the new architecture.*
- [ ] Define domain models (e.g., `WebPage`, `Tab`, `Provider`) that are clean and independent of the Android framework.
- [ ] Create repository interfaces in the `:domain` module and implementations in the `:data` module.
- [ ] Implement fakes for all repositories for use in unit tests and UI previews.
- [ ] Migrate existing persistence (SharedPreferences, PaperDB) to Jetpack DataStore and Room.
- [ ] Write and verify database migration tests for Room.
- [ ] Replace all RxJava usage in the data and domain layers with Kotlin Coroutines and Flow.
- [ ] Provide Rx-to-Flow and Flow-to-Rx adapters for backward compatibility during the transition.

### Wave 2 – UI Rewrite (Compose)
*Goal: Rebuild all primary screens and user-facing controls in Jetpack Compose.*
- [ ] Create skeleton Compose screens for: Main Tabs UI, Article Mode, Settings, and Provider Selection.
- [ ] Implement a `ComposeView`-based strategy to embed these new screens into legacy Activities/Fragments.
- [ ] Set up Navigation-Compose to handle navigation between the new Compose screens.
- [ ] Implement a full accessibility pass on all new screens (TalkBack, contrast ratios, touch targets).
- [ ] Ensure dynamic color and dark theme support are correctly implemented using the new design system.
- [ ] Configure and generate Baseline Profiles to optimize app startup and screen performance.

### Wave 3 – Platform Interop & Features
*Goal: Re-implement the core, platform-dependent features.*
- [ ] Re-implement Web Heads. Evaluate modern Android APIs (e.g., Bubbles API) vs. a custom `SYSTEM_ALERT_WINDOW` implementation for backward compatibility.
- [ ] Re-implement the background loading mechanism, ensuring it works reliably on modern Android versions.
- [ ] Preserve the recents/Document-API behavior for tab management or find a modern, sustainable equivalent.
- [ ] Rework the Article Mode pipeline. Verify the existing Crux dependency or find a modern alternative for local content extraction.

### Wave 4 – Hardening & Compliance
*Goal: Ensure the app is stable, performant, and compliant with modern standards.*
- [ ] Set `targetSdk` to 35 and rigorously test for any behavior changes.
- [ ] Conduct a full review of permissions, privacy manifests, and data handling practices.
- [ ] Profile the application for performance bottlenecks (startup time, jank, memory usage).
- [ ] Enable and test with StrictMode to identify and fix disk/network violations on the main thread.
- [ ] Configure and verify network security settings (e.g., certificate pinning).
- [ ] Set up crash/ANR reporting dashboards (e.g., Firebase Crashlytics) and define an error taxonomy.

### Wave 5 – Cutover & Decommission
*Goal: Remove the legacy code and release the fully rewritten application.*
- [ ] Enable all feature flags by default, making the modern implementation the primary user experience.
- [ ] After a monitoring period, remove all legacy Activities, Fragments, XML layouts, and Dagger/RxJava code.
- [ ] Clean up all feature flags and routing logic.
- [ ] Execute a full release checklist: update version codes, sign release builds, and run all regression tests.
- [ ] Publish the final artifact to the Google Play Console.
- [ ] Establish a post-release monitoring plan and a rollback strategy in case of critical issues.

## 6. Testing Strategy

| Test Type | Location | Coverage Target | Command to Run |
| :--- | :--- | :--- | :--- |
| **Unit Tests** | `*/src/test` | ≥ 80% for domain/data layers | `./gradlew testDebugUnitTest` |
| **Instrumentation** | `*/src/androidTest` | Critical user flows | `./gradlew connectedDebugAndroidTest` |
| **Compose UI Tests** | `*/src/androidTest` | Key screens and components | `./gradlew connectedDebugAndroidTest` |
| **DB Migration** | `core/data/src/androidTest` | All schema versions | `./gradlew connectedDebugAndroidTest` |

### Acceptance Criteria
- [ ] Unit test coverage targets are met for all new data and domain modules.
- [ ] Every new Compose screen has at least one smoke test to verify it renders without crashing.
- [ ] All database schema changes are covered by a passing migration test.

## 7. CI/CD & DevEx

- **CI Provider:** GitHub Actions.
- **Workflows:**
  - **Pull Request:** On every PR, run: `lint`, `detekt`, `./gradlew testDebugUnitTest`, `./gradlew assembleDebug`.
  - **Main Merge:** On merge to `main`, run all PR checks plus `./gradlew connectedDebugAndroidTest` on emulators.
  - **Release:** A manually triggered workflow to build, sign, and upload a release APK/AAB to the Play Store.
- **Emulator Matrix:** Run instrumentation tests on API levels 26, 30, and 35 to cover a range of platform behaviors.
- **Caching:** Enable Gradle dependency caching and build caching on CI to speed up build times.
- **Dependency Management:** Use Dependabot or Renovate to automatically create PRs for dependency updates.

### Acceptance Criteria
- [ ] The PR workflow is implemented and required for all branches.
- [ ] The emulator testing matrix is configured and running successfully.
- [ ] A release workflow is created and tested.

## 8. Data Migration & Backward Compatibility

- **Settings:** User preferences stored in SharedPreferences will be migrated to Jetpack DataStore using a `SharedPreferencesMigration`. This will be a one-time, automatic migration on the first launch of the updated app.
- **History/Tabs:** Any persisted tab or history information will be migrated via Room database migrations.
- **Failure Plan:** If a migration fails, the app will clear the corrupted data and fall back to default settings. A log will be sent to the crash reporting service to diagnose the issue. No rollback is planned for data corruption, as the data is largely ephemeral.

### Acceptance Criteria
- [ ] The DataStore migration for user settings is implemented and tested.
- [ ] Room migration tests are in place for all schema changes.
- [ ] The data corruption fallback mechanism is tested.

## 9. Risks & Mitigations

| Risk | Probability | Impact | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| **Web Heads Parity on Modern Android** | High | High | Investigate modern APIs (Bubbles) early. If they are insufficient, create a compatibility layer for the legacy `SYSTEM_ALERT_WINDOW` approach and clearly communicate permission requirements to the user. |
| **Legacy Build Is Unstable** | High | Medium | The legacy build is currently not compiling. This prevents direct A/B testing. **Mitigation:** The rewrite will proceed based on feature knowledge and code analysis. The first stable release of the rewrite will become the new baseline. |
| **Article Mode Extraction Accuracy** | Medium | Medium | The Crux library is not actively maintained. **Mitigation:** Evaluate the library's performance and consider replacing it with a modern alternative like `Mozilla Readability`. |
| **Behavior Changes in SDK 33-35** | High | Medium | Dedicate specific testing time during Wave 4 to a comprehensive review of Android behavior changes related to permissions, background execution, and privacy. |
| **DI Migration Complexity** | Low | High | Hilt is designed for this purpose. **Mitigation:** Maintain Dagger 2 alongside Hilt during the transition, using adapters where needed. Remove the old Dagger scaffolding only after all components have been migrated. |

### Acceptance Criteria
- [ ] All high-impact risks have a documented and reviewed mitigation plan.
- [ ] The project team acknowledges the risk of proceeding without a buildable legacy version.

## 10. Timeline & Milestones

| Wave | Start Date | End Date | Owner | Acceptance Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **Wave 0: Foundations** | TBD | TBD | Placeholder | CI is green; new module structure is in place; Hilt and design system are functional. |
| **Wave 1: Domain & Data** | TBD | TBD | Placeholder | All data logic is migrated to repositories with full test coverage. |
| **Wave 2: UI Rewrite** | TBD | TBD | Placeholder | All major screens are implemented in Compose behind feature flags. |
| **Wave 3: Features** | TBD | TBD | Placeholder | Web Heads and Article Mode are fully functional in the new implementation. |
| **Wave 4: Hardening** | TBD | TBD | Placeholder | App targets SDK 35 and meets all performance and quality benchmarks. |
| **Wave 5: Cutover** | TBD | TBD | Placeholder | Legacy code is removed; the rewritten app is released to production. |

## 11. Resourcing & Estimates

| Wave | Estimated Person-Weeks | Required Skills | Build-vs-Borrow |
| :--- | :--- | :--- | :--- |
| **Wave 0** | 3 | Android, Gradle KTS, CI/CD | **Build:** Convention plugins. |
| **Wave 1** | 4 | Android, Kotlin, Coroutines, Room | **Borrow:** Room, DataStore. |
| **Wave 2** | 6 | Jetpack Compose, UI/UX | **Borrow:** Navigation-Compose. |
| **Wave 3** | 5 | Android Platform APIs, System UI | **Build:** Web Heads logic. **Borrow:** Article parser. |
| **Wave 4** | 3 | Android, Performance Tuning, Security | **Borrow:** Firebase Crashlytics. |
| **Wave 5** | 2 | Android, Release Management | **N/A** |
| **Total** | **23** | | |

## 12. Quality Gates & Definition of Done

### Definition of Done (Per Feature)
- Feature is implemented in the new architecture.
- Unit and instrumentation test coverage meets targets.
- Passes accessibility checks.
- Deployed behind a feature flag and monitored with telemetry.
- Zero new crashes or performance regressions.

### Definition of Done (Per Wave)
- All tasks in the wave checklist are complete.
- All relevant quality gates are met.
- A stakeholder demo has been completed and approved.

### Quality Gates
- **Performance:** Cold start p50 ≤ 900 ms; ANR rate ≤ 0.47%.
- **Stability:** Crash-free sessions ≥ 99.8%.
- **Test Coverage:** Unit test coverage ≥ 80% for new data/domain code.
- **Compliance:** All Target SDK 35 requirements are met.

## 13. Appendix

### Glossary
- **ADR:** Architecture Decision Record.
- **BOM:** Bill of Materials. A single dependency that manages the versions of a set of libraries.
- **UDF:** Unidirectional Data Flow. An architectural pattern where state flows down and events flow up.
- **Web Heads:** The legacy name for Lynket's floating browser bubbles.

### Key Commands
- **Run All Tests:** `./gradlew testDebugUnitTest connectedDebugAndroidTest`
- **Run Linter:** `./gradlew detekt`
- **Assemble Release Build:** `./gradlew assembleRelease`

### Links
- **GitHub Repository:** [arunkumar9t2/lynket-browser](https://github.com/arunkumar9t2/lynket-browser)
- **Target SDK 35 Behavior Changes:** [Official Documentation](https://developer.android.com/about/versions/15/behavior-changes-all)
- **Crux Library:** (Link to be added if evaluation is positive)