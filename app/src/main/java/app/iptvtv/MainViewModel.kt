package app.iptvtv

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.iptvtv.data.*
import app.iptvtv.player.PlayerController
import app.iptvtv.update.UpdateState
import app.iptvtv.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class Screen { Setup, List, Guide, Player }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    private val repo = ChannelRepository(db.channelDao(), db.epgDao(), db.historyDao())
    private val settings = SettingsStore(app)

    val controller = PlayerController(app)

    private val updater: Updater? = BuildConfig.UPDATE_REPO.takeIf { it.isNotBlank() }
        ?.let { Updater(app, it, BuildConfig.VERSION_NAME) }
    val updatesEnabled = updater != null
    val versionName: String = BuildConfig.VERSION_NAME

    val channels = repo.channels.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val m3uUrl = settings.m3uUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val epgUrl = settings.epgUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Ostatnio oglądane (najnowsze pierwsze). null = jeszcze nie wczytane z bazy. */
    val history: StateFlow<List<ChannelEntity>?> =
        combine(repo.channels, repo.recentUrls) { chs, urls ->
            val byUrl = chs.associateBy { it.url }
            urls.mapNotNull { byUrl[it] }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Program "teraz / następnie", odświeżany co minutę i po każdym pobraniu EPG. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val guide: StateFlow<EpgSnapshot> = flow {
        while (true) {
            emit(System.currentTimeMillis() / 60_000 * 60_000)
            delay(60_000)
        }
    }
        .flatMapLatest { t ->
            repo.epgWindow(t, t + 3 * 3_600_000L).map { list ->
                EpgSnapshot(t, list.groupBy { it.channelKey })
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EpgSnapshot())

    /** Programy z najbliższych ~11 godzin dla siatki EPG (odświeżane co 5 min). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val gridData: StateFlow<Map<String, List<ProgrammeEntity>>> = flow {
        while (true) {
            emit(System.currentTimeMillis() / 300_000 * 300_000)
            delay(300_000)
        }
    }
        .flatMapLatest { t ->
            repo.epgWindow(t - 3_600_000L, t + 10 * 3_600_000L).map { list -> list.groupBy { it.channelKey } }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    var screen by mutableStateOf<Screen?>(null); private set
    var guideChannels by mutableStateOf<List<ChannelEntity>>(emptyList()); private set
    private var playOrigin = Screen.List
    var loading by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    var queue by mutableStateOf<List<ChannelEntity>>(emptyList()); private set
    var startIndex by mutableIntStateOf(0); private set
    var update by mutableStateOf<UpdateState>(UpdateState.Idle); private set

    private var historyJob: Job? = null

    init {
        viewModelScope.launch {
            val m3u = settings.m3uUrl.first()
            val epg = settings.epgUrl.first()
            if (m3u.isBlank()) {
                screen = Screen.Setup
            } else {
                screen = Screen.List
                refresh(m3u, epg)
            }
        }
        viewModelScope.launch {
            delay(4_000)
            checkUpdate(manual = false)
        }
    }

    fun refresh(m3u: String = m3uUrl.value, epg: String = epgUrl.value) {
        if (loading || (m3u.isBlank() && epg.isBlank())) return
        viewModelScope.launch {
            loading = true
            val parts = mutableListOf<String>()
            if (m3u.isNotBlank()) {
                status = "Pobieram playlistę…"
                parts += try {
                    "Kanały: ${repo.refresh(m3u)}"
                } catch (e: Exception) {
                    "Playlista: błąd (${e.message})"
                }
            }
            if (epg.isNotBlank()) {
                status = "Pobieram EPG…"
                parts += try {
                    "EPG: ${repo.refreshEpg(epg)} programów"
                } catch (e: Exception) {
                    "EPG: błąd (${e.message})"
                }
            }
            status = parts.joinToString("\n")
            loading = false
        }
    }

    fun save(m3u: String, epg: String) {
        val m = m3u.trim()
        val e = epg.trim()
        viewModelScope.launch {
            settings.save(m, e)
            screen = Screen.List
            refresh(m, e)
        }
    }

    fun openSettings() {
        controller.stop()
        screen = Screen.Setup
    }

    fun backToList() { screen = Screen.List }

    fun openGuide(list: List<ChannelEntity>) {
        guideChannels = list
        screen = Screen.Guide
    }

    fun closePlayer() { screen = playOrigin }

    fun play(list: List<ChannelEntity>, index: Int) {
        playOrigin = if (screen == Screen.Guide) Screen.Guide else Screen.List
        queue = list
        startIndex = index
        screen = Screen.Player
    }

    /** Przełącza odtwarzacz na kanał (podgląd lub pełny ekran); po 5 s oglądania trafia do historii. */
    fun tune(ch: ChannelEntity) {
        controller.play(ch)
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            delay(5_000)
            repo.markWatched(ch.url)
        }
    }

    fun toggleFavorite(ch: ChannelEntity) {
        viewModelScope.launch { repo.setFavorite(ch.id, !ch.favorite) }
    }

    // ---- aktualizacje ----
    fun checkUpdate(manual: Boolean) {
        val u = updater ?: return
        if (update is UpdateState.Checking || update is UpdateState.Downloading) return
        viewModelScope.launch {
            update = UpdateState.Checking
            update = try {
                u.check()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
            } catch (e: Exception) {
                if (manual) UpdateState.Failed("Nie udało się sprawdzić aktualizacji: ${e.message}") else UpdateState.Idle
            }
        }
    }

    fun installUpdate() {
        val u = updater ?: return
        when (val s = update) {
            is UpdateState.Available -> viewModelScope.launch {
                update = UpdateState.Downloading(s.info, 0f)
                update = try {
                    val file = u.download(s.info) { p -> update = UpdateState.Downloading(s.info, p) }
                    u.install(file)
                    UpdateState.Ready(s.info, file)
                } catch (e: Exception) {
                    UpdateState.Failed("Pobieranie nie powiodło się: ${e.message}")
                }
            }
            is UpdateState.Ready -> u.install(s.file)
            else -> Unit
        }
    }

    fun dismissUpdate() { update = UpdateState.Idle }

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }
}
