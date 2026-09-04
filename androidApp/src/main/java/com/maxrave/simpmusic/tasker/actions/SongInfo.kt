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
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.tasker.CommonRunner
import com.maxrave.simpmusic.tasker.TaskerConfigurationItem
import com.maxrave.simpmusic.tasker.TaskerConfigurationScreen
import kotlinx.coroutines.flow.firstOrNull
import com.maxrave.simpmusic.R

@TaskerInputRoot
class SongInfoInput @JvmOverloads constructor(
    @field:TaskerInputField(
        "song_id",
        labelResIdName = "song_id_name",
        descriptionResIdName = "song_id_description"
    ) var songId: String? = null
)

@TaskerOutputObject
class SongInfoOutput(
    @get:TaskerOutputVariable("song_id",
        labelResIdName = "out_song_id_name",
        htmlLabelResIdName = "out_song_id_description"
    ) var songId: String?,

    @get:TaskerOutputVariable("song_title",
        labelResIdName = "out_song_title_name",
        htmlLabelResIdName = "out_song_title_description"
    ) var songTitle: String?,

    @get:TaskerOutputVariable("artist_id",
        labelResIdName = "out_artist_id_name",
        htmlLabelResIdName = "out_artist_id_description"
    ) var artistId: String?,

    @get:TaskerOutputVariable("artist_name",
        labelResIdName = "out_artist_name_name",
        htmlLabelResIdName = "out_artist_name_description"
    ) var artistName: String?,

    @get:TaskerOutputVariable("liked",
        labelResIdName = "out_liked_name",
        htmlLabelResIdName = "out_liked_description"
    ) var liked: Boolean?,

    @get:TaskerOutputVariable("now_playing",
        labelResIdName = "out_now_playing_name",
        htmlLabelResIdName = "out_now_playing_description"
    ) var nowPlaying: Boolean?,

    @get:TaskerOutputVariable("album_title",
        labelResIdName = "out_album_title_name",
        htmlLabelResIdName = "out_album_title_description"
    ) var albumTitle: String?,

    @get:TaskerOutputVariable("album_id",
        labelResIdName = "out_album_id_name",
        htmlLabelResIdName = "out_album_id_description"
    ) var albumId: String?
)


class SongInfoActionHelper(config: TaskerPluginConfig<SongInfoInput>) :
    TaskerPluginConfigHelper<SongInfoInput, SongInfoOutput, SongInfoActionRunner>(config) {
    override val inputClass = SongInfoInput::class.java
    override val outputClass = SongInfoOutput::class.java
    override val runnerClass = SongInfoActionRunner::class.java
}


class SongInfoConfigActivity : ComponentActivity(), TaskerPluginConfig<SongInfoInput> {

    override val context get() = applicationContext

    val songId = mutableStateOf("")

    override fun assignFromInput(input: TaskerInput<SongInfoInput>) {
        songId.value = input.regular.songId ?: ""
    }

    override val inputForTasker: TaskerInput<SongInfoInput>
        get() = TaskerInput(SongInfoInput(songId = songId.value))

    private val taskerHelper by lazy { SongInfoActionHelper(this) }

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


class SongInfoActionRunner : CommonRunner<SongInfoInput, SongInfoOutput>() {

    override suspend fun runWithMusicService(
        context: Context,
        input: TaskerInput<SongInfoInput>,
    ): TaskerPluginResult<SongInfoOutput> {

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
                SongInfoOutput(
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

        /*


        val res = musicService.database.song(songId).firstOrNull()
        val songEntity = res?.song
        val artist = res?.artists?.first()
        val album = res?.album


        if (songEntity != null) {
            return TaskerPluginResultSucess(
                SongInfoOutput(
                    songId = songEntity.id,
                    songTitle = songEntity.title,
                    artistId = artist?.id,
                    artistName = artist?.name,
                    liked = songEntity.liked,
                    year = songEntity.year ?: album?.year,
                    albumTitle = album?.title,
                    albumId = album?.id
                )
            )
        }
        else {
            return TaskerPluginResultErrorWithOutput(
                code = 1,
                message = "Song with ID=${songId} not found"
            )
        }
         */
    }
}