// FieldNotes — AudioInputRouter.kt
// Authored by: audio module | Implements: 04_AUDIO_MODULE.md
package com.fieldnotes.app.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder.AudioSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Monitors available audio inputs and exposes headset connect/disconnect events. */
@Singleton
class AudioInputRouter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Returns available audio sources as (AudioSource constant, display label) pairs. */
    fun availableSources(): List<Pair<Int, String>> {
        val sources = mutableListOf(AudioSource.MIC to "Built-in Microphone")
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS) ?: emptyArray()
        val hasWiredMic = devices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        if (hasWiredMic) {
            sources.add(AudioSource.UNPROCESSED to "Headset Microphone")
        }
        return sources
    }

    /** Emits true when a mic-capable wired headset is connected. */
    fun headsetMicConnected(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getIntExtra("state", -1)
                val hasMic = intent.getIntExtra("microphone", 0) == 1
                trySend(state == 1 && hasMic)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_HEADSET_PLUG))
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
