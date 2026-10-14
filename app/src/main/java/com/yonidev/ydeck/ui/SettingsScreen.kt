package com.yonidev.ydeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.data.Computer

@Composable
fun SettingsScreen(
    computer: Computer.State?,
    onBack: () -> Unit,
    onPair: () -> Unit,
    onRefresh: () -> Unit,
    onUnpair: () -> Unit,
    onAddWidget: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val c = LocalDeck.current
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val paired = computer?.paired == true
    PageScaffold(title = "הגדרות", onBack = onBack) {
        item {
            SectionCard("מחשב") {
                if (paired) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c.closed))
                        Column(Modifier.weight(1f)) {
                            Text(computer!!.name.ifBlank { "המחשב" }, color = c.fg, fontSize = 16.sp)
                            Text(
                                if (computer.stateAt > 0) "עודכן ${ago(computer.checkedAt.coerceAtLeast(computer.stateAt))}" else "מחכה שהמחשב ישלח",
                                color = c.muted, fontSize = 13.sp,
                            )
                        }
                    }
                    Divider(inset = 18.dp)
                    Action("עדכון עכשיו", onClick = onRefresh)
                    Divider(inset = 18.dp)
                    Action("חיבור מחדש בסריקה", onClick = onPair)
                    Divider(inset = 18.dp)
                    Action("ניתוק מהמחשב", color = c.danger) { confirm = true }
                } else {
                    Action("חיבור למחשב", color = c.accentText, onClick = onPair)
                }
            }
        }
        item {
            FootNote(
                "עוברים רק נתונים, מוצפנים מקצה לקצה עם מפתח שעבר רק בקוד שסרקת. הממסר שבאמצע לא יכול לקרוא אותם, " +
                    "והטלפון לא מריץ שום דבר מהמחשב. הטלפון מתעדכן כשפותחים אותו, ופעם בשעה בשביל הווידג'ט.",
            )
        }
        item {
            SectionCard("ווידג'ט") {
                Action("הוספה למסך הבית", onClick = onAddWidget)
            }
        }
        item {
            SectionCard("אודות") {
                Action("מדיניות פרטיות", onClick = onPrivacy)
                Divider(inset = 18.dp)
                val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Mark(22.dp)
                    Text("YDeck", color = c.fg, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Text("גרסה ⁦$version⁩", color = c.faint, fontSize = 13.sp)
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("לנתק מהמחשב?") },
            text = { Text("הטלפון יפסיק להתעדכן. הפרויקטים והזיכרון שכבר כאן נשארים. אפשר להתחבר שוב בסריקה.") },
            confirmButton = { TextButton({ confirm = false; onUnpair() }) { Text("ניתוק", color = c.danger) } },
            dismissButton = { TextButton({ confirm = false }) { Text("ביטול", color = c.fg) } },
        )
    }
}

@Composable
private fun Action(text: String, color: Color = LocalDeck.current.fg, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
    )
}
