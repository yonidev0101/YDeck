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

        Text(tr("Only data goes through, sealed end to end: the relay in between can't read it or change it. The one thing the phone can ask this computer to do is open Claude Code in a project's folder, only once you turn it on above, and only in those ways; never a command of its own. Nothing here listens for connections; both sides only reach out.",
            "עוברים רק נתונים, מוצפנים מקצה לקצה: הממסר שבאמצע לא יכול לקרוא אותם או לשנות אותם. הדבר היחיד שהטלפון יכול לבקש מהמחשב הוא לפתוח את Claude Code בתיקיית פרויקט, רק אחרי שמדליקים את זה למעלה, ורק בדרכים האלה; אף פעם לא פקודה משלו. שום דבר כאן לא מחכה לחיבורים; שני הצדדים רק יוצאים החוצה."),
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
