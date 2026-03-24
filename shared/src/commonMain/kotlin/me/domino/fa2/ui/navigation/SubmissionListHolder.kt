package me.domino.fa2.ui.navigation

import cafe.adriel.voyager.core.model.ScreenModel
import me.domino.fa2.data.model.SubmissionThumbnail

/** 瀑布流与详情左右滑之间共享的投稿列表持有器。 */
class SubmissionListHolder : ScreenModel {
  /** 当前可浏览投稿 ID 列表。 */
  private val ids: MutableList<Int> = mutableListOf()

  /** ID 到投稿缩略信息的索引。 */
  private val byId: MutableMap<Int, SubmissionThumbnail> = mutableMapOf()

  /** 当前游标索引。 */
  private var currentIndex: Int = 0

  /** 下一页 URL。 */
  private var nextPageUrl: String? = null

  /**
   * 用最新瀑布流数据整体替换当前列表。
   *
   * @param submissions 最新投稿列表。
   */
  fun replace(submissions: List<SubmissionThumbnail>, nextPageUrl: String?) {
    ids.clear()
    byId.clear()
    submissions.forEach { item ->
      ids += item.id
      byId[item.id] = item
    }
    this.nextPageUrl = nextPageUrl
    currentIndex = currentIndex.coerceIn(0, (ids.lastIndex).coerceAtLeast(0))
  }

  /**
   * 追加下一页投稿。
   *
   * @param submissions 新页投稿。
   * @param nextPageUrl 新游标 URL。
   */
  fun append(submissions: List<SubmissionThumbnail>, nextPageUrl: String?) {
    submissions.forEach { item ->
      if (item.id in byId) {
        byId[item.id] = item
      } else {
        ids += item.id
        byId[item.id] = item
      }
    }
    this.nextPageUrl = nextPageUrl
    currentIndex = currentIndex.coerceIn(0, (ids.lastIndex).coerceAtLeast(0))
  }

  /**
   * 按投稿 ID 设置当前索引。
   *
   * @param sid 投稿 ID。
   * @return 是否设置成功。
   */
  fun setCurrentBySid(sid: Int): Boolean {
    val index = ids.indexOf(sid)
    if (index < 0) return false
    currentIndex = index
    return true
  }

  /**
   * 按索引设置当前游标。
   *
   * @param index 目标索引。
   */
  fun setCurrentIndex(index: Int) {
    if (ids.isEmpty()) {
      currentIndex = 0
      return
    }
    currentIndex = index.coerceIn(0, ids.lastIndex)
  }

  /** 返回当前索引。 */
  fun currentIndex(): Int = currentIndex

  /** 返回下一页 URL。 */
  fun nextPageUrl(): String? = nextPageUrl

  /** 返回列表大小。 */
  fun size(): Int = ids.size

  /**
   * 返回指定索引的投稿。
   *
   * @param index 目标索引。
   */
  fun getAt(index: Int): SubmissionThumbnail? = ids.getOrNull(index)?.let { sid -> byId[sid] }

  /** 返回当前投稿。 */
  fun current(): SubmissionThumbnail? = getAt(currentIndex)

  /** 返回上一条投稿。 */
  fun previous(): SubmissionThumbnail? = getAt(currentIndex - 1)

  /** 返回下一条投稿。 */
  fun next(): SubmissionThumbnail? = getAt(currentIndex + 1)

  /** 作用域销毁时清理内存。 */
  override fun onDispose() {
    ids.clear()
    byId.clear()
    currentIndex = 0
    nextPageUrl = null
  }
}
