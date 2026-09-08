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
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.tasker.TaskerConfigurationItem
import com.maxrave.simpmusic.tasker.TaskerConfigurationScreen
import kotlinx.coroutines.flow.firstOrNull
import com.maxrave.simpmusic.R

@TaskerInputRoot
class GetSongInfoInput @JvmOverloads constructor(
    @field:TaskerInputField(
        "song_id",
        labelResIdName = "songinfo_input_song_id_name",
        descriptionResIdName = "songinfo_input_song_id_description"
    ) var songId: String? = null
)

@TaskerOutputObject
class GetSongInfoOutput(
    @get:TaskerOutputVariable("song_id",
        labelResIdName = "songinfo_output_song_id_name",
        htmlLabelResIdName = "songinfo_output_song_id_description"
    ) var songId: String?,

    @get:TaskerOutputVariable("song_title",
        labelResIdName = "songinfo_output_song_title_name",
        htmlLabelResIdName = "songinfo_output_song_title_description"
    ) var songTitle: String?,

    @get:TaskerOutputVariable("artist_id",
        labelResIdName = "songinfo_output_artist_id_name",
        htmlLabelResIdName = "songinfo_output_artist_id_description"
    ) var artistId: String?,

    @get:TaskerOutputVariable("artist_name",
        labelResIdName = "songinfo_output_artist_name_name",
        htmlLabelResIdName = "songinfo_output_artist_name_description"
    ) var artistName: String?,

    @get:TaskerOutputVariable("liked",
        labelResIdName = "songinfo_output_liked_name",
        htmlLabelResIdName = "songinfo_output_liked_description"
    ) var liked: Boolean?,

    @get:TaskerOutputVariable("now_playing",
        labelResIdName = "songinfo_output_now_playing_name",
        htmlLabelResIdName = "songinfo_output_now_playing_description"
    ) var nowPlaying: Boolean?,

    @get:TaskerOutputVariable("album_title",
        labelResIdName = "songinfo_output_album_title_name",
        htmlLabelResIdName = "songinfo_output_album_title_description"
    ) var albumTitle: String?,

    @get:TaskerOutputVariable("album_id",
        labelResIdName = "songinfo_output_album_id_name",
        htmlLabelResIdName = "songinfo_output_album_id_description"
    ) var albumId: String?
)


class GetSongInfoActionHelper(config: TaskerPluginConfig<GetSongInfoInput>) :
    TaskerPluginConfigHelper<GetSongInfoInput, GetSongInfoOutput, GetSongInfoActionRunner>(config) {
    override val inputClass = GetSongInfoInput::class.java
    override val outputClass = GetSongInfoOutput::class.java
    override val runnerClass = GetSongInfoActionRunner::class.java
}


class GetSongInfoConfigActivity : TaskerCommonConfigActivity<GetSongInfoInput>() {

    val songId = mutableStateOf("")

    override fun assignFromInput(input: TaskerInput<GetSongInfoInput>) {
        songId.value = input.regular.songId ?: ""
    }

    override val inputForTasker: TaskerInput<GetSongInfoInput>
        get() = TaskerInput(GetSongInfoInput(songId = songId.value))

    private val taskerHelper by lazy { GetSongInfoActionHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContent {
            TaskerConfigurationScreen(
                title = "Configure Song Info action"
            ) {
                TaskerConfigurationItem(
                    inputLabel = stringResource(R.string.song_id_name),
                    inputDescription = stringResource(R.string.song_id_description),
                    songId,
                    taskerVariables = taskerHelper.relevantVariables.toList()
                )
            }
        }

        taskerHelper.onCreate()
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


class GetSongInfoActionRunner : TaskerCommonRunner<GetSongInfoInput, GetSongInfoOutput>() {

    override suspend fun runSuspended(
        context: Context,
        input: TaskerInput<GetSongInfoInput>,
    ): TaskerPluginResult<GetSongInfoOutput> {

        Logger.w("Tasker", "Log: ${mediaPlayerHandler.nowPlaying.value}")

        // 1. Get the current song ID
        val currentSongId = input.regular.songId.takeIf { it != "" } ?: mediaPlayerHandler.nowPlaying.value?.mediaId

        if (currentSongId == null) {
            return TaskerPluginResultErrorWithOutput(
                code = 1,
                message = "No song is currently playing"
            )
        }

        songRepository.getSongById(currentSongId).firstOrNull()?.let { song ->
            return TaskerPluginResultSucess(
                GetSongInfoOutput(
                    songId = song.videoId,
                    songTitle = song.title,
                    artistId = song.artistId?.first(),
                    artistName = song.artistName?.first(),
                    liked = song.liked,
                    albumTitle = song.albumName,
                    albumId = song.albumId,
                    nowPlaying = mediaPlayerHandler.player.isPlaying
                )
            )
        }

        return TaskerPluginResultErrorWithOutput(
            code = 1,
            message = "Song with ID=${currentSongId} not found"
        )

    }
}