package com.yonidev.ydeck.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.yonidev.ydeck.data.Computer
import com.yonidev.ydeck.data.Deck
import com.yonidev.ydeck.data.Favicons
import com.yonidev.ydeck.data.Project
import com.yonidev.ydeck.data.Repo
import com.yonidev.ydeck.widget.BrandArt
import com.yonidev.ydeck.widget.DeckWidget
import com.yonidev.ydeck.widget.DeckWidgetReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the app is: the projects, one project's page, or settings. */
sealed class Page {
    data object Home : Page()
    data class Project(val id: String) : Page()
    data object Settings : Page()
    /** YDeck's own scanner, when Google's can't open. */
    data object Scan : Page()
}

/**
 * YDeck on the phone: the projects' memory, in your pocket. The computer is where projects are made; the phone reads
 * them, with what each one's last session did and what's next, from the computer (see [Computer]).
 */
class MainActivity : ComponentActivity() {

    private var deck by mutableStateOf(Deck(emptyList()))
    private var page by mutableStateOf<Page>(Page.Home)

    /** The logo sting is playing (cold launch only). */
    private var intro by mutableStateOf(false)

    /** A scanned code waiting for "replace the projects here?". */
    private var pendingPair by mutableStateOf<String?>(null)

    /** Connecting to a computer just paired: its name while the first state is on its way. */
    private var connecting by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deck = Repo.deck(this)
        page = savedInstanceState?.getString(STATE_PAGE)?.let(::pageOf) ?: intent.getStringExtra(EXTRA_PROJECT)?.let { Page.Project(it) } ?: Page.Home
        // Only on a real launch: not on rotation, not from the widget, not with animations off.
        intro = savedInstanceState == null && page == Page.Home &&
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        Computer.state(this)
        Computer.schedule(this)
        // While the app is on screen, what the computer sends shows within seconds (Claude starting, stopping, done).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(10_000)
                    if (Computer.state.value?.paired == true && Computer.sync(this@MainActivity) == Computer.Result.UPDATED) deck = Repo.deck(this@MainActivity)
                }
            }
        }
        refreshFavicons()
        updateShortcuts()
        // Light bar icons on the night ground.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            DeckTheme {
                val computer by Computer.state.collectAsState()
                val current = page
                BackHandler(enabled = current != Page.Home) { page = Page.Home }
                AnimatedContent(current, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) }, label = "page") { p ->
                    when (p) {
                        Page.Home -> HomeScreen(
                            deck, computer, connecting,
                            onOpen = { page = Page.Project(it.id) },
                            onPair = ::scan,
                            onSample = ::addSample,
                            onSettings = { page = Page.Settings },
                            onRefresh = { sync(quiet = false) },
                        )
                        is Page.Project -> deck.byId(p.id)?.let { project ->
                            ProjectScreen(
                                project, computer?.memory?.get(project.id), computer?.live(project.id),
                                pending = computer?.pending.orEmpty().filter { it.project == project.id },
                                onBack = { page = Page.Home },
                                onWrite = if (computer?.paired == true) { kind, text -> write(project.id, kind, text) } else null,
                                launches = computer?.launches.orEmpty().filter { it.first.project == project.id },
                                launchOn = computer?.canLaunch == true,
                                onLaunch = if (computer?.paired == true && (computer?.claudeHere == true || computer?.canLaunch == true)) { mode, session, prompt, remote -> launch(project.id, mode, session, prompt, remote) } else null,
                            )
                        } ?: androidx.compose.runtime.LaunchedEffect(p) { page = Page.Home }
                        Page.Scan -> ScanScreen(onCode = { page = Page.Home; scanned(it) }, onClose = { page = Page.Home })
                        Page.Settings -> SettingsScreen(
                            computer,
                            onBack = { page = Page.Home },
                            onPair = ::scan,
                            onRefresh = { sync(quiet = false) },
                            onUnpair = { Computer.unpair(this@MainActivity); toast("המחשב נותק. הפרויקטים נשארים כאן.") },
                            onAddWidget = ::pinWidget,
                            onPrivacy = { openUrl("https://ydeck-app.pages.dev/privacy") },
                        )
                    }
                }
                pendingPair?.let { code ->
                    AlertDialog(
                        onDismissRequest = { pendingPair = null },
                        title = { Text("להחליף את הפרויקטים שכאן?") },
                        text = { Text("יש בטלפון ${deck.projects.size} פרויקטים. מעכשיו הפרויקטים יגיעו מהמחשב, והם יחליפו את אלה שכאן.") },
                        confirmButton = { TextButton({ pendingPair = null; pair(code) }) { Text("חיבור", color = LocalDeck.current.accentText) } },
                        dismissButton = { TextButton({ pendingPair = null }) { Text("ביטול", color = LocalDeck.current.fg) } },
                    )
                }
                AnimatedVisibility(intro, enter = EnterTransition.None, exit = fadeOut(tween(400))) {
                    LogoIntro(onDone = { intro = false })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Debug builds only: a pairing code through adb (the emulator has no camera to scan with).
        //   adb shell am start -n com.yonidev.ydeck/.ui.MainActivity --es debug_pair "ydeck:pair?..."
        val debuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        intent.getStringExtra("debug_pair")?.takeIf { debuggable }?.let { intent.removeExtra("debug_pair"); pair(it); return }
        if (debuggable && intent.getBooleanExtra("debug_scan", false)) { intent.removeExtra("debug_scan"); page = Page.Scan }
        sync(quiet = true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PAGE, when (val p = page) { Page.Home, Page.Scan -> "home"; Page.Settings -> "settings"; is Page.Project -> "p:" + p.id })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_PROJECT)?.let { deck = Repo.deck(this); page = Page.Project(it) }
    }

    private fun pageOf(s: String): Page = when {
        s == "settings" -> Page.Settings
        s.startsWith("p:") -> Page.Project(s.removePrefix("p:"))
        else -> Page.Home
    }

    // ── the computer ──

    /**
     * Google's code scanner first: the camera runs inside Play services, so no camera permission. It's a module Play
     * services downloads (with the app, see the manifest, or on first use); when it can't open, YDeck's own scanner.
     */
    private fun scan() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        runCatching {
            GmsBarcodeScanning.getClient(this, options).startScan()
                .addOnSuccessListener { code -> scanned(code.rawValue.orEmpty()) }
                .addOnFailureListener { page = Page.Scan }
        }.onFailure { page = Page.Scan }
    }

    private fun scanned(text: String) {
        when {
            com.yonidev.ydeck.link.Pairing.parse(text) == null -> toast("זה לא קוד של YDeck. במחשב: YDeck ← הגדרות ← טלפון.")
            deck.projects.any { it.id != Project.sample().id } && !(Computer.state.value?.paired ?: false) -> pendingPair = text
            else -> pair(text)
        }
    }

    private fun pair(code: String) {
        lifecycleScope.launch {
            val name = when (val r = Computer.pair(this@MainActivity, code)) {
                is Computer.Paired.Done -> r.name
                Computer.Paired.Expired -> { toast("הקוד כבר לא בתוקף. במחשב: הגדרות ← טלפון, מציגים קוד חדש וסורקים שוב."); return@launch }
                Computer.Paired.Offline -> { toast("אין חיבור לאינטרנט"); return@launch }
                Computer.Paired.NotOurs -> return@launch
            }
            connecting = name
            Computer.schedule(this@MainActivity)
            // The computer takes the key within a few seconds (it looks every 3) and sends its state at once.
            var result = Computer.Result.EMPTY
            for (attempt in 0 until 8) {
                result = Computer.sync(this@MainActivity)
                if (result == Computer.Result.UPDATED || result == Computer.Result.SAME) break
                delay(2_500)
            }
            connecting = null
            deck = Repo.deck(this@MainActivity)
            page = Page.Home
            when (result) {
                Computer.Result.UPDATED, Computer.Result.SAME -> toast("מחובר ל־$name")
                Computer.Result.OFFLINE -> toast("אין חיבור לאינטרנט. ננסה שוב כשיהיה.")
                else -> toast("המחשב עוד לא שלח. ודאו ש־YDeck פתוח במחשב, ונסו לרענן.")
            }
            afterChange()
        }
    }

    private fun sync(quiet: Boolean) {
        if (Computer.state.value?.paired != true) return
        lifecycleScope.launch {
            val result = Computer.sync(this@MainActivity)
            if (result == Computer.Result.UPDATED) {
                deck = Repo.deck(this@MainActivity)
                afterChange()
            }
            if (!quiet) toast(when (result) {
                Computer.Result.UPDATED -> "עודכן"
                Computer.Result.SAME -> "הכל מעודכן"
                Computer.Result.OFFLINE -> "אין חיבור לאינטרנט"
                Computer.Result.REFUSED -> "המחשב ניתק את הטלפון. אפשר להתחבר מחדש בסריקה."
                Computer.Result.EMPTY -> "המחשב עוד לא שלח"
                Computer.Result.BROKEN -> "משהו בנתונים לא תקין. נסו להתחבר מחדש."
            })
        }
    }

    /**
     * A decision or a next step for a project: kept on the phone at once, sent to the relay, then watched for a
     * minute or so until the computer says it's stored (YDeck on the computer takes notes every 20 seconds).
     */
    private fun write(projectId: String, kind: String, text: String) {
        lifecycleScope.launch {
            val sent = Computer.write(this@MainActivity, projectId, kind, text)
            toast(when {
                !sent -> "נשמר בטלפון, יישלח כשיהיה חיבור"
                kind == com.yonidev.ydeck.link.Note.DONE -> "סומן כבוצע"
                else -> "נשלח למחשב"
            })
            for (attempt in 0 until 8) {
                delay(10_000)
                if (Computer.state.value?.pending.orEmpty().none { it.project == projectId }) break
                if (Computer.sync(this@MainActivity) == Computer.Result.UPDATED) deck = Repo.deck(this@MainActivity)
            }
        }
    }

    /**
     * Claude Code on the computer, asked for: sent at once (or not at all), then watched for a minute or so until the
     * computer says what it did (YDeck there takes requests every 20 seconds).
     */
    private fun launch(projectId: String, mode: String, session: String, prompt: String, remote: Boolean) {
        // Whoever has the phone in hand proves it's theirs first: its screen lock (PIN, pattern, password or a finger).
        confirmOwner { send(projectId, mode, session, prompt, remote) }
    }

    /** Runs [then] after the phone's own unlock is confirmed; at once when the phone has no screen lock. */
    private fun confirmOwner(then: () -> Unit) {
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) { then(); return }
        val auth = android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
            android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        android.hardware.biometrics.BiometricPrompt.Builder(this)
            .setTitle("פתיחת Claude במחשב")
            .setSubtitle("אישור שזה אתה")
            .setAllowedAuthenticators(auth)
            .build()
            .authenticate(android.os.CancellationSignal(), mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) = then()
            })
    }

    private fun send(projectId: String, mode: String, session: String, prompt: String, remote: Boolean) {
        lifecycleScope.launch {
            if (!Computer.launch(this@MainActivity, projectId, mode, session, prompt, remote)) {
                toast("אין חיבור לאינטרנט. הבקשה לא נשלחה.")
                return@launch
            }
            toast("נשלח למחשב")
            for (attempt in 0 until 15) {
                delay(5_000)
                if (Computer.sync(this@MainActivity) == Computer.Result.UPDATED) deck = Repo.deck(this@MainActivity)
                if (Computer.state.value?.launches.orEmpty().none { it.first.project == projectId && it.second == null }) break
            }
        }
    }

    /** "Try a sample": Nova, with its memory, to see the app before pairing. */
    private fun addSample() {
        val next = Deck(deck.projects + Project.sample())
        deck = next
        Computer.addSample(this)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { Repo.save(this@MainActivity, next) }
            afterChange()
        }
    }

    private suspend fun afterChange() {
        DeckWidget().updateAll(this)
        updateShortcuts()
        refreshFavicons()
    }

    /** Long-press shortcuts on the app icon: the first four projects open their page. */
    private fun updateShortcuts() {
        val list = deck.projects.take(4).mapIndexed { rank, p ->
            ShortcutInfoCompat.Builder(this, "project_${p.id}")
                .setShortLabel(p.name.ifBlank { "פרויקט" })
                .setIcon(IconCompat.createWithBitmap(BrandArt.logo(this, p, 48)))
                .setIntent(projectIntent(this, p.id).setAction(Intent.ACTION_VIEW))
                .setRank(rank)
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(this, list) }
    }

    /** Fetches site icons for links without a brand mark, then redraws the widgets that show them. */
    private fun refreshFavicons() {
        lifecycleScope.launch {
            if (Favicons.fetchMissing(this@MainActivity, deck)) DeckWidget().updateAll(this@MainActivity)
        }
    }

    private fun openUrl(url: String) = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun pinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, DeckWidgetReceiver::class.java), null, null)
        } else {
            toast("לחיצה ארוכה על מסך הבית ← ווידג'טים ← YDeck")
        }
    }

    companion object {
        const val EXTRA_PROJECT = "project"
        private const val STATE_PAGE = "page"

        /** Opens the app at a project's page (from the widget's sheet and the icon's shortcuts). */
        fun projectIntent(context: Context, projectId: String) = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_PROJECT, projectId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
