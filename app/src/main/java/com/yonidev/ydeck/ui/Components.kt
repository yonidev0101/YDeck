package com.yonidev.ydeck.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import com.yonidev.ydeck.data.BrandColors
import com.yonidev.ydeck.data.Favicons
import com.yonidev.ydeck.data.Logos
import com.yonidev.ydeck.data.Service
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yonidev.ydeck.data.Project

/** App-icon style tile: the picked logo image, or the monogram on the brand gradient. */
@Composable
fun Logo(p: Project, size: Dp) {
    // A near-black project colour would vanish on the night ground: it takes the raised surface instead.
    val c = Color(p.color).let { if (android.graphics.Color.luminance(p.color) < 0.03f) LocalDeck.current.surface2 else it }
    val context = LocalContext.current
    val image = remember(p.logo) { Logos.load(context, p.logo)?.asImageBitmap() }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.29f)).background(c),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, p.name, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Text(p.mono.ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.45f).sp)
        }
    }
}

/**
 * A service's real mark: the brand vector in its (theme-adjusted) brand colour, or the site's favicon for services
 * the icon library doesn't carry. [tint] overrides the colour, e.g. white on the project-coloured primary tile.
 */
@Composable
fun ServiceMark(service: Service, faviconHost: String?, size: Dp, tint: Color? = null) {
    val c = LocalDeck.current
    val context = LocalContext.current
    val dark = true // the app is always on the night ground
    val favVersion by Favicons.version.collectAsState()
    val favicon = remember(faviconHost, favVersion) { Favicons.load(context, faviconHost)?.asImageBitmap() }
    if (favicon != null) {
        Image(favicon, service.label, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.22f)))
    } else {
        val color = tint ?: service.color?.let { Color(BrandColors.adapt(it, dark)) } ?: c.fg
        Icon(painterResource(service.icon), service.label, tint = color, modifier = Modifier.size(size))
    }
}

/** [ServiceMark] in a rounded tile; [onBrand] fills the tile with the project colour (the primary action). */
@Composable
fun ServiceIcon(service: Service, faviconHost: String?, size: Dp = 34.dp, onBrand: Color? = null) {
    val c = LocalDeck.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(onBrand ?: c.surface2),
        contentAlignment = Alignment.Center,
    ) {
        ServiceMark(service, faviconHost, size * 0.55f, tint = if (onBrand != null) Color.White else null)
    }
}

/**
 * Page shell, as in YVoice: a large thin title that collapses on scroll, optional back arrow, actions on the
 * left (RTL end), and one scrolling list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val c = LocalDeck.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = c.bg,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "חזרה") }
                    }
                },
                actions = actions,
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.bg, scrolledContainerColor = c.bg),
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 40.dp),
            modifier = Modifier.fillMaxSize().imePadding(),
            content = content,
        )
    }
}

/** Small label above a group ("פרויקטים", "קישורים"). */
@Composable
fun SectionHeader(text: String) {
    Text(
        text, style = SectionLabelStyle, color = LocalDeck.current.muted,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp),
    )
}

/** A short explanation under a group, in the muted colour. */
@Composable
fun FootNote(text: String) {
    Text(
        text, color = LocalDeck.current.muted, fontSize = 13.sp, lineHeight = 19.sp,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp),
    )
}

/**
 * A group in the YVoice style: a label, then rows between a top and a bottom hairline. A list, not a card.
 * Rows add their own inset [Divider] between them.
 */
@Composable
fun SectionCard(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalDeck.current
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), content = content)
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        }
        if (hint != null) FootNote(hint)
    }
}

/**
 * Labelled text field in the app's style. [ltr] for URLs, emails and codes: typed left-to-right
 * even inside the Hebrew layout.
 */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    ltr: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
) {
    val c = LocalDeck.current
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(label, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 4.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface2).padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            val style = TextStyle(
                color = c.fg, fontSize = 15.sp,
                textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
            )
            if (value.isEmpty()) Text(placeholder, style = style.copy(color = c.muted.copy(alpha = 0.7f)))
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true, textStyle = style,
                cursorBrush = SolidColor(c.fg),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = !ltr),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun RoundIconButton(icon: Int, label: String, tint: Color = LocalDeck.current.fg, size: Dp = 34.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), label, tint = tint, modifier = Modifier.size(size * 0.52f)) }
}

/** Selectable pill, used for Chrome channels and kind suggestions. */
@Composable
fun SelectChip(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val c = LocalDeck.current
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        color = if (selected) Color.White else c.fg,
        modifier = Modifier.clip(CircleShape).background(if (selected) accent else c.surface2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Hairline between rows of a group, inset past the row's icon. */
@Composable
fun Divider(inset: Dp = 56.dp) {
    Box(Modifier.fillMaxWidth().padding(start = inset, end = 16.dp).height(1.dp).background(LocalDeck.current.line))
}

/** Bottom sheet that fills a translucent activity; tapping the scrim dismisses. */
@Composable
fun Sheet(brand: Color, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalDeck.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scrim by animateFloatAsState(if (shown) 0.32f else 0f, tween(250), label = "scrim")
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(shown, enter = slideInVertically(tween(320)) { it }) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(c.surface)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Box(
                    Modifier.padding(top = 8.dp, bottom = 2.dp).align(Alignment.CenterHorizontally)
                        .size(38.dp, 5.dp).clip(CircleShape).background(c.fg.copy(alpha = 0.18f)),
                )
                content()
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text, color = LocalDeck.current.muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
fun IconBox(icon: Int, size: Dp = 34.dp, tint: Color = LocalDeck.current.fg, bg: Color = LocalDeck.current.surface2) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(bg), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun ListRow(
    onClick: (() -> Unit)?,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalDeck.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing()
    }
}

@Composable
fun Chip(text: String, color: Color = LocalDeck.current.muted, bg: Color = LocalDeck.current.surface2) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
