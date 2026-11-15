package com.example.adblocker

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "whitelist")
data class WhitelistEntry(
    @PrimaryKey val domain: String
)

@Entity(tableName = "blocked_log")
data class BlockedLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val timestamp: Long
)

data class GroupedBlockedLog(
    val domain: String,
    val latestTimestamp: Long,
    val repeatCount: Int
)

@Dao
interface AdBlockDao {
    @Query("SELECT * FROM whitelist ORDER BY domain ASC")
    fun getAllWhitelistFlow(): Flow<List<WhitelistEntry>>

    @Query("SELECT * FROM whitelist")
    suspend fun getAllWhitelistOnce(): List<WhitelistEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhitelist(entry: WhitelistEntry)

    @Query("DELETE FROM whitelist WHERE domain = :domain")
    suspend fun removeWhitelist(domain: String)

    @Query("DELETE FROM whitelist")
    suspend fun clearWhitelist()

    @Insert
    suspend fun insertBlockedLogs(entries: List<BlockedLogEntry>)

    @Query(
        """
        DELETE FROM blocked_log 
        WHERE id NOT IN (
            SELECT id FROM blocked_log 
            ORDER BY timestamp DESC, id DESC 
            LIMIT :maxRows
        )
        """
    )
    suspend fun trimBlockedLogs(maxRows: Int = Config.MAX_BLOCKED_LOG_ROWS)

    @Query(
        """
        SELECT domain, MAX(timestamp) AS latestTimestamp, COUNT(*) AS repeatCount 
        FROM blocked_log 
        GROUP BY domain 
        ORDER BY latestTimestamp DESC
        """
    )
    fun getGroupedBlockedLogsFlow(): Flow<List<GroupedBlockedLog>>

    @Query("DELETE FROM blocked_log")
    suspend fun clearBlockedLogs()
}

@Database(
    entities = [WhitelistEntry::class, BlockedLogEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun adBlockDao(): AdBlockDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "adblocker.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
