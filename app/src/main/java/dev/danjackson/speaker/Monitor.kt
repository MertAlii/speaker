package dev.danjackson.speaker

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import kotlin.math.ln


class Monitor(private var applicationContext: Context) {

    private var sharedPreferencesListener: OnSharedPreferenceChangeListener? = null
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyManager: TelephonyManager? = null

    var playing = MutableLiveData<Boolean>()
    var summary = MutableLiveData<List<String>?>()

    private var autoplay = true
    private var sound: String = "brown"
    private var setVolume = 1.0   // percent
    private var triggerDevices = listOf<String>()
    private var wasPlayingBeforeCall = false
    private var shouldResumeAfterCall = false
    private var continuousPlay = true  // Sürekli çalma özelliği

    private fun preferencesUpdated(prefs: SharedPreferences) {
        autoplay = prefs.getBoolean("autoplay", true)
        sound = prefs.getString("sound", "brown")!!
        setVolume = prefs.getInt("volume", 10) / 100.0  // 0-100 -> 0.0-1.0

        val selectedDevices = prefs.getStringSet("device_list", setOf<String>()).orEmpty()
        triggerDevices = selectedDevices.toList()

        summary.value = if (autoplay) {
            val list = mutableListOf<String>()
            list.addAll(triggerDevices)
            list
        } else null
    }

    private fun refreshState(deviceName: String?, deviceConnected: Boolean) {
        val listed = arrayListOf<String>()

        val bluetoothManager = applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        // Ignore everything if Autoplay disabled or Bluetooth is not enabled
        if (!autoplay || bluetoothAdapter?.isEnabled == true) {
            // If we've just been told a listed device is connected, trust this fact (in case it doesn't show up in the list)
            if (deviceName != null && triggerDevices.contains(deviceName) && deviceConnected) listed.add(deviceName)

            // If we're on a recent version, also check the list of connected devices (this will cope with multiple connections)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val audioManager = applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val audioDeviceInfo = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                //println(">>> AUDIODEVICES: $audioDeviceInfo")
                for (devInfo in audioDeviceInfo) {
                    // Ignore a listed device if we are being told about its connectivity (in case the list lags the disconnection event)
                    if (devInfo.productName != deviceName) {
                        if (triggerDevices.contains(devInfo.productName)) listed.add(devInfo.productName.toString())
                    }
                }
            }
        }

        // Sürekli çalma aktifse her zaman çal
        if (continuousPlay) {
            if (!isPlaying()) {
                play()
            }
        } else {
            // Sürekli çalma kapalıysa sadece cihaz bağlıysa çal
            if (listed.isNotEmpty()) {
                if (!isPlaying()) {
                    play()
                }
            } else {
                if (isPlaying()) {
                    stop()
                }
            }
        }
    }

    fun mainActivityResumed() {
        //println("MONITOR: mainActivityResumed")
        refreshState(null, false)
    }

    fun connectionStateChanged(device: String?, state: Int) {
        //println("MONITOR: connectionStateChanged $previousState -> $state: $device")
        val connected = state == BluetoothProfile.STATE_CONNECTED
        refreshState(device, connected)
    }

    private fun sendService(playing: Boolean) {
        //println("SENDSERVICE: playing=$playing")

        val intent = Intent(applicationContext, BackgroundSound::class.java)
        // intent.setClass(context, BackgroundSound::class.java)

        if (playing) {
            intent.putExtra("start", playing)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        } else {
            applicationContext.stopService(intent)
        }
    }

    fun play() {
        if (!isPlaying()) {
            sendService(true)
        }
    }

    fun stop() {
        if (isPlaying()) {
            sendService(false)
        }
    }

    // --- media player ---

    private var mediaPlayer: MediaPlayer? = null

    fun mediaStart() {
        //println("MEDIA-START")
        synchronized (this) {
            try {
                if (mediaPlayer == null) {
                    val res = when (sound) {
                        "brown" -> R.raw.noise_brown
                        else -> -1
                    }

                    if (res != -1) {
                        mediaPlayer = MediaPlayer.create(applicationContext, res)
                        mediaPlayer?.isLooping = true

                        val maxVolume = 100.0
                        val volume = 1.0 - (ln(maxVolume - setVolume) / ln(maxVolume))
                        mediaPlayer?.setVolume(volume.toFloat(), volume.toFloat())
                        
                        // Set audio stream type to avoid conflicts with phone calls
                        mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
                    }
                }
                if (mediaPlayer != null) {
                    mediaPlayer?.start()
                    playing.value = true
                }
            } catch (e: Exception) {
                // Handle any MediaPlayer errors gracefully
                mediaPlayer?.release()
                mediaPlayer = null
                playing.value = false
            }
        }
    }

    fun mediaStop() {
        synchronized(this) {
            //println("MEDIA-STOP")
            try {
                if (mediaPlayer != null) {
                    if (mediaPlayer?.isPlaying == true) {
                        mediaPlayer?.stop()
                    }
                    mediaPlayer?.release()
                    mediaPlayer = null
                }
                playing.value = false
            } catch (e: Exception) {
                // Handle any MediaPlayer errors gracefully
                mediaPlayer = null
                playing.value = false
            }
        }
    }

    private fun isPlaying(): Boolean {
        synchronized (this) {
            return try {
                mediaPlayer?.isPlaying == true
            } catch (e: Exception) {
                false
            }
        }
    }

    // Phone call handling
    private fun setupPhoneStateListener() {
        try {
            telephonyManager = applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            
            phoneStateListener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING -> {
                            // Gelen çağrı - ses seviyesini düşür ama çalmaya devam et
                            if (isPlaying()) {
                                wasPlayingBeforeCall = true
                                shouldResumeAfterCall = true
                                // Ses seviyesini düşür ama durdurma
                                mediaPlayer?.setVolume(0.05f, 0.05f)
                            }
                        }
                        TelephonyManager.CALL_STATE_OFFHOOK -> {
                            // Çağrı aktif - ses seviyesini düşük tut ama çalmaya devam et
                            if (isPlaying()) {
                                wasPlayingBeforeCall = true
                                shouldResumeAfterCall = true
                                // Ses seviyesini düşük tut ama durdurma
                                mediaPlayer?.setVolume(0.05f, 0.05f)
                            }
                        }
                        TelephonyManager.CALL_STATE_IDLE -> {
                            // Çağrı bitti - normal ses seviyesine dön
                            if (shouldResumeAfterCall && wasPlayingBeforeCall) {
                                wasPlayingBeforeCall = false
                                shouldResumeAfterCall = false
                                // Normal ses seviyesine dön
                                val maxVolume = 100.0
                                val volume = 1.0 - (ln(maxVolume - setVolume) / ln(maxVolume))
                                mediaPlayer?.setVolume(volume.toFloat(), volume.toFloat())
                            }
                        }
                    }
                }
            }
            
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: Exception) {
            // Handle permission or other errors gracefully
        }
    }

    private fun cleanupPhoneStateListener() {
        try {
            if (phoneStateListener != null && telephonyManager != null) {
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
                phoneStateListener = null
                telephonyManager = null
            }
        } catch (e: Exception) {
            // Handle cleanup errors gracefully
        }
    }

    // Singleton
    companion object {
        private var singleInstance: Monitor? = null
        fun getInstance(applicationContext: Context): Monitor {
            synchronized(this) {
                if (singleInstance == null) {
                    singleInstance = Monitor(applicationContext)
                }
                return singleInstance!!
            }
        }
    }

    init {
        playing.value = false
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        preferencesUpdated(sharedPreferences)
        sharedPreferencesListener = OnSharedPreferenceChangeListener { _, _ ->
            preferencesUpdated(sharedPreferences)
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
        
        // Setup phone state listener to handle call interruptions
        setupPhoneStateListener()
    }

    // Cleanup method to be called when the app is destroyed
    fun cleanup() {
        cleanupPhoneStateListener()
        mediaStop()
        sharedPreferencesListener?.let { listener ->
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
                .unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}