package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationMetaDao {
    @Query("SELECT * FROM conversation_meta")
    fun getAllMeta(): Flow<List<ConversationMetaEntity>>

    @Query("SELECT * FROM conversation_meta WHERE threadId = :threadId LIMIT 1")
    fun getMetaForThread(threadId: Long): Flow<ConversationMetaEntity?>

    @Query("SELECT * FROM conversation_meta WHERE threadId = :threadId LIMIT 1")
    suspend fun getMetaForThreadSync(threadId: Long): ConversationMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(meta: ConversationMetaEntity)

    @Query("UPDATE conversation_meta SET isPinned = :isPinned WHERE threadId = :threadId")
    suspend fun setPinned(threadId: Long, isPinned: Boolean)

    @Query("UPDATE conversation_meta SET isArchived = :isArchived WHERE threadId = :threadId")
    suspend fun setArchived(threadId: Long, isArchived: Boolean)

    @Query("UPDATE conversation_meta SET isMuted = :isMuted WHERE threadId = :threadId")
    suspend fun setMuted(threadId: Long, isMuted: Boolean)

    @Query("UPDATE conversation_meta SET draftText = :draftText, draftAttachmentUri = :draftAttachmentUri WHERE threadId = :threadId")
    suspend fun setDraft(threadId: Long, draftText: String?, draftAttachmentUri: String?)

    @Query("DELETE FROM conversation_meta WHERE threadId = :threadId")
    suspend fun deleteMeta(threadId: Long)
}

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledTimestamp ASC")
    fun getAllScheduledMessages(): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE status = 'PENDING' AND scheduledTimestamp <= :currentTime")
    suspend fun getDueMessages(currentTime: Long): List<ScheduledMessageEntity>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScheduledMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ScheduledMessageEntity): Long

    @Update
    suspend fun update(message: ScheduledMessageEntity)

    @Query("UPDATE scheduled_messages SET status = :status, failureReason = :reason WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, reason: String? = null)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FavoriteMessageDao {
    @Query("SELECT * FROM favorite_messages ORDER BY timestamp DESC")
    fun getAllFavorites(): Flow<List<FavoriteMessageEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_messages WHERE messageId = :messageId)")
    fun isFavorite(messageId: Long): Flow<Boolean>

    @Query("SELECT messageId FROM favorite_messages")
    fun getAllFavoriteIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fav: FavoriteMessageEntity)

    @Query("DELETE FROM favorite_messages WHERE messageId = :messageId")
    suspend fun delete(messageId: Long)
}

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY blockedAt DESC")
    fun getAllBlocked(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE normalizedNumber = :normalizedNumber)")
    fun isBlocked(normalizedNumber: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockNumber(blocked: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers WHERE normalizedNumber = :normalizedNumber")
    suspend fun unblockNumber(normalizedNumber: String)
}
