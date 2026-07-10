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

各层职责：

- `ui`：Compose UI、Voyager screen、screen model、路由、app shell、交互状态。
- `data`：应用数据、资源仓储、FA/session/network/local persistence/provider 的具体实现，以及简单业务逻辑。data 不应知道 UI。
- `domain`：少量复杂、独立、可复用的业务链路。domain service 可以调用 `data` 来获取资源或执行持久化。
- `utils`：少量跨层通用工具，例如 URL、模板字符串、并发节流、HTML 文本块抽取。避免放 feature 专属 helper。
- `di`：Koin 装配层。DI 负责组装依赖图，因此允许引用所有层。

稳定边界规则：

- `data` 不得引用 `domain`/`ui`；domain 所需的资源 source contract 放在对应的 `data` 资源包，由 repository 实现。
- `data/fa/*` 资源包的依赖图必须无环。共享 listing/parsing 能力下沉到专用叶子包，跨资源失效使用窄接口，由 DI 组装。
- data 的 suspend API 只返回终态 `ResourceResult`，加载、刷新、追加等过程状态只存在于 UI 的 `UiPageState`/feature state。
- `ui/components`、`ui/navigation`、`ui/translation` 和 `ui/submissionviewer/api` 不得依赖 `ui/pages` 的实现。
- feature 间跳转通过 `AppNavigationRequest`；feature 不直接构造另一个 feature 的 screen。
- suspend 路径捕获异常时必须重抛 `CancellationException`，统一使用 `runSuspendCatching` 或显式处理。
- Cookie 只能由 `data/fa/session` 的凭据传输边界读写；裸媒体请求和不可信 origin 不得读取或污染 FA Cookie。

## 当前布局

`ui` 按 app shell、复用组件和页面分组：

- `ui/app`：应用入口、composition local、navigation、scaffold/top bar、challenge overlay、theme。
- `ui/components`：真正跨页面复用的 UI 组件，按能力分组：feedback、forms、html、media、platform、state、user、waterfall。
- `ui/pages`：页面自己的 screen、screen model、route screen 和页面专属组件。
- `ui/navigation`：feature 无关的导航请求 contract；screen 构造只在 app navigation 中完成。
- `ui/submissionviewer/api`：各资源页面调用投稿查看器时共享的上下文/source contract；`adapters` 提供 data 到 viewer API 的装配适配。
- `ui/translation`：跨 feature 的翻译展示状态和 support。
- `ui/metadata`、`ui/i18n`、`ui/state`、`ui/utils`：UI 支撑代码；`ui/state` 持有含 Loading 的 UI 状态。

`domain` 当前包含少量复杂/独立业务功能：

- `domain/attachmenttext`：附件文本提取、解析 service 和模型。
- `domain/submissionseries`：submission 串联检测与解析。
- `domain/translation`：submission description 和 image OCR 翻译场景。
- `domain/watchrecommendation`：关注推荐算法，以及 blocklist/source 端口。
- `domain/i18n`、`domain/ocr`：小型业务/支撑 contract。

`data` 按具体实现和资源切片组织：

- `data/fa/*`：FA 资源切片，例如 feed、submission、user、gallery、journal、search、watchlist、auth、session、media、social。
- `data/fa/core`：FA HTTP、page resource、cache 支撑代码。
- `data/fa/session`：cookies、凭据 HTTP、auth session、Cloudflare challenge、user agent。
- `data/fa/listing`、`data/fa/parsing`：多个资源切片复用的 listing/card 解析和页面访问能力。
- `data/local`：Room/DataStore/KSafe 本地持久化。
- `data/model`：FA 页面和应用数据模型，例如终态 `ResourceResult`、`Submission`、`User`、分页结果。
- `data/settings`：应用设置模型、枚举、读写 service 和 store contract；`data/local/settings` 提供持久化实现。
- `data/translation`：provider client、HTTP transport、request normalization、chunking/execution/alignment。
- `data/taxonomy`：taxonomy resource 加载和查询。

# 开发说明

- 尽可能在 `nix develop` 环境中测试，否则可能报 skiko 相关错误。
- gradle 的常用命令：`:desktopApp:……`、`:shared:……`。
- perl 不在环境里，想用的话就 `nix run` 或者 `comma perl`。
- github release 使用 dummy keystore 是 feature 而非 bug。
