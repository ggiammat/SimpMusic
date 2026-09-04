package com.maxrave.simpmusic.tasker.actions

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import com.joaomgcd.taskerpluginlibrary.SimpleResultError
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.maxrave.common.Config
import com.maxrave.common.LOCAL_PLAYLIST_ID
import com.maxrave.domain.mediaservice.handler.PlaylistType
import com.maxrave.domain.mediaservice.handler.QueueData
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.simpmusic.tasker.TaskerConfigurationScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.tasker.CommonRunner
import com.maxrave.simpmusic.tasker.TaskerConfigurationItem
import com.maxrave.simpmusic.R
import kotlinx.coroutines.flow.collectLatest
import multiplatform.network.cmptoast.ToastDuration
import multiplatform.network.cmptoast.ToastGravity
import multiplatform.network.cmptoast.showToast
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue
import kotlin.collections.first
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.utils.toTrack
import kotlinx.coroutines.flow.map
import kotlin.getValue

enum class AutoPlaylist(val title: String, val description: String? = null) {
    Liked("Liked Songs"),
    DownloadedSongs("Downloaded Songs"),
    MostPlayed("Most Played"),
    RecentPlayed("Recent Played"),
    ArtistSongs("Artist Songs"),
    AlbumSongs("Album Songs"),
    ArtistRadio(
        "Artist Radio",
        "Start radio of the current artist or the artist with the id specified in the Argument input"
    );
    /*
    QuickPics("[Auto] Quick Picks", ""),

    Recommendations("[Auto] Recommendations"),

    KeepListing("[Auto] Keep Listening"),

    ForgottenFavorites("[Auto] Forgotten Favorites"),

    MostPlayed("[Auto] Most Played Songs", "Argument specify the number of days"),
    Downloaded("[Auto] Downloaded Songs"),


    ArtistSongs("[Auto] Artist Songs"),
    AlbumSongs("[Auto] Album Songs");

    */

    fun toPair() = Pair("[Auto] " + title, description)
}

enum class SortType {
    Natural,
    Shuffled
}


@TaskerInputRoot
class PlaylistInput @JvmOverloads constructor(
    @field:TaskerInputField(
        "playlistName",
        labelResIdName = "tasker_input_playlist_name",
        descriptionResIdName = "tasker_input_playlist_name_description"
    ) var playlistName: String? = null,

    @field:TaskerInputField(
        "arg",
        labelResIdName = "tasker_input_arg",
        descriptionResIdName = "tasker_input_arg_description"
    ) var arg: String? = null,

    @field:TaskerInputField(
        "sorting",
        labelResIdName = "tasker_input_sorting",
        descriptionResIdName = "tasker_input_sorting_description"
    ) var sorting: String? = null,

    @field:TaskerInputField(
        "limit",
        labelResIdName = "tasker_input_limit",
        descriptionResIdName = "tasker_input_limit_description"
    ) var limit: String? = null
)

@TaskerOutputObject
class PlaylistOutput(
)


class PlaylistActionHelper(config: TaskerPluginConfig<PlaylistInput>) :
    TaskerPluginConfigHelper<PlaylistInput, PlaylistOutput, PlaylistActionRunner>(config) {
    override val inputClass = PlaylistInput::class.java
    override val outputClass = PlaylistOutput::class.java
    override val runnerClass = PlaylistActionRunner::class.java
}


//@AndroidEntryPoint
class PlaylistConfigActivity : ComponentActivity(), TaskerPluginConfig<PlaylistInput>, KoinComponent {

    val playlistRepository by inject<PlaylistRepository>()
    val localPlaylistRepository by inject<LocalPlaylistRepository>()
    /*
    @Inject
    lateinit var database: MusicDatabase
    */

    override val context get() = applicationContext

    val playlistName = mutableStateOf("")
    val arg = mutableStateOf("")
    val sorting = mutableStateOf("")
    val limit = mutableStateOf("")

    override fun assignFromInput(input: TaskerInput<PlaylistInput>) {
        playlistName.value = input.regular.playlistName ?: AutoPlaylist.Liked.title
        arg.value = input.regular.arg ?: ""
        sorting.value = input.regular.sorting ?: SortType.Natural.toString()
        limit.value = input.regular.limit ?: ""
    }

    override val inputForTasker: TaskerInput<PlaylistInput>
        get() = TaskerInput(
            PlaylistInput(
                playlistName = playlistName.value,
                arg = arg.value,
                sorting = sorting.value,
                limit = limit.value
            )
        )

    private val taskerHelper by lazy { PlaylistActionHelper(this) }

    private fun getPlaylistNames(): List<Pair<String, String?>> {

        val res: MutableList<Pair<String, String?>> = mutableListOf()
        runBlocking {
            res += (playlistRepository.getLibraryPlaylist().first() ?: emptyList()).map { Pair("[YT] " + it.title, null) }
            res += Pair("---", null)
            res += (playlistRepository.getMixedForYou().first() ?: emptyList()).map{ Pair("[YT Mix] " + it.title, null)}
            res += Pair("---", null)
            res += (localPlaylistRepository.getAllLocalPlaylists().first() ?: emptyList()).map { Pair("[Local] " + it.title, null) }
        }
        return res
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContent {
            TaskerConfigurationScreen(
                title = "Configure Playback Command action"
            ) {
                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.tasker_input_playlist_name),
                    inputDescription = stringResource(R.string.tasker_input_playlist_name_description),
                    playlistName,
                    inputOptions = AutoPlaylist.entries.map { it.toPair() }.plus(Pair("---", null))
                        .plus(getPlaylistNames()),
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )

                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.tasker_input_arg),
                    inputDescription = stringResource(R.string.tasker_input_arg_description),
                    arg,
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )

                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.tasker_input_sorting),
                    inputDescription = stringResource(R.string.tasker_input_sorting_description),
                    sorting,
                    inputOptions = SortType.entries.map { Pair(it.toString(), null) },
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )

                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.tasker_input_limit),
                    inputDescription = stringResource(R.string.tasker_input_limit_description),
                    limit,
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )
            }
        }

        taskerHelper.onCreate()
        //taskerHelper.finishForTasker()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val result = taskerHelper.onBackPressed()
                if (result is SimpleResultError) {
                    Logger.d("Tasker", "Settings are not valid:\n\n${result.message}")
                }
                if (result.success) finish()
            }
        })
    }
}

class TaskerQueueData(
    val listTracks: List<Track>,
    val playlistId: String?,
    val playlistType: PlaylistType,
)

class PlaylistActionRunner : CommonRunner<PlaylistInput, PlaylistOutput>() {


    suspend fun startMixPlaylist(
        playlistName: String,
        arg: String?
    ): Pair<String, TaskerQueueData?> {
        val playlist = playlistRepository.getMixedForYou().first()?.first { it.title == playlistName }
        if(playlist != null) {
            playlistRepository.getPlaylistData(playlist.browseId, "view_count").first().let { pd ->
                if(pd.data != null) {

                    return TaskerQueueData(
                        listTracks = pd.data!!.first.tracks,
                        playlistId = pd.data!!.first.id,
                        playlistType = PlaylistType.PLAYLIST,
                    ).let { data -> Pair("Success", data) }

                }
            }
        } else {
            return Pair("Playlist not found", null)
        }
        return Pair("Error", null)
    }


    suspend fun startLocalPlaylist(
        playlistName: String,
        arg: String?
    ): Pair<String, TaskerQueueData?> {

        Logger.d("Tasker", "PL NAME: ${playlistName}")

        localPlaylistRepository.getAllLocalPlaylists().first().forEach { Logger.d("Tasker", "PL ${it.title}") }

        val playlist = localPlaylistRepository.getAllLocalPlaylists().first().first { it.title == playlistName }

        if(playlist != null) {
            return TaskerQueueData(
                listTracks = localPlaylistRepository.getFullPlaylistTracks(playlist.id).map { it.toTrack() },
                playlistId = LOCAL_PLAYLIST_ID + playlist.id,
                playlistType = PlaylistType.PLAYLIST,
            ).let { data -> Pair("Success", data) }

        }
        return Pair("Error", null)
    }

    suspend fun startLibraryPlaylist(
        playlistName: String,
        arg: String?
    ): Pair<String, TaskerQueueData?> {
        val playlist = playlistRepository.getLibraryPlaylist().first()?.first { it.title ==playlistName }
        if(playlist != null) {
            playlistRepository.getPlaylistData(playlist.browseId, "view_count").first().let { pd ->
                if(pd.data != null) {
                    return TaskerQueueData(
                        listTracks = pd.data!!.first.tracks,
                        playlistId = pd.data!!.first.id,
                        playlistType = PlaylistType.PLAYLIST,
                    ).let { data -> Pair("Success", data) }

                }
            }
        }
        return Pair("Error", null)
    }

    suspend fun startAutoPlaylist(
        playlistName: String,
        arg: String?
    ): Pair<String, TaskerQueueData?> {

        when (playlistName) {

            AutoPlaylist.DownloadedSongs.title -> {
                return TaskerQueueData(
                    listTracks = songRepository.getDownloadedSongs().first()?.map { it.toTrack() } ?: emptyList(),
                    playlistId = "",
                    playlistType = PlaylistType.PLAYLIST,
                ).let { data -> Pair("Success", data) }
            }

            AutoPlaylist.Liked.title -> {
                return TaskerQueueData(
                    listTracks = songRepository.getLikedSongs().first().map { it.toTrack() },
                    playlistId = "",
                    playlistType = PlaylistType.PLAYLIST,
                ).let { data -> Pair("Success", data) }
            }

            AutoPlaylist.MostPlayed.title -> {
                return TaskerQueueData(
                    listTracks = songRepository.getMostPlayedSongs().first().map { it.toTrack() },
                    playlistId = "",
                    playlistType = PlaylistType.PLAYLIST,
                ).let { data -> Pair("Success", data) }
            }

            AutoPlaylist.RecentPlayed.title -> {
                return TaskerQueueData(
                    listTracks = songRepository.getRecentSong(100, 0).map { it.toTrack() },
                    playlistId = "",
                    playlistType = PlaylistType.PLAYLIST,
                ).let { data -> Pair("Success", data) }
            }

            AutoPlaylist.ArtistRadio.title -> {

                var artistId = arg

                if (arg == null) {
                    val currentSongId = mediaPlayerHandler.nowPlaying.value?.mediaId
                    if(currentSongId != null) {
                        songRepository.getSongById(currentSongId).firstOrNull()?.let { song ->
                            song.artistId?.let {
                                artistId = it.first()
                            }
                        }
                    }
                }

                /*
                val artistId =
                    arg ?: musicService.database.song(musicService.currentMediaMetadata.value?.id)
                        .firstOrNull()?.artists?.firstOrNull()?.id
                */

                if (artistId != null) {
                    Logger.d("Tasker", "ArtistID: ${artistId}")


                    artistRepository.getArtistData(artistId).first().let { artistData ->
                        if (artistData.data != null && artistData.data?.radioId != null) {
                            songRepository.getRadioFromEndpoint(artistData.data?.radioId!!).first().let { res ->
                                val data = res.data
                                when (res) {
                                    is Resource.Success if data != null && data.first.isNotEmpty() -> {

                                        return TaskerQueueData(
                                            listTracks = data.first,
                                            playlistId = artistData.data?.radioId!!.playlistId,
                                            playlistType = PlaylistType.RADIO,
                                            ).let { data -> Pair("Success", data) }

                                    }

                                    else -> {
                                        return Pair("Error", null)
                                    }
                                }
                            }
                        }
                    }


                } else {
                    return Pair("Error", null)
                }


            }

            AutoPlaylist.ArtistSongs.title -> {

                var artistId = arg

                if (arg == null) {
                    val currentSongId = mediaPlayerHandler.nowPlaying.value?.mediaId
                    if (currentSongId != null) {
                        songRepository.getSongById(currentSongId).firstOrNull()?.let { song ->
                            song.artistId?.let {
                                artistId = it.first()
                            }
                        }
                    }
                }

                /*
                val artistId =
                    arg ?: musicService.database.song(musicService.currentMediaMetadata.value?.id)
                        .firstOrNull()?.artists?.firstOrNull()?.id
                */

                if (artistId != null) {
                    Logger.d("Tasker", "ArtistID: ${artistId}")


                    artistRepository.getArtistData(artistId).first().let { artistData ->
                        if (artistData.data != null && artistData.data?.shuffleId != null) {
                            songRepository.getRadioFromEndpoint(artistData.data?.shuffleId!!).first().let { res ->
                                val data = res.data
                                when (res) {
                                    is Resource.Success if data != null && data.first.isNotEmpty() -> {

                                        return TaskerQueueData(
                                            listTracks = data.first,
                                            playlistId = artistData.data?.radioId!!.playlistId,
                                            playlistType = PlaylistType.RADIO,
                                        ).let { data -> Pair("Success", data) }

                                    }

                                    else -> {
                                        return Pair("Error", null)
                                    }
                                }
                            }
                        }
                    }


                } else {
                    return Pair("Error", null)
                }
            }

            AutoPlaylist.AlbumSongs.title -> {

                var albumId = arg

                if (arg == null) {
                    val currentSongId = mediaPlayerHandler.nowPlaying.value?.mediaId
                    if (currentSongId != null) {
                        songRepository.getSongById(currentSongId).firstOrNull()?.let { song ->
                            albumId = song.albumId
                        }
                    }
                }


                if (albumId != null) {
                    Logger.d("Tasker", "AlbumID: ${albumId}")
                    albumRepository.getAlbumData(albumId).first().let { res ->
                        val data = res.data
                        when (res) {
                            is Resource.Success if data != null && data.tracks.isNotEmpty() -> {

                                return TaskerQueueData(
                                    listTracks = data.tracks,
                                    playlistId = data.audioPlaylistId,
                                    playlistType = PlaylistType.ALBUM,
                                ).let { data -> Pair("Success", data) }

                            }

                            else -> {
                                return Pair("Error", null)
                            }
                        }
                    }
                }
            }

        }
        return Pair("Error", null)
    }

    override suspend fun runWithMusicService(
        context: Context,
        input: TaskerInput<PlaylistInput>,
    ): TaskerPluginResult<PlaylistOutput> {

        val playlistName = input.regular.playlistName ?: ""
        val arg = input.regular.arg

        Logger.d("Tasker", "Starting playlist: ${playlistName} with arg: ${arg}")

        val tracksResult = when {
            playlistName.startsWith("[Auto]") -> {
                val autoPlaylistName = playlistName.removePrefix("[Auto] ").trim()
                startAutoPlaylist(autoPlaylistName, arg)
            }
            playlistName.startsWith("[YT]") -> {
                val libraryPlaylistName = playlistName.removePrefix("[YT] ").trim()
                startLibraryPlaylist(libraryPlaylistName, arg)
            }
            playlistName.startsWith("[YT Mix]") -> {
                val mixPlaylistName = playlistName.removePrefix("[YT Mix] ").trim()
                startMixPlaylist(mixPlaylistName, arg)
            }
            playlistName.startsWith("[Local]") -> {
                val localPlaylistName = playlistName.removePrefix("[Local] ").trim()
                startLocalPlaylist(localPlaylistName, arg)
            }
            else -> {
                Pair("Error: Unknown playlist type", null)
            }
        }


        if (tracksResult.second != null) {
            val queueData = tracksResult.second!!

            val limit = input.regular.limit?.toInt()

            mediaPlayerHandler.reset()
            mediaPlayerHandler.setQueueData(
                QueueData.Data(
                    listTracks = if ((limit != null) && (limit > 0)) queueData.listTracks.take(limit) else queueData.listTracks,
                    firstPlayedTrack = queueData.listTracks.first(),
                    playlistId = queueData.playlistId,
                    playlistName = playlistName,
                    playlistType = queueData.playlistType,
                    continuation = null,
                ),
            )

            if (input.regular.sorting == SortType.Shuffled.toString()) {
                mediaPlayerHandler.shufflePlaylist(0)
            }

            if (limit != null && limit == -1) {
                dataStoreManager.setEndlessQueue(true)
            } else {
                dataStoreManager.setEndlessQueue(false)
            }

            mediaPlayerHandler.loadMediaItem(
                queueData.listTracks.first(),
                Config.PLAYLIST_CLICK,
                0,
            )
        } else {
            return TaskerPluginResultErrorWithOutput(1, "Error starting playlist: ${tracksResult.first}")
        }

        return TaskerPluginResultSucess()

    }

}