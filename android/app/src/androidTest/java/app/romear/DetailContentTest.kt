package app.romear

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.romear.data.LocalPool
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DetailContentTest {
    private fun paragraph(view: View, prefix: String): TextView? {
        if (view is TextView && view.text.toString().startsWith(prefix)) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) paragraph(view.getChildAt(index), prefix)?.let { return it }
        return null
    }
    private fun contains(view: View, text: String): Boolean {
        if (view is TextView && view.text.toString() == text) return true
        return view is ViewGroup && (0 until view.childCount).any { contains(view.getChildAt(it), text) }
    }
    @Test fun detailsAndCreatedPoolSurviveReopening() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "detail-review").apply { mkdirs() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val pages = listOf(
                Triple("pool", "table-tennis", listOf("Activity details", "What to expect", "Joining & participation", "Organised by Kabir Singh")),
                Triple("event", "market", listOf("Event information", "What’s happening", "Tickets & important details")),
                Triple("venue", "riverside", listOf("About the venue", "Facilities & services", "Before reserving")),
                Triple("person", "riya", listOf("About Riya", "Member since", "Communities")),
                Triple("profile", "", listOf("About you", "What visitors can see")),
                Triple("club", "runners", listOf("About the club", "Founded", "Before you join"))
            )
            pages.forEach { (page, id, expected) ->
                scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    a.navigate(page, id, true)
                    expected.forEach { assertTrue("$page missing $it", contains(a.binding.content, it)) }
                    if (page == "pool") {
                        assertTrue("Pool action must remain visible", a.binding.detailDock.isShown)
                        val text = paragraph(a.binding.content, "Meet at reception")!!
                        var target: View = text
                        while (!target.isClickable) target = target.parent as View
                        if (text.maxLines != 2) target.performClick()
                        assertEquals(2, text.maxLines)
                        target.performClick()
                        assertEquals(Int.MAX_VALUE, text.maxLines)
                        assertTrue(text.text.toString().contains("rotate partners"))
                        target.performClick()
                    }
                }
                instrumentation.waitForIdleSync()
                for (position in listOf(0, 450)) {
                    scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); a.binding.scroll.scrollTo(0, (position * a.resources.displayMetrics.density).toInt()) }
                    instrumentation.waitForIdleSync(); Thread.sleep(200)
                    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                        File(folder, "$page-$position.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                    }
                }
            }
            val description = "Bring a sketchbook and meet at the park gate for a relaxed drawing session."
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                a.model.repository.update { it.copy(pools = it.pools.filterNot { p -> p.id == "qa-pool" } + LocalPool("qa-pool", "Sketching together", "Park gate", "2026-12-12", 4, description, "4:00 PM")) }
                val savedState = a.getSharedPreferences("romear_local_v1", Context.MODE_PRIVATE)
                    .getString("state", "{}")
                val pools = JSONObject(savedState!!).getJSONArray("pools")
                val savedPool = (0 until pools.length()).map(pools::getJSONObject)
                    .first { it.getString("id") == "qa-pool" }
                assertEquals(description, savedPool.getString("description"))
                a.navigate("pool", "qa-pool")
                assertTrue(contains(a.binding.content, description))
                assertTrue(contains(a.binding.content, "Remove your pool"))
            }
            scenario.recreate()
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                assertEquals(description, a.model.state.value.pools.first { it.id == "qa-pool" }.description)
                assertEquals("4:00 PM", a.model.state.value.pools.first { it.id == "qa-pool" }.time)
                a.model.repository.update { it.copy(pools = it.pools.filterNot { p -> p.id == "qa-pool" }) }
                a.navigate("home", root = true)
            }
        }
    }
}
