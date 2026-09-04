package com.maxrave.simpmusic.tasker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultErrorWithOutput
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.repository.AlbumRepository
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.repository.HomeRepository
import com.maxrave.domain.repository.LocalPlaylistRepository
import com.maxrave.domain.repository.PlaylistRepository
import com.maxrave.domain.repository.SongRepository
import com.maxrave.logger.Logger
import com.maxrave.media3.service.SimpleMediaService
import com.maxrave.simpmusic.viewModel.HomeViewModel
import kotlinx.coroutines.runBlocking
import multiplatform.network.cmptoast.ToastDuration
import multiplatform.network.cmptoast.ToastGravity
import multiplatform.network.cmptoast.showToast
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.getValue

abstract class CommonRunner<TInput : Any, TOutput : Any> :
    TaskerPluginRunnerAction<TInput, TOutput>(),
    KoinComponent {

    val mediaPlayerHandler by inject<MediaPlayerHandler>()
    val dataStoreManager: DataStoreManager by inject()
    val songRepository by inject<SongRepository>()
    val artistRepository by inject<ArtistRepository>()
    val albumRepository by inject<AlbumRepository>()
    val playlistRepository by inject<PlaylistRepository>()
    val homeRepository by inject<HomeRepository>()
    val localPlaylistRepository by inject<LocalPlaylistRepository>()

    fun showToast(message: String?) {
        showToast(
            message = message ?: "NO MESSAGE",
            duration = ToastDuration.Short,
            gravity = ToastGravity.Bottom,
        )
    }
    /**
     * This is a suspend function to simplify the implementations, since many MusicService
     * and MusicService.database functions are suspend functions.
     */
    abstract suspend fun runWithMusicService(
        context: Context,
        input: TaskerInput<TInput>,
    ): TaskerPluginResult<TOutput>

    override fun run(context: Context, input: TaskerInput<TInput>): TaskerPluginResult<TOutput> {

        /*
        var result: TaskerPluginResult<TOutput>

        runBlocking {
            result = runWithMusicService(context, input)
        }

        return result
        */

        var result: TaskerPluginResult<TOutput>

        runBlocking {
            result = runWithMusicService(context, input)
            /*
            result = musicService?.let {
                runWithMusicService(context, input, it)
            } ?: TaskerPluginResultErrorWithOutput(
                1,
                "Cannot connect to the MusicService"
            )

             */
        }

        return result

        /*

        val countDownLatch = CountDownLatch(1)
        //var musicService: MusicService? = null

        // Create listener for MusicService connection
        val serviceConnection =
            object : ServiceConnection {
                @OptIn(UnstableApi::class)
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {

                    Logger.w("Tasker", "Settings are not valid")

                    //(service as? SimpleMediaService.MusicBinder)?.service.

                    /*
                    if (service is MusicBinder) {
                        musicService = service.service
                        countDownLatch.countDown()
                    }
                    */
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    //musicService = null
                }
            }

        com.maxrave.media3.di
            .startService(context, serviceConnection)
        // Request connection to the music service

        // Wait for musicService to be initialized
        countDownLatch.await(10, TimeUnit.SECONDS)

        try {
            var result: TaskerPluginResult<TOutput>

            // use runBlocking because runWithMusicService is a suspend function
            runBlocking {
                result = runWithMusicService(context, input)
                /*
                result = musicService?.let {
                    runWithMusicService(context, input, it)
                } ?: TaskerPluginResultErrorWithOutput(
                    1,
                    "Cannot connect to the MusicService"
                )

                 */
            }

            return result
        } finally {
            context.unbindService(serviceConnection)
        }

         */

    }
}