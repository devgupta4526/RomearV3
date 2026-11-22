package app.romear.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import app.romear.data.CityAuth
import app.romear.data.CityRepository
import java.io.IOException
import kotlinx.coroutines.launch

data class Route(val page: String, val id: String = "")

class CityViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val repository = CityRepository.get(application)
    val state = repository.state
    internal val auth = CityAuth(application)
    private var authInProgress = false

    internal fun authenticate(onError: (String) -> Unit, action: suspend CityAuth.() -> Unit) {
        if (authInProgress) return
        authInProgress = true
        val startingRoute = route
        viewModelScope.launch {
            try {
                auth.action()
            } catch (exception: IOException) {
                if (route == startingRoute) onError(exception.message ?: "Could not connect. Try again.")
            } finally {
                authInProgress = false
            }
        }
    }
    var route: Route
        get() = Route(saved["page"] ?: if (state.value.onboarded) "home" else "welcome", saved["entity"] ?: "")
        set(value) { saved["page"] = value.page; saved["entity"] = value.id }
    private var history: ArrayList<String>
        get() = saved["history"] ?: arrayListOf()
        set(value) { saved["history"] = value }
    fun navigate(page: String, id: String = "", root: Boolean = false) {
        if (route == Route(page, id)) return
        history = if (root) arrayListOf() else ArrayList(history + "${route.page}|${route.id}")
        route = Route(page, id)
    }
    fun back(): Boolean {
        if (history.isEmpty()) return false
        val previous = history.last().split('|', limit = 2)
        history = ArrayList(history.dropLast(1))
        route = Route(previous[0], previous.getOrElse(1) { "" })
        return true
    }
    fun finishOnboarding() {
        repository.update { it.copy(onboarded = true) }
        navigate("home", root = true)
    }
    fun draft(key: String, default: String = ""): String = saved["draft_$key"] ?: default
    fun saveDraft(key: String, value: String) { saved["draft_$key"] = value }
    fun reset() {
        viewModelScope.launch { auth.signOut() }
        saved.keys().toList().forEach { saved.remove<Any>(it) }
        route = Route("welcome")
        repository.reset()
    }
}
