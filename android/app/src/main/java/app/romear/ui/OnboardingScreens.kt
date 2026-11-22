package app.romear.ui

import android.view.Gravity
import android.widget.LinearLayout
import app.romear.R

fun CityScreens.onboarding() {
    when (route.page) {
        "welcome" -> {
            body.addView(ui.image(R.drawable.photo_river, 280))
            ui.gap(body, 28)
            body.addView(ui.text("MEET · CREATE · EXPLORE", 11, true, ui.rust).apply { letterSpacing = .16f })
            ui.gap(body, 12)
            ui.heading(body, "Discover. Connect.\nBelong in your city.", "Find your people, your favourite places, and a reason to step outside.")
            ui.button(body, "Find my city", icon = R.drawable.ic_arrow) {
                if (model.auth.deviceEnabled) {
                    model.authenticate({ toast(it) }) {
                        val profile = registerDevice()
                        if (route.page == "welcome") {
                            if (profile == null) go("city") else {
                                repo.update { profile }
                                model.navigate("home", root = true)
                                refresh()
                            }
                        }
                    }
                } else go("signin")
            }
            ui.button(body, "Explore the demo", false) { model.auth.chooseDemo(); model.finishOnboarding(); refresh() }
            if (model.auth.deviceEnabled) ui.button(body, "Use a transfer code", false) { showTransferInput() }
            ui.note(body, "A city-first social network. Built around real people and real experiences.")
        }
        "signin" -> {
            ui.heading(body, "Sign up or Log in", "Enter your phone number or email to continue.")
            val method = draft("method", if (model.auth.enabled) "Email" else "Phone")
            ui.chips(body, listOf("Phone", "Email"), method) { save("method", it); refresh() }
            val example = if (model.auth.enabled) "" else if (method == "Phone") "+91 98765 43210" else "aarav@example.com"
            val contact = ui.input(body, if (method == "Phone") "Phone number" else "Email address", draft("contact", example)) { save("contact", it) }
            ui.gap(body, 120)
            ui.button(body, "Continue") {
                val address = contact.text.toString().trim()
                when {
                    address.isBlank() -> contact.error = "Enter your details"
                    !model.auth.enabled -> go("otp")
                    method == "Phone" -> contact.error = "Use email to sign in"
                    else -> startEmailChallenge(address)
                }
            }
        }
        "otp" -> {
            val live = model.auth.enabled
            val email = draft("email")
            ui.heading(body, if (live) "Verify your email" else "Verify your number", "Enter the 6-digit code sent to\n${if (live) email else "+91 98765 43210"}")
            val code = ui.input(body, "Verification code", draft("code", if (live) "" else "472916"), type = android.text.InputType.TYPE_CLASS_NUMBER) { save("code", it) }
            ui.gap(body, 24)
            ui.button(body, "Verify") {
                val entered = code.text.toString()
                if (!entered.matches(Regex("[0-9]{6}"))) code.error = "Enter six digits"
                else if (!live) go("city")
                else {
                    val challenge = draft("challenge")
                    model.authenticate({ toast(it) }) {
                        val profile = verify(challenge, entered)
                        if (route.page == "otp") {
                            save("code", "")
                            if (profile == null) go("city") else {
                                repo.update { profile }
                                model.navigate("home", root = true)
                                refresh()
                            }
                        }
                    }
                }
            }
            ui.button(body, "Resend code", false) {
                if (live) startEmailChallenge(email) else toast("Mock verification code: 472916")
            }
        }
        "city" -> {
            progress(1)
            ui.heading(body, "Select your city", "Choose a city to make your experience feel closer to home.")
            val current = draft("selected", s.city)
            listOf("Rome", "Milan", "Florence", "Naples", "Bologna", "Venice").forEach { city ->
                ui.action(body, city, if (city == current) "Selected city" else "Explore the community", if (city == current) R.drawable.ic_check else R.drawable.ic_pin) {
                    save("selected", city); refresh()
                }
            }
            ui.button(body, "Continue") { repo.update { it.copy(city = current) }; go("interests") }
            ui.note(body, "The reference collection features Rome. Other city selections update your profile; their discovery catalogues are not connected yet.")
        }
        "interests" -> {
            progress(2)
            ui.heading(body, "Choose your interests", "Pick at least three interests. Your people are out there.")
            val selected = draft("selected", s.interests.joinToString(",")).split(',').filter(String::isNotBlank).toSet()
            val options = listOf("Cafés" to R.drawable.photo_cafe, "Music" to R.drawable.photo_market, "Running" to R.drawable.photo_runners,
                "Culture" to R.drawable.photo_colosseum, "Travel" to R.drawable.photo_river, "Tech" to R.drawable.photo_tech,
                "Books" to R.drawable.photo_cafe_river, "Outdoors" to R.drawable.photo_park)
            options.chunked(2).forEach { pair ->
                body.addView(ui.row().apply {
                    pair.forEachIndexed { index, (label, image) ->
                        addView(ui.column().apply {
                            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { if (index == 0) marginEnd = ui.dp(12); bottomMargin = ui.dp(14) }
                            addView(ui.image(image, 108))
                            addView(ui.text(if (label in selected) "$label  ✓" else label, 15, true, if (label in selected) ui.forest else ui.ink).apply { setPadding(0, ui.dp(8), 0, ui.dp(8)) })
                            contentDescription = "$label, ${if (label in selected) "selected" else "not selected"}"
                            setOnClickListener {
                                save("selected", (if (label in selected) selected - label else selected + label).joinToString(",")); refresh()
                            }
                        })
                    }
                })
            }
            ui.button(body, "Continue (${selected.size} selected)") {
                if (selected.size < 3) toast("Choose at least three interests")
                else { repo.update { it.copy(interests = selected) }; go("setup") }
            }
        }
        "setup" -> {
            progress(3)
            ui.heading(body, "Create your profile", if (model.auth.hasSession) "Other signed-in members can see your name, bio, city and interests." else "Give your local profile a name and a story.")
            body.addView(ui.avatar(R.drawable.avatar_aarav, 104).apply { layoutParams = LinearLayout.LayoutParams(ui.dp(104), ui.dp(104)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = ui.dp(24) } })
            val name = ui.input(body, "Your name", draft("name", if (model.auth.hasSession) "" else s.name)) { save("name", it) }
            val username = ui.input(body, "Username", draft("username", if (model.auth.hasSession) "" else s.username)) { save("username", it) }
            val bio = ui.input(body, "A little about you", draft("bio", if (model.auth.hasSession) "" else s.bio), true) { save("bio", it) }
            ui.button(body, "Continue") {
                val n = name.text.toString().trim(); val u = username.text.toString().trim().removePrefix("@")
                if (n.length !in 2..50) name.error = "Use 2–50 characters"
                else if (!u.matches(Regex("[a-zA-Z0-9_.]{3,24}"))) username.error = "Use 3–24 letters, numbers, dots or underscores"
                else if (bio.text.toString().length > 160) bio.error = "Keep your bio under 160 characters"
                else { repo.update { it.copy(name = n, username = u, bio = bio.text.toString().trim()) }; go("ready") }
            }
        }
        "ready" -> {
            ui.gap(body, 32)
            body.addView(ui.icon(R.drawable.ic_check, "Ready").apply { layoutParams = LinearLayout.LayoutParams(ui.dp(88), ui.dp(88)).apply { gravity = Gravity.CENTER_HORIZONTAL }; background = ui.shape(ui.sand, 44) })
            ui.gap(body, 24)
            ui.heading(body, "Almost ready!", "Your ${s.city} story begins here, ${s.name.substringBefore(' ')}.")
            ui.action(body, "A city to discover", "Clubs, cafés and real experiences", R.drawable.ic_compass) { }
            ui.action(body, "Your privacy comes first", if (model.auth.hasSession) "Messages and personal plans are private to their participants" else "This demo keeps your activity on your device", R.drawable.ic_shield) { }
            ui.button(body, "Start exploring", icon = R.drawable.ic_arrow) {
                if (model.auth.hasSession) {
                    model.authenticate({ toast(it) }) {
                        saveProfile(repo.state.value, discoverable = true)
                        if (route.page == "ready") { model.finishOnboarding(); refresh() }
                    }
                } else {
                    model.finishOnboarding()
                    refresh()
                }
            }
        }
    }
}

private fun CityScreens.startEmailChallenge(email: String) {
    val startingPage = route.page
    model.authenticate({ toast(it) }) {
        val challenge = requestCode(email)
        if (route.page == startingPage) {
            model.saveDraft("otp__challenge", challenge)
            model.saveDraft("otp__email", email)
            model.saveDraft("otp__code", "")
            if (startingPage == "otp") refresh() else go("otp")
        }
    }
}

private fun CityScreens.progress(step: Int) {
    body.addView(ui.text("YOUR CITY, YOUR WAY   ·   $step / 3", 11, true, ui.rust).apply { letterSpacing = .1f })
    ui.gap(body, 20)
}
