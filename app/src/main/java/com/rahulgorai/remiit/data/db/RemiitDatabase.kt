package com.rahulgorai.remiit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rahulgorai.remiit.data.model.Automation
import com.rahulgorai.remiit.data.model.ReminderEvent
import com.rahulgorai.remiit.data.model.ReminderRule

@Database(
    entities = [ReminderRule::class, ReminderEvent::class, Automation::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RemiitDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao
    abstract fun reminderEventDao(): ReminderEventDao
    abstract fun automationDao(): AutomationDao

    companion object {
        private const val NAME = "remiit.db"

        /**
         * Adds the automations table.
         *
         * A real migration rather than destructive fallback, because by the time
         * this ships there are installs with saved rules in them and losing
         * someone's reminders to a feature they did not ask for is not a
         * trade-off worth making. The statement is the one Room generates for
         * the entity — it has to match exactly or Room rejects the schema on the
         * next open, which the migration test is there to catch.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `automations` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`is_enabled` INTEGER NOT NULL, " +
                        "`trigger` TEXT NOT NULL, " +
                        "`actions` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "`last_run_at` INTEGER NOT NULL, " +
                        "`last_result` TEXT NOT NULL, " +
                        "`last_run_ok` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
            }
        }

        fun build(context: Context): RemiitDatabase =
            Room.databaseBuilder(context, RemiitDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
