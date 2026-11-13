package com.yonidev.ydeck.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nayuki.qrcodegen.QrCode
import kotlinx.coroutines.delay

/* Settings → Phone: pair YDeck on the phone by QR, see when it last read, unpair. */

@Composable
fun PhoneSettings() {
    val c = LocalDeck.current
    val s by PhoneLink.state.collectAsState()
    val settings by Settings.state.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    // "Just now" stays true while the page is open.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); now = System.currentTimeMillis() } }
    // A code left on screen stops working when the page closes.
    DisposableEffect(Unit) { onDispose { PhoneLink.cancelPair() } }

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.widthIn(max = 560.dp)) {
            Text(tr("The projects' memory, in your pocket.", "הזיכרון של הפרויקטים, בכיס."), color = c.fg, fontSize = 20.sp, fontFamily = Display)
            Text(tr("On the phone: where every project stands, what the last session did, what's next, the decisions. Write a decision or a next step there, and the next Claude session here starts knowing it.",
                "בטלפון: איפה כל פרויקט עומד, מה הסשן האחרון עשה, מה הבא, וההחלטות. כותבים שם החלטה או צעד הבא, והסשן הבא של Claude כאן מתחיל כשהוא כבר יודע."),
                color = c.muted, fontSize = 14.sp, lineHeight = 21.sp, fontFamily = PlexSans)
        }

        when {
            s.qr != null -> PairCard(s.qr!!, s.qrUntil, onClose = { PhoneLink.cancelPair() })
            !s.paired -> Row { Pill(tr("Pair a phone", "חיבור טלפון"), Tone.PRIMARY, Glyph.PHONE) { PhoneLink.pair() } }
            else -> Column {
                Group {
                    Item {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.offline) c.faint else Color(0xFF34C77B)))
                        Column(Modifier.weight(1f)) {
                            Text(tr("Phone paired", "טלפון מחובר"), color = c.fg, fontSize = 14.sp, fontFamily = PlexSans)
                            Text(when {
                                s.offline -> tr("No connection to the relay; trying again every minute", "אין חיבור לממסר, מנסה שוב כל דקה")
                                s.phoneReadAt != null -> tr("The phone last updated ", "הטלפון התעדכן ") + ago(s.phoneReadAt!!, now)
                                else -> tr("Waiting for the phone to open YDeck", "מחכה שהטלפון יפתח את YDeck")
                            }, color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
                        }
                    }
                    if (Tools.claude != null) Setting(tr("The phone can open Claude Code", "הטלפון יכול לפתוח את Claude Code"),
                        hint = tr("Off until you turn it on here. In a project's folder, in a new terminal: a new session, the last one, or an earlier one, with Remote Control if asked. Claude works in your usual permission mode, and a notification shows each time.",
                            "כבוי עד שמדליקים כאן. בתיקיית הפרויקט, בטרמינל חדש: סשן חדש, האחרון או סשן קודם, עם Remote Control אם ביקשו. Claude עובד במצב ההרשאות הרגיל שלך, וכל פתיחה מופיעה כהתראה.")) {
                        Toggle(settings.phoneLaunch) { on -> Settings.update { it.copy(phoneLaunch = on) }; PhoneLink.nudge() }
                    }
                    if (Tools.claude != null) Setting(tr("The phone can ask YDeck", "הטלפון יכול לשאול את YDeck"),
                        hint = tr("Off until you turn it on here. Questions typed on the phone are answered here by your Claude Code, as in Ask YDeck: it reads the projects' folders, never changes them, and never reads the files that hold keys.",
                            "כבוי עד שמדליקים כאן. שאלות שנכתבות בטלפון נענות כאן על ידי ה־Claude Code שלך, כמו ב״לשאול את YDeck״: הוא קורא את תיקיות הפרויקטים, אף פעם לא משנה אותן, ולא קורא קבצים שמחזיקים מפתחות.")) {
                        Toggle(settings.phoneAsk) { on -> Settings.update { it.copy(phoneAsk = on) }; PhoneLink.nudge() }
                    }
                    // A good morning on the phone: Claude here goes over the projects and writes first, like a partner.
                    if (Tools.claude != null) Setting(tr("Good morning on the phone", "בוקר טוב בטלפון"),
                        hint = tr("Off until you turn it on here. Each morning, at the hour below, your Claude Code (reading only) goes over the projects and writes to you: what to finish today, what is stuck, an idea to think about, with questions. It comes as a notification, and you answer it on the phone. Needs \"The phone can ask YDeck\" on.",
                            "כבוי עד שמדליקים כאן. כל בוקר, בשעה שלמטה, ה־Claude Code שלך (בקריאה בלבד) עובר על הפרויקטים וכותב לך: מה לסיים היום, מה תקוע, רעיון לחשוב עליו, עם שאלות. זה מגיע כהתראה, ועונים לו מהטלפון. צריך ש״הטלפון יכול לשאול את YDeck״ יהיה דלוק.")) {
                        Toggle(settings.phoneMorning, enabled = settings.phoneAsk) { on -> Settings.update { it.copy(phoneMorning = on) }; PhoneLink.nudge() }
                    }
                    if (Tools.claude != null && settings.phoneMorning && settings.phoneAsk) {
                        Item {
                            Text(tr("When", "מתי"), color = c.fg, fontSize = 14.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f))
                            listOf(7 * 60, 7 * 60 + 30, 8 * 60, 8 * 60 + 30, 9 * 60).forEach { m ->
                                Pill("%d:%02d".format(m / 60, m % 60), if (settings.morningAt == m) Tone.PRIMARY else Tone.SOFT, small = true) {
                                    Settings.update { it.copy(morningAt = m) }; PhoneLink.nudge()
                                }
                            }
                        }
                        Setting(tr("On Friday and Saturday too", "גם בשישי ובשבת"), hint = tr("Otherwise Sunday to Thursday.", "אחרת ראשון עד חמישי.")) {
                            Toggle(settings.morningWeekend) { on -> Settings.update { it.copy(morningWeekend = on) } }
                        }
                        // Which projects: picked, left out, or (the rest) worked on in the last two weeks.
                        var picking by remember { mutableStateOf(false) }
                        var reviewDone by remember { mutableStateOf<String?>(null) }
                        val picked = remember(settings, Repo.deck.collectAsState().value) { GoodMorning.projects().map { it.id }.toSet() }
                        Setting(tr("Which projects", "על אילו פרויקטים"), value = tr("${picked.size} projects", "${picked.size} פרויקטים"),
                            hint = tr("By itself: the ones you worked on in the last two weeks. Pick or leave out any.", "לבד: אלה שעבדת עליהם בשבועיים האחרונים. אפשר להוסיף או להוציא."),
                            onClick = { picking = !picking })
                        if (picking) Repo.deck.value.active.forEach { p ->
                            Item {
                                Text(p.name, color = c.fg, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f))
                                Toggle(p.id in picked) { on ->
                                    Settings.update { s -> s.copy(morningIn = if (on) s.morningIn + p.id else s.morningIn - p.id,
                                        morningOut = if (on) s.morningOut - p.id else s.morningOut + p.id) }
                                    PhoneLink.nudge()
                                }
                            }
                        }
                        // The day's review: one project's code read, and suggestions for the next good morning.
                        var reviewing by remember { mutableStateOf<String?>(null) }
                        Setting(tr("Review a project now", "לסקור פרויקט עכשיו"),
                            hint = reviewing ?: tr("Each morning, an hour before, Claude reads one project's code in turn and suggests an idea or something to close; the good morning brings it. This runs it now (a few minutes).", "כל בוקר, שעה לפני, Claude קורא את הקוד של פרויקט אחד בתורו ומציע רעיון או משהו לסגור, והבוקר טוב מביא את זה. כאן מריצים עכשיו (כמה דקות)."),
                            onClick = if (reviewing != null) null else ({ reviewing = tr("Claude is reading…", "Claude קורא…"); PhoneLink.reviewNow { name -> reviewing = null; reviewDone = name?.let { tr("Reviewed: $it. Send a good morning to see it.", "נסקר: $it. אפשר לשלוח בוקר טוב כדי לראות.") } ?: tr("The review didn't work.", "הסקירה לא הצליחה.") } }))
                        reviewDone?.let { Item { Text(it, color = c.muted, fontSize = 12.sp, fontFamily = PlexSans) } }
                        val news by PhoneLink.morningNews.collectAsState()
                        Setting(tr("Send one now", "לשלוח אחד עכשיו"),
                            hint = when {
                                news.busy -> tr("Claude is going over ${news.projects} projects…", "Claude עובר על ${news.projects} פרויקטים…")
                                news.error.isNotEmpty() -> tr("Didn't work: ", "לא הצליח: ") + news.error
                                news.at > 0 -> tr("The last one went to the phone ", "האחרון נשלח לטלפון ") + ago(news.at, System.currentTimeMillis())
                                else -> tr("To see what it says. It becomes today's good morning.", "כדי לראות מה הוא כותב. הוא יהיה הבוקר טוב של היום.")
                            },
                            onClick = if (news.busy) null else ({ PhoneLink.morningNow() }))
                    }
                    if (Tools.claude != null) Setting(tr("The phone can reply to waiting sessions", "הטלפון יכול לענות לסשנים שמחכים"),
                        hint = tr("Off until you turn it on here. A session's question, held in YDeck while you're away, shows on the phone with the drafted reply; what you send goes to Claude as your words. Never a permission: those stay in the terminal.",
                            "כבוי עד שמדליקים כאן. שאלה של סשן, שמחכה ב־YDeck כשאתה לא ליד המחשב, מופיעה בטלפון עם התשובה המנוסחת; מה ששולחים מגיע ל־Claude כמילים שלך. אף פעם לא הרשאה: אלה נשארות בטרמינל.")) {
                        Toggle(settings.phoneReply) { on -> Settings.update { it.copy(phoneReply = on) }; PhoneLink.nudge() }
                    }
                    Setting(tr("The phone can ask to run commands", "הטלפון יכול לבקש להריץ פקודות"),
                        hint = tr("Off until you turn it on here. A command typed on the phone waits for you here: run it once, allow that exact line always in that project, or don't. It runs in a new terminal window, in the project's folder, in sight.",
                            "כבוי עד שמדליקים כאן. פקודה שנכתבת בטלפון מחכה לך כאן: להריץ פעם אחת, לאשר תמיד את השורה הזו בדיוק בפרויקט הזה, או לא. היא רצה בחלון טרמינל חדש, בתיקיית הפרויקט, לעיני כל.")) {
                        Toggle(settings.phoneRun) { on -> Settings.update { it.copy(phoneRun = on) }; PhoneLink.nudge() }
                    }
                    // What was approved always, each one undone with a click.
                    settings.runOk.forEach { (pid, lines) ->
                        val name = Repo.deck.value.byId(pid)?.name ?: return@forEach
                        lines.forEach { line ->
                            Item {
                                Column(Modifier.weight(1f)) {
                                    Text(line, color = c.fg, fontSize = 13.sp, fontFamily = PlexMono, maxLines = 1)
                                    Text(tr("Always allowed in $name", "מאושר תמיד ב־$name"), color = c.muted, fontSize = 12.sp, fontFamily = PlexSans)
                                }
                                Pill(tr("Remove", "הסרה"), Tone.SOFT, small = true) { Runs.forget(pid, line); PhoneLink.nudge() }
                            }
                        }
                    }
                    Setting(tr("Pair a phone again", "חיבור טלפון מחדש"), hint = tr("A new code; the phone that scans it replaces this one.", "קוד חדש; הטלפון שיסרוק אותו יחליף את המחובר עכשיו."),
                        onClick = { PhoneLink.pair() })
                    Item({ confirm = true }) {
                        Text(tr("Unpair phone", "ניתוק טלפון"), color = c.danger, fontSize = 14.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f))
                    }
                }
                if (confirm) Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("The phone stops receiving updates and keeps what it last saw.", "הטלפון יפסיק להתעדכן וישאר עם מה שראה לאחרונה."),
                        color = c.muted, fontSize = 13.sp, fontFamily = PlexSans, modifier = Modifier.weight(1f, fill = false))
                    Pill(tr("Unpair", "ניתוק"), Tone.DARK, small = true) { confirm = false; PhoneLink.unpair() }
                    Pill(tr("Cancel", "ביטול"), Tone.SOFT, small = true) { confirm = false }
                }
            }
        }

        Text(tr("Only data goes through, sealed end to end: the relay in between can't read it or change it. The phone can ask this computer for four things, each only once you turn it on above: to open Claude Code in a project's folder, only in those ways; to answer a question, reading only; to pass your reply to a session that waits for you; and to run a command line, which waits for you here unless you allowed that exact line always. Never a permission for Claude. Nothing here listens for connections; both sides only reach out.",
            "עוברים רק נתונים, מוצפנים מקצה לקצה: הממסר שבאמצע לא יכול לקרוא אותם או לשנות אותם. הטלפון יכול לבקש מהמחשב ארבעה דברים, כל אחד רק אחרי שמדליקים אותו למעלה: לפתוח את Claude Code בתיקיית פרויקט, רק בדרכים האלה; לענות על שאלה, בקריאה בלבד; להעביר את התשובה שלך לסשן שמחכה לך; ולהריץ פקודה, שמחכה לך כאן אלא אם אישרת את השורה הזו בדיוק לתמיד. אף פעם לא הרשאה ל־Claude. שום דבר כאן לא מחכה לחיבורים; שני הצדדים רק יוצאים החוצה."),
            color = c.faint, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = PlexSans, modifier = Modifier.widthIn(max = 560.dp))
    }
}

@Composable
private fun PairCard(text: String, until: Long, onClose: () -> Unit) {
    val c = LocalDeck.current
    var left by remember { mutableStateOf(0L) }
    LaunchedEffect(until) { while (true) { left = ((until - System.currentTimeMillis()) / 1000).coerceAtLeast(0); delay(1_000) } }
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        // Dark on light, with its quiet zone: every scanner reads it, also in the dark theme.
        Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)) { Qr(text, 216.dp) }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.widthIn(max = 300.dp)) {
            listOf(
                tr("Open YDeck on the phone", "פותחים את YDeck בטלפון"),
                tr("Tap “Connect to a computer”", "נוגעים ב״חיבור למחשב״"),
                tr("Scan this code", "סורקים את הקוד הזה"),
            ).forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(c.surface2), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = c.fg, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = PlexSans)
                    }
                    Text(step, color = c.fg, fontSize = 14.sp, fontFamily = PlexSans)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFF5A524)))
                Text(tr("Waiting for the phone… new code in ${left / 60}:${"%02d".format(left % 60)}", "מחכה לטלפון… קוד חדש בעוד ${left / 60}:${"%02d".format(left % 60)}"),
                    color = c.muted, fontSize = 13.sp, fontFamily = PlexSans)
                Pill(tr("Cancel", "ביטול"), Tone.SOFT, small = true, onClick = onClose)
            }
            Text(tr("Works once, for two minutes. The lasting key is made by the phone and never shown.", "עובד פעם אחת, לשתי דקות. המפתח הקבוע נוצר בטלפון ולא מוצג אף פעם."),
                color = c.faint, fontSize = 12.sp, fontFamily = PlexSans)
        }
    }
}

/** A QR code, drawn module by module (medium error correction). */
@Composable
fun Qr(text: String, size: Dp, color: Color = Color(0xFF0B0B0D)) {
    val qr = remember(text) { QrCode.encodeText(text, QrCode.Ecc.MEDIUM) }
    Canvas(Modifier.size(size)) {
        val cell = this.size.width / qr.size
        for (y in 0 until qr.size) for (x in 0 until qr.size) {
            if (qr.getModule(x, y)) drawRect(color, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
