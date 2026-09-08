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
import com.maxrave.simpmusic.R
import com.maxrave.simpmusic.tasker.ConfigUIChoiceOption
import com.maxrave.simpmusic.tasker.TaskerConfigurationItem
import com.maxrave.simpmusic.tasker.TaskerConfigurationScreen
import kotlinx.coroutines.flow.firstOrNull

enum class PlayerCommand(val title: String, val description: String? = null) {
    TOGGLE_PLAY_PAUSE("Play/Pause", "Toggles the playback state of the player"),
    PLAY("Play", "Plays the current track"),
    STOP("Stop", "Stops the player"),
    NEXT_SONG("Next Song", "Skips to the next track"),
    PREVIOUS_SONG("Previous Song", "Skips to the previous track"),
    LIKE_SONG("Like Song", "Likes the current song"),
    UNLIKE_SONG("Unlike Song", "Unlikes the current song"),
    TOGGLE_LIKE("Toggle Like", "Toggles the like status of the current song");

    fun toConfigUIChoiceOption(): ConfigUIChoiceOption {
        return ConfigUIChoiceOption(
            id = this.name,
            value = this.title,
            description = this.description
        )
    }
}

@TaskerInputRoot
class ManagePlayerInput @JvmOverloads constructor(
    @field:TaskerInputField(
        "command",
        labelResIdName = "command_name",
        descriptionResIdName = "command_description"
    ) var command: String? = null
)

@TaskerOutputObject
class ManagePlayerOutput(
    @get:TaskerOutputVariable(
        "executed",
        labelResIdName = "out_executed_name",
        htmlLabelResIdName = "out_executed_description"
    ) var executed: Boolean?,
)

class ManagePlayerActionHelper(config: TaskerPluginConfig<ManagePlayerInput>) :
    TaskerPluginConfigHelper<ManagePlayerInput, ManagePlayerOutput, ManagePlayerActionRunner>(config) {
    override val inputClass = ManagePlayerInput::class.java
    override val outputClass = ManagePlayerOutput::class.java
    override val runnerClass = ManagePlayerActionRunner::class.java
}

class ManagePlayerConfigActivity : TaskerCommonConfigActivity<ManagePlayerInput>() {

    val command = mutableStateOf("")

    override fun assignFromInput(input: TaskerInput<ManagePlayerInput>) {
        command.value = input.regular.command ?: ""
    }

    override val inputForTasker: TaskerInput<ManagePlayerInput>
        get() = TaskerInput(ManagePlayerInput(command = command.value))

    override val taskerHelper by lazy { ManagePlayerActionHelper(this) }

    @Composable
    override fun ConfigurationUI() {
        TaskerConfigurationScreen(
            title = "Configure Manage Player action"
        ) {
            TaskerConfigurationItem(
                inputLabel = stringResource(R.string.command_name),
                inputDescription = stringResource(R.string.command_description),
                command,
                inputOptions = PlayerCommand.entries.map { it.toConfigUIChoiceOption() },
                taskerVariables = taskerHelper.relevantVariables.toList()
            )
        }
    }
}


class ManagePlayerActionRunner : TaskerCommonRunner<ManagePlayerInput, ManagePlayerOutput>() {

    override suspend fun runSuspended(
        context: Context,
        input: TaskerInput<ManagePlayerInput>,
    ): TaskerPluginResult<ManagePlayerOutput> {

        var executed = false
        val command: PlayerCommand




        try {
            command = PlayerCommand.valueOf(input.regular.command ?: "")
        } catch (e: Exception) {
            return TaskerPluginResultErrorWithOutput(
                code = 1,
                message = "Invalid command (${input.regular.command})"
            )
        }

        when (command) {

            PlayerCommand.TOGGLE_LIKE -> {
                mediaPlayerHandler.toggleLike()
                executed = true
            }

            PlayerCommand.LIKE_SONG -> {
                // apparently the like() method does not work so we use the toggleLike()
                val currentSongId = mediaPlayerHandler.nowPlaying.value?.mediaId
                if (currentSongId != null) {
                    songRepository.getSongById(currentSongId).firstOrNull()?.let { song ->
                        if (!song.liked) {
                            mediaPlayerHandler.toggleLike()
                            executed = true
                        } else {
                            executed = false
                        }
                    }
                }
            }

            PlayerCommand.UNLIKE_SONG -> {
                // apparently the like() method does not work so we use the toggleLike()
                val currentSongId = mediaPlayerHandler.nowPlaying.value?.mediaId
                if (currentSongId != null) {
                    songRepository.getSongById(currentSongId).firstOrNull()?.let { song ->
                        if (song.liked) {
                            mediaPlayerHandler.toggleLike()
                            executed = true
                        } else {
                            executed = false
                        }
                    }
                }
            }

            PlayerCommand.TOGGLE_PLAY_PAUSE -> {
                if (mediaPlayerHandler.player.isPlaying) {
                    mediaPlayerHandler.player.pause()
                } else {
                    mediaPlayerHandler.player.play()
                }
                executed = true
            }

            PlayerCommand.PLAY -> {
                mediaPlayerHandler.player.play()
                executed = true
            }

            PlayerCommand.STOP -> {
                mediaPlayerHandler.player.pause()
                executed = true
            }

            PlayerCommand.NEXT_SONG -> {
                if (mediaPlayerHandler.player.hasNextMediaItem()) {
                    mediaPlayerHandler.player.seekToNext()
                    executed = true
                }
            }

            PlayerCommand.PREVIOUS_SONG -> {
                if (mediaPlayerHandler.player.hasPreviousMediaItem()) {
                    mediaPlayerHandler.player.seekToPreviousMediaItem()
                    executed = true
                }
            }
        }


        return TaskerPluginResultSucess(
            regular = ManagePlayerOutput(
                executed = executed
            )
        )

    }
}