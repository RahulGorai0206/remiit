package com.rahulgorai.remiit.trigger.applaunch

import com.rahulgorai.remiit.data.model.ReminderRule
import com.rahulgorai.remiit.data.model.Trigger
import com.rahulgorai.remiit.engine.TriggerEvent
import com.rahulgorai.remiit.engine.TriggerSink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The dispatcher decides which foreground events are a launch, and that
 * decision has been wrong twice in ways no amount of reading the code caught:
 * once firing on the home screen, once re-firing forever because dismissing a
 * reminder looked like relaunching the app under it. These are those cases,
 * written down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLaunchDispatcherTest {

    private companion object {
        const val YOUTUBE = "com.google.android.youtube"
        const val LAUNCHER = "com.android.launcher3"
        const val REMIIT = "com.rahulgorai.remiit"
        const val CHROME = "com.android.chrome"
    }

    private class FakePackages : PackageIntrospector {
        override val ownPackage = REMIIT
        override fun launcherPackage() = LAUNCHER
        override fun label(packageName: String) = packageName.substringAfterLast('.')
    }

    private class RecordingSink : TriggerSink {
        val events = mutableListOf<TriggerEvent>()
        override suspend fun onTriggerFired(event: TriggerEvent) {
            events += event
        }
    }

    /** A clock the test moves by hand, so the re-launch guard is not a sleep. */
    private class MutableClock(var instant: Instant) : Clock() {
        override fun instant() = instant
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }

    private val packages = FakePackages()

    private fun ruleFor(vararg apps: String) = ReminderRule(
        id = "rule",
        title = "Stop scrolling",
        triggers = listOf(Trigger.AppLaunch(id = "trigger", packages = apps.toSet())),
    )

    private fun fixture(
        clock: MutableClock,
        scope: TestScope,
    ): Pair<AppLaunchDispatcher, RecordingSink> {
        val sink = RecordingSink()
        val dispatcher = AppLaunchDispatcher(
            packages = packages,
            sink = sink,
            scope = scope,
            clock = clock,
        )
        dispatcher.updateRules(listOf(ruleFor(YOUTUBE)))
        return dispatcher to sink
    }

    @Test
    fun `opening a watched app fires once`() = runTest(StandardTestDispatcher()) {
        val clock = MutableClock(Instant.parse("2026-09-01T10:00:00Z"))
        val (dispatcher, sink) = fixture(clock, this)

        dispatcher.onAppForegrounded(YOUTUBE)
        runCurrent()

        assertEquals(1, sink.events.size)
        assertEquals("youtube opened", sink.events.single().summary)
    }

    /**
     * The second half of the same bug: a banner or alarm reminder puts Remiit's
     * own overlay in front of YouTube. Dismissing it must not read as a fresh
     * launch, or the reminder re-fires itself forever.
     */
    @Test
    fun `dismissing our own reminder overlay does not re-fire`() =
        runTest(StandardTestDispatcher()) {
            val clock = MutableClock(Instant.parse("2026-09-01T10:00:00Z"))
            val (dispatcher, sink) = fixture(clock, this)

            dispatcher.onAppForegrounded(YOUTUBE)
            dispatcher.onAppForegrounded(REMIIT)
            dispatcher.onAppForegrounded(YOUTUBE)
            runCurrent()

            assertEquals(1, sink.events.size)
        }

    /** Same for the notification shade going down and back up. */
    @Test
    fun `the notification shade does not re-fire`() = runTest(StandardTestDispatcher()) {
        val clock = MutableClock(Instant.parse("2026-09-01T10:00:00Z"))
        val (dispatcher, sink) = fixture(clock, this)

        dispatcher.onAppForegrounded(YOUTUBE)
        dispatcher.onAppForegrounded("com.android.systemui")
        dispatcher.onAppForegrounded(YOUTUBE)
        runCurrent()

        assertEquals(1, sink.events.size)
    }

    /** Going home is leaving the app, but is not itself worth a reminder. */
    @Test
    fun `the launcher never fires`() = runTest(StandardTestDispatcher()) {
        val clock = MutableClock(Instant.parse("2026-09-01T10:00:00Z"))
        val (dispatcher, sink) = fixture(clock, this)

        dispatcher.onAppForegrounded(LAUNCHER)
        runCurrent()

        assertTrue(sink.events.isEmpty())
    }

    /** Leaving for real and coming back later is a second launch, and should fire. */
    @Test
    fun `re-opening the app after using another one fires again`() =
        runTest(StandardTestDispatcher()) {
            val clock = MutableClock(Instant.parse("2026-09-01T10:00:00Z"))
            val (dispatcher, sink) = fixture(clock, this)

            dispatcher.onAppForegrounded(YOUTUBE)
            dispatcher.onAppForegrounded(CHROME)
            clock.instant = clock.instant.plusSeconds(30)
            dispatcher.onAppForegrounded(YOUTUBE)
            runCurrent()

            assertEquals(2, sink.events.size)
        }

    /** A bounce out and straight back is the OS being noisy, not two launches. */
    @Test
    fun `an immediate bounce through another app is suppressed`() =
        runTest(StandardTestDispatcher()) {
            val clock = MutableClock(Instant.parse("2026-09-01T10:00:00Z"))
            val (dispatcher, sink) = fixture(clock, this)

            dispatcher.onAppForegrounded(YOUTUBE)
            dispatcher.onAppForegrounded(CHROME)
            clock.instant = clock.instant.plusMillis(200)
            dispatcher.onAppForegrounded(YOUTUBE)
            runCurrent()

            assertEquals(1, sink.events.size)
        }
}
