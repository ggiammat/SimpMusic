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
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.maxrave.simpmusic.tasker.CommonRunner
import com.maxrave.simpmusic.tasker.TaskerConfigurationItem
import com.maxrave.simpmusic.tasker.TaskerConfigurationScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
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
class PlaybackCommandInput @JvmOverloads constructor(
    @field:TaskerInputField(
        "command",
        labelResIdName = "command_name",
        descriptionResIdName = "command_description"
    ) var command: String? = null
)

@TaskerOutputObject
class PlaybackCommandOutput(
    @get:TaskerOutputVariable("executed",
        labelResIdName = "out_executed_name",
        htmlLabelResIdName = "out_executed_description"
    ) var executed: Boolean?,
)

class PlaybackCommandActionHelper(config: TaskerPluginConfig<PlaybackCommandInput>) :
    TaskerPluginConfigHelper<PlaybackCommandInput, PlaybackCommandOutput, PlaybackCommandActionRunner>(config) {
    override val inputClass = PlaybackCommandInput::class.java
    override val outputClass = PlaybackCommandOutput::class.java
    override val runnerClass = PlaybackCommandActionRunner::class.java
}

class PlaybackCommandConfigActivity : ComponentActivity(), TaskerPluginConfig<PlaybackCommandInput> {

    override val context get() = applicationContext

    val command = mutableStateOf("")

    override fun assignFromInput(input: TaskerInput<PlaybackCommandInput>) {
        command.value = input.regular.command ?: ""
    }

    override val inputForTasker: TaskerInput<PlaybackCommandInput>
        get() = TaskerInput(PlaybackCommandInput(command = command.value))

    private val taskerHelper by lazy { PlaybackCommandActionHelper(this) }

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


class PlaybackCommandActionRunner : CommonRunner<PlaybackCommandInput, PlaybackCommandOutput>() {

    override suspend fun runWithMusicService(
        context: Context,
        input: TaskerInput<PlaybackCommandInput>,
    ): TaskerPluginResult<PlaybackCommandOutput> {

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
                    mediaPlayerHandler.player.seekToPrevious()
                    executed = true
                }
                /*
                withContext(Dispatchers.Main) {
                    if(musicService.player.hasPreviousMediaItem()) {
                        musicService.player.seekToPrevious()
                        executed = true
                    }
                }

                 */
            }
        }


        return TaskerPluginResultSucess(
            regular = PlaybackCommandOutput(
                executed = executed
            )
        )

    }
}