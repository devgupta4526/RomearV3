package app.romear.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.view.WindowManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

internal fun CityScreens.showTransferCode(code: String) {
    if (route.page != "settings" || host.isFinishing) return
    val formatted = code.chunked(5).joinToString("-")
    val dialog = MaterialAlertDialogBuilder(host)
        .setTitle("Transfer account")
        .setMessage("Enter this code on the other device within 10 minutes. Redeeming it removes this device’s server access.\n\n$formatted")
        .setPositiveButton("Copy code") { _, _ ->
            val clip = ClipData.newPlainText("ROMEAR transfer code", formatted)
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            (host.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        }
        .setNegativeButton("Close", null)
        .show()
    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

internal fun CityScreens.showTransferInput() {
    val container = ui.column(20)
    val field = ui.input(container, "Transfer code")
    val dialog = MaterialAlertDialogBuilder(host)
        .setTitle("Restore your account")
        .setView(container)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Restore", null)
        .show()
    dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener { button ->
        val code = field.text.toString().filterNot { it == '-' || it.isWhitespace() }.lowercase(Locale.ROOT)
        if (!code.matches(Regex("[a-f0-9]{40}"))) {
            field.error = "Enter the complete transfer code"
            return@setOnClickListener
        }
        button.isEnabled = false
        model.authenticate({ message -> button.isEnabled = true; field.error = message }) {
            val profile = transferDevice(code)
            dialog.dismiss()
            if (route.page == "welcome") {
                if (profile == null) go("city") else {
                    repo.update { profile }
                    host.navigate("home", root = true)
                }
            }
        }
    }
}
