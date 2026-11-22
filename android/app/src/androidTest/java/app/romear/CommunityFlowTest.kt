package app.romear

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommunityFlowTest {
    @Test
    fun deviceAccountCanSaveAndCancelPersonalPlan() {
        assumeTrue(BuildConfig.DEVICE_AUTH && BuildConfig.BACKEND_URL.startsWith("http://127.0.0.1:"))
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("romear_auth", Context.MODE_PRIVATE).edit().clear().commit()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                it.model.reset()
                it.render()
            }
            click(scenario, "Find my city")
            waitFor(scenario, "city")
            click(scenario, "Continue")
            click(scenario, "Continue (3 selected)")
            scenario.onActivity {
                val fields = views(it.binding.content).filterIsInstance<EditText>().toList()
                fields[0].setText("Community QA")
                fields[1].setText("qa_${System.currentTimeMillis()}")
            }
            click(scenario, "Continue")
            click(scenario, "Start exploring")
            waitFor(scenario, "home")
            scenario.onActivity { it.navigate("venue", "riverside") }
            click(scenario, "Book a Table")
            click(scenario, "Save plan")
            waitFor(scenario, "pass")
            scenario.onActivity {
                assertEquals(1, it.model.personalPlans.size)
                assertEquals("riverside", it.model.personalPlans.single().itemId)
            }
            click(scenario, "Cancel plan")
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val confirm = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
                .findAccessibilityNodeInfosByText("Cancel plan")
                .first { it.text?.toString() == "Cancel plan" && it.isClickable }
            assertTrue(confirm.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitFor(scenario, "pass", cancelled = true)
        }
    }

    private fun click(scenario: ActivityScenario<MainActivity>, label: String) {
        scenario.onActivity {
            val button = views(it.binding.root).filterIsInstance<TextView>().firstOrNull { view -> view.text.toString() == label }
                ?: error("Missing button: $label on ${it.model.route.page}")
            assertTrue("Button: $label", button.performClick())
        }
    }

    private fun waitFor(scenario: ActivityScenario<MainActivity>, page: String, cancelled: Boolean = false) {
        repeat(100) {
            var arrived = false
            scenario.onActivity { activity ->
                arrived = activity.model.route.page == page &&
                    (!cancelled || activity.model.personalPlans.singleOrNull()?.cancelled == true)
            }
            if (arrived) return
            Thread.sleep(100)
        }
        error("Timed out waiting for $page")
    }

    private fun views(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
        }
    }
}
