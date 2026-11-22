package app.romear.ui

import android.location.Location
import android.widget.LinearLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.romear.R
import app.romear.data.CityData
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

fun CityScreens.nearbyMap() {
    ui.heading(body, "Nearby", "Explore your area on the map.")
    val filter = draft("map-filter", "All")
    ui.chips(body, listOf("All", "People", "Events", "Pools"), filter) { save("map-filter", it); refresh() }
    Configuration.getInstance().apply {
        userAgentValue = "ROMEAR/0.1 (Android mock app)"
        osmdroidBasePath = File(host.cacheDir, "maps")
        osmdroidTileCache = File(osmdroidBasePath, "tiles")
    }
    val map = MapView(host).apply {
        layoutParams = LinearLayout.LayoutParams(-1, ui.dp(400))
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        setOnTouchListener { view, event ->
            view.parent.requestDisallowInterceptTouchEvent(event.actionMasked !in listOf(android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL))
            false
        }
    }
    body.addView(map)
    val observer = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) = map.onResume()
        override fun onPause(owner: LifecycleOwner) = map.onPause()
    }
    host.lifecycle.addObserver(observer)
    map.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: android.view.View) = Unit
        override fun onViewDetachedFromWindow(view: android.view.View) {
            host.lifecycle.removeObserver(observer)
            map.onDetach()
        }
    })
    val location = host.nearbyLocation
    val rome = GeoPoint(41.9028, 12.4964)
    map.controller.setZoom(13.0)
    map.controller.setCenter(location?.let { GeoPoint(it.latitude, it.longitude) } ?: rome)
    fun pin(point: GeoPoint, title: String, resource: Int, action: (() -> Unit)? = null) {
        map.overlays.add(Marker(map).apply {
            position = point
            this.title = title
            icon = androidx.core.content.ContextCompat.getDrawable(host, resource)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ -> action?.invoke(); true }
        })
    }
    location?.let { pin(GeoPoint(it.latitude, it.longitude), "Your approximate location", R.drawable.ic_user) }
    ui.action(body, "Use my location", "Centre on your approximate location", R.drawable.ic_pin) { host.locateNearby() }
    ui.note(body, "© OpenStreetMap contributors")
    val distance = FloatArray(1)
    if (location != null) Location.distanceBetween(location.latitude, location.longitude, rome.latitude, rome.longitude, distance)
    if (location != null && distance[0] > 25000) {
        ui.empty(body, "No activity listed here yet", "The sample people, events and pools are in Rome.")
        ui.button(body, "Explore Rome on the map", false) { map.controller.animateTo(rome) }
    }
    val groups = listOf("People", "Events", "Pools")
    val entries = listOf(CityData.people.take(2), CityData.events.take(2), CityData.pools.take(2))
    val points = listOf(41.911 to 12.480, 41.895 to 12.503, 41.902 to 12.473, 41.918 to 12.498, 41.895 to 12.486, 41.907 to 12.511)
    ui.section(body, "Sample activity in Rome")
    entries.forEachIndexed { group, items ->
        if (filter == "All" || filter == groups[group]) items.forEachIndexed { index, item ->
            val (lat, lon) = points[group * 2 + index]
            pin(GeoPoint(lat, lon), item.title, listOf(R.drawable.ic_user, R.drawable.ic_calendar, R.drawable.ic_users)[group]) { open(item) }
            ui.action(body, item.title, item.subtitle, R.drawable.ic_pin) { open(item) }
        }
    }
    map.invalidate()
}
