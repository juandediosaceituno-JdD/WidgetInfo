package com.jdd.weatherwidget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CALENDAR
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        updateUI(permissions.values.all { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val allGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        updateUI(allGranted)

        findViewById<Button>(R.id.btn_grant).setOnClickListener {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    private fun updateUI(allGranted: Boolean) {
        val statusText = findViewById<TextView>(R.id.tv_status)
        val btn = findViewById<Button>(R.id.btn_grant)

        if (allGranted) {
            statusText.text = getString(R.string.permissions_ok)
            btn.isEnabled = false
            WidgetUpdateWorker.schedule(this)
        } else {
            statusText.text = getString(R.string.permissions_needed)
            btn.isEnabled = true
        }
    }
}
