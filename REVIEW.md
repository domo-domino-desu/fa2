# fa2 架构审查归档

审查日期：2026-07-10；瘦身实施：2026-07-11

## 当前结论

项目回到与实际复杂度匹配的结构：普通 FA 页面由 `Repository + Parser` 获取资源，repository 直接暴露 `Flow<QueryState<T>>`，Compose 按状态渲染；一次性写操作继续返回终态 `ResourceResult`。`domain` 只保留附件解析、投稿串联、OCR port 和关注推荐等确有独立复杂度的能力。

本轮删除了 Store5、资源切片中的 Endpoint/DataSource/PageCache 转发层、`UiPageState`/`PaginationReducer`、translation/OCR 透传服务、viewer source 转发接口和导航请求总线。缓存统一由 `PageQueryCache` 提供 Room TTL、陈旧数据和同键请求去重。

## 保留的复杂度

- FA session、Cookie 信任边界与 Cloudflare challenge 装饰链。
- HTML parser 及其 fixture 测试。
- 投稿查看器中彼此独立的 OCR、翻译、附件、社交操作和上下文分页状态。
- 平台 port、持久化 contract，以及确有多个实现或测试替身的外部边界。

这些部分不是为了分层而分层；它们各自拥有独立状态、生命周期、算法或平台实现，继续拆平反而会降低内聚。

## 当前规则

- 默认依赖方向是 `ui -> data`；只有复杂、独立、可复用链路使用 `ui -> domain -> data`。
- 被观察资源返回 `Flow<QueryState<T>>`，UI 不再复制加载、刷新和错误状态。
- `data/remote/fa/*` 普通资源切片保持 `Repository + Parser`，共享抓取、缓存与 listing 解析下沉到叶子包。
- feature 直接使用 Voyager 导航；共享组件和 viewer API 不依赖 feature 实现。
- 仅保留能验证依赖方向、资源包无环和稳定边界的架构测试，不维护目录白名单。

历史 finding 已完成或被本轮结构替代，不再作为当前代码说明保留。图标缺失决策见 `docs/decisions/missing-contact-icons.md`。
