package com.rahulgorai.remiit.di

import android.app.Application
import android.content.Context
import org.junit.Test
import org.koin.test.verify.verify
import java.time.Clock

/**
 * Verifies the Koin graph can actually be built.
 *
 * This test exists because of a real bug: extracting the ReminderDelivery
 * interface changed RuleEngine to depend on it without adding the binding, and
 * nothing caught it. The code compiled (Koin resolves by reified type, so a
 * missing definition is not a compile error) and every other unit test passed
 * (they construct RuleEngine directly with a fake). The first sign of trouble
 * was the app dying in Application.onCreate on a device.
 *
 * verify() walks each definition's constructor parameters and fails on anything
 * the module cannot supply — which is exactly that class of mistake, caught at
 * build time instead of on first launch.
 */
class AppModuleTest {

    @Test
    fun `every dependency in the app module can be resolved`() {
        appModule.verify(
            // Supplied at runtime by androidContext() rather than by a
            // definition, so verify has to be told they will exist.
            extraTypes = listOf(
                Context::class,
                Application::class,
                Clock::class,
            )
        )
    }
}
