# fa2 全仓库 Code Review 与防腐方案

审查日期：2026-07-10

## 结论与整改结果

初次审查发现的 13 组问题已经全部完成整改。当前依赖方向恢复为 `ui -> data` 或 `ui -> domain -> data`；`data` 不再反向引用 `domain`，`data/fa/*` 包依赖图由测试保证无环，通用 UI/viewer API 不再引用 feature 实现。签名不属于本次 bug 或整改范围，未作调整。

这次没有引入一层层空壳 use case，也没有做大爆炸重写；修复集中在真实边界、状态语义、资源所有权和可自动验证的约束上。下面的 finding 正文保留为“整改前诊断与设计依据”，不是当前仍然存在的问题清单。

## 整改状态

| Finding | 状态 | 落地结果 |
| --- | --- | --- |
| A01 Cookie 信任边界 | 已修复 | 新增统一 `FaOriginPolicy`；凭据 transport 独立；发送、redirect、接收三侧校验；外部媒体不能读写 FA Cookie。 |
| A02 data/domain 循环 | 已修复 | source/blocklist contract 下沉 data；实现改为单向依赖；架构测试扫描 FQN；`data/fa` 资源包新增自动环检测。 |
| A03 虚假 Loading | 已修复 | data 统一终态 `ResourceResult`；Loading/refreshing/appending 收回 UI `UiPageState`；旧 `PageState` 删除。 |
| A04 吞协程取消 | 已修复 | 引入 `runSuspendCatching` 并清理 suspend 裸 `runCatching`/`catch(Throwable)`；取消语义有契约测试。 |
| A05 同步 i18n/启动阻塞 | 已修复 | `appString` 改为 suspend；Compose 使用 `stringResource`；错误改为结构化 code；Android/desktop bootstrap 异步化。 |
| A06 UI feature 环与巨型 workflow | 已修复 | feature 环已解除；抽出 navigation、viewer API/adapters、translation support 与 OCR、文本翻译、附件、social、prefetch controller；context 的纯快照归并逻辑独立为 reducer。workflow 从约 2,039 行降至 525 行，context screen model 从约 868 行降至 651 行。 |
| A07 data 资源环 | 已修复 | 新建 `listing`/`parsing` 共享能力；gallery/favorites 和 submission/gallery 改为窄失效/存储接口；自动检测包依赖环。 |
| A08 死依赖/测试 seam 污染 | 已修复 | 删除未使用注入；repository 构造依赖改为 required contract；social repository 依赖窄 `SocialActionSource`，测试使用显式 fake；能力残缺的 DataSource backend 与不可达 fallback 删除。 |
| A09 设置事务与并发覆盖 | 已修复 | Preferences 单次 edit；service 在同一 mutex 内 read-transform-save-publish；secret 写失败回滚；新增并发测试。 |
| A10 非原子下载覆盖 | 已修复 | desktop 使用临时 sibling + atomic replace/fallback；Android SAF 先写临时文档，成功后再替换；新增 Android host test。 |
| A11 下层泄漏中文 UI 文案 | 已修复 | domain/data 输出结构化 stage/reason；UI 负责本地化；parser 的“未知”哨兵删除。 |
| A12 CI/发布无门禁 | 已修复 | 新增统一 `qualityCheck` 与 PR workflow；release workflow 以独立、只读权限的 quality job 前置阻断 build job，仅发布 job 获得 `contents: write`；`release.sh` 不承担质量检查；Android host test 纳入 check。 |
| A13 其他防腐项 | 已修复 | closeable 统一关闭、日志统一、manifest 历史兜底删除、expect/actual 参数统一、版本号只由 release 管理、禁止项目内 wildcard import；Room 升至 2.8.4，恢复 KSP2 schema export 参数且不再产生构建警告。 |

## 整改后验证

- `nix develop -c ./gradlew qualityCheck --no-daemon --stacktrace`：最终验收通过，93 tasks，耗时 4 分 36 秒；覆盖 shared、Android host test、Android app 编译/lint、desktop app 编译/测试与架构边界测试。
- shared desktop：351 tests，0 failure；Android host：1 test，0 failure；desktop app：2 tests，0 failure。删除的 1 项是随 `DataSourceSocialActionBackend` 一并移除、只验证该测试便利实现的测试。
- 静态边界复核：生产代码无 `runBlocking`；data 无 `domain` 引用；无旧 `PageState`/`ResourceResult.Loading`；无项目内 wildcard import。
- 中英文资源各 379 个 string key，集合一致；Room schema export 编译无警告。
- `git diff --check`：通过。
- 用户原有 `gradle.properties` 中 `APP_VERSION_CODE=115` 保持不变；Android 签名配置未修改。

## 整改新增文件审计

本轮新增文件也已纳入审查，而不是只保留在“整改前基线”之外：

| 能力 | 文件与结论 |
| --- | --- |
| FA 凭据边界 | `FaOriginPolicy.kt`、`FaCredentialedHttpDataSource.kt`：统一 origin 校验，凭据实现与 core contract 单向依赖。 |
| data 共享能力 | `data/fa/listing/*`、`data/fa/parsing/*`：承接跨资源 listing/parser 能力，资源包依赖图无环。 |
| data/domain ports | submission series、user/watchlist recommendation、blocklist contract：接口位于 data，domain 不再被 data 反向实现。 |
| 终态资源结果 | `ResourceResult.kt`、`ResourceResultLogSummary.kt`：只表达终态；Loading 留在 UI。 |
| UI 公共边界 | `ui/navigation/*`、`ui/submissionviewer/api/*`、`adapters/*`、`ui/translation/*`、`ui/state/*`：API/复用包不依赖 feature 实现。 |
| submission controllers | `SubmissionImageOcrController.kt`、`SubmissionTextTranslationController.kt`、`SubmissionAttachmentTextController.kt`、`SubmissionSocialActionController.kt`、`SubmissionDetailPrefetchController.kt`：各自拥有状态转换与 Job 生命周期，workflow 只编排。`SubmissionContextSnapshotReducer.kt` 承接无副作用的分页合并、选择与 viewport 归一化。 |
| 协程安全 | `CoroutineResult.kt`：统一重抛 cancellation；有对应测试。 |
| 平台测试 | `PlatformUrlDownloaderAndroidTest.kt`：使用独立 Android host source-set tree，不污染 commonTest actual。 |
| CI | `.github/workflows/quality.yml`：PR/main 运行统一 `qualityCheck`；Android release workflow 使用独立 quality job，成功后才启动 build/release job。 |
| 决策文档 | `docs/decisions/missing-contact-icons.md`：原根目录临时说明归档到 decisions。 |

## 审查范围与方法（整改前基线）

- 已纳入 460 个受 Git 跟踪的 Kotlin/Gradle Kotlin 文件（约 59,981 行）：`commonMain` 335、`androidMain` 13、`desktopMain` 16、`commonTest` 77、桌面测试 14、两个应用入口 9、Gradle 脚本 6。
- 同时审查了 Gradle/version catalog、Android manifest/ProGuard、GitHub Actions、release shell、Nix、lefthook、资源清单和本地持久化 schema。
- 104 个由 SymbolCraft 生成且被 `.gitignore` 排除的 Kotlin 文件不按手写源码评价；二进制图片、fixture 文档和 HTML 只检查归属及使用方式。
- 执行 `nix develop -c ./gradlew :shared:desktopTest --no-daemon`：通过，22 tasks，耗时约 30 秒。
- 执行 `nix develop -c ./gradlew check --no-daemon`：通过，88 tasks，耗时约 1 分 52 秒；覆盖 shared/desktop tests、Android debug compile 与 lint。Android unit test 为 `NO-SOURCE`。
- 中英文 string key 各 374 个，集合一致。
- 工作区原有 `gradle.properties` 的 `APP_VERSION_CODE=115` 改动未触碰。

严重度：P0 是明确的凭据/数据边界缺陷；P1 应在继续扩功能前修复；P2 纳入近期防腐迭代；P3 随迁移清理。

## A01 / P0：Cookie 信任边界缺失，可泄漏或被外部响应污染

涉及：`data/fa/core/FaHttpClient.kt:43-68`、`data/fa/social/SocialActionHttpTransport.kt:52-59,93-126`、`data/fa/social/SocialActionEndpoint.kt`、`data/fa/media/CachedDownloadHttpClient.kt:82-121`、`data/fa/session/FaCookiesStorage.kt:88-112`、`data/fa/submission/SubmissionPageParser.kt:229-244`、`data/fa/user/UserParser.kt:156-175`。

- `FaHttpClient.get(url)` 和 social GET 会向传入的任意 URL 附加完整 FA Cookie，没有在凭据边界处强制 HTTPS + FA host。action URL 来自 HTML；parser 只做相对转绝对，不验证 origin。
- cached download client 发送 Cookie 时检查了 FA host，但对**任何外部图片/附件响应**都执行 `mergeSetCookieValues`。外部服务器可把任意 cookie name/value 写入扁平 FA cookie map，之后该值会被发给 FA。
- Cookie 存储丢弃 Domain/Path/Secure/SameSite 等属性，因此它不能自行判断某个响应是否有权修改 FA Cookie。

架构级修复：建立唯一的 `FaOriginPolicy`/typed URL 边界，而不是在三个 client 各写一段字符串判断。

- `FaPageUrl`、`FaActionUrl` 只能由校验过的 `https://*.furaffinity.net`（更严格时只允许 `www`）构造；repository/endpoint 接受 typed URL，不接受裸 `String`。
- `FaCredentialedTransport` 是唯一能读取 `FaCookiesStorage` 的组件，并在发送和接收两侧检查 origin。外部媒体使用无凭据 transport，绝不能写 FA cookie jar。
- parser 对 watch/fav/unfav/分页链接做 allowlist（scheme、host、预期 path）；不合法链接作为 parse anomaly 返回，不交给网络层。
- 添加恶意绝对 action URL、跨域 redirect、外部 `Set-Cookie`、HTTP downgrade 测试。

## A02 / P1：概念分层已经形成 `data <-> domain` 循环，架构测试还会误报绿色

涉及：

- `data/fa/submission/SubmissionRepository.kt:19`
- `data/fa/user/UserRepository.kt:15`
- `data/fa/watchlist/WatchlistRepository.kt:13-14`
- `domain/watchrecommendation/WatchRecommendationService.kt:4-9`
- `di/DataModule.kt`、`di/DomainModule.kt`
- `architecture/ArchitectureBoundaryTest.kt:47-75`

三个 data repository 用全限定名实现 domain 中的 source 接口。边界测试只扫描以 `import me.domino.fa2.domain` 开头的行，因此这些依赖绕过测试。与此同时 domain 又依赖 data model、settings、translation 和 local blocklist，真实依赖图是双向的。

这不是改 import 能修好的问题。按本仓库既定方向，应采用以下统一规则：

```text
ui feature ───────> data-api <────── data-impl
    │                   ▲
    └──> domain feature ┘

app/di ──> ui feature + domain feature + data-impl
```

- repository contract、domain 所需 source contract、稳定资源模型和终态结果放在 `data-api`（概念包可先为 `data/*/contract`，最终独立 Gradle module）。
- HTTP/parser/cache/Room/KSafe/provider 放 `data-impl`；实现只依赖 `data-api`，不依赖 domain。
- domain 只依赖 `data-api`，输出自己的业务结果；不得引用 `data/local/*` 具体位置。
- `WatchRecommendationBlocklist` 从 `data/local/watchrecommendation` 移到 data contract；持久化实现留在 local。
- `di` 是唯一同时看见 impl、domain、UI 的 composition root。

边界测试先改为扫描整个文件中的 FQN（排除 package 声明和注释仍需谨慎），随后尽快以 Gradle module dependency 替代字符串测试。物理模块才是可靠防腐层。

## A03 / P1：`PageState` 混合“UI 过程状态”和“一次性数据结果”，制造大量不可达分支

涉及：`data/model/PageState.kt`、`data/fa/core/CachedPageStoreSupport.kt`、`data/fa/core/StoreSupport.kt`，以及所有 repository/domain/UI 对 `PageState.Loading` 的 `when`。

数据层的单次 suspend load 会等待首个非 Loading 状态；data 中没有一次性 fetch 正常返回 `PageState.Loading` 的路径。可是同一个 sealed type 又被 Flow 用作加载过程，导致几十处调用方必须处理实际上不会到达的 `Loading`，甚至 fetcher 遇到它直接抛 `Unexpected loading state`。这是用户所说“完全不会到达的兜底”的系统性来源。

架构级修复：

- data-api 定义终态 `ResourceResult<T> = Success | AuthRequired | Challenge | MatureBlocked | Failure`，不含 Loading。
- repository 的 suspend API 只返回 `ResourceResult<T>`；stream API 返回 `Flow<ResourceSnapshot<T>>` 或让 UI 自己从 coroutine 生命周期表达 loading。
- UI screen state 持有 `loading/refreshing/appending`；这些是交互状态，不进入 data result。
- 迁移时先新增终态类型并适配 cache core，再逐 feature 删除 `PageState.Loading` 分支，最后删除旧类型。不要同时复制两套永久共存。

## A04 / P1：协程取消被当作普通失败吞掉

涉及：`data/translation/SubmissionTranslationChunkTranslator.kt:27-57`、`data/taxonomy/FaTaxonomyRepository.kt:37-43`、`ui/i18n/search/SearchUiLabelsRepository.kt:103-113`、`ui/pages/more/MoreScreenModel.kt:39-68,81-97`、settings/about/watch-recommendation/user blocklist screen models、`ui/pages/submission/pager/SubmissionScreenSources.kt:57-64`、`SubmissionScreenWorkflow.kt:1764-1780` 等。

Kotlin `runCatching` 捕获 `CancellationException`。多处在 `launch` 或 suspend service 中把 cancellation 转成 Error/Failure 并继续写 UI 状态；`translateImageOcrBlocks` 的 `catch(Throwable)` 也没有先重抛 cancellation。这会造成离开页面后仍出现错误反馈、取消操作变失败、上层 timeout/structured concurrency 失效。

全局修复：提供最小的 coroutine-safe result helper（失败分支遇 `CancellationException` 必须重抛），并禁止在 suspend 路径直接使用裸 `runCatching`/`catch(Throwable)`。用静态规则或架构测试守住；为 translation、taxonomy load、screen disposal 增加 cancellation contract tests。

## A05 / P1：同步资源读取成为 UI 全局隐性阻塞点

涉及：`ui/i18n/AppResourceAccess.kt:8-16` 及 21 个调用文件（137 个 `appString` 调用），Android/desktop 启动入口的 `runBlocking`。

`appString` 每次通过 `runBlocking { getString(...) }` 把 suspend 资源 API 强制同步化，screen model 和 Compose 事件在主线程调用它。该捷径把资源加载、线程阻塞和业务状态耦合在一起，也催生了只在少数位置出现的硬编码英文 fallback。Android `Application.onCreate` 还同步加载整个 settings store。

架构级修复：

- screen model/domain/data 只产生 `UiMessage.Resource(id,args)` / `UiMessage.Plain` 或结构化 error code；Composable 在 `stringResource` 上下文解析。
- 若非 Composable 必须解析，注入可挂起的 `StringResolver` 并在已有 coroutine 内调用，禁止内部 `runBlocking`。
- 启动改为异步 bootstrap state（settings/session/taxonomy 并发加载），根 UI 显示 boot state；不要阻塞 `Application.onCreate`。
- 资源 fallback 只处理业务上允许缺省的动态 catalog；编译期 `StringResource` 不用逐点硬编码英文掩盖资源系统错误。

## A06 / P1：UI 不是 feature 边界，submission viewer 已成为隐形平台层

涉及：`ui/pages/submission/pager/SubmissionScreenWorkflow.kt`（2,070 行、约 50 个方法、15 个可变容器/job）、`SubmissionContextScreenModel.kt`（891 行）、`SubmissionSourceAdapters.kt`（737 行）、`ui/app/navigation/MainRouteScreen.kt`（644 行），以及 browse/feed/search/user 与 submission 的双向 imports。

当前 submission pager 同时负责详情加载、翻页缓冲、预取、收藏、tag block、附件解析、description 翻译、OCR、OCR 编辑/合并/翻译、导出、toast 和资源文案。browse/search/feed/user 都依赖其 context/adapters；submission route 又反向依赖 browse/search/user route，形成 feature 环。文件拆分本身不会消除这个环。

目标 UI 拓扑：

```text
app-shell/navigation
  ├─ feature-feed ───────┐
  ├─ feature-browse ─────┤
  ├─ feature-search ─────┼──> feature-submission-viewer-api
  ├─ feature-user ───────┘              ▲
  └─ feature-submission-viewer-impl ─────┘

ui-components ──> 仅 core-ui/model，不依赖任何 feature
```

- 抽出 `submission-viewer-api`：navigation request、source descriptor、viewport/pagination contract；调用 feature 不导入 pager 实现类型。
- viewer 内按独立状态机拆成 detail、pager buffer、translation、attachment、OCR、social action controllers；`SubmissionScreenModel` 只编排并组合 state。
- route 跳转使用 app-level navigation contract/event，不让 submission feature 直接构造 Browse/User/Search screen。
- `ui/components/html/TranslatableHtmlBlockSection.kt` 和 `ui/components/waterfall/SubmissionWaterfall.kt` 当前反向依赖 submission 页面类型；移回 feature 或改成 slot/通用模型。`ui/components` 必须保持叶子依赖。
- 每个 feature 对外只暴露 `api` 包；实现 package internal。用 module/build rule 禁止 feature-to-feature impl import。

## A07 / P2：data 资源切片存在循环，公共解析能力放错位置

涉及：`gallery <-> favorites`、`submission <-> gallery`，以及 feed/gallery/user 对 `submission` parser helper 的依赖。

`TagBlockSettings`、image tags、avatar 等其实是 submission listing 解析能力，却挂在 submission detail feature；GalleryPageCache 直接持有 FavoritesDataSource；SubmissionRepository 又直接失效 GalleryPageCache。目录看起来按资源切片，真实职责却是交叉的。

修复方式：把 listing card parsing 放到 `data/fa/listing`（或 core 中明确命名的能力包）；cache invalidation 用 `CacheInvalidator`/事件接口，由 DI 组合涉及的 cache，不让 repository 互相引用具体 cache；gallery/favorites 共享一个 section-aware listing resource，而不是双向调用。

## A08 / P2：测试便利性污染生产构造器，且存在成批死依赖

涉及：9 个 ScreenModel 中从未读取的 `settingsService`/`systemLanguageProvider`；`JournalRepository(journalStore: JournalPageCache? = null)` 与 `open` repository；`SocialActionEndpoint.backend: SocialActionBackend?`。

- 18 个注入字段在各自文件只出现一次，却仍由 Koin 装配，属于确定死代码。
- JournalRepository 为便于 fake subclass 允许无 store 构造，生产方法再 `requireNotNull`；这是测试 seam 反向塑造生产 API。
- SocialActionEndpoint 只有两个 public constructor，都会提供非空 backend，private constructor 又不可从外部传 null，因此两处 `No HTTP backend` fallback 不可达。

修复方式：UI/domain 依赖小 contract，测试实现 contract；production class 保持 final、required dependency。删除死注入和不可达 fallback，并新增 DI graph verification，避免“能注入但从不使用”。

## A09 / P2：设置保存不是单次事务，并发字段更新可丢失

涉及：`data/settings/AppSettingsService.kt`、`data/local/settings/AppSettingsStorage.kt`、`data/local/KeyValueStorage.kt`。

保存一个 AppSettings 会逐 key 执行二十余次 DataStore edit，中途取消/崩溃会留下混合版本；各 `updateX` 在 mutex 外读取 snapshot，再进入 `updateSettings`，并发更新可能后写覆盖先写。

修复方式：`AppSettingsStore.save` 对 Preferences 使用一次 `edit` 写入完整 snapshot；service 提供 `update(transform: (AppSettings)->AppSettings)` 并在同一 mutex 内 read-normalize-save-publish。secret vault 与 Preferences 无法原子时，明确提交顺序和恢复协议/版本号。

## A10 / P2：下载覆盖非原子，失败会破坏已有文件

涉及：Android/desktop `PlatformUrlDownloader.*.kt`。

Android 先删除同名文件再创建/写入；desktop 直接 truncate 目标。创建或写入失败会丢失旧文件并可能留下半文件。应写临时 sibling，flush/close 成功后 replace；Android SAF 尽可能 create temp + rename，不能原子时至少保留旧文件直到新内容完成。

## A11 / P2：domain/data 向 UI 泄漏中文展示文案

涉及：`domain/attachmenttext/*Parser*.kt`、`AttachmentTextProgressReporter.kt`、`data/fa/auth/AuthDataSource.kt`。

附件解析进度把中文 `stageLabel/message/currentItemLabel` 作为 domain model 输出并由 UI 直接展示，英文 UI 仍会显示中文。AuthDataSource 也直接生成中文用户提示。domain/data 应输出 stage/error code + 参数；UI 本地化。日志文案可保留中文，因为它不是 UI contract。

## A12 / P2：发布与 CI 缺少质量闸门

`.github/workflows/android-release.yml` 只 build/publish，不运行测试、lint 或架构检查；CodeQL workflow 只 compile；`scripts/release.sh` 在本地不验证就 push commit/tag；默认 desktopTest 排除 E2E；Android 平台实现目前 0 个 Android 测试文件。

建立聚合任务 `qualityCheck`（architecture、common/desktop tests、Android unit/lint）。PR 必跑 qualityCheck；release workflow 将 quality 与 build/publish 分成有依赖关系的独立 jobs，质量失败时不启动构建。格式化由 pre-commit 的 treefmt 负责，release shell 只负责版本与 tag，不重复承担 CI 职责。为 Android SAF、WebView cookie bridge、MLKit adapter 和 lifecycle 添加最小平台测试。

## A13 / P3：其余防腐项

- `stopAppKoin` 只关闭无 qualifier 的 HttpClient，cached download client 等资源没有统一 lifecycle ownership。为 closeable singleton 注册 `onClose`，由 composition root 统一关闭。
- `PlatformDirectoryPicker.android.kt` 使用 `printStackTrace`，应进入统一日志和用户错误通道。
- Android lint 报告 `AndroidManifest.xml` 中 `application@android:theme` 的 `tools:replace` 没有任何被替换声明；这是可直接删除的历史兜底。
- Android 编译仍产生 expect/actual beta opt-in 警告。统一在 KMP compiler options 显式 opt-in，或保留警告作为升级提醒；不要只在单个文件零散 `@Suppress`。
- submission 子包大量 same-feature wildcard import，掩盖真正耦合面；先完成边界迁移，再由 formatter/lint 禁止手写 wildcard。
- architecture test 主要断言固定目录/文件名，容易把一次重构快照当架构。测试应断言允许的依赖图、public API 和 forbidden references，而不是“某目录必须存在”。
- lefthook 在每次 pre-commit 自动增加 `APP_VERSION_CODE`，而 release script 又增加一次，造成与发布无关的版本 churn。版本应由 release 流程唯一管理。
- `missing.md` 是一次性图标搜索记录，建议移到 `docs/decisions/` 并说明是否仍有效，或转 issue 后删除根目录杂项。

## 建议的目标模块划分

不要一步拆十几个 Gradle module。先以 package/API 迁移验证边界，再物理拆分：

```text
:core:model          稳定值对象、终态 ResourceResult；无 UI/网络/存储
:core:common         URL、日志、并发等真正跨层能力
:data:api            repository/source contracts、FA resource models
:data:impl           HTTP/parser/cache/session/local/settings/provider implementations
:domain:*            仅复杂业务：attachment、series、translation、recommendation
:core:ui             theme、通用无业务组件、UiMessage
:feature:*           auth/feed/browse/search/user/settings/submission-viewer 等
:app                  navigation、DI、platform bootstrap
```

若模块数量成本过高，可先保留一个 `:shared`，但必须保持同样的 package dependency DAG；当边界稳定后再拆 Gradle module。关键不是模块数量，而是 `data-api` 与 `data-impl`、feature API 与实现之间存在单向可验证依赖。

## 迁移波次与验收

### Wave 0：止血（1-2 个短迭代）

- 建立 release quality gate，并让发布复用已验证 artifact。
- 统一 FA origin policy，阻止 Cookie 跨域发送/写回。
- 修复 cancellation swallowing；加守护测试。

验收：恶意跨域测试通过；发布前质量任务通过；所有 suspend cancellation test 通过。

### Wave 1：清除虚假复杂度

- 引入终态 ResourceResult，移除一次性 API 的 Loading。
- 删除 18 个死注入、nullable backend、测试专用 open/nullable seam。
- settings 单事务更新；移除同步 `appString/runBlocking`。

验收：生产源码无裸 suspend `runCatching`、无 `PageState.Loading` terminal branch、无未使用 constructor property、主线程无资源 `runBlocking`。

### Wave 2：建立真实层边界

- 建立 data-api contracts，消除 data -> domain。
- 把 blocklist/source ports、稳定 models 迁到正确 API。
- 将 architecture checks 从目录快照升级为 dependency DAG；随后拆 `data-api/data-impl` module。

验收：编译图中不存在 data-impl -> domain；domain 只依赖 data-api/core；DI 是唯一组合点。

### Wave 3：按 feature 解开 UI

- 提取 submission-viewer API/navigation contract。
- 分离 pager/detail/OCR/translation/attachment/social 状态机。
- 清理 components -> pages 和 feature 双向 imports。

验收：`ui-components` 不依赖 feature；任意 feature implementation 不依赖另一 feature implementation；workflow 主类职责可单独测试且不再持有所有 job/map。

### Wave 4：持续防腐

- 平台测试、DI graph test、dependency rule、format/lint 全进 PR gate。
- 记录少量 ADR：层依赖、contract 归属、navigation、error/message、credential boundary。
- 每次迁移删除旧 adapter，不设永久“双轨兼容层”。

## 不建议做的事

- 不要给每个 repository 方法机械包一层 UseCase；只有跨资源/算法/复用链路进入 domain。
- 不要先按行数把 2,070 行文件切成十个 extension 文件；要先按状态所有权和依赖方向拆 controller。
- 不要以更多 nullable/default/fallback 保证“永不崩”；区分可恢复业务失败、程序不变量和取消。
- 不要只扩充当前字符串架构测试后停止；单 module 内它永远容易绕过。

## 逐文件结论说明

下方附录将列出所有受审手写 Kotlin/Gradle 文件的状态。标记“无独立发现”表示该文件没有需要单独登记的问题，不代表不受上述全局迁移影响；同一根因涉及几十个调用点时只在根因 finding 中解释一次，避免把同一个架构问题伪装成几十条局部建议。

<details>
<summary>展开逐文件状态（567 个受 Git 跟踪文件）</summary>

```text
无独立 finding                        .envrc
无独立 finding                        .gitattributes
无独立 finding                        .github/actions/setup-android-build/action.yml
A12                                .github/workflows/android-release.yml
A12                                .github/workflows/codeql.yml
无独立 finding                        .gitignore
架构审查基线                             AGENTS.md
无独立 finding                        LICENSE
无独立 finding                        androidApp/build.gradle.kts
无独立 finding                        androidApp/proguard-rules.pro
A13                                androidApp/src/main/AndroidManifest.xml
无独立 finding                        androidApp/src/main/kotlin/me/domino/fa2/android/AndroidPlatformModule.kt
A05                                androidApp/src/main/kotlin/me/domino/fa2/android/Fa2Application.kt
无独立 finding                        androidApp/src/main/kotlin/me/domino/fa2/android/MainActivity.kt
无独立 finding                        androidApp/src/main/kotlin/me/domino/fa2/android/ocr/MlKitImageTextRecognitionPort.kt
资源/Schema；无独立 finding              androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
资源/Schema；无独立 finding              androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-hdpi/ic_launcher.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-hdpi/ic_launcher_foreground.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-hdpi/ic_launcher_round.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-mdpi/ic_launcher.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-mdpi/ic_launcher_foreground.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-mdpi/ic_launcher_round.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-xhdpi/ic_launcher.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-xhdpi/ic_launcher_round.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-xxhdpi/ic_launcher.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-xxhdpi/ic_launcher_round.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png
二进制/视觉资源；仅核对归属                     androidApp/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png
资源/Schema；无独立 finding              androidApp/src/main/res/values/colors.xml
资源/Schema；无独立 finding              androidApp/src/main/res/values/strings.xml
资源/Schema；无独立 finding              androidApp/src/main/res/values/themes.xml
二进制/视觉资源；仅核对归属                     branding/logo.svg
无独立 finding                        build.gradle.kts
无独立 finding                        buildSrc/build.gradle.kts
无独立 finding                        buildSrc/src/main/kotlin/me/domino/fa2/build/BrandingIcons.kt
A12                                desktopApp/build.gradle.kts
无独立 finding                        desktopApp/src/desktopMain/kotlin/me/domino/fa2/desktop/DesktopPlatformModule.kt
A05                                desktopApp/src/desktopMain/kotlin/me/domino/fa2/desktop/Main.kt
无独立 finding                        desktopApp/src/desktopMain/kotlin/me/domino/fa2/desktop/ocr/DesktopOcrBlockExtractor.kt
无独立 finding                        desktopApp/src/desktopMain/kotlin/me/domino/fa2/desktop/ocr/RapidImageTextRecognitionPort.kt
无独立 finding                        desktopApp/src/desktopMain/kotlin/me/domino/fa2/desktop/ocr/RapidOcrBlockExtractor.kt
二进制/视觉资源；仅核对归属                     desktopApp/src/desktopMain/resources/icon/icon-1024.png
二进制/视觉资源；仅核对归属                     desktopApp/src/desktopMain/resources/icon/icon-128.png
二进制/视觉资源；仅核对归属                     desktopApp/src/desktopMain/resources/icon/icon-16.png
二进制/视觉资源；仅核对归属                     desktopApp/src/desktopMain/resources/icon/icon-256.png
二进制/视觉资源；仅核对归属                     desktopApp/src/desktopMain/resources/icon/icon-32.png
二进制/视觉资源；仅核对归属                     desktopApp/src/desktopMain/resources/icon/icon-512.png
二进制/视觉资源；仅核对归属                     desktopApp/src/desktopMain/resources/icon/icon-64.png
二进制/视觉资源；仅核对归属                     desktopApp/src/desktopMain/resources/icon/icon.icns
二进制/视觉资源；仅核对归属                     desktopApp/src/desktopMain/resources/icon/icon.ico
二进制/视觉资源；仅核对归属                     desktopApp/src/desktopMain/resources/icon/icon.png
测试文件；无独立 finding                   desktopApp/src/desktopTest/kotlin/me/domino/fa2/desktop/e2e/DesktopE2eChallengePolicy.kt
测试文件；无独立 finding                   desktopApp/src/desktopTest/kotlin/me/domino/fa2/desktop/e2e/DesktopE2eProfileSupport.kt
测试文件；无独立 finding                   desktopApp/src/desktopTest/kotlin/me/domino/fa2/desktop/e2e/DesktopE2eRuntime.kt
测试文件；无独立 finding                   desktopApp/src/desktopTest/kotlin/me/domino/fa2/desktop/e2e/DesktopE2eSessionLoader.kt
测试文件；无独立 finding                   desktopApp/src/desktopTest/kotlin/me/domino/fa2/desktop/e2e/DesktopE2eSessionStorage.kt
测试文件；无独立 finding                   desktopApp/src/desktopTest/kotlin/me/domino/fa2/desktop/e2e/DesktopLocalSessionE2eTest.kt
测试文件；无独立 finding                   desktopApp/src/desktopTest/kotlin/me/domino/fa2/desktop/ocr/RapidOcrBlockExtractorTest.kt
二进制/视觉资源；仅核对归属                     dummy.keystore
无独立 finding                        flake.lock
无独立 finding                        flake.nix
A13                                gradle.properties
无独立 finding                        gradle/libs.versions.toml
二进制/视觉资源；仅核对归属                     gradle/wrapper/gradle-wrapper.jar
无独立 finding                        gradle/wrapper/gradle-wrapper.properties
Gradle wrapper；无独立 finding         gradlew
Gradle wrapper；无独立 finding         gradlew.bat
A13                                lefthook.yml
A13                                missing.md
A12                                scripts/release.sh
无独立 finding                        settings.gradle.kts
A12                                shared/build.gradle.kts
资源/Schema；无独立 finding              shared/schemas/me.domino.fa2.data.local.AppDatabase/1.json
A11                                shared/src/androidMain/kotlin/me/domino/fa2/domain/attachmenttext/AttachmentTextPlatform.android.kt
A11                                shared/src/androidMain/kotlin/me/domino/fa2/domain/attachmenttext/AttachmentTextPlatformInitializer.android.kt
无独立 finding                        shared/src/androidMain/kotlin/me/domino/fa2/ui/components/platform/PlatformBackHandler.android.kt
A13                                shared/src/androidMain/kotlin/me/domino/fa2/ui/components/platform/PlatformDirectoryPicker.android.kt
无独立 finding                        shared/src/androidMain/kotlin/me/domino/fa2/ui/components/platform/PlatformFileSavePicker.android.kt
无独立 finding                        shared/src/androidMain/kotlin/me/domino/fa2/ui/components/platform/PlatformScrollbar.android.kt
无独立 finding                        shared/src/androidMain/kotlin/me/domino/fa2/ui/components/platform/PlatformTextActions.android.kt
无独立 finding                        shared/src/androidMain/kotlin/me/domino/fa2/ui/components/platform/PlatformTextFileWriter.android.kt
A10                                shared/src/androidMain/kotlin/me/domino/fa2/ui/components/platform/PlatformUrlDownloader.android.kt
无独立 finding                        shared/src/androidMain/kotlin/me/domino/fa2/ui/i18n/AppLocale.android.kt
无独立 finding                        shared/src/androidMain/kotlin/me/domino/fa2/ui/pages/overlays/about/AboutLibrariesAndroidContextHolder.kt
无独立 finding                        shared/src/androidMain/kotlin/me/domino/fa2/ui/pages/overlays/about/AboutLibrariesJsonLoader.android.kt
无独立 finding                        shared/src/androidMain/kotlin/me/domino/fa2/ui/theme/ThemePlatform.android.kt
资源/Schema；无独立 finding              shared/src/commonMain/composeResources/files/aboutlibraries.json
资源/Schema；无独立 finding              shared/src/commonMain/composeResources/files/fa-search.json
资源/Schema；无独立 finding              shared/src/commonMain/composeResources/files/fa-tags.json
资源/Schema；无独立 finding              shared/src/commonMain/composeResources/values-zh-rCN/strings.xml
资源/Schema；无独立 finding              shared/src/commonMain/composeResources/values/strings.xml
A11                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/auth/AuthDataSource.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/auth/AuthRepository.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/auth/AuthSessionProfileStore.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/auth/HomeEndpoint.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/browse/BrowseDataSource.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/browse/BrowseEndpoint.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/browse/BrowsePageCache.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/browse/BrowseRepository.kt
A03                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/core/CachedPageResource.kt
A03                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/core/CachedPageStoreSupport.kt
A01                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/core/FaHttpClient.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/core/HtmlResponseLogSummary.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/core/HtmlResponseResult.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/core/NetworkFailureMessages.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/core/RemoteDataSourceUtils.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/core/StoreJson.kt
A03                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/core/StoreSupport.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/favorites/FavoritesDataSource.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/favorites/FavoritesEndpoint.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/favorites/FavoritesRepository.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/feed/FeedDataSource.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/feed/FeedEndpoint.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/feed/FeedPageCache.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/feed/FeedParser.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/feed/FeedRepository.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/gallery/GalleryDataSource.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/gallery/GalleryEndpoint.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/gallery/GalleryPageCache.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/gallery/GalleryParser.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/gallery/GalleryRepository.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/journal/JournalDataSource.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/journal/JournalEndpoint.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/journal/JournalPageCache.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/journal/JournalParser.kt
A08                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/journal/JournalRepository.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/journal/JournalsDataSource.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/journal/JournalsEndpoint.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/journal/JournalsPageCache.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/journal/JournalsParser.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/journal/JournalsRepository.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/media/AttachmentDownloadEndpoint.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/media/AttachmentDownloadPort.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/media/AttachmentFileNameSupport.kt
A01                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/media/CachedDownloadHttpClient.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/media/CoilImageProgressSupport.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/media/ImageBytesSource.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/media/ImageProgressTracker.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/media/KtorImageBytesSource.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/search/SearchDataSource.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/search/SearchEndpoint.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/search/SearchPageCache.kt
A07                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/search/SearchRepository.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/AuthAwareFaHtmlDataSource.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/AuthSessionController.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/CfChallengeController.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/CfChallengeCoordinator.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/ChallengeAwareFaHtmlDataSource.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/ChallengeCookiePolicy.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/ChallengeProbeVerifier.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/ChallengeResolver.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/ChallengeSessionStorage.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/ChallengeWebViewSync.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/CloudflareCookieUtils.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/CookiePersistence.kt
A01                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/FaCookiesStorage.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/PendingFaRouteStore.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/SessionWebViewPort.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/session/UserAgentStorage.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/social/SocialActionBackend.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/social/SocialActionChallengePolicy.kt
A08                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/social/SocialActionEndpoint.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/social/SocialActionHttpTransport.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/social/SocialActionTagBlockingResponseParser.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/submission/SubmissionCommentPostingParser.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/submission/SubmissionCommentsParser.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/submission/SubmissionDataParsingUtils.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/submission/SubmissionDataSource.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/submission/SubmissionEndpoint.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/submission/SubmissionMediaParser.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/submission/SubmissionPageCache.kt
A01                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/submission/SubmissionPageParser.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/submission/SubmissionParser.kt
A02                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/submission/SubmissionRepository.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/submission/TagBlockParsingUtils.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/user/ActivityHistoryRepository.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/user/UserDataSource.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/user/UserEndpoint.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/user/UserPageAccessUtils.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/user/UserPageCache.kt
A01                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/user/UserParser.kt
A02                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/user/UserRepository.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/watchlist/WatchlistDataSource.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/watchlist/WatchlistEndpoint.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/watchlist/WatchlistPageCache.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/fa/watchlist/WatchlistParser.kt
A02                                shared/src/commonMain/kotlin/me/domino/fa2/data/fa/watchlist/WatchlistRepository.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/i18n/AppI18n.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/local/AppDatabase.kt
A09                                shared/src/commonMain/kotlin/me/domino/fa2/data/local/KeyValueStorage.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/local/dao/HistoryDao.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/local/dao/PageCacheDao.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/local/entity/PageCacheEntity.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/local/entity/SearchHistoryEntity.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/local/entity/SubmissionHistoryEntity.kt
A09                                shared/src/commonMain/kotlin/me/domino/fa2/data/local/settings/AppSettingsStorage.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/local/watchrecommendation/WatchRecommendationBlocklistRepository.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/AuthProbeResult.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/FeedPage.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/GalleryFolder.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/GalleryPage.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/JournalDetail.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/JournalPage.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/JournalSummary.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/PageComment.kt
A03                                shared/src/commonMain/kotlin/me/domino/fa2/data/model/PageState.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/PageStateLogSummary.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/SearchHistoryRecord.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/Submission.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/SubmissionListingPage.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/SubmissionThumbnail.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/User.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/WatchlistCategory.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/WatchlistPage.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/model/WatchlistUser.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/settings/AppSettings.kt
A09                                shared/src/commonMain/kotlin/me/domino/fa2/data/settings/AppSettingsService.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/settings/AppSettingsStore.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/taxonomy/FaTaxonomyModels.kt
A04                                shared/src/commonMain/kotlin/me/domino/fa2/data/taxonomy/FaTaxonomyRepository.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/taxonomy/FaTaxonomyResourceLoader.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/taxonomy/FaTaxonomySelectionModels.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/translation/GoogleTranslationClient.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/translation/KtorTranslationPort.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/translation/MicrosoftTranslationClient.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/translation/OpenAiCompatibleTranslationClient.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/translation/SubmissionTextTranslationEngine.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/translation/SubmissionTranslationChunkExecutor.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/translation/SubmissionTranslationChunkPlanner.kt
A04                                shared/src/commonMain/kotlin/me/domino/fa2/data/translation/SubmissionTranslationChunkTranslator.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/translation/SubmissionTranslationResultAligner.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/translation/TranslationHttpTransport.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/translation/TranslationProviderClient.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/data/translation/TranslationRequest.kt
A13                                shared/src/commonMain/kotlin/me/domino/fa2/di/AppModule.kt
A02                                shared/src/commonMain/kotlin/me/domino/fa2/di/DataModule.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/di/DependencyQualifiers.kt
A02                                shared/src/commonMain/kotlin/me/domino/fa2/di/DomainModule.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/di/UiModule.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/ArchiveAttachmentTextSupport.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/AttachmentHtmlBuilder.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/AttachmentTextExceptions.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/AttachmentTextExtractor.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/AttachmentTextModels.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/AttachmentTextParser.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/AttachmentTextParserSupport.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/AttachmentTextProgressReporter.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/AttachmentTextService.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/DocxAttachmentTextParser.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/HtmlAttachmentTextParser.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/MarkdownAttachmentTextParser.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/OdtAttachmentTextParser.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/PdfAttachmentTextParser.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/PdfLineCollectorSupport.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/PdfParagraphHeuristics.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/PlainTextAttachmentTextParser.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/RtfAttachmentTextInterpreter.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/RtfAttachmentTextParser.kt
A11                                shared/src/commonMain/kotlin/me/domino/fa2/domain/attachmenttext/RtfAttachmentTextTokenizer.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/domain/ocr/ImageOcrOverlayMergeSupport.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/domain/ocr/ImageTextRecognitionPort.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/domain/ocr/SubmissionImageOcrService.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/domain/submissionseries/SubmissionSeriesService.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/domain/translation/SubmissionDescriptionTranslationModels.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/domain/translation/SubmissionDescriptionTranslationService.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/domain/translation/SubmissionImageOcrTranslationService.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/domain/watchrecommendation/RandomWatchlistSampler.kt
A02                                shared/src/commonMain/kotlin/me/domino/fa2/domain/watchrecommendation/WatchRecommendationService.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/Fa2App.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/UiDependencies.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/challenge/CfChallengeOverlay.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/challenge/CfChallengeOverlayHost.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/navigation/AppNavigator.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/navigation/AppScaffold.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/navigation/BootRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/navigation/FaLinkUriHandler.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/app/navigation/MainRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/navigation/NavigatorHome.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/navigation/SubmissionNavigation.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/scaffold/BackHomeTopBarNavigation.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/scaffold/ScreenTopBars.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/scaffold/TopBarShareAction.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/app/theme/Theme.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/AccessibilitySemantics.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/ExpressiveButtons.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/feedback/AppFeedbackHost.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/feedback/ToastFeedback.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/forms/FilterDropdownField.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/forms/FilterOptionAdapters.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/html/HtmlInlineContent.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/html/HtmlText.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/html/PageCommentsCard.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/html/SubmissionDescriptionDisplayBlock.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/components/html/TranslatableHtmlBlockSection.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/media/ImageLoadingProgress.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/media/NetworkMedia.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/platform/PlatformBackHandler.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/platform/PlatformDirectoryPicker.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/platform/PlatformDownloadSupport.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/platform/PlatformFileSavePicker.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/platform/PlatformScrollbar.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/platform/PlatformTextActions.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/platform/PlatformTextFileWriter.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/platform/PlatformUrlDownloader.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/state/PageStateWrapper.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/state/PaginationRetryBar.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/state/Skeleton.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/state/StatusSurface.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/user/UserHeaderSummaryCard.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/user/UserHorizontalCard.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/components/waterfall/SubmissionWaterfall.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/waterfall/WaterfallLoadingSkeleton.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/components/waterfall/WaterfallRefreshBox.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/i18n/AppLocale.kt
A05                                shared/src/commonMain/kotlin/me/domino/fa2/ui/i18n/AppResourceAccess.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/i18n/search/SearchUiLabelKeys.kt
A04                                shared/src/commonMain/kotlin/me/domino/fa2/ui/i18n/search/SearchUiLabelsRepository.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/icons/FaBrandIcons.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/icons/FaContactIcons.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/icons/FaMaterialSymbols.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/auth/AuthRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/auth/AuthScreen.kt
A08                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/auth/AuthScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/auth/SessionWebView.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/browse/BrowseRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/browse/BrowseScreen.kt
A08                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/browse/BrowseScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/feed/FeedRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/feed/FeedScreen.kt
A08                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/feed/FeedScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/history/SearchHistoryRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/history/SubmissionHistoryRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/more/MoreScreen.kt
A04,A08                            shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/more/MoreScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/about/AboutLibrariesJsonLoader.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/about/AboutLibrariesRouteScreen.kt
A04                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/about/AboutRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/journaldetail/JournalDetailRouteScreen.kt
A08                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/journaldetail/JournalDetailScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/journalpager/JournalPagerContextScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/journalpager/JournalPagerRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/userpager/UserPagerContextScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/userpager/UserPagerRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/usershouts/UserShoutsRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/usershouts/UserShoutsScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/userwatchlist/UserWatchlistRouteScreen.kt
A08                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/userwatchlist/UserWatchlistScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/userwatchlist/WatchlistUserListComponents.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/watchrecommendation/SimilarUsersRouteScreen.kt
A04                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/watchrecommendation/SimilarUsersScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/watchrecommendation/WatchRecommendationBlocklistRouteScreen.kt
A04                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/watchrecommendation/WatchRecommendationBlocklistScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/watchrecommendation/WatchRecommendationRouteScreen.kt
A04                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/overlays/watchrecommendation/WatchRecommendationScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/search/SearchRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/search/SearchScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/search/SearchScreenActions.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/search/SearchScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/search/components/SearchFilterSections.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/search/components/SearchManualDateSection.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/search/components/SearchOverlayContent.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/search/components/SearchStatusComponents.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/search/form/SearchFilterOptions.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/search/results/SearchScreenWorkflow.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/search/util/SearchDateUtils.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/settings/SettingsDraft.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/settings/SettingsLabels.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/settings/SettingsRouteScreen.kt
A04                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/settings/SettingsScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/settings/SettingsSections.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/settings/components/SettingsDropdownField.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/settings/components/SettingsSwitchRow.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/settings/components/SettingsWidgets.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/SubmissionRouteChrome.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/SubmissionRouteScreen.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/attachmenttext/SubmissionAttachmentTextCard.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/attachmenttext/SubmissionAttachmentTextUiState.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/content/SubmissionBrowseMetadataSection.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/content/SubmissionDescriptionCard.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/content/SubmissionDetailErrorContent.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/content/SubmissionDetailLoadingContent.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/content/SubmissionDetailSuccessContent.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/content/SubmissionDetailUiState.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/content/SubmissionInfoMetricChip.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/content/SubmissionKeywordsSection.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/content/SubmissionModels.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/content/SubmissionPagerDetailContent.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/imageocr/SubmissionImageOcrUiState.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/imageocr/SubmissionOverlayAndAuthor.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/pager/SubmissionContextModels.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/pager/SubmissionContextNavigation.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/pager/SubmissionContextScreenModel.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/pager/SubmissionPager.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/pager/SubmissionPagerContextController.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/pager/SubmissionPagerUiState.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/pager/SubmissionScreenModel.kt
A04,A06                            shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/pager/SubmissionScreenSources.kt
A04,A06                            shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/pager/SubmissionScreenWorkflow.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/pager/SubmissionSourceAdapters.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/series/SubmissionSeriesProbeUi.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/translation/SubmissionTranslationExport.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/translation/SubmissionTranslationStateSupport.kt
A06                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/submission/translation/SubmissionTranslationUiState.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserChildRoute.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserChildRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserContactActions.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserHeaderCard.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserHeaderNavigationActions.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserJournalsScreen.kt
A08                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserJournalsScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserRouteCompositionLocals.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserRouteScreen.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserRouteScrollKeys.kt
A04,A08                            shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserSectionTopScaffold.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserSubmissionSectionScreen.kt
A08                                shared/src/commonMain/kotlin/me/domino/fa2/ui/pages/user/UserSubmissionSectionScreenModel.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/utils/PaginationReducer.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/ui/utils/UiUtils.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/utils/FaUrls.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/utils/TemplateStringUtils.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/utils/UrlParsingUtils.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/utils/concurrency/SequentialRequestThrottle.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/utils/html/HtmlTextBlockExtractor.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/utils/logging/FaLog.kt
无独立 finding                        shared/src/commonMain/kotlin/me/domino/fa2/utils/logging/LogSummaries.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/attachmenttext/AttachmentTextExtractorTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/network/CachedDownloadHttpClientTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/network/HtmlResponseResultTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/network/SequentialRequestThrottleTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/network/challenge/CfChallengeCoordinatorTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/network/challenge/ChallengeCookiePolicyTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/network/challenge/ChallengeSessionStorageTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/network/endpoint/AttachmentDownloadEndpointTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/network/endpoint/SocialActionBackendTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/network/endpoint/SocialActionChallengePolicyTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/network/endpoint/SocialActionTagBlockingResponseParserTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/network/media/SubmissionImageOcrServiceTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/parser/BrowseSearchParserTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/parser/FeedParserTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/parser/GalleryParserTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/parser/JournalParserTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/parser/JournalsParserTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/parser/SubmissionParserTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/parser/UserParserTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/parser/WatchlistParserTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/parser/support/ParserUtilsTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/repository/FeedRepositoryPaginationTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/repository/SubmissionRepositoryTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/repository/UserRepositoryTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/repository/UserSectionRepositoriesTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/repository/WatchRecommendationBlocklistRepositoryTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/repository/WatchlistRepositoryTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/settings/AppSettingsStorageTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/store/CachedPageStoreSupportTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/store/PageCacheTtlPolicyTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/taxonomy/FaTaxonomyRepositoryTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/data/translation/KtorTranslationPortTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/domain/ocr/ImageOcrOverlayMergeSupportTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/domain/submissionseries/SubmissionSeriesServiceTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/domain/translation/SubmissionDescriptionTranslationServiceTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/domain/translation/SubmissionImageOcrTranslationServiceTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/domain/watchrecommendation/RandomWatchlistSamplerTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/domain/watchrecommendation/WatchRecommendationServiceTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/fake/InMemoryPageCacheDao.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/fake/TestFixtures.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/integration/MinimalFlowIntegrationTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/components/HtmlTextTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/components/ImageLoadingProgressStateTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/components/PageStateWrapperPolicyTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/components/PaginationRetryBarModelTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/components/platform/PlatformDownloadSupportTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/components/waterfall/SubmissionWaterfallScrollTargetTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/auth/AuthScreenModelTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/browse/BrowseScreenModelTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/search/SearchDateUtilsTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/search/SearchFormStateValidationTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/search/SearchGenderKeywordSyncTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/submission/SeriesSubmissionSourceAdapterTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/submission/SubmissionContextScreenModelTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/submission/SubmissionRouteScreenKeyTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/submission/SubmissionScreenModelAttachmentTextTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/submission/SubmissionScreenModelImageOcrTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/submission/SubmissionScreenModelPrefetchTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/submission/SubmissionScreenModelTestSupport.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/submission/SubmissionScreenModelTranslationAndScrollTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/submission/SubmissionTranslationExportTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/submission/components/SubmissionOverlayAndAuthorTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/user/UserChildRouteScreenKeyTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/user/UserSectionTopScaffoldTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/user/UserSubmissionSectionScreenModelTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/user/journal/JournalDetailScreenModelTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/user/journal/JournalPagerContextScreenModelTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/user/profile/UserContactActionsTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/user/profile/UserScreenModelRecommendationHideTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/user/profile/UserWatchButtonActionTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/user/route/UserPagerContextScreenModelTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/pages/user/watchlist/UserWatchlistScreenModelTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/ui/utils/UiUtilsTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/utils/FaUrlsTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/utils/TemplateStringUtilsTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/utils/logging/FaLogTest.kt
测试文件；无独立 finding                   shared/src/commonTest/kotlin/me/domino/fa2/utils/logging/LogSummariesTest.kt
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/attachmenttext-external.docx
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/attachmenttext-external.odt
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/attachmenttext-external.pdf
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/attachmenttext-external.rtf
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/attachmenttext-multipage.pdf
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/attachmenttext-sources.txt
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/attachmenttext-styled.docx
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/attachmenttext-styled.odt
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:browse:blocked-canine.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:browse:default-page1.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:favorites:artist-alpha:.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:gallery:artist-alpha:.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:gallery:artist-beta:.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:journal:20000001-withcomments.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:journal:20000002-disabled-comments.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:journals:artist-alpha:.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:journals:empty-user-empty.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:loggedin.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:loggedout.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:msg:others-comments-journals-shout.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:msg:others-empty.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:msg:pms-contents.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:msg:pms-empty.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:msg:pms-unread.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:msg:submissions-firstpage.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:msg:submissions-lastpage.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:msg:submissions-middlepage.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:newpm:username.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:scraps:artist-alpha:.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:search:wolf-keywords-female-trans_female.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:user:artist-alpha.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:user:disabled-user-message.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:user:pending-deletion-message.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:user:username-system-error.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:view:10000006-disabled-comments.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:view:10000007.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:view:10000008-mature-content-message.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:view:20000009.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:watchlist:by:user-alpha.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:watchlist:to:user-alpha.html
测试 fixture；无独立源码 finding           shared/src/commonTest/resources/fixture/www.furaffinity.net:watchlist:to:user-beta.html
A11                                shared/src/desktopMain/kotlin/me/domino/fa2/domain/attachmenttext/AttachmentTextPlatform.desktop.kt
无独立 finding                        shared/src/desktopMain/kotlin/me/domino/fa2/ui/components/platform/PlatformBackHandler.desktop.kt
无独立 finding                        shared/src/desktopMain/kotlin/me/domino/fa2/ui/components/platform/PlatformDirectoryPicker.desktop.kt
无独立 finding                        shared/src/desktopMain/kotlin/me/domino/fa2/ui/components/platform/PlatformFileSavePicker.desktop.kt
无独立 finding                        shared/src/desktopMain/kotlin/me/domino/fa2/ui/components/platform/PlatformScrollbar.desktop.kt
无独立 finding                        shared/src/desktopMain/kotlin/me/domino/fa2/ui/components/platform/PlatformTextActions.desktop.kt
无独立 finding                        shared/src/desktopMain/kotlin/me/domino/fa2/ui/components/platform/PlatformTextFileWriter.desktop.kt
A10                                shared/src/desktopMain/kotlin/me/domino/fa2/ui/components/platform/PlatformUrlDownloader.desktop.kt
无独立 finding                        shared/src/desktopMain/kotlin/me/domino/fa2/ui/i18n/AppLocale.desktop.kt
无独立 finding                        shared/src/desktopMain/kotlin/me/domino/fa2/ui/pages/overlays/about/AboutLibrariesJsonLoader.desktop.kt
无独立 finding                        shared/src/desktopMain/kotlin/me/domino/fa2/ui/theme/ThemePlatform.desktop.kt
A02                                shared/src/desktopTest/kotlin/me/domino/fa2/architecture/ArchitectureBoundaryTest.kt
测试文件；无独立 finding                   shared/src/desktopTest/kotlin/me/domino/fa2/ui/components/AccessibilitySemanticsTest.kt
测试文件；无独立 finding                   shared/src/desktopTest/kotlin/me/domino/fa2/ui/pages/submission/components/SubmissionOverlayAccessibilityStateTest.kt
测试文件；无独立 finding                   shared/src/desktopTest/kotlin/me/domino/fa2/ui/pages/user/profile/UserContactActionsAccessibilityTest.kt
测试文件；无独立 finding                   shared/src/desktopTest/kotlin/me/domino/fa2/ui/pages/user/route/UserRouteTopBarTest.kt
测试文件；无独立 finding                   shared/src/desktopTest/kotlin/me/domino/fa2/ui/pages/user/watchlist/UserWatchlistRouteTopBarTest.kt
测试文件；无独立 finding                   shared/src/desktopTest/kotlin/me/domino/fa2/ui/theme/UiColorUsageTest.kt
无独立 finding                        treefmt.toml
```

</details>
