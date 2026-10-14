package com.yonidev.ydeck.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.yonidev.ydeck.R
import com.yonidev.ydeck.data.Project
import com.yonidev.ydeck.data.Repo
import com.yonidev.ydeck.widget.DeckWidget
import kotlinx.coroutines.launch

/**
 * Picks which project one widget instance shows: from the widget (tap on the name), and, as [ConfigureActivity],
 * right when a widget is added to the home screen.
 */
open class PickerActivity : ComponentActivity() {

    /** Started by the launcher as the widget's configuration step. */
    private val configuring get() = intent.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val deck = Repo.deck(this)
        val widgetId = if (configuring) {
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        } else {
            intent.getIntExtra(EXTRA_WIDGET, -1)
        }
        // Backing out of the configuration step still keeps the widget, showing the first project.
        if (configuring) setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        val current = deck.byId(intent.getStringExtra(ProjectActivity.EXTRA_PROJECT)) ?: deck.projects.firstOrNull()
        setContent {
            DeckTheme {
                Sheet(Color(current?.color ?: 0xFF6E6E73.toInt()), onDismiss = ::finish) {
                    Picker(deck.projects, current?.id) { pick(widgetId, it) }
                }
            }
        }
    }

    private fun pick(widgetId: Int, project: Project) {
        lifecycleScope.launch {
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@PickerActivity).getGlanceIdBy(widgetId)
                updateAppWidgetState(this@PickerActivity, glanceId) { it[DeckWidget.KEY_PROJECT] = project.id }
                DeckWidget().update(this@PickerActivity, glanceId)
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_WIDGET = "widget"
    }
}

/** The widget's configuration activity (exported for the launcher); same picker, launched on add. */
class ConfigureActivity : PickerActivity()

@Composable
private fun Picker(projects: List<Project>, currentId: String?, onPick: (Project) -> Unit) {
    val c = LocalDeck.current
    var query by remember { mutableStateOf("") }
    val shown = projects.filter {
        query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) || it.kind.contains(query.trim(), ignoreCase = true)
    }
    Text(
        "פרויקטים", color = c.fg, fontSize = 19.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(44.dp).clip(RoundedCornerShape(13.dp)).background(c.surface2)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(R.drawable.ic_s_search), null, tint = c.muted, modifier = Modifier.size(17.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("חיפוש פרויקט", color = c.muted, fontSize = 15.sp)
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = c.fg, fontSize = 15.sp),
                cursorBrush = SolidColor(c.fg),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    LazyColumn(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
        items(shown, key = { it.id }) { p ->
            ListRow(
                onClick = { onPick(p) },
                leading = { Logo(p, 36.dp) },
                title = p.name,
                subtitle = p.kind.ifBlank { null },
                trailing = {
                    if (p.id == currentId) {
                        Icon(painterResource(R.drawable.ic_s_check), "נבחר", tint = Color(p.color), modifier = Modifier.size(20.dp))
                    }
                },
            )
        }
        if (shown.isEmpty()) {
            item { Text(if (projects.isEmpty()) "אין עדיין פרויקטים. מוסיפים אותם באפליקציה." else "אין פרויקט בשם הזה", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(18.dp)) }
        }
    }
}
