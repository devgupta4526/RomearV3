package app.romear.ui

import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.ImageView
import app.romear.R
import app.romear.data.CityData

fun CityScreens.publicPosts(personId: String) {
    ui.section(body, "Recent public posts")
    val post = when (personId) {
        "riya" -> CityData.posts.first { it.id == "sunset" }
        "meera" -> CityData.posts.first { it.id == "weekend" }
        else -> null
    }
    if (post != null) feedPost(post)
    else body.addView(ui.text("No public posts yet.", 14, color = ui.muted))
}

fun CityUi.postPhoto(resource: Int, crop: Boolean = false) = image(resource).apply {
    portraitPostFrame(crop)
}

fun ImageView.portraitPostFrame(crop: Boolean = false) {
    layoutParams = LinearLayout.LayoutParams(-1, -2)
    scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
    setBackgroundColor(0xFF191D19.toInt())
    addOnLayoutChangeListener { view, left, _, right, _, _, _, _, _ ->
        val height = (right - left) * 5 / 4
        if (height > 0 && view.layoutParams.height != height) {
            view.layoutParams = view.layoutParams.apply { this.height = height }
        }
    }
    adjustViewBounds = true
}


fun CityScreens.addPostMedia(parent: LinearLayout, key: String) {
    if (key.startsWith("video:")) {
        val post = s.posts.lastOrNull { it.image == key }
        if (post != null) ui.button(parent, "Watch reel", icon = R.drawable.ic_video) { go("reel", post.id) }
        else ui.button(parent, "Preview reel", false, R.drawable.ic_video) { go("reel", "draft") }
        return
    }
    CityData.referencePhoto(key)?.let { parent.addView(ui.postPhoto(it)); return }
    val uri = android.net.Uri.parse(key)
    if (uri.scheme != "content") return
    // Decode a display-sized bitmap so full-resolution camera photos stay within memory limits.
    if (android.os.Build.VERSION.SDK_INT < 28) {
        parent.addView(ImageView(host).apply { setImageURI(uri); portraitPostFrame() })
        return
    }
    val bitmap = runCatching {
        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(host.contentResolver, uri)) { decoder, info, _ ->
            val factor = maxOf(1, (maxOf(info.size.width, info.size.height) + 1599) / 1600)
            decoder.setTargetSampleSize(factor)
        }
    }.getOrNull()
    if (bitmap != null) parent.addView(ImageView(host).apply { setImageBitmap(bitmap); portraitPostFrame() })
    else parent.addView(ui.text("Photo unavailable", 13, color = ui.muted))
}
