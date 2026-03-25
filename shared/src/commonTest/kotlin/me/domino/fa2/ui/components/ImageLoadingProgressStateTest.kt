package me.domino.fa2.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import me.domino.fa2.data.network.ImageDownloadProgressSnapshot

/** 图片加载状态合成测试。 */
class ImageLoadingProgressStateTest {
  /** Loading 且无总量时应为不确定进度。 */
  @Test
  fun loadingWithoutSnapshotUsesUnknownProgress() {
    val state =
        resolveImageLoadProgressState(
            lifecycleState = ImageLoadLifecycleState.Loading,
            progressSnapshot = null,
        )

    assertEquals(ImageLoadProgressState.LoadingUnknown, state)
  }

  /** Loading 且总量未知时应为不确定进度。 */
  @Test
  fun loadingWithUnknownTotalUsesUnknownProgress() {
    val state =
        resolveImageLoadProgressState(
            lifecycleState = ImageLoadLifecycleState.Loading,
            progressSnapshot = ImageDownloadProgressSnapshot(bytesRead = 128L, totalBytes = -1L),
        )

    assertEquals(ImageLoadProgressState.LoadingUnknown, state)
  }

  /** Loading 且总量已知时应转为确定进度。 */
  @Test
  fun loadingWithKnownTotalUsesDeterminateProgress() {
    val state =
        resolveImageLoadProgressState(
            lifecycleState = ImageLoadLifecycleState.Loading,
            progressSnapshot = ImageDownloadProgressSnapshot(bytesRead = 50L, totalBytes = 100L),
        )

    val known = assertIs<ImageLoadProgressState.LoadingKnown>(state)
    assertEquals(0.5f, known.progress, absoluteTolerance = 0.0001f)
    assertEquals(50L, known.bytesRead)
    assertEquals(100L, known.totalBytes)
  }

  /** 成功状态应覆盖字节进度。 */
  @Test
  fun successStateOverridesProgressSnapshot() {
    val state =
        resolveImageLoadProgressState(
            lifecycleState = ImageLoadLifecycleState.Success,
            progressSnapshot = ImageDownloadProgressSnapshot(bytesRead = 1L, totalBytes = 2L),
        )

    assertEquals(ImageLoadProgressState.Success, state)
  }

  /** 失败状态应覆盖字节进度。 */
  @Test
  fun errorStateOverridesProgressSnapshot() {
    val state =
        resolveImageLoadProgressState(
            lifecycleState = ImageLoadLifecycleState.Error,
            progressSnapshot = ImageDownloadProgressSnapshot(bytesRead = 1L, totalBytes = 2L),
        )

    assertEquals(ImageLoadProgressState.Error, state)
  }

  /** 进度应被限制在 0..1。 */
  @Test
  fun knownProgressIsClampedToRange() {
    val overflow =
        resolveImageLoadProgressState(
            lifecycleState = ImageLoadLifecycleState.Loading,
            progressSnapshot = ImageDownloadProgressSnapshot(bytesRead = 220L, totalBytes = 100L),
        )
    val underflow =
        resolveImageLoadProgressState(
            lifecycleState = ImageLoadLifecycleState.Loading,
            progressSnapshot = ImageDownloadProgressSnapshot(bytesRead = -50L, totalBytes = 100L),
        )

    assertEquals(1f, assertIs<ImageLoadProgressState.LoadingKnown>(overflow).progress, 0.0001f)
    assertEquals(0f, assertIs<ImageLoadProgressState.LoadingKnown>(underflow).progress, 0.0001f)
  }
}
