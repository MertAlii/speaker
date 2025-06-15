package dev.danjackson.speaker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        // Check if this is the first launch
        val sharedPrefs = getSharedPreferences("speaker_prefs", MODE_PRIVATE)
        val isFirstLaunch = sharedPrefs.getBoolean("is_first_launch", true)

        if (!isFirstLaunch) {
            // Skip welcome screen and go directly to main activity
            startMainActivity()
            return
        }

        // Mark as not first launch
        sharedPrefs.edit().putBoolean("is_first_launch", false).apply()

        setupViews()
    }

    private fun setupViews() {
        val getStartedButton = findViewById<MaterialButton>(R.id.btn_get_started)
        getStartedButton.setOnClickListener {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        
        permissions.add(Manifest.permission.READ_PHONE_STATE)
        
        // Bildirim izni Android 13+ için
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            showBatteryOptimizationDialog()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = mutableListOf<String>()
            
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i])
                }
            }
            
            if (deniedPermissions.isNotEmpty()) {
                showPermissionExplanationDialog(deniedPermissions)
            } else {
                startMainActivity()
            }
        }
    }

    private fun showPermissionExplanationDialog(deniedPermissions: List<String>) {
        val message = buildString {
            append("Bu uygulamanın düzgün çalışması için aşağıdaki izinler gereklidir:\n\n")
            
            if (deniedPermissions.contains(Manifest.permission.BLUETOOTH_CONNECT)) {
                append("• Bluetooth izni: Bluetooth cihazlarınızı algılamak için\n")
            }
            
            if (deniedPermissions.contains(Manifest.permission.READ_PHONE_STATE)) {
                append("• Telefon durumu izni: Telefon çağrıları sırasında uygulamanın düzgün çalışması için\n")
            }
            
            append("\nBu izinler olmadan uygulama tam olarak çalışmayabilir.")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("İzinler Gerekli")
            .setMessage(message)
            .setPositiveButton("Tekrar Dene") { _, _ ->
                requestPermissions()
            }
            .setNegativeButton("Devam Et") { _, _ ->
                startMainActivity()
            }
            .setCancelable(false)
            .show()
    }

    private fun showBatteryOptimizationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Pil Tasarrufu")
            .setMessage("Uygulamanın arka planda çalışabilmesi için pil tasarrufu ayarlarından uygulamayı muaf tutmanız gerekiyor. Bu sayede uygulama kesintisiz çalışabilir.")
            .setPositiveButton("Ayarlara Git") { _, _ ->
                openBatteryOptimizationSettings()
            }
            .setNegativeButton("Şimdilik Geç") { _, _ ->
                startMainActivity()
            }
            .setCancelable(false)
            .show()
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            // Eğer ayarlar açılamazsa direkt ana ekrana git
            startMainActivity()
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
} 