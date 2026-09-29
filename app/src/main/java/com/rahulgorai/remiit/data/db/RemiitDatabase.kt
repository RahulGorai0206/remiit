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
    version = 3,
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

        /**
         * Adds the restore-on-exit flag and the snapshot it restores from.
         *
         * Two plain ADD COLUMNs, so no table rebuild and nothing existing is
         * touched. The flag needs its DEFAULT because SQLite refuses to add a
         * NOT NULL column without one; the entity declares the same default so
         * Room's validation sees the column it expects.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `automations` ADD COLUMN `restore_on_exit` " +
                        "INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE `automations` ADD COLUMN `saved_state` TEXT")
            }
        }

        /** Every migration, in order. Internal so the migration test runs these exact ones. */
        internal val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun build(context: Context): RemiitDatabase =
            Room.databaseBuilder(context, RemiitDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
