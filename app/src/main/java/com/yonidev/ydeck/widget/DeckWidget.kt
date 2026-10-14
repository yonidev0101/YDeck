package com.yonidev.ydeck.widget

import android.content.Context
import android.graphics.Bitmap
import android.util.LayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.yonidev.ydeck.R
import com.yonidev.ydeck.data.BrandColors
import com.yonidev.ydeck.data.Deck
import com.yonidev.ydeck.data.Computer
import com.yonidev.ydeck.data.Favicons
import com.yonidev.ydeck.link.Memory
import com.yonidev.ydeck.data.Project
import com.yonidev.ydeck.data.Repo
import com.yonidev.ydeck.ui.MainActivity
import com.yonidev.ydeck.ui.OpenActivity
import com.yonidev.ydeck.ui.PickerActivity
import com.yonidev.ydeck.ui.ProjectActivity
import androidx.glance.unit.ColorProvider as Fixed
import androidx.glance.unit.ColorProvider as GlanceColor

/**
 * One widget for every project ("Glass" design: bare service marks on a translucent card tinted with the project colour).
 * Arrows (or the picker) switch the project per widget instance; each square opens its link through
 * [OpenActivity], which picks the account and the Chrome channel.
 * Squares keep a fixed size computed from the space available, so tall launcher cells add air, never stretch them.
 */
class DeckWidget : GlanceAppWidget() {
    // Exact, not Responsive: LocalSize must be the widget's real size (on tall launcher cells it is about twice
    // the LARGE bucket), or the squares come out tiny and the labels never fit.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        // The widget is always laid out right-to-left. Glance has no layout-direction switch, so when the
        // phone itself is LTR every row is built in reverse ("mirror") to keep the Hebrew order.
        val mirror = context.resources.configuration.layoutDirection != LayoutDirection.RTL
        provideContent {
            // Re-read after every in-app save (version bumps) so edits show without re-adding the widget.
            Repo.version.collectAsState().value
            Favicons.version.collectAsState().value
            // The memory the computer last sent: the "next" under the project's name.
            val memory = (Computer.state.collectAsState().value ?: Computer.state(context)).memory
            val deck = Repo.deck(context)
            val selected = currentState<Preferences>()[KEY_PROJECT]
            val project = deck.byId(selected) ?: deck.projects.firstOrNull()
            if (project == null) Empty() else Content(deck, project, memory[project.id], widgetId, mirror)
        }
    }

    companion object {
        val KEY_PROJECT = stringPreferencesKey("project")
        val SMALL = DpSize(110.dp, 100.dp)
        val MEDIUM = DpSize(220.dp, 100.dp)
        val LARGE = DpSize(220.dp, 170.dp)

        val P_PROJECT = ActionParameters.Key<String>(ProjectActivity.EXTRA_PROJECT)
        val P_ACTION = ActionParameters.Key<Int>(OpenActivity.EXTRA_ACTION)
        val P_WIDGET = ActionParameters.Key<Int>(PickerActivity.EXTRA_WIDGET)
        val P_DIR = ActionParameters.Key<Int>("dir")
    }
}

class DeckWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeckWidget()
}

/** Steps this widget instance to the next/previous project. */
class CycleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val list = Repo.deck(context).projects
        if (list.isEmpty()) return
        val dir = parameters[DeckWidget.P_DIR] ?: 1
        updateAppWidgetState(context, glanceId) { prefs ->
            val i = list.indexOfFirst { it.id == prefs[DeckWidget.KEY_PROJECT] }.coerceAtLeast(0)
            prefs[DeckWidget.KEY_PROJECT] = list[(i + dir + list.size) % list.size].id
        }
        DeckWidget().update(context, glanceId)
    }
}

// Palette: the glass card on the brand's night (0.9: always dark, like the app), washed with a little of the
// project's colour; bare service marks, no squares. The next step, when one waits, in amber.
private val FG = Fixed(Color(0xFFF4F4F5))
private val MUTED = Fixed(Color(0xFFA1A1A8))
private val LABEL = Fixed(Color(0xFFD1D1D6))
private val DOT = Fixed(Color(0x4DF5F5F7))
private val OPEN_TEXT = Fixed(Color(0xFFF5C46B))
private const val TINT = 0.14f

/** Near-black project colours stay neutral glass: a black wash only greys the card. */
private fun isNearBlack(color: Int) = android.graphics.Color.luminance(color) < 0.03f

/** [top] at alpha [ta] laid over [base] at alpha [ba], as one translucent colour. */
private fun over(top: Int, ta: Float, base: Color, ba: Float): Color {
    val a = ta + ba * (1 - ta)
    val t = Color(top)
    fun ch(tc: Float, bc: Float) = (tc * ta + bc * ba * (1 - ta)) / a
    return Color(ch(t.red, base.red), ch(t.green, base.green), ch(t.blue, base.blue), a)
}

/**
 * The card: the night, nearly opaque, tinted with the project's colour. Launchers can't blur the wallpaper behind a
 * widget, so it stays dark enough for the labels on any wallpaper.
 */
private fun glass(projectColor: Int): GlanceColor {
    val tint = if (isNearBlack(projectColor)) 0f else TINT
    return Fixed(over(projectColor, tint * 1.2f, Color(0xFF0B0B0D), 0.84f))
}

/** The current project's page dot: its own colour (a near-black project shows the ink colour instead). */
private fun activeDot(projectColor: Int): GlanceColor =
    if (isNearBlack(projectColor)) FG else Fixed(Color(projectColor))

private fun openAction(p: Project, index: Int): Action =
    actionStartActivity<OpenActivity>(actionParametersOf(DeckWidget.P_PROJECT to p.id, DeckWidget.P_ACTION to index))

private fun moreAction(p: Project): Action =
    actionStartActivity<ProjectActivity>(actionParametersOf(DeckWidget.P_PROJECT to p.id))

private fun pickerAction(p: Project, widgetId: Int): Action =
    actionStartActivity<PickerActivity>(actionParametersOf(DeckWidget.P_WIDGET to widgetId, DeckWidget.P_PROJECT to p.id))

private fun cycle(dir: Int): Action = actionRunCallback<CycleAction>(actionParametersOf(DeckWidget.P_DIR to dir))

/** The service's brand colour, adjusted per theme so near-black and pale marks stay visible. */
private fun serviceTint(color: Int?): GlanceColor =
    if (color == null) FG else Fixed(Color(BrandColors.adapt(color, dark = true)))

/** A row whose first item sits on the right, whatever the phone's own direction. */
@Composable
private fun RtlRow(
    mirror: Boolean,
    modifier: GlanceModifier = GlanceModifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    items: List<@Composable RowScope.() -> Unit>,
) {
    Row(modifier, verticalAlignment = verticalAlignment) {
        (if (mirror) items.asReversed() else items).forEach { it() }
    }
}

/** Size of one square and whether labels fit, from the widget's real size. */
private data class Metrics(val square: Dp, val icon: Dp, val labels: Boolean)

private fun metrics(width: Dp, height: Dp, cols: Int, rows: Int, header: Dp): Metrics {
    val colW = (width.value - 28f) / cols
    val rowH = (height.value - 30f - header.value - 6f) / rows
    val labels = rowH >= 70f && colW >= 54f
    val square = minOf(60f, colW - 12f, if (labels) rowH - 24f else rowH - 8f).coerceAtLeast(30f)
    // The square is the touch cell now; the mark itself stays small and bare, as in the glass design.
    return Metrics(square.dp, minOf(28f, square * 0.5f).dp, labels)
}

@Composable
private fun Content(deck: Deck, p: Project, memory: Memory?, widgetId: Int, mirror: Boolean) {
    val size = LocalSize.current
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(30.dp).background(glass(p.color))) {
        if (size.height < STRIP_MAX) {
            Strip(p, widgetId, mirror, size.width)
        } else if (size.width < DeckWidget.MEDIUM.width) {
            Small(p, widgetId, mirror, metrics(size.width, size.height, cols = 2, rows = 2, header = 28.dp))
        } else {
            val rows = if (size.height >= DeckWidget.LARGE.height) 2 else 1
            Wide(deck, p, memory, widgetId, mirror, rows, metrics(size.width, size.height, cols = 4, rows = rows, header = 38.dp))
        }
    }
}

/** Slots for the grid: the first actions, then "more", padded with empties so rows keep their rhythm. */
private fun slots(p: Project, count: Int): List<Int?> {
    val shown = p.actions.indices.take(count - 1)
    return (shown + MORE).let { it + List(count - it.size) { null } }
}

private const val MORE = -1

@Composable
private fun Wide(deck: Deck, p: Project, memory: Memory?, widgetId: Int, mirror: Boolean, rows: Int, m: Metrics) {
    Column(GlanceModifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp)) {
        Header(deck, p, memory, widgetId, mirror)
        Spacer(GlanceModifier.height(6.dp))
        slots(p, rows * 4).chunked(4).forEach { row -> SlotRow(p, row, mirror, m) }
    }
}

/** Below this height there's no room for a header: the widget becomes one strip (4×1, 2×1). */
private val STRIP_MAX = 110.dp

/** One row: the project's logo (opens the picker), then as many bare marks as fit, "more" last. */
@Composable
private fun Strip(p: Project, widgetId: Int, mirror: Boolean, width: Dp) {
    val context = LocalContext.current
    val cols = ((width.value - 24f - 40f) / 46f).toInt().coerceIn(2, 7)
    val m = Metrics(square = 40.dp, icon = 24.dp, labels = false)
    val logo: @Composable RowScope.() -> Unit = {
        Box(GlanceModifier.size(40.dp).clickable(pickerAction(p, widgetId)), contentAlignment = Alignment.Center) {
            Image(ImageProvider(BrandArt.logo(context, p, 30)), p.name, GlanceModifier.size(30.dp))
        }
    }
    RtlRow(
        mirror,
        GlanceModifier.fillMaxSize().padding(horizontal = 12.dp),
        Alignment.CenterVertically,
        listOf(logo) + slots(p, cols).map { slot -> { Slot(p, slot, m) } },
    )
}

@Composable
private fun Small(p: Project, widgetId: Int, mirror: Boolean, m: Metrics) {
    val context = LocalContext.current
    Column(GlanceModifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
        RtlRow(
            mirror,
            GlanceModifier.fillMaxWidth().height(28.dp).clickable(pickerAction(p, widgetId)),
            Alignment.CenterVertically,
            listOf(
                { Image(ImageProvider(BrandArt.logo(context, p, 26)), p.name, GlanceModifier.size(26.dp)) },
                { Spacer(GlanceModifier.width(8.dp)) },
                {
                    Text(
                        p.name, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = FG, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Right),
                    )
                },
            ),
        )
        Spacer(GlanceModifier.height(6.dp))
        slots(p, 4).chunked(2).forEach { row -> SlotRow(p, row, mirror, m.copy(labels = false)) }
    }
}

/** One row of squares; it takes an equal share of the height and centres its squares in it. */
@Composable
private fun ColumnScope.SlotRow(p: Project, row: List<Int?>, mirror: Boolean, m: Metrics) {
    RtlRow(
        mirror,
        GlanceModifier.fillMaxWidth().defaultWeight(),
        Alignment.CenterVertically,
        row.map { slot -> { Slot(p, slot, m) } },
    )
}

@Composable
private fun Header(deck: Deck, p: Project, memory: Memory?, widgetId: Int, mirror: Boolean) {
    val context = LocalContext.current
    val index = deck.projects.indexOf(p)
    // Right edge in either phone direction: Start under RTL, End under LTR.
    val right = if (mirror) Alignment.End else Alignment.Start

    val identity: @Composable RowScope.() -> Unit = {
        RtlRow(
            mirror,
            GlanceModifier.defaultWeight().clickable(pickerAction(p, widgetId)),
            Alignment.CenterVertically,
            listOf(
                { Image(ImageProvider(BrandArt.logo(context, p, 30)), p.name, GlanceModifier.size(30.dp)) },
                { Spacer(GlanceModifier.width(10.dp)) },
                {
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = right) {
                        Text(
                            p.name, maxLines = 1,
                            style = TextStyle(color = FG, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Right),
                        )
                        // What's next, from the computer; without it, the account the taps open in.
                        if (memory?.open == true) {
                            Text(memory.next, style = TextStyle(color = OPEN_TEXT, fontSize = 11.sp, textAlign = TextAlign.Right), maxLines = 1)
                        } else {
                            Text(p.googleEmail ?: p.accountLabel, style = TextStyle(color = MUTED, fontSize = 11.sp), maxLines = 1)
                        }
                    }
                },
            ),
        )
    }

    val items = mutableListOf(identity)
    if (deck.projects.size > 1) items += { PageDots(deck.projects.size, index, p.color, mirror) }
    RtlRow(mirror, GlanceModifier.fillMaxWidth().height(36.dp), Alignment.CenterVertically, items)
}

/**
 * Page dots instead of "1/4" and arrows: the current project is a short bar in its colour. Tapping them steps to
 * the next project (the picker, on the name, reaches any). Past six projects the dots become "3/9".
 */
@Composable
private fun PageDots(count: Int, index: Int, color: Int, mirror: Boolean) {
    Box(GlanceModifier.height(36.dp).padding(start = 8.dp, end = 4.dp).clickable(cycle(1)), contentAlignment = Alignment.Center) {
        if (count > 6) {
            Text("${index + 1}/$count", style = TextStyle(color = MUTED, fontSize = 12.sp))
        } else {
            RtlRow(
                mirror, verticalAlignment = Alignment.CenterVertically,
                items = (0 until count).flatMap { i ->
                    val dot: @Composable RowScope.() -> Unit = {
                        Box(
                            GlanceModifier.size(if (i == index) 14.dp else 5.dp, 5.dp).cornerRadius(3.dp)
                                .background(if (i == index) activeDot(color) else DOT),
                        ) {}
                    }
                    if (i == 0) listOf(dot) else listOf({ Spacer(GlanceModifier.width(4.dp)) }, dot)
                },
            )
        }
    }
}

@Composable
private fun RowScope.Slot(p: Project, slot: Int?, m: Metrics) {
    val context = LocalContext.current
    val cell = GlanceModifier.defaultWeight().fillMaxHeight()
    when (slot) {
        null -> Spacer(cell)
        MORE -> Mark(cell, m, R.drawable.ic_s_dots, null, LABEL, "עוד", moreAction(p))
        else -> {
            val a = p.actions[slot]
            // the primary action no longer gets a filled square: it leads the grid in the ink colour
            val tint = if (slot == 0) FG else serviceTint(a.service.color)
            Mark(cell, m, a.service.icon, Favicons.load(context, a.faviconHost), tint, a.tileLabel, openAction(p, slot))
        }
    }
}

/** A bare service mark on the glass, and its label underneath when there is room. */
@Composable
private fun Mark(
    cell: GlanceModifier,
    m: Metrics,
    icon: Int,
    favicon: Bitmap?,
    tint: GlanceColor,
    label: String,
    action: Action,
) {
    Box(cell.clickable(action), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // an invisible touch-sized cell keeps the rhythm and the tap target of the old squares
            Box(GlanceModifier.size(m.square, m.icon + 8.dp), contentAlignment = Alignment.Center) {
                if (favicon != null) {
                    Image(ImageProvider(favicon), label, GlanceModifier.size(m.icon).cornerRadius(5.dp))
                } else {
                    Image(ImageProvider(icon), label, GlanceModifier.size(m.icon), colorFilter = ColorFilter.tint(tint))
                }
            }
            if (m.labels) {
                Spacer(GlanceModifier.height(5.dp))
                Text(
                    label, maxLines = 1,
                    style = TextStyle(color = LABEL, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

@Composable
private fun Empty() {
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(30.dp).background(glass(0xFF000000.toInt()))
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text("מתחברים למחשב באפליקציה", style = TextStyle(color = MUTED, fontSize = 13.sp))
    }
}
