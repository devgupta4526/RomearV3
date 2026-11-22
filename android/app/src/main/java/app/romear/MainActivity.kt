package app.romear

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.romear.databinding.ActivityMainBinding
import app.romear.ui.*
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    val model: CityViewModel by viewModels()
    lateinit var binding: ActivityMainBinding
    private lateinit var ui: CityUi
    private var drawn: Route? = null
    var microJob: kotlinx.coroutines.Job? = null
    var restoreChatFocus = false
    var nearbyLocation: android.location.Location? = null
        private set
    private val locationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {
        if (it) locateNearby() else CityScreens(this).toast("Location permission was not granted")
    }
    private var locationRequest: androidx.core.os.CancellationSignal? = null
    @android.annotation.SuppressLint("MissingPermission")
    fun locateNearby() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }
        val manager = getSystemService(android.location.LocationManager::class.java)
        val provider = manager.getBestProvider(android.location.Criteria().apply { accuracy = android.location.Criteria.ACCURACY_COARSE }, true)
        if (provider == null) { CityScreens(this).toast("Turn on device location and try again"); return }
        locationRequest?.cancel()
        val request = androidx.core.os.CancellationSignal()
        locationRequest = request
        androidx.core.location.LocationManagerCompat.getCurrentLocation(manager, provider, request, androidx.core.content.ContextCompat.getMainExecutor(this)) { result ->
            if (isDestroyed) return@getCurrentLocation
            if (result == null) CityScreens(this).toast("Location unavailable. Try again outdoors or with Wi-Fi enabled.")
            else {
                nearbyLocation = result
                if (model.route.page == "home" && CityScreens(this).draft("tab") == "Nearby") render()
            }
        }
    }

    override fun onDestroy() {
        locationRequest?.cancel()
        super.onDestroy()
    }
    private val onboarding = setOf("welcome", "signin", "otp", "city", "interests", "setup", "ready")
    val postPhotoPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && model.route.page == "compose") {
            try {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val prefix = if (contentResolver.getType(uri)?.startsWith("video/") == true) "video:" else ""
                CityScreens(this).save("image", prefix + uri.toString())
                render()
            } catch (_: SecurityException) {
                CityScreens(this).toast("Choose a photo from another provider")
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ui = CityUi(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        androidx.core.view.WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            binding.bottomNav.isVisible = model.route.page !in onboarding && model.route.page !in setOf("chat", "story", "reel") && binding.detailDock.childCount == 0 && !insets.isVisible(WindowInsetsCompat.Type.ime())
            WindowInsetsCompat.CONSUMED
        }
        binding.leading.setOnClickListener {
            if (model.route.page in setOf("home", "explore", "clubs", "events", "inbox", "profile", "welcome")) {
                CityScreens(this).menu()
            } else back()
        }
        binding.trailing.setOnClickListener { navigate(when(model.route.page) { "profile" -> "settings"; "inbox" -> "people"; else -> "notifications" }) }
        binding.compose.setOnClickListener { if (model.route.page == "inbox") CityScreens(this).choose("New message", app.romear.data.CityData.people.map { it.title }) { name -> navigate("chat", app.romear.data.CityData.people.first { it.title == name }.id) } else navigate("compose") }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!model.back()) { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
                else render()
            }
        })
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect { render() } } }
        render()
    }
    fun navigate(page: String, id: String = "", root: Boolean = false) {
        currentFocus?.let { focus ->
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(focus.windowToken, 0)
            focus.clearFocus()
        }
        model.navigate(page, id, root); render()
    }
    fun back() { if (model.back()) render() else navigate("home", root = true) }
    fun render() {
        val route = model.route
        val samePage = drawn == route
        val scrollY = if (samePage) binding.scroll.scrollY else 0
        drawn = route
        microJob?.cancel()
        binding.microHost.removeAllViews()
        val micro = route.page in setOf("chat", "story", "reel")
        binding.microHost.isVisible = micro
        binding.toolbar.isVisible = !micro
        binding.scroll.isVisible = !micro
        binding.root.setBackgroundColor(if (route.page == "story") android.graphics.Color.BLACK else ui.cream)
        androidx.core.view.WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = route.page != "story"
            isAppearanceLightNavigationBars = route.page != "story"
        }
        binding.content.removeAllViews()
        binding.detailDock.removeAllViews()
        binding.detailDock.isVisible = false
        val rootPage = route.page in setOf("home", "explore", "clubs", "events", "inbox", "profile", "welcome")
        binding.leading.setImageResource(if (rootPage) R.drawable.ic_menu else R.drawable.ic_back)
        binding.leading.contentDescription = if (rootPage) "Open city menu" else "Go back"
        binding.leading.isVisible = route.page != "inbox"
        binding.toolbarTitle.text = if (route.page == "profile") "Profile" else if (rootPage) "ROMEAR" else CityScreens.titles[route.page].orEmpty()
        if (route.page in setOf("people", "club", "shared", "connected")) binding.toolbarTitle.text = ""
        binding.toolbarTitle.textSize = if (rootPage) 22f else 18f
        binding.toolbarTitle.letterSpacing = if (rootPage) .09f else 0f
        binding.toolbarTitle.gravity = if (route.page == "inbox") Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
        if (route.page == "inbox") { binding.toolbarTitle.text = "Chat"; binding.toolbarTitle.letterSpacing = 0f }
        binding.trailing.setImageResource(if (route.page == "profile") R.drawable.ic_settings else R.drawable.ic_bell)
        binding.trailing.contentDescription = if (route.page == "profile") "Settings" else "Notifications"
        if (route.page == "inbox") { binding.trailing.setImageResource(R.drawable.ic_search); binding.trailing.contentDescription = "Find a person to message" }
        binding.trailing.isVisible = route.page !in onboarding
        binding.bottomNav.isVisible = route.page !in onboarding && !micro
        binding.compose.isVisible = route.page in setOf("home", "inbox")
        navItems(route.page)
        CityScreens(this).render()
        if (binding.detailDock.childCount > 0) {
            binding.detailDock.isVisible = true
            binding.bottomNav.isVisible = false
        }
        binding.scroll.post { binding.scroll.scrollTo(0, scrollY) }
    }
    private fun navItems(page: String) {
        binding.bottomNav.removeAllViews()
        val selected = when (page) {
            "home", "compose", "post", "story", "saved" -> "home"
            "explore", "search", "people", "person", "shared", "connected", "venues", "venue", "places" -> "explore"
            "clubs", "club", "verification" -> "clubs"
            "pools", "pool", "create-pool" -> "events"
            "events", "event", "booking", "plans", "pass" -> "events"
            "inbox", "chat" -> "inbox"
            else -> "profile"
        }
        listOf(Triple("home", "Home", R.drawable.ic_home), Triple("explore", "Explore", R.drawable.ic_compass),
            Triple("clubs", "Clubs", R.drawable.ic_users), Triple("events", "Events", R.drawable.ic_calendar),
            Triple("inbox", "Chat", R.drawable.ic_chat), Triple("profile", "Profile", R.drawable.ic_user)).forEach { (key, title, icon) ->
            val active = key == selected
            val cell = ui.column().apply {
                gravity = Gravity.CENTER; minimumHeight = ui.dp(60)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                background = ui.shape(if (active) android.graphics.Color.rgb(234, 237, 218) else android.graphics.Color.TRANSPARENT, 20)
                addView(ui.icon(icon, title).apply { layoutParams = LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)); setPadding(ui.dp(5)); imageTintList = ColorStateList.valueOf(if (active) ui.forest else ui.ink) })
                addView(ui.text(title, 10, active, if (active) ui.forest else ui.ink).apply { gravity = Gravity.CENTER })
                contentDescription = title; isSelected = active
                setOnClickListener { navigate(key, root = true) }
            }
            binding.bottomNav.addView(cell)
        }
    }
}
