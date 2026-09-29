package com.rahulgorai.remiit.data.db

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Real migrations against real old databases.
 *
 * Each old database is built from the committed schema file for its version —
 * `schemas/…/1.json`, `2.json` — which is the record of what actually shipped
 * to phones. Room then opens it with the production migration list and
 * validates the result against the current entities, which is exactly the
 * check that runs on a user's device at first launch after an update. A
 * mismatch there is not a test failure, it is a crash on every launch; this is
 * where it gets caught instead.
 *
 * Robolectric for a real SQLite and a real Room, with no device needed.
 */
@RunWith(RobolectricTestRunner::class)
// Pinned below the app's targetSdk, which is newer than Robolectric supports.
// A bare Application, not RemiitApplication: that one starts Koin, WorkManager
// and the trigger coordinator on create, none of which a database test wants —
// and Koin refuses to start twice across test methods.
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val name = "migration-test.db"
    private var room: RemiitDatabase? = null

    @Before
    fun clean() {
        context.deleteDatabase(name)
    }

    @After
    fun close() {
        room?.close()
        context.deleteDatabase(name)
    }

    /** Creates the database a phone on [version] has, from that version's schema file. */
    private fun createAt(version: Int, seed: SQLiteDatabase.() -> Unit) {
        val schema = JSONObject(
            File("schemas/com.rahulgorai.remiit.data.db.RemiitDatabase/$version.json").readText()
        ).getJSONObject("database")

        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(
                        indices.getJSONObject(j).getString("createSql")
                            .replace("\${TABLE_NAME}", table)
                    )
                }
            }
            // room_master_table and the identity hash — without them Room
            // treats the file as foreign and refuses to migrate it.
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
            db.seed()
        }
    }

    /** Opens with the production migrations, which forces migrate-and-validate. */
    private fun openCurrent(): RemiitDatabase =
        Room.databaseBuilder(context, RemiitDatabase::class.java, name)
            .addMigrations(*RemiitDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also {
                room = it
                // Opening is lazy; this is the moment Room migrates and throws
                // if the result does not match the entities.
                it.openHelper.writableDatabase
            }

    @Test
    fun `version 2 migrates to the current schema and keeps automations`() {
        createAt(2) {
            execSQL(
                "INSERT INTO automations (id, name, is_enabled, `trigger`, actions, created_at, " +
                    "updated_at, last_run_at, last_result, last_run_ok) VALUES (" +
                    "'a1', 'Silent at the office', 1, " +
                    "'{\"type\":\"wifi\",\"ssid\":\"Office\",\"edge\":\"ENTER\"}', " +
                    "'{\"sound\":\"SILENT\"}', 1000, 1000, 2000, 'Silent', 1)"
            )
        }

        val automation = runBlocking { openCurrent().automationDao().getById("a1") }

        assertNotNull("the automation must survive the migration", automation)
        automation!!
        assertEquals("Silent at the office", automation.name)
        assertEquals("Silent", automation.lastResult)
        // The new columns arrive with their defaults: off, and nothing held.
        assertFalse(automation.restoreOnExit)
        assertNull(automation.savedState)
    }

    /**
     * A phone that skipped a release goes 1 → 2 → 3 in one launch. Chaining
     * migrations is where a later one assuming a table an earlier one never
     * made would surface.
     */
    @Test
    fun `version 1 migrates all the way and keeps rules`() {
        createAt(1) {
            execSQL(
                "INSERT INTO reminder_rules (id, title, body, is_enabled, triggers, `match`, " +
                    "delivery, constraints, created_at, updated_at) VALUES (" +
                    "'r1', 'Take medication', '', 1, '[]', 'ANY', '{}', '{}', 1000, 1000)"
            )
        }

        val db = openCurrent()
        val rule = runBlocking { db.ruleDao().getById("r1") }
        val automations = runBlocking { db.automationDao().getAll() }

        assertNotNull("a rule saved before automations existed must survive", rule)
        assertEquals("Take medication", rule!!.title)
        assertEquals(emptyList<Any>(), automations)
    }

    @Test
    fun `the new columns round trip after migrating`() {
        createAt(2) {
            execSQL(
                "INSERT INTO automations (id, name, is_enabled, `trigger`, actions, created_at, " +
                    "updated_at, last_run_at, last_result, last_run_ok) VALUES (" +
                    "'a1', 'x', 1, '{\"type\":\"wifi\",\"ssid\":\"Office\",\"edge\":\"ENTER\"}', " +
                    "'{}', 0, 0, 0, '', 1)"
            )
        }
        val dao = openCurrent().automationDao()

        val snapshot = com.rahulgorai.remiit.data.model.DeviceSnapshot(
            ringer = com.rahulgorai.remiit.data.model.RingerMode.VIBRATE,
            volumes = mapOf(com.rahulgorai.remiit.data.model.VolumeStream.MEDIA to 60),
        )
        runBlocking { dao.setSavedState("a1", snapshot) }

        assertEquals(snapshot, runBlocking { dao.getById("a1") }!!.savedState)
    }
}
