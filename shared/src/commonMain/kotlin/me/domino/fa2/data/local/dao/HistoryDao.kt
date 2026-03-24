package me.domino.fa2.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import me.domino.fa2.data.local.entity.SearchHistoryEntity
import me.domino.fa2.data.local.entity.SubmissionHistoryEntity

/**
 * 浏览历史访问接口（投稿 + 搜索）。
 */
@Dao
interface HistoryDao {
    /**
     * 写入投稿历史（相同 sid 覆盖为最新一条）。
     */
    @Upsert
    suspend fun upsertSubmission(entity: SubmissionHistoryEntity)

    /**
     * 按时间倒序读取投稿历史。
     */
    @Query("SELECT * FROM fa_submission_history ORDER BY visitedAtMs DESC")
    suspend fun listSubmissionsByLatest(): List<SubmissionHistoryEntity>

    /**
     * 写入搜索历史（相同 queryKey 覆盖为最新一条）。
     */
    @Upsert
    suspend fun upsertSearch(entity: SearchHistoryEntity)

    /**
     * 按时间倒序读取搜索历史。
     */
    @Query("SELECT * FROM fa_search_history ORDER BY visitedAtMs DESC")
    suspend fun listSearchesByLatest(): List<SearchHistoryEntity>
}

