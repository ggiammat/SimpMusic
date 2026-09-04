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
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.utils.collectLatestResource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.R
import com.maxrave.simpmusic.tasker.CommonRunner
import com.maxrave.simpmusic.tasker.TaskerConfigurationItem
import com.maxrave.simpmusic.tasker.TaskerConfigurationScreen
import multiplatform.network.cmptoast.ToastDuration
import multiplatform.network.cmptoast.ToastGravity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

enum class Command {
    ADD_SONG_TO_PLAYLIST,
    REMOVE_SONG_TO_PLAYLIST
}

@TaskerInputRoot
class ManagePlaylistInput @JvmOverloads constructor(
    @field:TaskerInputField(
        "playlistName",
        labelResIdName = "tasker_input_playlist_name",
        descriptionResIdName = "tasker_input_playlist_name_description"
    ) var playlistName: String? = null,

    @field:TaskerInputField(
        "song_id",
        labelResIdName = "song_id_name",
        descriptionResIdName = "song_id_description"
    ) var songId: String? = null,

    @field:TaskerInputField(
        "command",
        labelResIdName = "command_name",
        descriptionResIdName = "command_description"
    ) var command: String? = null
)

@TaskerOutputObject
class ManagePlaylistOutput (
    @get:TaskerOutputVariable("executed",
        labelResIdName = "out_executed_name",
        htmlLabelResIdName = "out_executed_description"
    ) var executed: Boolean?,
)

class ManagePlaylistActionHelper(config: TaskerPluginConfig<ManagePlaylistInput>) :
    TaskerPluginConfigHelper<ManagePlaylistInput, ManagePlaylistOutput, ManagePlaylistActionRunner>(config) {
    override val inputClass = ManagePlaylistInput::class.java
    override val outputClass = ManagePlaylistOutput::class.java
    override val runnerClass = ManagePlaylistActionRunner::class.java
}


//@AndroidEntryPoint
class ManagePlaylistConfigActivity : ComponentActivity(), TaskerPluginConfig<ManagePlaylistInput>, KoinComponent {


    val localPlaylistRepository by inject<LocalPlaylistRepository>()
    val playlistRepository by inject<PlaylistRepository>()

    override val context get() = applicationContext

    val playlistName = mutableStateOf("")
    val songId = mutableStateOf("")
    val command = mutableStateOf("")


    override fun assignFromInput(input: TaskerInput<ManagePlaylistInput>) {
        playlistName.value = input.regular.playlistName ?: ""
        songId.value = input.regular.songId ?: ""
        command.value = input.regular.command ?: ""
    }

    override val inputForTasker: TaskerInput<ManagePlaylistInput>
        get() = TaskerInput(
            ManagePlaylistInput(
                playlistName = playlistName.value,
                songId = songId.value,
                command = command.value
            )
        )

    private val taskerHelper by lazy { ManagePlaylistActionHelper(this) }

    private fun getPlaylistNames(): List<Pair<String, String?>> {
        val res: MutableList<Pair<String, String?>> = mutableListOf()
        runBlocking {
            res += (playlistRepository.getLibraryPlaylist().first() ?: emptyList()).map { Pair("[YT] " + it.title, null) }
            res += Pair("---", null)
            res += (localPlaylistRepository.getAllLocalPlaylists().first() ?: emptyList()).map { Pair("[Local] " + it.title, null) }        }
        return res
    }

    override fun onCreate(savedInstanceState: Bundle?) {


        super.onCreate(savedInstanceState)
        setContent {
            TaskerConfigurationScreen(
                title = "Configure Playback Command action"
            ) {

                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.command_name),
                    inputDescription = stringResource(R.string.command_description),
                    command,
                    inputOptions = Command.entries.map { c -> Pair(c.toString(), null) },
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )

                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.song_id_name),
                    inputDescription = stringResource(R.string.song_id_description),
                    songId,
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )

                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.tasker_input_playlist_name),
                    inputDescription = stringResource(R.string.tasker_input_playlist_name_description),
                    playlistName,
                    inputOptions = getPlaylistNames(),
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
                    Logger.w("Tasker", "Settings are not valid:\n\n${result.message}")
                }
                if (result.success) finish()
            }
        })
    }
}



class ManagePlaylistActionRunner : CommonRunner<ManagePlaylistInput, ManagePlaylistOutput>() {

    override suspend fun runWithMusicService(
        context: Context,
        input: TaskerInput<ManagePlaylistInput>,
    ): TaskerPluginResult<ManagePlaylistOutput> {

        val playlistName = input.regular.playlistName ?: ""
        val command = input.regular.command ?: Command.ADD_SONG_TO_PLAYLIST
        val songId = input.regular.songId.takeIf { it != "" } ?: mediaPlayerHandler.nowPlaying.value?.mediaId

        songRepository.getSongById(songId!!).firstOrNull()?.let { song ->
            when {
                playlistName.startsWith("[YT]") -> {
                    val ytPlaylistName = playlistName.removePrefix("[YT] ").trim()
                    val playlist = playlistRepository.getLibraryPlaylist().first()?.first { it.title == ytPlaylistName }
                    when(command) {
                        Command.ADD_SONG_TO_PLAYLIST.toString() -> {
                            localPlaylistRepository.addYouTubePlaylistItem(playlist?.browseId ?: "", song.videoId).collectLatestResource(
                                onSuccess = {
                                    Logger.i("Tasker", "Add to YT Playlist result: ${it}")
                                },
                                onError = {
                                    Logger.e("Tasker", "Add to YT Playlist result: ${it}")
                                },
                            )
                        }

                        Command.REMOVE_SONG_TO_PLAYLIST.toString() -> {
                            TODO("Not implemented. Not supported by the playlistRepository")
                            Logger.e("Tasker", "Remove from YT Playlist not implemented!. However it is possible to sync the YT playlist locally and then add/remove song to the local playlist")
                        }
                    }

                }
                playlistName.startsWith("[Local]") -> {
                    val localPlaylistName = playlistName.removePrefix("[Local] ").trim()
                    val playlist = localPlaylistRepository.getAllLocalPlaylists().first().first { it.title == localPlaylistName }
                    when(command) {
                        Command.ADD_SONG_TO_PLAYLIST.toString() -> {
                            localPlaylistRepository.addTrackToLocalPlaylist(playlist.id, song, "Done", "YT Done", "Error").collectLatestResource(
                                onSuccess = {
                                    Logger.i("Tasker", "Add to Local Playlist result: ${it}")
                                },
                                onError = {
                                    Logger.e("Tasker", "Add to Local Playlist result: ${it}")
                                },
                            )
                        }

                        Command.REMOVE_SONG_TO_PLAYLIST.toString() -> {
                            localPlaylistRepository.removeTrackFromLocalPlaylist(playlist.id, song, "Done", "YT Done", "Error").collectLatestResource(
                                onSuccess = {
                                    Logger.i("Tasker", "Remove frmo Local Playlist result: ${it}")
                                },
                                onError = {
                                    Logger.e("Tasker", "Remove from Local Playlist result: ${it}")
                                },
                            )
                        }
                    }
                }
                else -> {
                    return TaskerPluginResultErrorWithOutput(1, "Error playlist not recognized: ${playlistName}")
                }
            }

        }


        return TaskerPluginResultSucess(
            ManagePlaylistOutput(executed = true)
        )

    }
}