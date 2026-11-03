package uz.callguard.app

import android.graphics.Bitmap
import android.graphics.Canvas
import org.robolectric.shadows.ShadowDialog
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import uz.callguard.app.data.CallGuardDatabase
import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.callguard.app.ui.FeedsScreen
import uz.callguard.app.ui.WeeklyStatsChart
import uz.callguard.app.ui.theme.CallGuardTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeedsAndChartRenderTest {
    @get:Rule val compose = createComposeRule()
    private var host: View? = null
    private var dialogSnapshotName = "feed-add-dialog"
    private fun capture(name: String): ImageBitmap = compose.runOnUiThread {
        val view = requireNotNull(host)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/reports/ui/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.asImageBitmap()
    }

    private fun layoutDialog() = compose.runOnUiThread {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
        ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let { view ->
            val density = view.resources.displayMetrics.density
            view.measure(View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((700 * density).toInt(), View.MeasureSpec.AT_MOST))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            if (view.width > 0 && view.height > 0) {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file = File("build/reports/ui/$dialogSnapshotName.png").apply { parentFile?.mkdirs() }
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun chartDrawsInBothThemesWithEmptyZeroAndExtremeData() {
        var data by mutableStateOf(listOf(3,7,1,14,5,2,9))
        var dark by mutableStateOf(false)
        var primary = Color.Unspecified
        compose.setContent {
            CallGuardTheme(darkTheme = dark, dynamicColor = false) {
                val color = MaterialTheme.colorScheme.primary
                val view = LocalView.current
                SideEffect { primary = color; host = view }
                Surface { Box(Modifier.width(320.dp).testTag("chart")) { WeeklyStatsChart(data) } }
            }
        }
        val light = capture("weekly-light")
        assertTrue(light.width > 0 && light.height > 0)
        val pixels = light.toPixelMap()
        var colored = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) if (pixels[x,y] == primary) colored++
        assertTrue("Bars must actually be painted", colored > 0)
        for (values in listOf(emptyList(), List(7) { 0 }, listOf(Int.MAX_VALUE, -1, 0))) {
            compose.runOnIdle { data = values; dark = true }
            compose.waitForIdle()
            val image = capture("weekly-dark-${values.size}-${values.firstOrNull() ?: 0}")
            assertEquals(light.width, image.width)
            assertTrue(image.height > 0)
        }
        assertNotEquals(light.toPixelMap()[0,0], capture("weekly-dark").toPixelMap()[0,0])
    }

    private fun nodes(view: View): List<SemanticsNode> {
        val result = mutableListOf<SemanticsNode>()
        fun visit(node: SemanticsNode) { result += node; node.children.forEach(::visit) }
        val getter = view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
        if (getter != null) visit((getter.invoke(view) as SemanticsOwner).unmergedRootSemanticsNode)
        else if (view is ViewGroup) for (i in 0 until view.childCount) result += nodes(view.getChildAt(i))
        return result
    }
    private fun hasText(node: SemanticsNode, text: String): Boolean =
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true || node.children.any { hasText(it, text) }
    private fun hasDescription(node: SemanticsNode, text: String): Boolean =
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(text) == true || node.children.any { hasDescription(it, text) }
    private fun pump() {
        compose.mainClock.advanceTimeBy(100)
        layoutDialog()
    }
    private fun dialogNodes(): List<SemanticsNode> = compose.runOnUiThread {
        nodes(requireNotNull(ShadowDialog.getLatestDialog()?.window?.decorView))
    }
    private fun clickDialog(text: String) = compose.runOnUiThread {
        val node = dialogNodes().first { it.config.getOrNull(SemanticsActions.OnClick) != null && hasText(it, text) }
        assertTrue(requireNotNull(node.config[SemanticsActions.OnClick].action).invoke())
    }

    @Test fun feedsScreenRendersDefaultFeedAndAddsAndDeletesSubscription() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            val view = LocalView.current
            SideEffect { host = view }
            CallGuardTheme(dynamicColor = false) { FeedsScreen(onBack = {}) }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Recommended database").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("https://1byte.uz/spam_base.json").assertIsDisplayed()
        // Dialog windows in headless Robolectric need explicit native traversal. Query their
        // actual accessibility semantics directly instead of waiting for a display VSync/PixelCopy.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(context.getString(R.string.feeds_add))
            .performSemanticsAction(SemanticsActions.OnClick) { assertTrue(it()) }
        compose.waitUntil(10_000) { pump(); compose.runOnUiThread { ShadowDialog.getLatestDialog()?.isShowing == true } }
        val fields = dialogNodes().filter { it.config.getOrNull(SemanticsActions.SetText) != null }
        assertEquals(2, fields.size)
        compose.runOnUiThread { assertTrue(requireNotNull(fields[0].config[SemanticsActions.SetText].action).invoke(AnnotatedString("Test feed"))) }
        pump()
        compose.runOnUiThread { assertTrue(requireNotNull(fields[1].config[SemanticsActions.SetText].action).invoke(AnnotatedString("https://example.org/feed.json"))) }
        pump()
        clickDialog(context.getString(R.string.add))
        compose.waitUntil(10_000) { pump(); compose.runOnUiThread { nodes(requireNotNull(host)).any { hasText(it, "Test feed") } } }
        repeat(3) { pump() }
        val dao = CallGuardDatabase.getInstance(context).spamFeedDao()
        val entry = runBlocking(Dispatchers.IO) { dao.getAll().first { it.name == "Test feed" }.also { dao.markSynced(it.id, 1_700_000_000_000L) } }
        repeat(3) { pump() }
        compose.runOnUiThread {
            val switches = nodes(requireNotNull(host)).filter { it.config.getOrNull(SemanticsProperties.ToggleableState) != null }
            assertEquals(2, switches.size)
            assertTrue(requireNotNull(switches.last().config[SemanticsActions.OnClick].action).invoke())
        }
        compose.waitUntil(10_000) {
            pump()
            runBlocking(Dispatchers.IO) { dao.findById(entry.id)?.isActive == false }
        }
        assertEquals(1_700_000_000_000L, runBlocking(Dispatchers.IO) { dao.findById(entry.id)!!.lastSync })
        repeat(3) { pump() }
        capture("feeds-list")
        dialogSnapshotName = "feed-delete-dialog"
        compose.runOnUiThread {
            val deletes = nodes(requireNotNull(host)).filter {
                it.config.getOrNull(SemanticsActions.OnClick) != null && hasDescription(it, context.getString(R.string.feeds_delete))
            }
            assertEquals(2, deletes.size)
            assertTrue(requireNotNull(deletes.last().config[SemanticsActions.OnClick].action).invoke())
        }
        compose.waitUntil(10_000) { pump(); compose.runOnUiThread { ShadowDialog.getLatestDialog()?.isShowing == true } }
        clickDialog(context.getString(R.string.yes))
        compose.waitUntil(10_000) { pump(); compose.runOnUiThread { nodes(requireNotNull(host)).none { hasText(it, "Test feed") } } }
        pump()
    }
}
