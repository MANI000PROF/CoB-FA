package com.cobfa.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cobfa.app.data.local.dao.ExpenseDao
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.data.local.db.Converters
import com.google.firebase.BuildConfig

@Database(
    entities = [ExpenseEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

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
                    // ✅ Add explicit migrations
                    .addMigrations(
                        MIGRATION_1_2
                    )
                    // ✅ Only use destructive fallback in DEBUG
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
        // ✅ Define migration from v1 to v2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No schema changes between v1 and v2, so this is a no-op.
                // Add actual migration SQL here if schema changes in future.
            }
        }
    }
}
