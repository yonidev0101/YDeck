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
import com.yonidev.ydeck.data.Overlay
import com.yonidev.ydeck.data.Deck
import com.yonidev.ydeck.data.Favicons
import com.yonidev.ydeck.data.Project
import com.yonidev.ydeck.data.Repo
import com.yonidev.ydeck.data.MorningNotice
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
    /** Ask YDeck; [project]: the project it was opened from. */
    data class Ask(val project: String? = null) : Page()
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

    /** What was done here with each topic of the good morning (see [MorningCard]). */
    private var handled by mutableStateOf<Map<String, Handled>>(emptyMap())

    /** What the conversation's field starts with (talking about a topic of the good morning). */
    private var askText by mutableStateOf("")
    /** What was done with the lines of YDeck's answers (see AnswerCard), by line, while the app is open. */
    private var lines by mutableStateOf<Map<String, Pair<CardAct, Long>>>(emptyMap())

    /** Connecting to a computer just paired: its name while the first state is on its way. */
    private var connecting by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deck = Repo.deck(this)
        page = savedInstanceState?.getString(STATE_PAGE)?.let(::pageOf) ?: intent.getStringExtra(EXTRA_PROJECT)?.let { Page.Project(it) }
            ?: Page.Ask().takeIf { intent.getBooleanExtra(EXTRA_ASK, false) } ?: Page.Home
        // Only on a real launch: not on rotation, not from the widget, not with animations off.
        intro = savedInstanceState == null && page == Page.Home &&
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        Computer.state(this)
        Computer.schedule(this)
        handled = MorningNotice.handled(this).mapNotNull { (k, v) -> runCatching { k to Handled(TopicDone.valueOf(v.substringBefore(':')), v.substringAfter(':', "0").toLong()) }.getOrNull() }.toMap()
        // While the app is on screen, what the computer sends shows within seconds (Claude starting, stopping, done).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    // Quicker while a question waits for its answer.
                    delay(if (Computer.state.value?.chat?.busy == true || Computer.state.value?.waiting?.isNotEmpty() == true || Computer.state.value?.morningBusy == true || Computer.state.value?.memory?.values?.any { it.talkBusy || it.dossierBusy || it.savingBusy } == true) 2_500 else 10_000)
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
                // The good morning comes as a notification: asked for once, when the computer turns it on.
                val morningOn = computer?.morningAt?.let { it >= 0 } == true
                androidx.compose.runtime.LaunchedEffect(morningOn) { if (morningOn) askNotifications() }
                val current = page
                BackHandler(enabled = current != Page.Home) { page = (current as? Page.Ask)?.project?.let { Page.Project(it) } ?: Page.Home }
                AnimatedContent(current, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) }, label = "page") { p ->
                    when (p) {
                        Page.Home -> HomeScreen(
                            Overlay.deck(deck, computer?.pending.orEmpty()), computer?.let { it.copy(memory = it.memory.mapValues { (id, m) -> Overlay.memory(m, it.pending.filter { n -> n.project == id })!! }) }, connecting,
                            onOpen = { page = Page.Project(it.id) },
                            onPair = ::scan,
                            onSample = ::addSample,
                            onSettings = { page = Page.Settings },
                            onRefresh = { sync(quiet = false) },
                            onAsk = { page = Page.Ask() },
                            onReply = ::reply,
                            onNewProject = if (computer?.paired == true) { name, color -> write("p" + System.currentTimeMillis().toString(36), com.yonidev.ydeck.link.Note.NEW_PROJECT, name, listOf(color)) } else null,
                            morning = computer?.takeIf { it.paired && (it.canAsk || it.morning != null) }?.let { comp -> {
                                MorningCard(comp, deck, handled,
                                    onTry = ::tryMorning,
                                    onGo = { t -> goTopic(comp, t) },
                                    onLater = { t -> comp.morning?.let { m -> mark(m, t, TopicDone.LATER) }; write(t.project, com.yonidev.ydeck.link.Note.MORNING_LATER, t.say) },
                                    onTalk = { t -> comp.morning?.let { m -> talkTopic(m, t) } },
                                    onTake = { t, how -> comp.morning?.let { m -> mark(m, t, how) }
                                        when (how) {
                                            TopicDone.IDEA -> write(t.project, com.yonidev.ydeck.link.Note.IDEA_ADD, t.step)
                                            TopicDone.LEFT -> write(t.project, com.yonidev.ydeck.link.Note.LEFT_ADD, t.step)
                                            else -> write(t.project, com.yonidev.ydeck.link.Note.MORNING_SKIP, t.say)
                                        } },
                                    onPick = { id, on -> write(id, com.yonidev.ydeck.link.Note.MORNING_PICK, if (on) "1" else "0") },
                                )
                            } },
                        )
                        is Page.Ask -> AskScreen(
                            computer, deck.projects, p.project?.let(deck::byId),
                            onBack = { page = p.project?.let { Page.Project(it) } ?: Page.Home },
                            onAsk = { q, projectId -> ask(q, projectId) },
                            onNewChat = { Computer.newChat(this@MainActivity) },
                            initial = askText,
                            done = lines,
                            onAct = { key, item, act -> act(computer, key, item, act) },
                        )
                        is Page.Project -> Overlay.deck(deck, computer?.pending.orEmpty()).byId(p.id)?.let { project ->
                            val mine = computer?.pending.orEmpty().filter { it.project == project.id }
                            ProjectScreen(
                                project, Overlay.memory(computer?.memory?.get(project.id), mine), computer?.live(project.id),
                                pending = mine,
                                onBack = { page = Page.Home },
                                onWrite = if (computer?.paired == true) { kind, text -> write(project.id, kind, text) } else null,
                                onEdit = if (computer?.paired == true) { kind, text, args -> write(project.id, kind, text, args) } else null,
                                launches = computer?.launches.orEmpty().filter { it.first.project == project.id },
                                launchOn = computer?.canLaunch == true,
                                onLaunch = if (computer?.paired == true && (computer?.claudeHere == true || computer?.canLaunch == true)) { mode, session, prompt, remote -> launch(project.id, mode, session, prompt, remote) } else null,
                                onAsk = if (computer?.paired == true) { { page = Page.Ask(project.id) } } else null,
                                chat = computer?.chat,
                                waiting = computer?.waiting.orEmpty().filter { it.project == project.id },
                                canReply = computer?.canReply == true,
                                replies = computer?.replies.orEmpty(),
                                onReply = if (computer?.paired == true) ::reply else null,
                                runOn = computer?.canRun == true,
                                runs = computer?.runs.orEmpty().filter { it.first.project == project.id },
                                runOk = computer?.runOk?.get(project.id).orEmpty(),
                                onRun = if (computer?.paired == true) { line, window -> run(project.id, line, window) } else null,
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
        if (debuggable && intent.getBooleanExtra("debug_ask", false)) { intent.removeExtra("debug_ask"); page = Page.Ask() }
        sync(quiet = true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PAGE, when (val p = page) { Page.Home, Page.Scan -> "home"; Page.Settings -> "settings"; is Page.Project -> "p:" + p.id; is Page.Ask -> "ask:" + p.project.orEmpty() })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_PROJECT)?.let { deck = Repo.deck(this); page = Page.Project(it) }
        if (intent.getBooleanExtra(EXTRA_ASK, false)) { intent.removeExtra(EXTRA_ASK); page = Page.Ask() }
    }

    /** A topic of the good morning, done with here: shown quietly under it from now on. */
    private fun mark(m: com.yonidev.ydeck.link.Morning, t: com.yonidev.ydeck.link.MorningTopic, how: TopicDone) {
        val key = topicKey(m, m.topics.indexOf(t))
        val now = System.currentTimeMillis()
        handled = handled + (key to Handled(how, now))
        MorningNotice.handle(this, key, how.name + ":" + now)
    }

    /** A good morning now: asked of the computer, then watched until it comes (or says why not). */
    private fun tryMorning() {
        lifecycleScope.launch {
            val before = Computer.state.value?.morning?.id
            if (!Computer.askMorning(this@MainActivity)) { toast("אין חיבור לאינטרנט. נסו שוב."); return@launch }
            for (attempt in 0 until 50) {
                delay(3_000)
                Computer.sync(this@MainActivity)
                val s = Computer.state.value ?: break
                if (attempt > 3 && !s.morningBusy && (s.morning?.id != before || s.morningError.isNotEmpty())) break
            }
        }
    }

    /** "Let's go": Claude opens on the computer, in that project, with the step the good morning suggested. */
    private fun goTopic(computer: Computer.State, t: com.yonidev.ydeck.link.MorningTopic) {
        if (!computer.canLaunch) {
            toast("כדי לפתוח את Claude מהטלפון, מדליקים פעם אחת במחשב: הגדרות ← טלפון ← ״הטלפון יכול לפתוח את Claude Code״.")
            return
        }
        val m = computer.morning ?: return
        confirmOwner("פתיחת Claude במחשב") {
            mark(m, t, TopicDone.GO)
            write(t.project, com.yonidev.ydeck.link.Note.MORNING_GO, t.say)
            send(t.project, com.yonidev.ydeck.link.Launch.NEW, "", t.step.ifBlank { t.say }, false)
        }
    }

    /**
     * A line of an answer, acted on: Claude opens on the computer to do it, it's marked done, or it goes into what's
     * left or the ideas; or the project opens. Shown quietly in the line's place from then on.
     */
    private fun act(computer: Computer.State?, key: String, item: com.yonidev.ydeck.link.CardItem, act: CardAct) {
        val now = System.currentTimeMillis()
        fun done() { lines = lines + (key to (act to now)) }
        when (act) {
            CardAct.OPEN -> page = Page.Project(item.project)
            CardAct.GO -> {
                if (computer?.canLaunch != true) {
                    toast("כדי לפתוח את Claude מהטלפון, מדליקים פעם אחת במחשב: הגדרות ← טלפון ← ״הטלפון יכול לפתוח את Claude Code״.")
                    return
                }
                // Claude works on it in the project, in the conversation on its page, here on the phone.
                confirmOwner("לכתוב ל־Claude בפרויקט") {
                    done()
                    write(item.project, com.yonidev.ydeck.link.Note.TALK_SAY, item.text)
                    page = Page.Project(item.project)
                }
            }
            CardAct.DONE -> { done(); write(item.project, com.yonidev.ydeck.link.Note.LEFT_DONE, item.text) }
            CardAct.TAKE -> { done(); write(item.project, com.yonidev.ydeck.link.Note.IDEA_TAKE, item.text) }
            CardAct.LEFT -> { done(); write(item.project, com.yonidev.ydeck.link.Note.LEFT_ADD, item.text) }
            CardAct.IDEA -> { done(); write(item.project, com.yonidev.ydeck.link.Note.IDEA_ADD, item.text) }
        }
    }

    /** "Talk about it": the conversation goes on from the good morning, about this topic. */
    private fun talkTopic(m: com.yonidev.ydeck.link.Morning, t: com.yonidev.ydeck.link.MorningTopic) {
        mark(m, t, TopicDone.TALK)
        Computer.talkMorning(this, m)
        askText = "לגבי " + (deck.byId(t.project)?.name ?: t.project) + ": "
        page = Page.Ask()
    }

    /** Android 13 and up ask before an app shows notifications: once, the first time the good morning is on. */
    private fun askNotifications() {
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("ydeck", MODE_PRIVATE)
        if (prefs.getBoolean("askedNotifications", false)) return
        prefs.edit().putBoolean("askedNotifications", true).apply()
        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7)
    }

    private fun pageOf(s: String): Page = when {
        s == "settings" -> Page.Settings
        s.startsWith("ask:") -> Page.Ask(s.removePrefix("ask:").ifEmpty { null })
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
    private fun write(projectId: String, kind: String, text: String, args: List<String> = emptyList()) {
        lifecycleScope.launch {
            val sent = Computer.write(this@MainActivity, projectId, kind, text, args)
            // A change in the lists or the details shows at once on the page; only a written note says so in words.
            val quiet = kind != com.yonidev.ydeck.link.Note.NEXT && kind != com.yonidev.ydeck.link.Note.DECISION && kind != com.yonidev.ydeck.link.Note.DONE
            if (!sent) toast("נשמר בטלפון, יישלח כשיהיה חיבור")
            else if (!quiet) toast(if (kind == com.yonidev.ydeck.link.Note.DONE) "סומן כבוצע" else "נשלח למחשב")
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
        confirmOwner("פתיחת Claude במחשב") { send(projectId, mode, session, prompt, remote) }
    }

    /** Runs [then] after the phone's own unlock is confirmed; at once when the phone has no screen lock. */
    private fun confirmOwner(title: String, then: () -> Unit) {
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) { then(); return }
        val auth = android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
            android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        android.hardware.biometrics.BiometricPrompt.Builder(this)
            .setTitle(title)
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

    /**
     * A command line for the computer: the phone's owner confirms first, then it goes at once, and is watched while it
     * waits for approval there and runs (a few minutes at most; the page shows what came after).
     */
    private fun run(projectId: String, line: String, window: Boolean) {
        confirmOwner("הרצת פקודה במחשב") {
            lifecycleScope.launch {
                if (!Computer.run(this@MainActivity, projectId, line, window)) { toast("אין חיבור לאינטרנט. הפקודה לא נשלחה."); return@launch }
                toast("נשלח למחשב")
                for (attempt in 0 until 40) {
                    delay(4_000)
                    if (Computer.sync(this@MainActivity) == Computer.Result.UPDATED) deck = Repo.deck(this@MainActivity)
                    val news = Computer.state.value?.runs.orEmpty().lastOrNull { it.first.project == projectId }?.second
                    if (news != null && (news.state == com.yonidev.ydeck.link.Ran.DONE || news.state == com.yonidev.ydeck.link.Ran.NO)) break
                }
            }
        }
    }

    /** A question for Ask YDeck: sent now, or said that it couldn't be; the answer comes with the next states. */
    private fun ask(question: String, projectId: String?, morning: Boolean = false) {
        lifecycleScope.launch {
            if (morning && Computer.state.value?.chat?.turns?.isNotEmpty() == true) Computer.newChat(this@MainActivity)
            if (!Computer.ask(this@MainActivity, question, projectId, morning)) toast("אין חיבור לאינטרנט. השאלה לא נשלחה.")
        }
    }

    /**
     * A reply to a session waiting on the computer: the phone's owner confirms first (as for opening Claude), then it
     * goes at once, and is watched until the computer says it reached Claude.
     */
    private fun reply(waiting: String, text: String, choices: Map<String, String>) {
        confirmOwner("תשובה ל־Claude במחשב") {
            lifecycleScope.launch {
                if (!Computer.reply(this@MainActivity, waiting, text, choices)) { toast("אין חיבור לאינטרנט. התשובה לא נשלחה."); return@launch }
                for (attempt in 0 until 24) {
                    delay(2_500)
                    if (Computer.sync(this@MainActivity) == Computer.Result.UPDATED) deck = Repo.deck(this@MainActivity)
                    if (Computer.state.value?.replies.orEmpty().none { it.first.waiting == waiting && it.second == null }) break
                }
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
        const val EXTRA_ASK = "ask"

        /** Opens the app at the projects, where the good morning is (from its notification). */
        fun homeIntent(context: Context) = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Opens the app at the conversation with YDeck. */
        fun askIntent(context: Context) = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_ASK, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        private const val STATE_PAGE = "page"

        /** Opens the app at a project's page (from the widget's sheet and the icon's shortcuts). */
        fun projectIntent(context: Context, projectId: String) = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_PROJECT, projectId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
