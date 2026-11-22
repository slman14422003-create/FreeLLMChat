package com.freellm.chat.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.freellm.chat.domain.SaverPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SaverState(context: Context, settings: SettingsStore, scope: CoroutineScope) {
    private val ctx = context.applicationContext
    private val pm = ctx.getSystemService(PowerManager::class.java)
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val power = MutableStateFlow(pm?.isPowerSaveMode == true)
    private val metered = MutableStateFlow(cm?.isActiveNetworkMetered == true)

    val active: StateFlow<Boolean> = combine(settings.saverMode, power, metered) { m, p, n ->
        SaverPolicy.active(m, p, n)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    fun start() {
        runCatching {
            ContextCompat.registerReceiver(
                ctx,
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, i: Intent?) {
                        power.value = pm?.isPowerSaveMode == true
                    }
                },
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    metered.value = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
            })
        }
    }
}
