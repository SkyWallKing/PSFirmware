package com.etawen.psfirmware

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var card: View
    private lateinit var region: TextView
    private lateinit var toggle: Button
    private lateinit var refresh: Button
    private lateinit var updateCard: View
    private lateinit var updateVersion: TextView
    private lateinit var updateButton: Button
    private lateinit var updateWhatsNew: TextView
    private lateinit var checkUpdate: TextView
    private lateinit var dynamicTheme: Switch
    private lateinit var themePreview: ThemePreview
    private lateinit var iconTuning: View
    private lateinit var iconOffset: SeekBar
    private lateinit var iconScale: SeekBar
    private var regionDialog: AlertDialog? = null
    private var changelogDialog: AlertDialog? = null
    private var installing = false
    /** Usuário foi às configurações liberar a instalação; continua quando voltar. */
    private var resumeInstallAfterPermission = false
    /** Aberto pelo aviso do widget ícone: rolar até a seção de ajuste assim que ela estiver visível. */
    private var scrollToIconTuning = false

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
        updateCard = findViewById(R.id.update_card)
        updateVersion = findViewById(R.id.update_version)
        updateButton = findViewById(R.id.button_update)
        updateWhatsNew = findViewById(R.id.update_whats_new)
        checkUpdate = findViewById(R.id.button_check_update)
        dynamicTheme = findViewById(R.id.switch_dynamic_theme)
        themePreview = ThemePreview(findViewById(R.id.theme_preview))

        iconTuning = findViewById(R.id.icon_tuning)
        iconOffset = findViewById(R.id.seek_icon_offset)
        iconScale = findViewById(R.id.seek_icon_scale)
        setupIconTuning()

        findViewById<View>(R.id.row_dynamic_theme).setOnClickListener { dynamicTheme.toggle() }
        dynamicTheme.setOnCheckedChangeListener { _, checked ->
            if (checked == FirmwareStore.isDynamicTheme(this)) return@setOnCheckedChangeListener
            FirmwareStore.setDynamicTheme(this, checked)
            // Notificação, widget e esta tela trocam de cor na hora.
            if (FirmwareStore.isEnabled(this)) FirmwareUpdater.postNotification(this)
            FirmwareWidget.updateAll(this)
            render()
        }

        updateButton.setOnClickListener { startUpdate() }
        updateWhatsNew.setOnClickListener { showUpdateNotes() }
        findViewById<View>(R.id.button_whats_new).setOnClickListener { showChangelog() }
        checkUpdate.text = getString(R.string.version_footer, UpdateChecker.installedVersion(this))
        checkUpdate.setOnClickListener { checkForUpdates(manual = true) }
        toggle.setOnClickListener { onToggle() }
        refresh.setOnClickListener { refreshNow() }
        findViewById<View>(R.id.button_region).setOnClickListener { chooseRegion() }
        findViewById<View>(R.id.button_battery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Toque na notificação de atualização: já inicia o download. */
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_INSTALL_UPDATE -> {
                intent.action = null
                startUpdate()
            }
            ACTION_SHOW_ICON_TUNING -> {
                intent.action = null
                scrollToIconTuning = true
                // Com a tela já aberta não passa pelo onStart de novo.
                if (::iconTuning.isInitialized) showIconTuningIfPending()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (resumeInstallAfterPermission && packageManager.canRequestPackageInstalls()) {
            resumeInstallAfterPermission = false
            startUpdate()
        }
    }

    private fun checkForUpdates(manual: Boolean) {
        Thread {
            val update = UpdateChecker.checkIfDue(
                applicationContext,
                if (manual) 0 else UpdateChecker.ON_OPEN_INTERVAL_MS,
            )
            runOnUiThread {
                renderUpdate()
                if (manual && update == null) Toast.makeText(this, R.string.up_to_date, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun startUpdate() {
        if (installing) return
        val update = UpdateChecker.availableUpdate(this) ?: return
        if (!packageManager.canRequestPackageInstalls()) {
            resumeInstallAfterPermission = true
            Toast.makeText(this, R.string.update_allow_install, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            )
            return
        }
        installing = true
        UpdateChecker.cancelNotification(this)
        updateButton.isEnabled = false
        updateButton.text = getString(R.string.update_downloading_unknown)
        Thread {
            try {
                UpdateInstaller.downloadAndInstall(applicationContext, update) { percent ->
                    runOnUiThread {
                        updateButton.text = if (percent >= 0) {
                            getString(R.string.update_downloading, percent)
                        } else {
                            getString(R.string.update_downloading_unknown)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show() }
            } finally {
                runOnUiThread {
                    installing = false
                    updateButton.isEnabled = true
                    updateButton.setText(R.string.update_button)
                }
            }
        }.start()
    }

    private fun renderUpdate() {
        val update = UpdateChecker.availableUpdate(this)
        updateCard.visibility = if (update != null) View.VISIBLE else View.GONE
        if (update != null) updateVersion.text = getString(R.string.update_version, update.version)
        updateWhatsNew.visibility = if (update?.notes.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /** Ajuste fino do widget estilo ícone; cada mudança redesenha o widget na hora. */
    private fun setupIconTuning() {
        iconOffset.max = 2 * FirmwareStore.ICON_OFFSET_RANGE
        iconScale.max = FirmwareStore.ICON_SCALE_MAX - FirmwareStore.ICON_SCALE_MIN
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) saveIconTuning(offsetFromBar(), scaleFromBar())
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        }
        iconOffset.setOnSeekBarChangeListener(listener)
        iconScale.setOnSeekBarChangeListener(listener)
        findViewById<View>(R.id.button_icon_reset).setOnClickListener {
            saveIconTuning(0, 100)
            renderIconTuning()
        }
    }

    private fun showIconTuningIfPending() {
        if (!scrollToIconTuning) return
        scrollToIconTuning = false
        renderIconTuning()
        val scroll = findViewById<ScrollView>(R.id.scroll)
        // Depois do layout, para a posição da seção já estar calculada.
        scroll.post {
            scroll.smoothScrollTo(0, iconTuning.top)
            iconTuning.alpha = 0.4f
            iconTuning.animate().alpha(1f).setDuration(900).start()
        }
    }

    private fun offsetFromBar() = iconOffset.progress - FirmwareStore.ICON_OFFSET_RANGE
    private fun scaleFromBar() = iconScale.progress + FirmwareStore.ICON_SCALE_MIN

    private fun saveIconTuning(offset: Int, scale: Int) {
        FirmwareStore.setIconTuning(this, offset, scale)
        FirmwareIconWidget.updateAll(this, FirmwareWidget.texts(this))
        renderIconTuningLabels(offset, scale)
    }

    private fun renderIconTuning() {
        iconTuning.visibility = if (FirmwareIconWidget.hasWidgets(this)) View.VISIBLE else View.GONE
        val offset = FirmwareStore.iconOffset(this)
        val scale = FirmwareStore.iconScale(this)
        iconOffset.progress = offset + FirmwareStore.ICON_OFFSET_RANGE
        iconScale.progress = scale - FirmwareStore.ICON_SCALE_MIN
        renderIconTuningLabels(offset, scale)
    }

    private fun renderIconTuningLabels(offset: Int, scale: Int) {
        val position = when {
            offset < 0 -> getString(R.string.icon_tuning_up, -offset)
            offset > 0 -> getString(R.string.icon_tuning_down, offset)
            else -> getString(R.string.icon_tuning_auto)
        }
        findViewById<TextView>(R.id.label_icon_offset).text = getString(R.string.icon_tuning_offset, position)
        findViewById<TextView>(R.id.label_icon_scale).text = getString(R.string.icon_tuning_scale, scale)
    }

    /** Novidades da versão disponível para atualizar (texto da release no GitHub). */
    private fun showUpdateNotes() {
        val update = UpdateChecker.availableUpdate(this) ?: return
        val entry = Changelog.Entry(update.version, null, update.notes)
        showChangelogDialog(getString(R.string.whats_new_in, update.version), listOf(entry), showAll = false)
    }

    /** Histórico completo, embutido no app. */
    private fun showChangelog() {
        showChangelogDialog(getString(R.string.changelog_title), Changelog.load(this), showAll = false)
    }

    /** Depois de uma atualização, mostra uma vez o que mudou desde a última versão aberta. */
    private fun showUnseenChangelog(unseen: List<Changelog.Entry>) {
        if (unseen.isEmpty()) return
        Changelog.markSeen(this)
        val title = getString(R.string.whats_new_in, UpdateChecker.installedVersion(this))
        showChangelogDialog(title, unseen, showAll = true)
    }

    private fun showChangelogDialog(title: String, entries: List<Changelog.Entry>, showAll: Boolean) {
        if (entries.isEmpty() || changelogDialog?.isShowing == true) return
        val builder = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(title)
            .setMessage(Changelog.format(this, entries))
            .setPositiveButton(android.R.string.ok, null)
        if (showAll) {
            builder.setNeutralButton(R.string.changelog_all) { dialog, _ ->
                // Fecha antes de abrir o próximo: durante o clique este diálogo ainda conta como aberto.
                dialog.dismiss()
                showChangelog()
            }
        }
        changelogDialog = builder.show()
    }

    override fun onStart() {
        super.onStart()
        FirmwareStore.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        render()
        renderUpdate()
        renderIconTuning()
        showIconTuningIfPending()
        themePreview.start()
        checkForUpdates(manual = false)
        // Primeira execução: nenhuma região definida, o usuário precisa escolher uma.
        // Calculado antes de tudo: é aqui que uma instalação nova fica marcada como "já vista", mesmo que o
        // usuário ainda não tenha escolhido a região (senão a próxima atualização mostraria esta versão).
        val unseen = Changelog.unseen(this)
        if (FirmwareStore.selectedRegion(this) == null) {
            chooseRegion()
        } else {
            refreshNow()
            showUnseenChangelog(unseen)
        }
    }

    override fun onStop() {
        FirmwareStore.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        themePreview.stop()
        super.onStop()
    }

    override fun onDestroy() {
        regionDialog?.dismiss()
        changelogDialog?.dismiss()
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
                FirmwareWidget.updateAll(this)
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
        card.setBackgroundResource(CardTheme.current(this).card)
        dynamicTheme.isChecked = FirmwareStore.isDynamicTheme(this)

        val enabled = FirmwareStore.isEnabled(this)
        toggle.setText(if (enabled) R.string.disable_notification else R.string.enable_notification)
        toggle.setBackgroundResource(if (enabled) R.drawable.bg_button_outline else R.drawable.bg_button_primary)
    }

    companion object {
        private const val REQ_NOTIFICATIONS = 10
        const val ACTION_INSTALL_UPDATE = "com.etawen.psfirmware.INSTALL_UPDATE"
        const val ACTION_SHOW_ICON_TUNING = "com.etawen.psfirmware.SHOW_ICON_TUNING"
    }
}
