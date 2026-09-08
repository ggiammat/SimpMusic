package com.maxrave.simpmusic.tasker.actions

import android.content.Context
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
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
import com.maxrave.domain.utils.Resource
import com.maxrave.simpmusic.tasker.TaskerConfigurationScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.tasker.TaskerConfigurationItem
import com.maxrave.simpmusic.R
import kotlin.getValue
import kotlin.collections.first
import com.maxrave.domain.data.model.browse.album.Track
import com.maxrave.domain.utils.toTrack
import com.maxrave.simpmusic.tasker.ConfigUIChoiceOption

enum class QueueCommand(val title: String, val description: String? = null) {
    SET_QUEUE_PLAYLIST(
        "Set Queue Playlist",
        "Sets the queue to the specified playlist and starts playback from the first track"
    ),
    EMPTY_QUEUE(
        "Empty Queue",
        "Clears the current queue"
    );


    fun toConfigUIChoiceOption(): ConfigUIChoiceOption {
        return ConfigUIChoiceOption(
            id = this.name,
            value = this.title,
            description = this.description
        )
    }
}

enum class AutoPlaylist(val title: String, val description: String? = null) {
    Liked("Liked Songs"),
    DownloadedSongs("Downloaded Songs"),
    MostPlayed("Most Played"),
    RecentPlayed("Recent Played"),
    ArtistSongs("Current Artist's Songs"),
    AlbumSongs("Current Album Songs"),
    ArtistRadio(
        "Artist's Radio",
        "Start radio of the current artist or the artist with the id specified in the Argument input");

    fun toConfigUIChoiceOption(): ConfigUIChoiceOption {
        return ConfigUIChoiceOption(
            id = "[Auto] ${this.name}",
            value = this.title,
            description = this.description
        )
    }
}

enum class SortType(val title: String, val description: String? = null) {
    Natural("Natural", "Keeps the order of the playlist as is"),
    Shuffled("Shuffled", "Shuffles the tracks in the playlist");

    fun toConfigUIChoiceOption(): ConfigUIChoiceOption {
        return ConfigUIChoiceOption(
            id = this.name,
            value = this.title,
            description = this.description
        )
    }
}


@TaskerInputRoot
class ManageQueueInput @JvmOverloads constructor(

    @field:TaskerInputField(
        "command",
        labelResIdName = "command_name",
        descriptionResIdName = "command_description"
    ) var command: String? = null,

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
class ManageQueueOutput ()


class ManageQueueActionHelper(config: TaskerPluginConfig<ManageQueueInput>) :
    TaskerPluginConfigHelper<ManageQueueInput, ManageQueueOutput, ManageQueueActionRunner>(config) {
    override val inputClass = ManageQueueInput::class.java
    override val outputClass = ManageQueueOutput::class.java
    override val runnerClass = ManageQueueActionRunner::class.java
}


//@AndroidEntryPoint
class ManageQueueConfigActivity : TaskerCommonConfigActivity<ManageQueueInput>() {

    val command = mutableStateOf("")
    val playlistName = mutableStateOf("")
    val arg = mutableStateOf("")
    val sorting = mutableStateOf("")
    val limit = mutableStateOf("")

    override fun assignFromInput(input: TaskerInput<ManageQueueInput>) {
        command.value = input.regular.command ?: ""
        playlistName.value = input.regular.playlistName ?: AutoPlaylist.Liked.title
        arg.value = input.regular.arg ?: ""
        sorting.value = input.regular.sorting ?: SortType.Natural.toString()
        limit.value = input.regular.limit ?: ""
    }

    override val inputForTasker: TaskerInput<ManageQueueInput>
        get() = TaskerInput(
            ManageQueueInput(
                command = command.value,
                playlistName = playlistName.value,
                arg = arg.value,
                sorting = sorting.value,
                limit = limit.value
            )
        )

    override val taskerHelper by lazy { ManageQueueActionHelper(this) }

    /**
     * Loads playlist names for the UI picker. This is currently synchronous and may block the UI briefly
     * while repositories fetch remote data. Keep calls defensive: any repository failure yields an empty list
     * rather than crashing the configuration screen.
     */
    private fun getPlaylistNames(): List<ConfigUIChoiceOption> {
        val res: MutableList<ConfigUIChoiceOption> = mutableListOf()
        runBlocking {
            try {
                val library = playlistRepository.getLibraryPlaylist().firstOrNull() ?: emptyList()
                res += library.map { ConfigUIChoiceOption("[YT] " + it.title, null) }
            } catch (t: Throwable) {
                Logger.w("Tasker", "Failed to load library playlists: ${t.message}")
            }

            res += ConfigUIChoiceOption("---")

            try {
                val mixes = playlistRepository.getMixedForYou().firstOrNull() ?: emptyList()
                res += mixes.map { ConfigUIChoiceOption("[YT Mix] " + it.title, null) }
            } catch (t: Throwable) {
                Logger.w("Tasker", "Failed to load mix playlists: ${t.message}")
            }

            res += ConfigUIChoiceOption("---")

            try {
                val local = localPlaylistRepository.getAllLocalPlaylists().firstOrNull() ?: emptyList()
                res += local.map { ConfigUIChoiceOption("[Local] " + it.title, null) }
            } catch (t: Throwable) {
                Logger.w("Tasker", "Failed to load local playlists: ${t.message}")
            }
        }
        return res
    }



    @Composable
    override fun ConfigurationUI() {
            TaskerConfigurationScreen(
                title = "Configure Playback Command action"
            ) {
                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.command_name),
                    inputDescription = stringResource(R.string.command_description),
                    command,
                    inputOptions = QueueCommand.entries.map { it.toConfigUIChoiceOption() },
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )

                if(command.value == QueueCommand.SET_QUEUE_PLAYLIST.toString()) {
                    TaskerConfigurationItem(
                        inputLabel = stringResource(R.string.tasker_input_playlist_name),
                        inputDescription = stringResource(R.string.tasker_input_playlist_name_description),
                        playlistName,
                        inputOptions = AutoPlaylist.entries.map { it.toConfigUIChoiceOption() }.plus(ConfigUIChoiceOption("---"))
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
                        inputOptions = SortType.entries.map { it.toConfigUIChoiceOption() },
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
    }
}

class TaskerQueueData(
    val listTracks: List<Track>,
    val playlistId: String?,
    val playlistName: String? = null,
    val playlistType: PlaylistType,
)

class ManageQueueActionRunner : TaskerCommonRunner<ManageQueueInput, ManageQueueOutput>() {


    /**
     * Resolve a mixed "For You" playlist by title. Returns (message, data) pair where data is null on error.
     */
    suspend fun getQueueForMixPlaylist(
        playlistName: String,
        arg: String?
    ): Pair<String, TaskerQueueData?> {
        try {
            val mixed = playlistRepository.getMixedForYou().firstOrNull() ?: emptyList()
            val playlist = mixed.firstOrNull { it.title == playlistName }
            if (playlist == null) return Pair("Playlist not found: $playlistName", null)

            val pd = playlistRepository.getPlaylistData(playlist.browseId, "view_count").firstOrNull()
            val payload = pd?.data?.first
            if (payload != null) {
                val tracks = payload.tracks ?: emptyList()
                if (tracks.isEmpty()) return Pair("Playlist is empty", null)
                return Pair(
                    "Success",
                    TaskerQueueData(
                        listTracks = tracks,
                        playlistId = payload.id,
                        playlistName = payload.title,
                        playlistType = PlaylistType.PLAYLIST,
                    )
                )
            }
            return Pair("Failed to load playlist data", null)
        } catch (t: Throwable) {
            Logger.e("Tasker", "Error fetching mix playlist: ${t.message}")
            return Pair("Exception: ${t.localizedMessage}", null)
        }
    }


    suspend fun getQueueForLocalPlaylist(
        playlistName: String,
        arg: String?
    ): Pair<String, TaskerQueueData?> {
        try {
            Logger.d("Tasker", "PL NAME: $playlistName")
            val all = localPlaylistRepository.getAllLocalPlaylists().firstOrNull() ?: emptyList()
            all.forEach { Logger.d("Tasker", "PL ${it.title}") }
            val playlist = all.firstOrNull { it.title == playlistName }
            if (playlist == null) return Pair("Local playlist not found: $playlistName", null)

            val tracks = localPlaylistRepository.getFullPlaylistTracks(playlist.id).map { it.toTrack() }
            if (tracks.isEmpty()) return Pair("Local playlist is empty", null)

            return Pair(
                "Success",
                TaskerQueueData(
                    listTracks = tracks,
                    playlistId = LOCAL_PLAYLIST_ID + playlist.id,
                    playlistName = playlist.title,
                    playlistType = PlaylistType.PLAYLIST,
                )
            )
        } catch (t: Throwable) {
            Logger.e("Tasker", "Error loading local playlist: ${t.message}")
            return Pair("Exception: ${t.localizedMessage}", null)
        }
    }

    suspend fun getQueueForLibraryPlaylist(
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
                        playlistName = playlist.title,
                        playlistType = PlaylistType.PLAYLIST,
                    ).let { data -> Pair("Success", data) }

                }
            }
        }
        return Pair("Error", null)
    }

    suspend fun getQueueForAutoPlaylist(
        playlistName: String,
        arg: String?
    ): Pair<String, TaskerQueueData?> {

        when (playlistName) {

            AutoPlaylist.DownloadedSongs.name -> {
                return TaskerQueueData(
                    listTracks = songRepository.getDownloadedSongs().first()?.map { it.toTrack() } ?: emptyList(),
                    playlistId = "",
                    playlistName = AutoPlaylist.DownloadedSongs.title,
                    playlistType = PlaylistType.PLAYLIST,
                ).let { data -> Pair("Success", data) }
            }

            AutoPlaylist.Liked.name -> {
                return TaskerQueueData(
                    listTracks = songRepository.getLikedSongs().first().map { it.toTrack() },
                    playlistId = "",
                    playlistName = AutoPlaylist.Liked.title,
                    playlistType = PlaylistType.PLAYLIST,
                ).let { data -> Pair("Success", data) }
            }

            AutoPlaylist.MostPlayed.name -> {
                return TaskerQueueData(
                    listTracks = songRepository.getMostPlayedSongs().first().map { it.toTrack() },
                    playlistId = "",
                    playlistName = AutoPlaylist.MostPlayed.title,
                    playlistType = PlaylistType.PLAYLIST,
                ).let { data -> Pair("Success", data) }
            }

            AutoPlaylist.RecentPlayed.name -> {
                return TaskerQueueData(
                    listTracks = songRepository.getRecentSong(100, 0).map { it.toTrack() },
                    playlistId = "",
                    playlistName = AutoPlaylist.RecentPlayed.title,
                    playlistType = PlaylistType.PLAYLIST,
                ).let { data -> Pair("Success", data) }
            }

            AutoPlaylist.ArtistRadio.name -> {

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
                                            playlistName = " ${artistData.data?.name} Radio",
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

            AutoPlaylist.ArtistSongs.name -> {

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
                                            playlistName = " ${artistData.data?.name} Songs",
                                            playlistType = PlaylistType.RADIO,
                                        ).let { data -> Pair("Success", data) }

                                    }

                                    else -> {
                                        return Pair("Error: ${res.message}", null)
                                    }
                                }
                            }
                        }
                    }


                } else {
                    return Pair("Error: Artist ID is null", null)
                }
            }

            AutoPlaylist.AlbumSongs.name -> {

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
                                    playlistName = data.title,
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

    override suspend fun runSuspended(
        context: Context,
        input: TaskerInput<ManageQueueInput>,
    ): TaskerPluginResult<ManageQueueOutput> {

        val command = input.regular.command ?: QueueCommand.EMPTY_QUEUE

        val playlistName = input.regular.playlistName ?: ""
        val arg = input.regular.arg

        Logger.d("Tasker", "Starting playlist: ${playlistName} with arg: ${arg}")

        val tracksResult = if(command == QueueCommand.EMPTY_QUEUE.toString()) {
            Pair("Success", TaskerQueueData(
                listTracks = emptyList(),
                playlistId = null,
                playlistType = PlaylistType.PLAYLIST,
            ))
        } else {

            when {
                playlistName.startsWith("[Auto]") -> {
                    val autoPlaylistName = playlistName.removePrefix("[Auto] ").trim()
                    getQueueForAutoPlaylist(autoPlaylistName, arg)
                }

                playlistName.startsWith("[YT]") -> {
                    val libraryPlaylistName = playlistName.removePrefix("[YT] ").trim()
                    getQueueForLibraryPlaylist(libraryPlaylistName, arg)
                }

                playlistName.startsWith("[YT Mix]") -> {
                    val mixPlaylistName = playlistName.removePrefix("[YT Mix] ").trim()
                    getQueueForMixPlaylist(mixPlaylistName, arg)
                }

                playlistName.startsWith("[Local]") -> {
                    val localPlaylistName = playlistName.removePrefix("[Local] ").trim()
                    getQueueForLocalPlaylist(localPlaylistName, arg)
                }

                else -> {
                    Pair("Error: Unknown playlist $playlistName", null)
                }
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
                    playlistName = queueData.playlistName,
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