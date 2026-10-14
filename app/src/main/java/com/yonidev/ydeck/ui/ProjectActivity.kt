package com.yonidev.ydeck.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.R
import com.yonidev.ydeck.data.CopyItem
import com.yonidev.ydeck.data.DeckAction
import com.yonidev.ydeck.data.Project
import com.yonidev.ydeck.data.Repo
import com.yonidev.ydeck.data.Router

/** The "more" sheet: every link of one project, which browser it opens in, and one-tap copies. */
class ProjectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val project = Repo.deck(this).byId(intent.getStringExtra(EXTRA_PROJECT)) ?: run { finish(); return }
        setContent {
            DeckTheme {
                Sheet(Color(project.color), onDismiss = ::finish) {
                    ProjectSheet(
                        project,
                        onOpen = { Router.open(this@ProjectActivity, project, it); finish() },
                        onCopy = ::copy,
                        onEdit = { startActivity(MainActivity.projectIntent(this@ProjectActivity, project.id)); finish() },
                    )
                }
            }
        }
    }

    private fun copy(item: CopyItem) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(item.label, item.value))
        // Android 13+ shows its own copy confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, "הועתק", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_PROJECT = "project"
        fun intent(context: Context, projectId: String) =
            Intent(context, ProjectActivity::class.java).putExtra(EXTRA_PROJECT, projectId)
    }
}

@Composable
private fun ProjectSheet(p: Project, onOpen: (DeckAction) -> Unit, onCopy: (CopyItem) -> Unit, onEdit: () -> Unit) {
    val c = LocalDeck.current
    val context = LocalContext.current
    val brand = Color(p.color)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Logo(p, 44.dp)
        Column(Modifier.weight(1f)) {
            Text(p.name, color = c.fg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(p.googleEmail ?: "חשבון גוגל לא הוגדר", color = c.muted, fontSize = 12.5.sp)
        }
        Row(
            Modifier.clip(CircleShape).background(c.surface2).clickable(onClick = onEdit).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("פתיחה", color = c.accentText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    if (p.actions.isEmpty()) {
        Text(
            "אין עדיין קישורים לפרויקט. מוסיפים אותם ב־YDeck במחשב.",
            color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 14.dp)) {
        SectionLabel("קישורים")
        p.actions.forEachIndexed { i, a ->
            val browser = Router.browserFor(p, a)
            val installed = remember(browser.pkg) { Router.isInstalled(context, browser.pkg) }
            ListRow(
                onClick = { onOpen(a) },
                leading = { ServiceIcon(a.service, a.faviconHost, onBrand = if (i == 0) brand else null) },
                title = a.label,
                subtitle = Router.displayUrl(a.url),
                trailing = {
                    if (installed) Chip(browser.label)
                    else Chip("${browser.label} לא מותקן", color = c.warn, bg = c.warn.copy(alpha = 0.12f))
                },
            )
        }
        if (p.copies.isNotEmpty()) {
            SectionLabel("העתקה")
            p.copies.forEach { item ->
                ListRow(
                    onClick = { onCopy(item) },
                    leading = { IconBox(R.drawable.ic_s_copy) },
                    title = item.label,
                    subtitle = item.value,
                    trailing = {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(brand.copy(alpha = 0.12f)).clickable { onCopy(item) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(R.drawable.ic_s_copy), "העתקה", tint = brand, modifier = Modifier.size(17.dp)) }
                    },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (p.googleEmail != null) "שירותי גוגל נפתחים בחשבון ${p.googleEmail}" else "חשבון הגוגל של הפרויקט נקבע ב־YDeck במחשב",
            color = c.muted, fontSize = 11.5.sp, fontFamily = FontFamily.Default,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
}
