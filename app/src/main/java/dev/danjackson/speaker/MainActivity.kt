package dev.danjackson.speaker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.ImageButton
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: MaterialTextView
    private lateinit var btnStartStop: MaterialButton
    private lateinit var switchAutoplay: SwitchMaterial
    private lateinit var sliderVolume: Slider
    private lateinit var tvVolumeValue: MaterialTextView
    private lateinit var toggleSoundType: MaterialButtonToggleGroup
    private lateinit var dropdownSoundType: AutoCompleteTextView
    private lateinit var tvSelectedDevices: MaterialTextView
    private lateinit var btnSoundType: MaterialButton
    private lateinit var switchNotifications: SwitchMaterial
    private lateinit var btnLanguageTr: ImageButton
    private lateinit var btnLanguageEn: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupViews()
        setupListeners()
        loadSettings()
        setupAboutSection()
        checkPermissions()
    }

    private fun setupViews() {
        tvStatus = findViewById(R.id.tv_status)
        btnStartStop = findViewById(R.id.btn_start_stop)
        switchAutoplay = findViewById(R.id.switch_autoplay)
        sliderVolume = findViewById(R.id.slider_volume)
        tvVolumeValue = findViewById(R.id.tv_volume_value)
        btnSoundType = findViewById(R.id.btn_sound_type)
        tvSelectedDevices = findViewById(R.id.tv_selected_devices)
        switchNotifications = findViewById(R.id.switch_notifications)
    }

    private fun setupListeners() {
        // Start/Stop Button
        btnStartStop.setOnClickListener {
            val monitor = Monitor.getInstance(applicationContext)
            if (monitor.playing.value == true) {
                monitor.stop()
                updateUI(false)
            } else {
                monitor.play()
                updateUI(true)
            }
        }

        // What is this button
        findViewById<MaterialButton>(R.id.btn_what_is_this).setOnClickListener {
            showInfoDialog()
        }

        // Auto play switch
        switchAutoplay.setOnCheckedChangeListener { _, isChecked ->
            saveAutoplaySetting(isChecked)
            val monitor = Monitor.getInstance(applicationContext)
            monitor.mainActivityResumed()
        }

        // Device selection button
        findViewById<MaterialButton>(R.id.btn_select_devices).setOnClickListener {
            showDeviceSelectionDialog()
        }

        // Volume slider
        sliderVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                saveVolumeSetting(value.toInt())
                tvVolumeValue.text = getString(R.string.volume_percent, value.toInt())
            }
        }

        // Sound type button
        btnSoundType.setOnClickListener {
            showSoundTypeDialog()
        }

        // Notification switch
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            saveNotificationSetting(isChecked)
            updateNotificationService(isChecked)
        }

        // Monitor playing state
        val monitor = Monitor.getInstance(applicationContext)
        monitor.playing.observe(this) { isPlaying ->
            updateUI(isPlaying)
        }
    }

    private fun setupSoundTypeDropdown() {
        val soundTypes = arrayOf(
            getString(R.string.brown_noise),
            getString(R.string.white_noise),
            getString(R.string.pink_noise)
        )
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, soundTypes)
        dropdownSoundType.setAdapter(adapter)
        
        dropdownSoundType.setOnItemClickListener { _, _, position, _ ->
            val soundType = when (position) {
                0 -> "brown"
                1 -> "white"
                2 -> "pink"
                else -> "brown"
            }
            saveSoundTypeSetting(soundType)
        }
    }

    private fun updateSelectedDevicesDisplay() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val selectedDevices = prefs.getStringSet("device_list", setOf()) ?: setOf()
        
        if (selectedDevices.isEmpty()) {
            tvSelectedDevices.text = getString(R.string.no_devices_selected)
        } else {
            val deviceCount = selectedDevices.size
            val deviceList = selectedDevices.joinToString(", ")
            tvSelectedDevices.text = "($deviceCount cihaz) $deviceList"
        }
    }

    private fun updateUI(isPlaying: Boolean) {
        if (isPlaying) {
            tvStatus.text = getString(R.string.status_playing)
            btnStartStop.text = getString(R.string.stop)
        } else {
            tvStatus.text = getString(R.string.status_ready)
            btnStartStop.text = getString(R.string.start)
        }
    }

    private fun showInfoDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.info_dialog_title)
            .setMessage(R.string.info_dialog_message)
            .setPositiveButton(R.string.info_dialog_ok, null)
            .show()
    }

    private fun showDeviceSelectionDialog() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth kapalı", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices = bluetoothAdapter.bondedDevices
        val deviceNames = pairedDevices.map { it.name }.toTypedArray()

        if (deviceNames.isEmpty()) {
            Toast.makeText(this, R.string.device_selection_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val selectedDevices = prefs.getStringSet("device_list", setOf()) ?: setOf()
        val checkedItems = deviceNames.map { selectedDevices.contains(it) }.toBooleanArray()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.device_selection_title)
            .setMultiChoiceItems(deviceNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(R.string.device_selection_select) { _, _ ->
                val newSelectedDevices = deviceNames.filterIndexed { index, _ -> checkedItems[index] }.toSet()
                prefs.edit().putStringSet("device_list", newSelectedDevices).apply()
                
                // Update selected devices display
                updateSelectedDevicesDisplay()
                
                val monitor = Monitor.getInstance(applicationContext)
                monitor.mainActivityResumed()
            }
            .setNegativeButton(R.string.device_selection_cancel, null)
            .setNeutralButton(R.string.device_selection_select_all) { _, _ ->
                // Tüm cihazları seç
                for (i in checkedItems.indices) {
                    checkedItems[i] = true
                }
                
                val newSelectedDevices = deviceNames.toSet()
                prefs.edit().putStringSet("device_list", newSelectedDevices).apply()
                
                // Update selected devices display
                updateSelectedDevicesDisplay()
                
                val monitor = Monitor.getInstance(applicationContext)
                monitor.mainActivityResumed()
                
                Toast.makeText(this, "Tüm Bluetooth cihazları seçildi", Toast.LENGTH_SHORT).show()
            }
            .create()

        dialog.show()
    }

    private fun loadSettings() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Auto play
        val autoplay = prefs.getBoolean("autoplay", true)
        switchAutoplay.isChecked = autoplay
        
        // Volume - 0-100 arasında
        val volume = prefs.getInt("volume", 10)
        sliderVolume.value = volume.toFloat()
        tvVolumeValue.text = getString(R.string.volume_percent, volume)
        
        // Sound type - sadece kahverengi gürültü
        btnSoundType.text = getString(R.string.brown_noise)
        
        // Notifications
        val notifications = prefs.getBoolean("notifications", true)
        switchNotifications.isChecked = notifications
        
        // Update selected devices display
        updateSelectedDevicesDisplay()
    }

    private fun saveAutoplaySetting(enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putBoolean("autoplay", enabled).apply()
    }

    private fun saveVolumeSetting(volume: Int) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putInt("volume", volume).apply()
    }

    private fun saveSoundTypeSetting(soundType: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putString("sound", soundType).apply()
    }

    private fun saveNotificationSetting(enabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().putBoolean("notifications", enabled).apply()
    }

    private fun setupAboutSection() {
        // GitHub Button
        findViewById<MaterialButton>(R.id.btn_github).setOnClickListener {
            openUrl("https://github.com/MertAlii")
        }

        // Instagram Button
        findViewById<MaterialButton>(R.id.btn_instagram).setOnClickListener {
            openUrl("https://www.instagram.com/mer1.alii/")
        }

        // Repository Button
        findViewById<MaterialButton>(R.id.btn_repository).setOnClickListener {
            openUrl("https://github.com/MertAlii/speaker")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Bağlantı açılamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions granted
            } else {
                Toast.makeText(this, "Bazı izinler reddedildi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val monitor = Monitor.getInstance(applicationContext)
        monitor.mainActivityResumed()
    }

    private fun showSoundTypeDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sound_type)
            .setItems(arrayOf(getString(R.string.brown_noise))) { _, _ ->
                // Sadece kahverengi gürültü var
                saveSoundTypeSetting("brown")
            }
            .show()
    }

    private fun updateNotificationService(enabled: Boolean) {
        val monitor = Monitor.getInstance(applicationContext)
        if (enabled) {
            // Bildirimleri aç
            if (monitor.playing.value == true) {
                // Eğer çalıyorsa bildirim göster
                val intent = Intent(this, BackgroundSound::class.java)
                intent.putExtra("start", true)
                intent.putExtra("show_notification", true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
        } else {
            // Bildirimleri kapat
            val intent = Intent(this, BackgroundSound::class.java)
            intent.putExtra("hide_notification", true)
            startService(intent)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
