package com.rahulgorai.remiit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rahulgorai.remiit.data.model.ReminderEvent
import com.rahulgorai.remiit.data.model.ReminderRule

@Database(
    entities = [ReminderRule::class, ReminderEvent::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RemiitDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao
    abstract fun reminderEventDao(): ReminderEventDao

    companion object {
        private const val NAME = "remiit.db"

        fun build(context: Context): RemiitDatabase =
            Room.databaseBuilder(context, RemiitDatabase::class.java, NAME)
                .build()
    }
}
