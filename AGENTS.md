# 架构说明

## 核心原则

本项目采用 Android 官方推荐架构的口径：基础结构是 UI 层 + Data 层；Domain（网域层）不是默认必经层，只在业务链路复杂、足够独立、或需要被多个 UI 场景复用时引入。

默认依赖方向是：

```text
ui -> data

复杂/独立链路：
ui -> domain -> data
```

也就是说，`domain` 不是“简单页面的可选中间层”，而是“只有必要时才出现的业务层”。只涉及加载、刷新、分页、简单写操作的 FA 资源页面，UI 应直接依赖 `data` 的仓储。只有当功能包含复杂业务逻辑、跨资源编排、独立算法、可复用策略，或明显不属于单一资源获取时，才放入 `domain`。

参考：[Android 官方架构文档](https://developer.android.com/courses/pathways/android-architecture?hl=zh-cn)将应用分为界面层、数据层，以及可选的网域层；网域层用于封装复杂业务逻辑或多个 ViewModel 复用的逻辑。

## 项目架构

整体形态是 React Query 式的 `ui = f(QueryState)`：data 层输出带缓存语义的状态流，UI 纯粹订阅渲染。

### 核心类型（`data/model`）

- `ResourceResult<T>`：一次性 suspend 请求的**终态**结果（`Success / AuthRequired / CfChallenge / MatureBlocked / Error`）。
- `QueryError`：4 个错误因子的值类型，全库唯一的错误编码；`ResourceResult.errorOrNull()` 是唯一的映射点，展示文案统一走 `QueryError.message()`。
- `QueryState<T>`：可观察的查询状态（`Loading / Success(refreshing) / Failure(error)`），三个分支都携带 `data`（陈旧数据），实现 stale-while-revalidate。

### data 层

每个 FA 资源切片（`data/remote/fa/{feed,browse,journal,...}`）只有两类文件：**Repository**（拼 URL → `fetchParsed` → 缓存）和 **Parser**（Ksoup 解析，配 fixture 测试）。不要恢复 Endpoint/DataSource/PageCache 这类纯转发层。

共享基础设施在 `data/remote/fa/core`：

- `PageQueryCache`：Room 支撑的查询缓存。`queryFlow`（观察式，SWR + 失效自动重取）、`queryOnce`（一次性 cache-first）、`invalidate(key|PageType)`（markStale，活跃 flow 经 `observeByKey` 感知并重取）。TTL 由 `PageType` enum 内联定义。跨资源失效直接调 `invalidate`（core 是各切片共享的叶子，无环约束天然满足）。
- `fetchParsed`：`FaHtmlDataSource.get` → `HtmlResponseResult` 分类 → parse → `ResourceResult`，HTTP 到结果的转换只写在这一处。
- `FaHttpClient → ChallengeAware → AuthAware` 装饰链承载 Cloudflare/登录检测；Cookie 只经 `data/remote/fa/session` 的凭据边界读写（`FaOriginPolicy`），不可信 origin 不得携带 FA Cookie。

`data/remote/translation` 是翻译 provider（Google/Microsoft/OpenAI）与分块引擎 `SubmissionTextTranslationEngine`；`data/local` 是 Room/DataStore/KSafe 持久化。

### ui 层

- 页面 = `RouteScreen`（Voyager Screen）+ `ScreenModel`（暴露 `StateFlow<QueryState<T>>`）+ 纯渲染 Composable。详情页直接 `repository.xxx(...).collect` 进状态；跨 feature 跳转直接 `navigator.push(...)`。
- `PageStateWrapper` 统一渲染 `QueryState`：有 `data` 就展示内容（SWR），否则按 `QueryError` 分类硬回退（登录/CF/受限/错误）。
- 列表页追加分页用 `ui/utils/PagedAppend.kt` 的 `appendTo()`（合并 + 按 key 去重 + typed 错误），页面自己只保留专属状态（如 watchlist 的随机打乱、gallery 的文件夹）。
- `ui/components` 只放真正跨页复用的组件；`ui/app` 是入口、导航与 scaffold。

### domain 层（仅存真实复杂逻辑）

`attachmenttext`（多格式文档文本解析）、`watchrecommendation`（关注推荐算法）、`submissionseries`（系列检测）、`ocr`（平台识别 port + 几何合并）、`submissionviewer`（投稿查看器跨 8 种来源的分页适配）。不要在 domain 加纯转发/透传服务。

### di 层

Koin 装配（`AppModule/DataModule/DomainModule/UiModule`），screen model 用 `factory { params -> ... }`。DI 可引用所有层。

### 边界规则（由 `desktopTest/architecture/ArchitectureBoundaryTest` 强制）

1. 禁止项目内通配 import；
2. data 不得依赖 domain/ui，domain 不得依赖 ui；
3. `data/remote/fa/*` 资源包依赖图无环；
4. FA feature 切片只能依赖共享基础设施包（core/parsing/listing/session/media/social）；
5. 复用 UI 与 viewer api 不得依赖具体 feature 页面实现。

# 开发说明

- 尽可能在 `nix develop` 环境中测试，否则可能报 skiko 相关错误。
- gradle 的常用命令：`:desktopApp:……`、`:shared:……`。
- perl 不在环境里，想用的话就 `nix run` 或者 `comma perl`。
- github release 使用 dummy keystore 是 feature 而非 bug。
