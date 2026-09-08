package com.maxrave.simpmusic.tasker.actions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.maxrave.domain.utils.collectLatestResource
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.R
import com.maxrave.simpmusic.tasker.ConfigUIChoiceOption
import com.maxrave.simpmusic.tasker.TaskerConfigurationItem
import com.maxrave.simpmusic.tasker.TaskerConfigurationScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

enum class PlaylistCommand(val title: String, val description: String? = null) {
    ADD_SONG_TO_PLAYLIST("Add Song to Playlist", "Adds the specified song to the given playlist"),
    REMOVE_SONG_FROM_PLAYLIST("Remove Song from Playlist", "Removes the specified song from the given playlist");

    fun toConfigUIChoiceOption(): ConfigUIChoiceOption {
        return ConfigUIChoiceOption(
            id = this.name,
            value = this.title,
            description = this.description
        )
    }
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
class ManagePlaylistConfigActivity : TaskerCommonConfigActivity<ManagePlaylistInput>() {

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

    override val taskerHelper by lazy { ManagePlaylistActionHelper(this) }

    @Composable
    override fun ConfigurationUI(){
            TaskerConfigurationScreen(
                title = "Configure Playback Command action"
            ) {

                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.command_name),
                    inputDescription = stringResource(R.string.command_description),
                    command,
                    inputOptions = PlaylistCommand.entries.map { it.toConfigUIChoiceOption() },
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

    private fun getPlaylistNames(): List<ConfigUIChoiceOption> {
        val res: MutableList<ConfigUIChoiceOption> = mutableListOf()
        runBlocking {
            res += (playlistRepository.getLibraryPlaylist().first() ?: emptyList()).map { ConfigUIChoiceOption("[YT] " + it.title) }
            res += ConfigUIChoiceOption("---", null)
            res += (localPlaylistRepository.getAllLocalPlaylists().first() ?: emptyList()).map { ConfigUIChoiceOption("[Local] " + it.title) }        }
        return res
    }
}



class ManagePlaylistActionRunner : TaskerCommonRunner<ManagePlaylistInput, ManagePlaylistOutput>() {

    override suspend fun runSuspended(
        context: Context,
        input: TaskerInput<ManagePlaylistInput>,
    ): TaskerPluginResult<ManagePlaylistOutput> {

        val playlistName = input.regular.playlistName ?: ""
        val command = input.regular.command ?: PlaylistCommand.ADD_SONG_TO_PLAYLIST
        val songId = input.regular.songId.takeIf { it != "" } ?: mediaPlayerHandler.nowPlaying.value?.mediaId

        songRepository.getSongById(songId!!).firstOrNull()?.let { song ->
            when {
                playlistName.startsWith("[YT]") -> {
                    val ytPlaylistName = playlistName.removePrefix("[YT] ").trim()
                    val playlist = playlistRepository.getLibraryPlaylist().firstOrNull()?.firstOrNull { it.title == ytPlaylistName }
                    if (playlist == null) {
                        Logger.w("Tasker", "YouTube playlist not found: $ytPlaylistName")
                        return TaskerPluginResultErrorWithOutput(1, "YouTube playlist not found: $ytPlaylistName")
                    }

                    when (command) {
                        PlaylistCommand.ADD_SONG_TO_PLAYLIST.toString() -> {
                            localPlaylistRepository.addYouTubePlaylistItem(playlist.browseId, song.videoId).collectLatestResource(
                                onSuccess = {
                                    Logger.i("Tasker", "Add to YT Playlist result: ${it}")
                                },
                                onError = {
                                    Logger.e("Tasker", "Add to YT Playlist result: ${it}")
                                },
                            )
                        }

                        PlaylistCommand.REMOVE_SONG_FROM_PLAYLIST.toString() -> {
                            // Removing from a YouTube playlist is currently not supported by the public playlistRepository.
                            Logger.e("Tasker", "Remove from YT Playlist not implemented. Consider syncing the playlist locally then modifying it.")
                            return TaskerPluginResultErrorWithOutput(1, "Remove from YouTube playlist is not supported")
                        }
                    }
                }
                playlistName.startsWith("[Local]") -> {
                    val localPlaylistName = playlistName.removePrefix("[Local] ").trim()
                    val allLocal = localPlaylistRepository.getAllLocalPlaylists().firstOrNull() ?: emptyList()
                    val playlist = allLocal.firstOrNull { it.title == localPlaylistName }
                    if (playlist == null) {
                        Logger.w("Tasker", "Local playlist not found: $localPlaylistName")
                        return TaskerPluginResultErrorWithOutput(1, "Local playlist not found: $localPlaylistName")
                    }
                    when (command) {
                        PlaylistCommand.ADD_SONG_TO_PLAYLIST.toString() -> {
                            localPlaylistRepository.addTrackToLocalPlaylist(playlist.id, song, "Done", "YT Done", "Error").collectLatestResource(
                                onSuccess = {
                                    Logger.i("Tasker", "Add to Local Playlist result: ${it}")
                                },
                                onError = {
                                    Logger.e("Tasker", "Add to Local Playlist result: ${it}")
                                },
                            )
                        }

                        PlaylistCommand.REMOVE_SONG_FROM_PLAYLIST.toString() -> {
                            localPlaylistRepository.removeTrackFromLocalPlaylist(playlist.id, song, "Done", "YT Done", "Error").collectLatestResource(
                                onSuccess = {
                                    Logger.i("Tasker", "Remove from Local Playlist result: ${it}")
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