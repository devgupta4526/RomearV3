package app.romear.ui

import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.VideoView
import app.romear.R

fun CityScreens.reelViewer() {
    val post = s.posts.firstOrNull { it.id == route.id }
    val draftVideo = if (route.id == "draft") model.draft("compose__image") else ""
    val media = (post?.image ?: draftVideo).takeIf { it.startsWith("video:") }
    val frame = FrameLayout(host).apply { setBackgroundColor(Color.BLACK) }
    host.binding.microHost.addView(frame, FrameLayout.LayoutParams(-1, -1))
    val viewport = FrameLayout(host)
    frame.addView(viewport, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
    frame.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
        val width = minOf(right - left, (bottom - top) * 9 / 16)
        val height = width * 16 / 9
        if (width > 0 && viewport.layoutParams.width != width) viewport.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
    }
    if (media != null) {
        val video = VideoView(host)
        viewport.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        video.setVideoURI(Uri.parse(media.removePrefix("video:")))
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onPause(owner: androidx.lifecycle.LifecycleOwner) { if (video.isPlaying) video.pause() }
        }
        host.lifecycle.addObserver(observer)
        video.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) = Unit
            override fun onViewDetachedFromWindow(view: android.view.View) {
                host.lifecycle.removeObserver(observer)
                video.stopPlayback()
            }
        })
        video.setOnPreparedListener { player ->
            player.isLooping = true
            if (host.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && video.isShown) video.start()
        }
        video.setOnErrorListener { _, _, _ -> toast("This video could not be played"); true }
        video.setOnClickListener { if (video.isPlaying) video.pause() else video.start() }
    } else {
        viewport.addView(ui.image(R.drawable.story_cafe).apply { contentDescription = "Portrait reel preview" }, FrameLayout.LayoutParams(-1, -1))
    }
    frame.addView(ui.icon(R.drawable.ic_close, "Close reel") { host.back() }.apply {
        imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        background = ui.shape(0x77000000, 24)
    }, FrameLayout.LayoutParams(ui.dp(48), ui.dp(48), Gravity.TOP or Gravity.START))
    frame.addView(ui.column(16).apply {
        background = ui.shape(0xAA000000.toInt(), 16)
        addView(ui.text(if (media == null) "Reel preview" else s.name, 17, true, Color.WHITE))
        addView(ui.text(post?.text ?: if (media == null) "Add a video to watch it here in portrait." else "Tap video to pause or play", 14, color = Color.WHITE))
    }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
}
