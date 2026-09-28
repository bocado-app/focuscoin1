package com.focuscoin.app

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_lock_configs")
data class AppLockEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isLocked: Boolean = false,
    val unlockDurationMinutes: Int = 30,
    val unlockCostCoins: Int = 30,
    val unlockedUntil: Long = 0L
)

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val status: String,
    val earnedCoins: Int
)

@Entity(tableName = "coin_transactions")
data class CoinTransactionEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val type: String,
    val amount: Int,
    val relatedPackageName: String? = null,
    val memo: String = "",
    val durationMinutes: Int = 0
)

@Dao
interface AppLockDao {
    @Query("SELECT * FROM app_lock_configs ORDER BY appName COLLATE NOCASE")
    fun observeAll(): Flow<List<AppLockEntity>>

    @Query("SELECT * FROM app_lock_configs WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): AppLockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppLockEntity)

    @Query("DELETE FROM app_lock_configs WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM app_lock_configs")
    suspend fun clear()
}

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<StudySessionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: StudySessionEntity): Long

    @Query("DELETE FROM study_sessions")
    suspend fun clear()
}

@Dao
interface CoinTransactionDao {
    @Query("SELECT * FROM coin_transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CoinTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: CoinTransactionEntity): Long

    @Query("SELECT COALESCE(SUM(CASE WHEN type IN ('EARN', 'BONUS') THEN amount WHEN type = 'SPEND' THEN -amount ELSE 0 END), 0) FROM coin_transactions")
    suspend fun netCoinBalance(): Int

    @Query("DELETE FROM coin_transactions")
    suspend fun clear()
}

@Database(
    entities = [AppLockEntity::class, StudySessionEntity::class, CoinTransactionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FocusCoinDatabase : RoomDatabase() {
    abstract fun appLockDao(): AppLockDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun coinTransactionDao(): CoinTransactionDao
}
