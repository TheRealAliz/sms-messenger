package com.tamadon.smsmessenger.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

object Status {
    const val NONE = 0            // incoming
    const val SENDING = 1
    const val SENT = 2
    const val DELIVERED = 3
    const val FAILED = 4
    const val DELIVERY_FAILED = 5
}

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["threadKey", "date"]),
        Index(value = ["trashedAt"]),
        Index(value = ["threadKey", "incoming", "read", "trashedAt"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadKey: String,
    val address: String,
    val body: String,
    val date: Long,
    val incoming: Boolean,
    val read: Boolean,
    val status: Int = Status.NONE,
    val totalParts: Int = 1,
    val sentParts: Int = 0,
    val deliveredParts: Int = 0,
    val deliveredAt: Long = 0,
    val providerId: Long = 0,
    val trashedAt: Long? = null,
    /** Subscription id of the SIM that sent / received this message; -1 when unknown. */
    @ColumnInfo(defaultValue = "-1") val subId: Int = -1,
    /** Kept in a separate "Starred" list. */
    @ColumnInfo(defaultValue = "0") val starred: Boolean = false
)

@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey val threadKey: String,
    val address: String,
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val archived: Boolean = false,
    val hideNotif: Boolean = false,
    val muted: Boolean = false,
    val trashedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val blocked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val spam: Boolean = false
)

@Entity(tableName = "quick_replies")
data class QuickReply(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val position: Int
)

data class ConversationRow(
    val threadKey: String,
    val address: String,
    val pinned: Boolean,
    val locked: Boolean,
    val archived: Boolean,
    val hideNotif: Boolean,
    val muted: Boolean,
    val trashedAt: Long?,
    val blocked: Boolean,
    val spam: Boolean,
    val lastBody: String,
    val lastDate: Long,
    val lastIncoming: Boolean,
    val lastStatus: Int,
    val unread: Int
)

data class TrashedMessageRow(
    val id: Long,
    val threadKey: String,
    val address: String,
    val body: String,
    val date: Long,
    val incoming: Boolean,
    val trashedAt: Long,
    val locked: Boolean
)

data class SearchRow(
    val id: Long,
    val threadKey: String,
    val address: String,
    val body: String,
    val date: Long,
    val incoming: Boolean
)

private const val ROW_COLS = """
 t.threadKey AS threadKey, t.address AS address, t.pinned AS pinned, t.locked AS locked,
 t.archived AS archived, t.hideNotif AS hideNotif, t.muted AS muted, t.trashedAt AS trashedAt,
 t.blocked AS blocked, t.spam AS spam,
 m.body AS lastBody, m.date AS lastDate, m.incoming AS lastIncoming, m.status AS lastStatus,
 (SELECT COUNT(*) FROM messages u WHERE u.threadKey = t.threadKey AND u.incoming = 1
   AND u.read = 0 AND u.trashedAt IS NULL) AS unread
"""

private const val LIVE_ROWS = "SELECT" + ROW_COLS +
    "FROM threads t JOIN messages m ON m.id = (SELECT x.id FROM messages x " +
    "WHERE x.threadKey = t.threadKey AND x.trashedAt IS NULL ORDER BY x.date DESC, x.id DESC LIMIT 1) "

private const val TRASH_ROWS = "SELECT" + ROW_COLS +
    "FROM threads t JOIN messages m ON m.id = (SELECT x.id FROM messages x " +
    "WHERE x.threadKey = t.threadKey ORDER BY x.date DESC, x.id DESC LIMIT 1) "

@Dao
interface SmsDao {
    // ---- conversation lists ----
    @Query(LIVE_ROWS + "WHERE t.archived = 0 AND t.trashedAt IS NULL AND t.blocked = 0 AND t.spam = 0 ORDER BY t.pinned DESC, m.date DESC")
    fun homeRows(): Flow<List<ConversationRow>>

    @Query(LIVE_ROWS + "WHERE t.archived = 1 AND t.trashedAt IS NULL AND t.blocked = 0 AND t.spam = 0 ORDER BY m.date DESC")
    fun archivedRows(): Flow<List<ConversationRow>>

    @Query(LIVE_ROWS + "WHERE t.spam = 1 AND t.trashedAt IS NULL ORDER BY m.date DESC")
    fun spamRows(): Flow<List<ConversationRow>>

    @Query(LIVE_ROWS + "WHERE t.blocked = 1 AND t.trashedAt IS NULL ORDER BY m.date DESC")
    fun blockedRows(): Flow<List<ConversationRow>>

    @Query(TRASH_ROWS + "WHERE t.trashedAt IS NOT NULL ORDER BY t.trashedAt DESC")
    fun trashedThreadRows(): Flow<List<ConversationRow>>

    @Query(
        "SELECT m.id AS id, m.threadKey AS threadKey, m.address AS address, m.body AS body, m.date AS date, " +
            "m.incoming AS incoming, m.trashedAt AS trashedAt, t.locked AS locked " +
            "FROM messages m JOIN threads t ON t.threadKey = m.threadKey " +
            "WHERE m.trashedAt IS NOT NULL AND t.trashedAt IS NULL ORDER BY m.trashedAt DESC"
    )
    fun trashedMessageRows(): Flow<List<TrashedMessageRow>>

    @Query(
        "SELECT m.id AS id, m.threadKey AS threadKey, m.address AS address, m.body AS body, m.date AS date, " +
            "m.incoming AS incoming FROM messages m JOIN threads t ON t.threadKey = m.threadKey " +
            "WHERE m.trashedAt IS NULL AND t.trashedAt IS NULL AND t.locked = 0 AND t.blocked = 0 AND t.spam = 0 " +
            "AND (m.body LIKE '%' || :q || '%' OR m.address LIKE '%' || :q || '%') " +
            "ORDER BY m.date DESC LIMIT 200"
    )
    fun search(q: String): Flow<List<SearchRow>>

    // ---- threads ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertThreadIfAbsent(t: ThreadEntity): Long

    @Query("SELECT * FROM threads WHERE threadKey = :k")
    suspend fun thread(k: String): ThreadEntity?

    @Query("SELECT * FROM threads WHERE threadKey = :k")
    fun observeThread(k: String): Flow<ThreadEntity?>

    /** A new message brings an archived / trashed conversation back, unless it is blocked or spam. */
    @Query("UPDATE threads SET archived = 0, trashedAt = NULL WHERE threadKey = :k AND blocked = 0 AND spam = 0")
    suspend fun revive(k: String)

    @Query("UPDATE threads SET archived = :v WHERE threadKey IN (:keys)")
    suspend fun setArchived(keys: List<String>, v: Boolean)

    @Query("UPDATE threads SET pinned = :v WHERE threadKey IN (:keys)")
    suspend fun setPinned(keys: List<String>, v: Boolean)

    @Query("UPDATE threads SET locked = :v WHERE threadKey IN (:keys)")
    suspend fun setLocked(keys: List<String>, v: Boolean)

    @Query("UPDATE threads SET hideNotif = :v WHERE threadKey IN (:keys)")
    suspend fun setHideNotif(keys: List<String>, v: Boolean)

    @Query("UPDATE threads SET muted = :v WHERE threadKey IN (:keys)")
    suspend fun setMuted(keys: List<String>, v: Boolean)

    @Query("UPDATE threads SET blocked = :v WHERE threadKey IN (:keys)")
    suspend fun setBlocked(keys: List<String>, v: Boolean)

    @Query("UPDATE threads SET spam = :v WHERE threadKey IN (:keys)")
    suspend fun setSpam(keys: List<String>, v: Boolean)

    @Query("UPDATE threads SET trashedAt = :ts WHERE threadKey IN (:keys)")
    suspend fun setTrashed(keys: List<String>, ts: Long?)

    @Query("UPDATE threads SET locked = 0")
    suspend fun unlockAllThreads()

    @Query("SELECT threadKey FROM threads WHERE trashedAt IS NOT NULL")
    suspend fun trashedThreadKeys(): List<String>

    @Query("SELECT threadKey FROM threads WHERE trashedAt IS NOT NULL AND trashedAt < :cutoff")
    suspend fun trashedThreadKeysBefore(cutoff: Long): List<String>

    @Query("DELETE FROM threads WHERE threadKey IN (:keys)")
    suspend fun deleteThreads(keys: List<String>)

    // ---- messages ----
    @Insert
    suspend fun insertMessage(m: MessageEntity): Long

    @Insert
    suspend fun insertMessages(list: List<MessageEntity>)

    @Query("SELECT * FROM messages WHERE threadKey = :k AND trashedAt IS NULL ORDER BY date DESC, id DESC")
    fun observeMessages(k: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadKey = :k AND incoming = 1 AND read = 0 AND trashedAt IS NULL ORDER BY date DESC LIMIT 5")
    suspend fun recentUnread(k: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE threadKey = :k AND incoming = 1 AND read = 0 AND trashedAt IS NULL")
    suspend fun unreadCount(k: String): Int

    /** SIM the latest incoming message of this conversation arrived on, for answering on the same SIM. */
    @Query("SELECT subId FROM messages WHERE threadKey = :k AND incoming = 1 ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun lastIncomingSub(k: String): Int?

    @Query("UPDATE messages SET read = 1 WHERE threadKey = :k AND incoming = 1 AND read = 0")
    suspend fun markRead(k: String)

    /** Provider ids of that conversation's unread incoming messages, to update the shared provider. */
    @Query(
        "SELECT providerId FROM messages WHERE threadKey = :k AND incoming = 1 AND read = 0 " +
            "AND trashedAt IS NULL AND providerId > 0"
    )
    suspend fun unreadIncomingProviderIds(k: String): List<Long>

    /** Provider ids of every locally unread incoming message (to pull read state from the provider). */
    @Query("SELECT providerId FROM messages WHERE incoming = 1 AND read = 0 AND providerId > 0")
    suspend fun unreadIncomingProviderIds(): List<Long>

    @Query("UPDATE messages SET read = 1 WHERE providerId IN (:ids)")
    suspend fun markReadByProviderIds(ids: List<Long>)

    /** Highest system provider id already linked to a local row; new provider rows are above it. */
    @Query("SELECT COALESCE(MAX(providerId), 0) FROM messages")
    suspend fun maxProviderId(): Long

    /** Provider ids of locally read incoming messages, to push their read flag to the provider. */
    @Query("SELECT providerId FROM messages WHERE read = 1 AND incoming = 1 AND providerId > 0")
    suspend fun readProviderIds(): List<Long>

    @Query("UPDATE messages SET providerId = :pid WHERE id = :id")
    suspend fun setProviderId(id: Long, pid: Long)

    @Query("UPDATE messages SET trashedAt = :ts WHERE id = :id")
    suspend fun setMessageTrashed(id: Long, ts: Long?)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun message(id: Long): MessageEntity?

    @Query("UPDATE messages SET starred = :v WHERE id = :id")
    suspend fun setStarred(id: Long, v: Boolean)

    @Query(
        "SELECT m.id AS id, m.threadKey AS threadKey, m.address AS address, m.body AS body, m.date AS date, " +
            "m.incoming AS incoming FROM messages m JOIN threads t ON t.threadKey = m.threadKey " +
            "WHERE m.starred = 1 AND m.trashedAt IS NULL AND t.trashedAt IS NULL " +
            "ORDER BY m.date DESC LIMIT 500"
    )
    fun starredRows(): Flow<List<SearchRow>>

    @Query("SELECT COUNT(*) FROM messages WHERE starred = 1 AND trashedAt IS NULL")
    suspend fun starredCount(): Int

    @Query("SELECT providerId FROM messages WHERE threadKey IN (:keys) AND providerId != 0")
    suspend fun providerIds(keys: List<String>): List<Long>

    @Query("SELECT providerId FROM messages WHERE providerId IN (:ids)")
    suspend fun existingProviderIds(ids: List<Long>): List<Long>

    @Query("DELETE FROM messages WHERE threadKey IN (:keys)")
    suspend fun deleteMessagesOfThreads(keys: List<String>)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("SELECT * FROM messages WHERE trashedAt IS NOT NULL")
    suspend fun allTrashedMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE trashedAt IS NOT NULL AND trashedAt < :cutoff")
    suspend fun trashedMessagesBefore(cutoff: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE id IN (:ids)")
    suspend fun deleteMessages(ids: List<Long>)

    // ---- send / delivery state machine (atomic SQL, safe for multipart) ----
    @Query("UPDATE messages SET status = 4 WHERE id = :id")
    suspend fun markFailed(id: Long)

    @Query("UPDATE messages SET sentParts = sentParts + 1 WHERE id = :id")
    suspend fun incSent(id: Long)

    @Query("UPDATE messages SET status = 2 WHERE id = :id AND status = 1 AND sentParts >= totalParts")
    suspend fun finishSent(id: Long)

    @Query("UPDATE messages SET status = 5 WHERE id = :id AND status != 4")
    suspend fun markDeliveryFailed(id: Long)

    @Query("UPDATE messages SET deliveredParts = deliveredParts + 1 WHERE id = :id")
    suspend fun incDelivered(id: Long)

    @Query("UPDATE messages SET status = 3, deliveredAt = :t WHERE id = :id AND status IN (1, 2) AND deliveredParts >= totalParts")
    suspend fun finishDelivered(id: Long, t: Long)

    // ---- quick replies ----
    @Query("SELECT * FROM quick_replies ORDER BY position ASC")
    fun observeQuickReplies(): Flow<List<QuickReply>>

    @Query("SELECT * FROM quick_replies ORDER BY position ASC")
    suspend fun allQuickReplies(): List<QuickReply>

    @Query("SELECT COUNT(*) FROM quick_replies")
    suspend fun quickCount(): Int

    @Query("SELECT COALESCE(MAX(position), 0) FROM quick_replies")
    suspend fun quickMaxPosition(): Int

    @Insert
    suspend fun insertQuick(q: QuickReply): Long

    @Update
    suspend fun updateQuick(q: QuickReply)

    @Delete
    suspend fun deleteQuick(q: QuickReply)

    @Query("UPDATE quick_replies SET position = :pos WHERE id = :id")
    suspend fun setQuickPosition(id: Long, pos: Int)
}

@Database(
    entities = [MessageEntity::class, ThreadEntity::class, QuickReply::class],
    version = 3,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): SmsDao
}

/** v2 -> v3: starred messages. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
    }
}

/** v1 -> v2: block / spam flags, SIM per message, faster unread counting. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE threads ADD COLUMN blocked INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE threads ADD COLUMN spam INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE messages ADD COLUMN subId INTEGER NOT NULL DEFAULT -1")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_messages_threadKey_incoming_read_trashedAt " +
                "ON messages (threadKey, incoming, read, trashedAt)"
        )
    }
}
