package app.romear

import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MicroScreenTest {
    @Test fun conversationStoryAndKeyboard() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "micro-screens").apply { mkdirs() }
        fun capture(name: String) {
            instrumentation.waitForIdleSync(); Thread.sleep(250)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); a.navigate("chat", "aarav", true) }
            capture("chat-direct")
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                assertEquals(View.GONE, a.binding.bottomNav.visibility)
                a.findViewById<EditText>(R.id.messageInput).setText("See you at the cafe!")
                a.findViewById<View>(R.id.send).performClick()
                assertEquals("See you at the cafe!", a.model.state.value.messages["aarav"]!!.last().text)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                val input = a.findViewById<EditText>(R.id.messageInput)
                input.requestFocus()
                (a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            Thread.sleep(700); capture("chat-keyboard")
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                val composer = a.findViewById<View>(R.id.composer)
                assertTrue(composer.isShown)
                assertTrue(composer.bottom <= a.binding.root.height)
                val headerPosition = IntArray(2)
                a.findViewById<View>(R.id.chatHeader).getLocationOnScreen(headerPosition)
                assertTrue("Chat header was panned off screen by keyboard", headerPosition[1] >= 0)
                a.navigate("chat", "runners")
            }
            capture("chat-group")
            scenario.onActivity { it.navigate("story", "0", true) }
            capture("story-cafe")
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                assertEquals(View.GONE, a.binding.toolbar.visibility)
                assertEquals(View.GONE, a.binding.bottomNav.visibility)
                val touch = a.findViewById<View>(R.id.storyTouch)
                val now = android.os.SystemClock.uptimeMillis()
                touch.dispatchTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, touch.width * .9f, 100f, 0))
                touch.dispatchTouchEvent(MotionEvent.obtain(now, now + 70, MotionEvent.ACTION_UP, touch.width * .9f, 100f, 0))
                assertEquals("1", a.model.route.id)
            }
            capture("story-run")
            scenario.onActivity { a ->
                a.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                a.findViewById<EditText>(R.id.storyReply).setText("Love this route!")
                a.findViewById<View>(R.id.storySend).performClick()
                assertEquals("chat", a.model.route.page)
                assertEquals("riya", a.model.route.id)
                assertEquals("Love this route!", a.model.state.value.messages["riya"]!!.last().text)
                a.navigate("inbox", root = true)
            }
            capture("inbox")
            scenario.onActivity { it.navigate("home", root = true) }
        }
    }
}
