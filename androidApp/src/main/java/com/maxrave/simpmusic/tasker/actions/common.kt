package com.maxrave.simpmusic.tasker.actions

import android.content.Context
import androidx.activity.ComponentActivity
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SongRepository
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue

abstract class TaskerCommonConfigActivity<TInput : Any> : ComponentActivity(), TaskerPluginConfig<TInput>, KoinComponent {

    val playlistRepository by inject<PlaylistRepository>()
    val localPlaylistRepository by inject<LocalPlaylistRepository>()

    override val context get() = applicationContext

}

abstract class TaskerCommonRunner<TInput : Any, TOutput : Any> :
    TaskerPluginRunnerAction<TInput, TOutput>(),
    KoinComponent {

    val mediaPlayerHandler by inject<MediaPlayerHandler>()
    val dataStoreManager: DataStoreManager by inject()
    val songRepository by inject<SongRepository>()
    val artistRepository by inject<ArtistRepository>()
    val albumRepository by inject<AlbumRepository>()
    val playlistRepository by inject<PlaylistRepository>()
    val localPlaylistRepository by inject<LocalPlaylistRepository>()


    /**
     * This is a suspend function to simplify the implementations, since many functions are suspend functions.
     */
    abstract suspend fun runSuspended(
        context: Context,
        input: TaskerInput<TInput>,
    ): TaskerPluginResult<TOutput>

    override fun run(context: Context, input: TaskerInput<TInput>): TaskerPluginResult<TOutput> {

        var result: TaskerPluginResult<TOutput>

        runBlocking {
            result = runSuspended(context, input)
        }

        return result
    }
}