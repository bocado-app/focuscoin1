package com.focuscoin.app

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

@Entity(tableName = "app_lock_configs")
data class AppLockEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isLocked: Boolean = false,
    val unlockDurationMinutes: Int = 30,
    val unlockCostCoins: Int = 30,
    val unlockedUntil: Long = 0L
)

@Entity(tableName = "study_bonus_apps")
data class StudyBonusAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val usageDay: String = "",
    val lastUsageMillis: Long = 0L,
    val remainderMillis: Long = 0L,
    val rewardedMinutes: Long = 0L
)

@Entity(tableName = "study_plans")
data class StudyPlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    val weekdaysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val allowedPackages: String,
    val isEnabled: Boolean = true,
    val dateEpochDay: Long? = null
) {
    fun allowedPackageSet(): Set<String> = allowedPackages.split(',').filter { it.isNotBlank() }.toSet()

    fun isActiveAt(timeMillis: Long): Boolean {
        if (!isEnabled) return false
        val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val dayBit = 1 shl (calendar.get(Calendar.DAY_OF_WEEK) - 1)
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val dateMatches = dateEpochDay?.let { epochDay ->
            java.time.LocalDate.ofEpochDay(epochDay) == java.time.Instant.ofEpochMilli(timeMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        } ?: (weekdaysMask and dayBit != 0)
        return dateMatches && minuteOfDay in startMinute until endMinute
    }
}

@Entity(tableName = "subject_plans")
data class SubjectPlanEntity(
    @PrimaryKey val id: String,
    val subjectName: String,
    val examDateEpochDay: Long,
    val dailyGoalMinutes: Int
)

@Entity(tableName = "daily_learning_memos")
data class DailyLearningMemoEntity(
    @PrimaryKey val date: String,
    val content: String,
    val updatedAt: Long
)

@Entity(tableName = "study_sessions")
data class StudySessionEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val status: String,
    val earnedCoins: Int,
    val subjectPlanId: String? = null,
    val subjectName: String = "",
    val breakEarnedMinutes: Int = 0
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
interface StudyBonusAppDao {
    @Query("SELECT * FROM study_bonus_apps ORDER BY appName COLLATE NOCASE")
    fun observeAll(): Flow<List<StudyBonusAppEntity>>

    @Query("SELECT * FROM study_bonus_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): StudyBonusAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StudyBonusAppEntity)

    @Query("DELETE FROM study_bonus_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

@Dao
interface StudyPlanDao {
    @Query("SELECT * FROM study_plans ORDER BY startMinute, title COLLATE NOCASE")
    fun observeAll(): Flow<List<StudyPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StudyPlanEntity)

    @Query("DELETE FROM study_plans WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface SubjectPlanDao {
    @Query("SELECT * FROM subject_plans ORDER BY examDateEpochDay, subjectName COLLATE NOCASE")
    fun observeAll(): Flow<List<SubjectPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubjectPlanEntity)

    @Query("DELETE FROM subject_plans WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface DailyLearningMemoDao {
    @Query("SELECT * FROM daily_learning_memos ORDER BY date DESC")
    fun observeAll(): Flow<List<DailyLearningMemoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyLearningMemoEntity)

    @Query("DELETE FROM daily_learning_memos WHERE date = :date")
    suspend fun delete(date: String)
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

    @Query("SELECT COALESCE(SUM(amount), 0) FROM coin_transactions WHERE type = 'BONUS' AND memo LIKE '공부 앱 보너스%' AND createdAt BETWEEN :startedAt AND :endedAt")
    suspend fun studyAppBonusCoins(startedAt: Long, endedAt: Long): Int

    @Query("DELETE FROM coin_transactions")
    suspend fun clear()
}

@Database(
    entities = [AppLockEntity::class, StudyBonusAppEntity::class, StudyPlanEntity::class, SubjectPlanEntity::class, DailyLearningMemoEntity::class, StudySessionEntity::class, CoinTransactionEntity::class],
    version = 7,
    exportSchema = false
)
abstract class FocusCoinDatabase : RoomDatabase() {
    abstract fun appLockDao(): AppLockDao
    abstract fun studyBonusAppDao(): StudyBonusAppDao
    abstract fun studyPlanDao(): StudyPlanDao
    abstract fun subjectPlanDao(): SubjectPlanDao
    abstract fun dailyLearningMemoDao(): DailyLearningMemoDao
    abstract fun studySessionDao(): StudySessionDao
    abstract fun coinTransactionDao(): CoinTransactionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS study_bonus_apps (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        appName TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        usageDay TEXT NOT NULL,
                        lastUsageMillis INTEGER NOT NULL,
                        remainderMillis INTEGER NOT NULL,
                        rewardedMinutes INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS study_plans (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        weekdaysMask INTEGER NOT NULL,
                        startMinute INTEGER NOT NULL,
                        endMinute INTEGER NOT NULL,
                        allowedPackages TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS subject_plans (
                        id TEXT NOT NULL PRIMARY KEY,
                        subjectName TEXT NOT NULL,
                        examDateEpochDay INTEGER NOT NULL,
                        dailyGoalMinutes INTEGER NOT NULL
                    )""".trimIndent()
                )
                database.execSQL("ALTER TABLE study_sessions ADD COLUMN subjectPlanId TEXT")
                database.execSQL("ALTER TABLE study_sessions ADD COLUMN subjectName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE study_sessions ADD COLUMN breakEarnedMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS daily_learning_memos (
                        date TEXT NOT NULL PRIMARY KEY,
                        content TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""CREATE TABLE IF NOT EXISTS academic_grades (id TEXT NOT NULL PRIMARY KEY, academicYear INTEGER NOT NULL, semester INTEGER NOT NULL, subjectName TEXT NOT NULL, grade INTEGER NOT NULL, credits INTEGER NOT NULL)""")
                database.execSQL("""CREATE TABLE IF NOT EXISTS university_benchmarks (id TEXT NOT NULL PRIMARY KEY, universityName TEXT NOT NULL, departmentName TEXT NOT NULL, admissionType TEXT NOT NULL, referenceGrade REAL NOT NULL, sourceNote TEXT NOT NULL, isGoal INTEGER NOT NULL)""")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE study_plans ADD COLUMN dateEpochDay INTEGER")
                database.execSQL("DROP TABLE IF EXISTS academic_grades")
                database.execSQL("DROP TABLE IF EXISTS university_benchmarks")
            }
        }
    }
}
