package com.etawen.psfirmware

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var card: View
    private lateinit var region: TextView
    private lateinit var toggle: Button
    private lateinit var refresh: Button
    private var regionDialog: AlertDialog? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { render() }
    }

    /** A interface do app é sempre em inglês, independente do idioma do aparelho. */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.ENGLISH)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        card = findViewById(R.id.card)
        region = findViewById(R.id.value_region)
        toggle = findViewById(R.id.button_toggle)
        refresh = findViewById(R.id.button_refresh)

        toggle.setOnClickListener { onToggle() }
        refresh.setOnClickListener { refreshNow() }
        findViewById<View>(R.id.button_region).setOnClickListener { chooseRegion() }
        findViewById<View>(R.id.button_battery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override fun onStart() {
        super.onStart()
        FirmwareStore.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        render()
        // Primeira execução: nenhuma região definida, o usuário precisa escolher uma.
        if (FirmwareStore.selectedRegion(this) == null) chooseRegion() else refreshNow()
    }

    override fun onStop() {
        FirmwareStore.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onStop()
    }

    override fun onDestroy() {
        regionDialog?.dismiss()
        super.onDestroy()
    }

    private fun refreshNow() {
        if (FirmwareStore.selectedRegion(this) == null) return
        refresh.isEnabled = false
        Thread {
            val ok = FirmwareUpdater.refresh(applicationContext)
            runOnUiThread {
                refresh.isEnabled = true
                if (!ok) Toast.makeText(this, R.string.fetch_failed, Toast.LENGTH_SHORT).show()
                render()
            }
        }.start()
    }

    private fun chooseRegion() {
        if (regionDialog?.isShowing == true) return
        val regions = FirmwareStore.regions(this).sortedBy { it.name }
        val selected = FirmwareStore.selectedRegion(this)
        val labels = regions.map {
            if (it.status == null || it.isAvailable) it.name else getString(R.string.region_unavailable, it.name)
        }.toTypedArray()
        regionDialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(if (selected == null) R.string.choose_region_first else R.string.choose_region)
            // Sem região definida não há o que mostrar, então a escolha é obrigatória.
            .setCancelable(selected != null)
            .setSingleChoiceItems(labels, regions.indexOfFirst { it.code == selected }) { dialog, which ->
                dialog.dismiss()
                val code = regions[which].code
                if (code == selected) return@setSingleChoiceItems
                FirmwareStore.setSelectedRegion(this, code)
                if (FirmwareStore.isEnabled(this)) FirmwareUpdater.postNotification(this)
                render()
                refreshNow()
            }
            .show()
    }

    private fun onToggle() {
        if (FirmwareStore.isEnabled(this)) {
            FirmwareUpdater.disable(this)
        } else {
            if (FirmwareStore.selectedRegion(this) == null) {
                chooseRegion()
                return
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
                return
            }
            FirmwareUpdater.enable(this)
        }
        render()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != REQ_NOTIFICATIONS) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            FirmwareUpdater.enable(this)
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
        }
        render()
    }

    private fun render() {
        val regionName = FirmwareStore.selectedRegionName(this)
        region.text = regionName ?: getString(R.string.no_region)
        CardTexts.create(this, Locale.ENGLISH, regionName).applyTo(card)

        val enabled = FirmwareStore.isEnabled(this)
        toggle.setText(if (enabled) R.string.disable_notification else R.string.enable_notification)
        toggle.setBackgroundResource(if (enabled) R.drawable.bg_button_outline else R.drawable.bg_button_primary)
    }

    companion object {
        private const val REQ_NOTIFICATIONS = 10
    }
}
