package com.maxrave.simpmusic.tasker.actions

import android.content.Context
import android.os.Bundle
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
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.maxrave.simpmusic.tasker.TaskerConfigurationItem
import com.maxrave.simpmusic.tasker.TaskerConfigurationScreen
import kotlinx.coroutines.flow.firstOrNull
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.R

enum class Commands {
    TOGGLE_PLAY_PAUSE,
    PLAY,
    STOP,
    NEXT_SONG,
    PREVIOUS_SONG,
    LIKE_SONG,
    UNLIKE_SONG,
    TOGGLE_LIKE,
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
    @get:TaskerOutputVariable("executed",
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

    private val taskerHelper by lazy { ManagePlayerActionHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContent {
            TaskerConfigurationScreen(
                title = "Configure Manage Player action"
            ) {
                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.command_name),
                    inputDescription = stringResource(R.string.command_description),
                    command,
                    inputOptions = Commands.entries.map { c -> Pair(c.toString(), null) },
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
                    Logger.w("Tasker","Settings are not valid:\n\n${result.message}")
                }
                if (result.success) finish()
            }
        })
    }
}


class ManagePlayerActionRunner : TaskerCommonRunner<ManagePlayerInput, ManagePlayerOutput>() {

    override suspend fun runSuspended(
        context: Context,
        input: TaskerInput<ManagePlayerInput>,
    ): TaskerPluginResult<ManagePlayerOutput> {

        var executed = false
        val command: Commands




        try{
            command = Commands.valueOf(input.regular.command ?: "")
        }catch (e: Exception){
            return TaskerPluginResultErrorWithOutput(
                code = 1,
                message = "Invalid command (${input.regular.command})"
            )
        }

        when (command) {

            Commands.TOGGLE_LIKE -> {
                mediaPlayerHandler.toggleLike()
                executed = true
            }

            Commands.LIKE_SONG -> {
                // apparently the like() method does not work
                // mediaPlayerHandler.like(true)
                val currentSongId = mediaPlayerHandler.nowPlaying.value?.mediaId
                if(currentSongId != null) {
                    songRepository.getSongById(currentSongId).firstOrNull()?.let { song ->
                        if(!song.liked) {
                            mediaPlayerHandler.toggleLike()
                        }
                    }
                }
            }

            Commands.UNLIKE_SONG -> {
                // apparently the like() method does not work
                // mediaPlayerHandler.like(false)
                val currentSongId = mediaPlayerHandler.nowPlaying.value?.mediaId
                if(currentSongId != null) {
                    songRepository.getSongById(currentSongId).firstOrNull()?.let { song ->
                        if(song.liked) {
                            mediaPlayerHandler.toggleLike()
                        }
                    }
                }
            }

            Commands.TOGGLE_PLAY_PAUSE -> {
                if(mediaPlayerHandler.player.isPlaying) {
                    mediaPlayerHandler.player.pause()
                } else {
                    mediaPlayerHandler.player.play()
                }
                executed = true
            }

            Commands.PLAY -> {
                mediaPlayerHandler.player.play()
                executed = true
            }

            Commands.STOP -> {
                mediaPlayerHandler.player.pause()
                executed = true
            }

            Commands.NEXT_SONG -> {
                if(mediaPlayerHandler.player.hasNextMediaItem()) {
                   mediaPlayerHandler.player.seekToNext()
                    executed = true
                }
            }

            Commands.PREVIOUS_SONG -> {
                if(mediaPlayerHandler.player.hasPreviousMediaItem()) {
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