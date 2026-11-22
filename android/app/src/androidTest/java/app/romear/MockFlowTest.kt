package app.romear

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

@RunWith(AndroidJUnit4::class)
class MockFlowTest {
    private fun findText(view: View, text: String): View? {
        if (view is TextView && view.text.toString() == text) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findText(view.getChildAt(index), text)?.let { return it }
        return null
    }
    @Test fun onboardingAndReservationButtons() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                a.model.reset(); a.render()
                listOf("Find my city" to "signin", "Continue" to "otp", "Verify" to "city", "Continue" to "interests", "Continue (3 selected)" to "setup", "Continue" to "ready", "Start exploring" to "home").forEach { (label, page) ->
                    val control = findText(a.binding.root, label)
                    assertNotNull("Missing button: $label", control)
                    assertTrue(control!!.performClick())
                    assertEquals("Button $label", page, a.model.route.page)
                }
                a.navigate("venue", "riverside")
                assertTrue(findText(a.binding.root, "Book a Table")!!.performClick())
                assertEquals("booking", a.model.route.page)
                assertTrue(findText(a.binding.root, "Confirm Booking")!!.performClick())
                assertEquals("pass", a.model.route.page)
                assertEquals(1, a.model.state.value.bookings.size)
            }
        }
    }
    @Test fun screenCatalogueAndPersistentInteractions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var booking = ""
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                a.model.reset()
                a.model.repository.update { it.copy(joined = setOf("runners", "cafe-circle", "urban"), following = setOf("aarav", "riya")) }
                booking = a.model.repository.book("market", "2026-12-01", "5:00 PM", 3)
            }
            val pages = listOf("welcome", "signin", "otp", "city", "interests", "setup", "ready", "home", "explore", "search", "clubs", "people", "events", "venues", "pools", "create-pool", "inbox", "notifications", "profile", "edit-profile", "passport", "map", "badges", "timeline", "places", "recap", "settings", "premium", "business", "approvals", "verification", "create-event", "create-offer", "promote", "compose", "saved", "plans") .map { it to "" } +
                listOf("club" to "runners", "person" to "aarav", "shared" to "riya", "connected" to "aarav", "post" to "sunset", "story" to "0", "event" to "market", "venue" to "riverside", "place" to "colosseum", "booking" to "riverside", "pool" to "table-tennis", "chat" to "aarav", "pass" to booking)
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
            pages.forEach { (page, id) ->
                scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); a.navigate(page, id, true) }
                instrumentation.waitForIdleSync()
                Thread.sleep(180)
                scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); assertTrue("Empty screen: $page", a.binding.content.childCount > 0 || a.binding.microHost.childCount > 0) }
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(directory, "$page.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                val repository = a.model.repository
                repository.toggle("sunset", "liked")
                repository.toggle("sunset", "saved")
                repository.send("aarav", "Coffee tomorrow?")
                assertTrue(repository.state.value.liked.contains("sunset"))
                assertTrue(repository.state.value.saved.contains("sunset"))
                assertEquals("Coffee tomorrow?", repository.state.value.messages["aarav"]!!.last().text)
                a.navigate("profile", root = true); a.navigate("passport")
                assertTrue(a.model.back()); assertEquals("profile", a.model.route.page)
                a.navigate("explore", root = true)
            }
            scenario.recreate()
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                assertTrue(a.model.repository.state.value.saved.contains("sunset"))
                assertEquals("explore", a.model.route.page)
            }
        }
    }
}
