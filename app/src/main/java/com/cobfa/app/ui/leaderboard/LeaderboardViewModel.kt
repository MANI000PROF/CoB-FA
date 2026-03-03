package com.cobfa.app.ui.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cobfa.app.data.remote.FirestoreService
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LeaderboardViewModel(
    private val firestore: FirestoreService = FirestoreService()
) : ViewModel() {

    enum class Mode { CITY, STATE }

    private val _mode = MutableStateFlow(Mode.CITY)
    val mode: StateFlow<Mode> = _mode

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _rows = MutableStateFlow<List<FirestoreService.PublicUser>>(emptyList())
    val rows: StateFlow<List<FirestoreService.PublicUser>> = _rows

    private val _myUser = MutableStateFlow<FirestoreService.PublicUser?>(null)
    val myUser: StateFlow<FirestoreService.PublicUser?> = _myUser

    private val _myRow = MutableStateFlow<FirestoreService.PublicUser?>(null)
    val myRow: StateFlow<FirestoreService.PublicUser?> = _myRow

    private val _myRank = MutableStateFlow<Int?>(null)
    val myRank: StateFlow<Int?> = _myRank

    private var loadJob: Job? = null

    fun setMode(m: Mode) {
        _mode.value = m
    }

    fun load(city: String, state: String, currentUid: String) {
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _loading.value = true
            _error.value = null

            val res = when (_mode.value) {
                Mode.CITY -> firestore.fetchCityLeaderboard(city = city, state = state)
                Mode.STATE -> firestore.fetchStateLeaderboard(state = state)
            }

            val meRes = firestore.fetchMyPublicUser()
            _myUser.value = meRes.getOrNull()

            if (res.isSuccess) {
                _rows.value = res.getOrNull().orEmpty()

                val list = res.getOrNull().orEmpty()
                _rows.value = list
                val idx = list.indexOfFirst { it.uid == currentUid }
                _myRow.value = if (idx >= 0) list[idx] else null
                _myRank.value = if (idx >= 0) idx + 1 else null
            } else {
                val e = res.exceptionOrNull()
                _error.value = when (e) {
                    is FirebaseFirestoreException ->
                        if (e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION)
                            "Leaderboard is initializing (index required). Create the Firestore index from the Logcat link, then retry."
                        else
                            e.message ?: "Failed to load leaderboard"
                    else -> e?.message ?: "Failed to load leaderboard"
                }
            }

            _loading.value = false
        }
    }

}
