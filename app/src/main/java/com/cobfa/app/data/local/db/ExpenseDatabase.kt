package com.cobfa.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cobfa.app.data.local.dao.AchievementDao
import com.cobfa.app.data.local.dao.BudgetDao
import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.data.local.dao.NudgeEventDao
import com.cobfa.app.data.local.dao.PointsDao
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.data.local.db.Converters
import com.cobfa.app.data.local.entity.AchievementEntity
import com.cobfa.app.data.local.entity.BudgetEntity
import com.cobfa.app.data.local.entity.NudgeEventEntity
import com.cobfa.app.data.local.entity.PointsEventEntity
import com.google.firebase.BuildConfig

@Database(
    entities = [
        ExpenseEntity::class,
        BudgetEntity::class,
        NudgeEventEntity::class,
        PointsEventEntity::class,
        AchievementEntity::class
    ],
    version = 5,

    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun nudgeEventDao(): NudgeEventDao
    abstract fun pointsDao(): PointsDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        fun getInstance(context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "cobfa_expense_db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5
                    )

                    .apply {
                        if (BuildConfig.DEBUG) {
                            fallbackToDestructiveMigration()
                        }
                    }
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes between v1 and v2, so this is a no-op.
                // Add actual migration SQL here if schema changes in future.
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) { /* no-op */ }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
            CREATE TABLE nudge_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                type TEXT NOT NULL,
                category TEXT NOT NULL,
                action TEXT,
                timestamp INTEGER NOT NULL DEFAULT (strftime('%s','now')*1000)
            )
        """)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS points_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sourceNudgeId INTEGER,
                delta INTEGER NOT NULL,
                reason TEXT NOT NULL,
                details TEXT,
                timestamp INTEGER NOT NULL
            )
        """.trimIndent())

                db.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_points_events_sourceNudgeId
            ON points_events(sourceNudgeId)
        """.trimIndent())

                db.execSQL("""
            CREATE TABLE IF NOT EXISTS achievements (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `key` TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                unlockedAt INTEGER NOT NULL
            )
        """.trimIndent())

                db.execSQL("""
            CREATE UNIQUE INDEX IF NOT EXISTS index_achievements_key
            ON achievements(`key`)
        """.trimIndent())
            }
        }
    }
}