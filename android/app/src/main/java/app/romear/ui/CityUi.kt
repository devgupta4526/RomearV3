package app.romear.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.setPadding
import androidx.core.widget.doAfterTextChanged
import app.romear.R
import app.romear.data.CityItem
import app.romear.databinding.ViewPhotoCardBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CityUi(val context: Context) {
    val forest = Color.rgb(31, 80, 58)
    val ink = Color.rgb(24, 37, 31)
    val muted = Color.rgb(108, 112, 103)
    val sand = Color.rgb(240, 231, 218)
    val line = Color.rgb(230, 221, 208)
    val cream = Color.rgb(250, 247, 241)
    val rust = Color.rgb(185, 83, 45)
    fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    fun shape(color: Int = cream, radius: Int = 16, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    fun column(padding: Int = 0) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        setPadding(dp(padding))
    }
    fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    fun text(value: String, size: Int = 16, bold: Boolean = false, color: Int = ink) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1f)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    fun heading(parent: LinearLayout, title: String, subtitle: String? = null) {
        val toolbar = (context as? app.romear.MainActivity)?.binding?.toolbarTitle?.text?.toString().orEmpty()
        if (!title.equals(toolbar, true)) parent.addView(text(title, 30, true).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); accessibilityHeading() })
        if (subtitle != null) { gap(parent, 8); parent.addView(text(subtitle, 16, color = muted)) }
        gap(parent, 24)
    }
    private fun TextView.accessibilityHeading() { androidx.core.view.ViewCompat.setAccessibilityHeading(this, true) }
    fun section(parent: LinearLayout, title: String, action: String? = null, click: (() -> Unit)? = null) {
        gap(parent, 16)
        parent.addView(row().apply {
            addView(text(title, 21, true).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f); accessibilityHeading() })
            if (action != null) addView(text(action, 14, true, forest).apply {
                layoutParams = LinearLayout.LayoutParams(-2, dp(48)); gravity = Gravity.CENTER
                setPadding(dp(8), 0, 0, 0); setOnClickListener { click?.invoke() }
            })
        })
        gap(parent, 12)
    }
    fun gap(parent: LinearLayout, height: Int = 12) { parent.addView(Space(context), LinearLayout.LayoutParams(1, dp(height))) }
    fun divider(parent: LinearLayout) { gap(parent, 12); parent.addView(View(context).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(-1, dp(1))); gap(parent, 12) }
    fun button(parent: LinearLayout, title: String, primary: Boolean = true, icon: Int? = null, click: () -> Unit): MaterialButton {
        val button = MaterialButton(context).apply {
            text = title; isAllCaps = false; textSize = 16f
            letterSpacing = 0f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            minHeight = dp(52); cornerRadius = dp(18); insetTop = 0; insetBottom = 0
            setPadding(dp(16), dp(8), dp(16), dp(8))
            backgroundTintList = ColorStateList.valueOf(if (primary) forest else cream)
            setTextColor(if (primary) Color.WHITE else forest)
            if (!primary) { strokeWidth = dp(1); strokeColor = ColorStateList.valueOf(forest) }
            if (icon != null) { setIconResource(icon); iconTint = ColorStateList.valueOf(if (primary) Color.WHITE else forest); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
            setOnClickListener { click() }
        }
        val host = context as? app.romear.MainActivity
        val dockAction = title in setOf("Join Club", "Joined · Leave club", "Request to join", "Withdraw local request", "Book a Table", "Continue to Checkout", "Follow", "Following")
        if (host != null && parent === host.binding.content && host.model.route.page in setOf("pool", "club", "event", "venue", "person") && dockAction && host.binding.detailDock.childCount == 0) {
            host.binding.detailDock.addView(column().apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12) }
                addView(text(when(host.model.route.page) { "pool" -> if (title.startsWith("Withdraw")) "Request pending" else when(host.model.route.id) { "table-tennis" -> "3 places left"; "cycling", "study" -> "2 places left"; else -> "Join this activity" }; "club" -> if (title.startsWith("Joined")) "You’re a member" else "Free membership"; "person" -> "Public profile"; "event" -> "€0.00"; else -> "Book your visit" }, 14, true))
                addView(text(when(host.model.route.page) { "pool" -> "Organiser approval"; "club" -> "Your community"; "event" -> "General admission"; "venue" -> "No booking fee"; else -> "Rome, Italy" }, 11, color = muted))
            })
            button.layoutParams = LinearLayout.LayoutParams(0, -2, 1.4f)
            host.binding.detailDock.addView(button)
        } else parent.addView(button)
        return button
    }
    fun icon(resource: Int, description: String, click: (() -> Unit)? = null) = androidx.appcompat.widget.AppCompatImageButton(context).apply {
        setImageResource(resource); contentDescription = description; imageTintList = ColorStateList.valueOf(forest)
        setPadding(dp(12)); background = shape(Color.TRANSPARENT)
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        if (click != null) setOnClickListener { click() } else { isClickable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    }
    fun photo(parent: LinearLayout, item: CityItem, saved: Boolean, onSave: () -> Unit, onOpen: () -> Unit) {
        when (item.kind) {
            app.romear.data.Kind.EVENT -> { eventCard(parent, item, saved, onSave, onOpen); return }
            app.romear.data.Kind.POOL -> { poolCard(parent, item, saved, onSave, onOpen); return }
            app.romear.data.Kind.CLUB -> { clubCard(parent, item, saved, onSave, onOpen); return }
            else -> Unit
        }
        val b = ViewPhotoCardBinding.inflate(android.view.LayoutInflater.from(context), parent, false)
        b.photo.setImageResource(item.image); b.photo.contentDescription = item.title
        b.title.text = item.title; b.subtitle.text = item.subtitle
        b.category.text = item.category.ifBlank { item.kind.name.lowercase().replaceFirstChar { it.uppercase() } }
        b.bookmark.alpha = if (saved) 1f else .65f
        b.bookmark.contentDescription = if (saved) "Unsave ${item.title}" else "Save ${item.title}"
        b.bookmark.setImageResource(if (saved) R.drawable.ic_check else R.drawable.ic_bookmark)
        b.bookmark.setOnClickListener { onSave() }
        b.root.setOnClickListener { onOpen() }
        parent.addView(b.root)
    }
    fun image(resource: Int, height: Int = 190) = ImageView(context).apply {
        setImageResource(resource); scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LinearLayout.LayoutParams(-1, dp(height))
        background = shape(); clipToOutline = true
    }
    fun avatar(resource: Int, size: Int = 64) = image(resource, size).apply {
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        background = shape(sand, size); clipToOutline = true
    }
    fun card(parent: LinearLayout, padding: Int = 16, block: (LinearLayout) -> Unit): MaterialCardView {
        val card = MaterialCardView(context).apply {
            radius = dp(22).toFloat(); cardElevation = dp(1).toFloat()
            setCardBackgroundColor(Color.rgb(255, 254, 250)); strokeColor = line; strokeWidth = 0
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        }
        val inside = column(padding); block(inside); card.addView(inside); parent.addView(card); return card
    }
    fun chips(parent: LinearLayout, options: List<String>, selected: String, onSelected: (String) -> Unit) {
        val group = ChipGroup(context).apply { isSingleSelection = true; isSelectionRequired = true; chipSpacingHorizontal = dp(4) }
        options.forEach { label ->
            group.addView(Chip(context).apply {
                text = label; isCheckable = true; isChecked = label == selected; isCheckedIconVisible = false
                chipBackgroundColor = ColorStateList.valueOf(if (isChecked) forest else sand)
                setTextColor(if (isChecked) Color.WHITE else ink)
                chipStrokeWidth = 0f; chipMinHeight = dp(42).toFloat(); setEnsureMinTouchTargetSize(true)
                setOnClickListener { onSelected(label) }
            })
        }
        parent.addView(group); gap(parent, 16)
    }
    fun input(parent: LinearLayout, label: String, value: String = "", multiline: Boolean = false,
              type: Int = InputType.TYPE_CLASS_TEXT, changed: (String) -> Unit = {}): TextInputEditText {
        val wrapper = TextInputLayout(context).apply {
            hint = label; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxBackgroundColor = Color.rgb(255, 252, 247)
            boxStrokeColor = forest
            boxStrokeWidth = dp(1)
            boxStrokeWidthFocused = dp(2)
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        }
        val edit = TextInputEditText(context).apply {
            background = null
            setText(value); textSize = 16f; setTextColor(ink)
            inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES else type
            minLines = if (multiline) 4 else 1; maxLines = if (multiline) 8 else 1
            gravity = Gravity.TOP or Gravity.START; setPadding(dp(14)); minHeight = dp(56)
            doAfterTextChanged { changed(it?.toString().orEmpty()) }
        }
        wrapper.addView(edit); parent.addView(wrapper); return edit
    }
    fun action(parent: LinearLayout, title: String, subtitle: String = "", resource: Int = R.drawable.ic_arrow, click: () -> Unit) {
        val card = card(parent, 12) { col ->
            col.addView(row().apply {
                val person = app.romear.data.CityData.people.firstOrNull { title.contains(it.title) }
                if (person != null) addView(avatar(person.image, 44).apply { layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(10) } })
                else addView(icon(resource, title).apply { background = shape(0xFFEAF0E4.toInt(), 14); layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) } })
                addView(column().apply {
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                    addView(text(title, 15, true))
                    if (subtitle.isNotEmpty()) addView(text(subtitle, 12, color = muted))
                })
                addView(icon(R.drawable.ic_arrow, "Open $title"))
            })
        }
        card.minimumHeight = dp(64); card.setOnClickListener { click() }
    }
    fun note(parent: LinearLayout, value: String) { if (listOf("demo", "offline", "reference", "device", "not connected", "not delivered", "self-reported").any { value.contains(it, true) }) return; parent.addView(text(value, 13, color = muted)); gap(parent, 16) }
    fun empty(parent: LinearLayout, title: String, description: String) {
        gap(parent, 28)
        parent.addView(icon(R.drawable.ic_compass, title).apply { layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL } })
        parent.addView(text(title, 22, true).apply { gravity = Gravity.CENTER })
        gap(parent, 8); parent.addView(text(description, 15, color = muted).apply { gravity = Gravity.CENTER }); gap(parent, 24)
    }
    fun stats(parent: LinearLayout, values: List<Pair<String, String>>) {
        card(parent, 14) { box -> box.addView(row().apply {
            values.forEach { (value, label) -> addView(column().apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                addView(text(value, 25, true, forest).apply { gravity = Gravity.CENTER })
                addView(text(label, 11, color = muted).apply { gravity = Gravity.CENTER })
            }) }
        }) }
    }
}
