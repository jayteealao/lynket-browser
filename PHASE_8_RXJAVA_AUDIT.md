# Phase 8: RxJava Removal - Audit Report

## Summary
- **RxJava 1.x imports**: 67
- **RxJava 2.x imports**: 36
- **Total files using RxJava 1.x**: 44
- **Total files with Observable/Single/Completable**: 79

## Conversion Strategy

### Priority 1: Core Data Layer (Repositories & Stores)
Convert all data repositories and stores from RxJava to Kotlin Flows:

#### Repositories (7 files)
1. `AppRepository.kt` + `DefaultAppRepository.kt`
2. `WebsiteRepository.kt` + `DefaultWebsiteRepository.kt`
3. `WebArticleRepository.kt` + `DefaultWebArticleRepository.kt`
4. `HistoryRepository.kt` + `DefaultHistoryRepository.kt`

#### Stores (9 files)
1. `AppStore.kt` + `AppSystemStore.kt` + `AppDiskStore.kt`
2. `WebsiteStore.kt` + `WebsiteDiskStore.kt` + `WebsiteNetworkStore.kt`
3. `WebArticleStore.kt` + `WebArticleCacheStore.kt` + `WebArticleNetworkStore.kt`
4. `HistorySqlDiskStore.kt`

### Priority 2: Core Services (2 files)
1. `TabsManager.kt` + `DefaultTabsManager.kt`

### Priority 3: ViewModels (11 files - Legacy)
1. `TabsViewModel.kt`
2. `ProviderSelectionViewModel.kt`
3. `PerAppSettingsViewModel.kt`
4. `HistoryFragmentViewModel.kt`
5. `HomeFragmentViewModel.kt`
6. `HomeActivityViewModel.kt`
7. `BrowsingArticleViewModel.kt`
8. `BrowsingViewModel.kt`

Note: ModernTabsViewModel and ModernProviderSelectionViewModel already converted in Phase 6

### Priority 4: Utilities & Helpers (8 files)
1. `RxSchedulerUtils.kt` - Remove entirely
2. `RxEventBus.kt` - Convert to Flow-based EventBus
3. `RxParser.kt` - Convert to suspend functions
4. `RxEpoxy.kt` - Remove or convert
5. `ArticlePreloader.kt` - Convert to coroutines
6. `WebHeadService.kt` - Remove RxJava usage
7. `Utils.kt` - Remove RxJava utility functions

### Priority 5: UI & Adapters (5 files)
1. `ArticleAdapter.kt`
2. `ProvidersAdapter.kt`
3. `PerAppListAdapter.kt`
4. Background loading strategies (3 files)

### Priority 6: Extension Functions & Interop (3 files)
These were created in Phase 6 - will be removed:
1. `AppRepositoryExtensions.kt`
2. `WebsiteRepositoryExtensions.kt`
3. `TabsManagerExtensions.kt`

### Priority 7: Misc Files (15+ files)
Activities, fragments, and other files with minor RxJava usage

## Conversion Patterns

### Observable<T> → Flow<T>
```kotlin
// Before
fun getData(): Observable<Data>

// After
fun getData(): Flow<Data>
```

### Single<T> → suspend fun
```kotlin
// Before
fun getData(): Single<Data>

// After
suspend fun getData(): Data
```

### Completable → suspend fun
```kotlin
// Before
fun doAction(): Completable

// After
suspend fun doAction()
```

### subscribe() → collect() or launch
```kotlin
// Before
observable.subscribe { data -> ... }

// After - in ViewModel
viewModelScope.launch {
    flow.collect { data -> ... }
}

// After - one-shot suspend
val data = repository.getData()
```

## Dependencies to Remove
After conversion:
- `io.reactivex.rxjava2:rxjava`
- `io.reactivex.rxjava2:rxandroid`
- `io.reactivex.rxjava2:rxkotlin`
- `com.jakewharton.rxbinding2:rxbinding`
- `kotlinx-coroutines-rx2` (RxJava interop)

## Estimated Effort
- Priority 1 (Data Layer): ~20 files - Core complexity
- Priority 2 (Services): ~2 files - Medium complexity
- Priority 3 (ViewModels): ~11 files - Medium complexity (RxJava already wrapped)
- Priority 4 (Utilities): ~8 files - Low-Medium complexity
- Priority 5-7 (UI & Misc): ~20 files - Low complexity

**Total: ~61 files to modify/convert**
