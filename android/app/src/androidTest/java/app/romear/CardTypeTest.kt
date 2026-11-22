package app.romear

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CardTypeTest {
    private fun find(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if (view is ViewGroup) for (n in 0 until view.childCount) find(view.getChildAt(n), predicate)?.let { return it }
        return null
    }
    private fun text(view: View, prefix: String) = find(view) { it is TextView && it.text.toString().startsWith(prefix) }
    @Test fun poolsAndClubsStayInTheirSelectedTabs() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                a.navigate("events", root = true)
                text(a.binding.root, "Small pools")!!.performClick()
                assertEquals("events", a.model.route.page)
                assertNotNull(text(a.binding.root, "Table Tennis Match"))
                text(a.binding.root, "Events")!!.let { heading ->
                    val chip = find(a.binding.root) { it is com.google.android.material.chip.Chip && it.text == "Events" }
                    assertNotNull(heading)
                    chip!!.performClick()
                }
                a.navigate("explore", root = true)
                find(a.binding.root) { it.isClickable && it.contentDescription == "Clubs" }!!.performClick()
                assertEquals("explore", a.model.route.page)
                assertNotNull(text(a.binding.root, "Trending clubs"))
                assertNotNull(text(a.binding.root, "Sponsored"))
                a.navigate("home", root = true)
                text(a.binding.root, "Nearby")!!.performClick()
                assertNotNull(find(a.binding.root) { it is org.osmdroid.views.MapView })
                assertNotNull(text(a.binding.root, "Riverside Night Market"))
                text(a.binding.root, "Reels")!!.performClick()
                text(a.binding.root, "Open reel viewer")!!.performClick()
                assertEquals("reel", a.model.route.page)
                assertTrue(a.binding.microHost.isShown)
                a.back()
                text(a.binding.root, "For you")!!.performClick()
                a.navigate("person", "riya", true)
                assertNotNull(text(a.binding.root, "Recent public posts"))
                assertNotNull(text(a.binding.root, "Sunset Run by the Tiber"))
            }
        }
    }
    @Test fun distinctCardsOpenTheirDetailsAndSaveIndependently() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "card-kinds").apply { mkdirs() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val cases = listOf(Triple("home", "Riya Sharma", "posts"), Triple("events", "CITY EVENT", "events"), Triple("clubs", "RUNNING COMMUNITY", "clubs"), Triple("pools", "OPEN POOL", "pools"))
            scenario.onActivity { a -> a.model.repository.update { it.copy(requested = emptySet(), saved = it.saved - "market") } }
            cases.forEach { (route, marker, name) ->
                scenario.onActivity { a ->
                    a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    a.navigate(route, root = true)
                    assertNotNull("Missing $name card identity", text(a.binding.root, marker))
                }
                instrumentation.waitForIdleSync(); Thread.sleep(200)
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
            }
            scenario.onActivity { a ->
                a.navigate("events", root = true)
                val bookmark = find(a.binding.root) { it.contentDescription?.toString() == "Save Riverside Night Market" }
                assertNotNull(bookmark); bookmark!!.performClick()
                assertEquals("events", a.model.route.page)
                assertTrue("market" in a.model.state.value.saved)
                text(a.binding.root, "View event & tickets")!!.performClick()
                assertEquals("event", a.model.route.page); assertEquals("market", a.model.route.id)
                a.navigate("clubs", root = true)
                text(a.binding.root, "Explore club")!!.performClick()
                assertEquals("club", a.model.route.page); assertEquals("runners", a.model.route.id)
                a.navigate("pools", root = true)
                text(a.binding.root, "View activity")!!.performClick()
                assertEquals("pool", a.model.route.page); assertEquals("table-tennis", a.model.route.id)
                a.navigate("home", root = true)
            }
        }
    }
}
