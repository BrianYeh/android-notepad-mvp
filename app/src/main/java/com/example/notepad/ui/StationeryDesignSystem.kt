package com.example.notepad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Warm stationery colors used by the Just Notes visual refresh.
 *
 * Decorative pastels are always paired with a dark content color. This keeps
 * text readable while letting individual note cards feel like different pieces
 * of stationery.
 */
internal object StationeryPalette {
    val Paper = Color(0xFFFFF9EE)
    val PaperRaised = Color(0xFFFFFDF8)
    val Ink = Color(0xFF292A27)
    val InkMuted = Color(0xFF625F58)

    val Forest = Color(0xFF315E50)
    val ForestDark = Color(0xFF173C32)
    val ForestSoft = Color(0xFFDDEFE5)

    val Berry = Color(0xFF7A5365)
    val BerryDark = Color(0xFF482C38)
    val Blush = Color(0xFFFBE3EA)

    val Lavender = Color(0xFFECE6F6)
    val LavenderInk = Color(0xFF342C43)
    val LavenderAccent = Color(0xFF76658D)

    val Butter = Color(0xFFF9EBC6)
    val ButterInk = Color(0xFF3B321E)
    val ButterAccent = Color(0xFF806A32)

    val Sky = Color(0xFFE2EEF4)
    val SkyInk = Color(0xFF26343B)
    val SkyAccent = Color(0xFF547487)

    val Outline = Color(0xFF766F65)
    val OutlineSoft = Color(0xFFDED4C6)
    val Rule = Color(0xFFB9CFCA)
}

internal val StationeryColorScheme = lightColorScheme(
    primary = StationeryPalette.Forest,
    onPrimary = Color.White,
    primaryContainer = StationeryPalette.ForestSoft,
    onPrimaryContainer = StationeryPalette.ForestDark,
    secondary = StationeryPalette.Berry,
    onSecondary = Color.White,
    secondaryContainer = StationeryPalette.Blush,
    onSecondaryContainer = StationeryPalette.BerryDark,
    tertiary = StationeryPalette.ButterAccent,
    onTertiary = Color.White,
    tertiaryContainer = StationeryPalette.Butter,
    onTertiaryContainer = StationeryPalette.ButterInk,
    background = StationeryPalette.Paper,
    onBackground = StationeryPalette.Ink,
    surface = StationeryPalette.PaperRaised,
    onSurface = StationeryPalette.Ink,
    surfaceVariant = Color(0xFFF1EBDD),
    onSurfaceVariant = StationeryPalette.InkMuted,
    outline = StationeryPalette.Outline,
    outlineVariant = StationeryPalette.OutlineSoft,
)

internal val StationeryShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** System fonts intentionally preserve complete Traditional Chinese support. */
internal val StationeryTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 29.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
)

@Composable
internal fun StationeryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StationeryColorScheme,
        typography = StationeryTypography,
        shapes = StationeryShapes,
        content = content,
    )
}

/**
 * Full-screen cream paper surface. Ruled lines are optional so dense screens
 * can remain visually quiet.
 */
@Composable
internal fun StationeryPaperSurface(
    modifier: Modifier = Modifier,
    ruled: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = StationeryPalette.Paper,
        contentColor = StationeryPalette.Ink,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (ruled) Modifier.stationeryRules() else Modifier),
            content = content,
        )
    }
}

private fun Modifier.stationeryRules(): Modifier = drawBehind {
    val spacing = 32.dp.toPx()
    val strokeWidth = 1.dp.toPx()
    var y = spacing
    while (y < size.height) {
        drawLine(
            color = StationeryPalette.Rule.copy(alpha = 0.22f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth,
        )
        y += spacing
    }
}

internal enum class StationeryCardTone(
    val container: Color,
    val content: Color,
    val accent: Color,
) {
    Cream(
        container = StationeryPalette.PaperRaised,
        content = StationeryPalette.Ink,
        accent = StationeryPalette.Forest,
    ),
    Blush(
        container = StationeryPalette.Blush,
        content = StationeryPalette.BerryDark,
        accent = StationeryPalette.Berry,
    ),
    Mint(
        container = StationeryPalette.ForestSoft,
        content = StationeryPalette.ForestDark,
        accent = StationeryPalette.Forest,
    ),
    Lavender(
        container = StationeryPalette.Lavender,
        content = StationeryPalette.LavenderInk,
        accent = StationeryPalette.LavenderAccent,
    ),
    Butter(
        container = StationeryPalette.Butter,
        content = StationeryPalette.ButterInk,
        accent = StationeryPalette.ButterAccent,
    ),
    Sky(
        container = StationeryPalette.Sky,
        content = StationeryPalette.SkyInk,
        accent = StationeryPalette.SkyAccent,
    ),
}

/**
 * Reusable paper card with a small notebook-margin accent. When [onClick] is
 * present the card enforces a minimum 48 dp interactive height.
 */
@Composable
internal fun StationeryPaperCard(
    modifier: Modifier = Modifier,
    tone: StationeryCardTone = StationeryCardTone.Cream,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(
        start = 22.dp,
        top = 18.dp,
        end = 18.dp,
        bottom = 18.dp,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactiveModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
    }

    Surface(
        modifier = modifier.then(interactiveModifier),
        shape = StationeryShapes.large,
        color = tone.container,
        contentColor = tone.content,
        border = BorderStroke(1.dp, tone.accent.copy(alpha = 0.24f)),
        shadowElevation = 1.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    val margin = 13.dp.toPx()
                    val lineWidth = 3.dp.toPx()
                    drawRoundRect(
                        color = tone.accent.copy(alpha = 0.72f),
                        topLeft = Offset(margin, 14.dp.toPx()),
                        size = Size(
                            width = lineWidth,
                            height = (size.height - 28.dp.toPx()).coerceAtLeast(0f),
                        ),
                        cornerRadius = CornerRadius(lineWidth, lineWidth),
                    )
                }
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** A compact, readable section title with optional leading and trailing slots. */
@Composable
internal fun StationerySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                leading()
            }
            Spacer(Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (action != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                action()
            }
        }
    }
}

/**
 * Friendly empty state without a mascot. The notebook, pencil and sparkles are
 * decorative and deliberately absent from the accessibility tree.
 */
@Composable
internal fun StationeryEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StationeryNotebookDoodle()
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        if (body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier.heightIn(min = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                action()
            }
        }
    }
}

@Composable
private fun StationeryNotebookDoodle(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(width = 132.dp, height = 104.dp)
            .clearAndSetSemantics { },
    ) {
        val paperLeft = 20.dp.toPx()
        val paperTop = 12.dp.toPx()
        val paperWidth = 86.dp.toPx()
        val paperHeight = 78.dp.toPx()
        val radius = 12.dp.toPx()

        drawRoundRect(
            color = StationeryPalette.Forest.copy(alpha = 0.10f),
            topLeft = Offset(paperLeft + 5.dp.toPx(), paperTop + 6.dp.toPx()),
            size = Size(paperWidth, paperHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawRoundRect(
            color = StationeryPalette.PaperRaised,
            topLeft = Offset(paperLeft, paperTop),
            size = Size(paperWidth, paperHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
        drawRoundRect(
            color = StationeryPalette.Forest.copy(alpha = 0.48f),
            topLeft = Offset(paperLeft, paperTop),
            size = Size(paperWidth, paperHeight),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 1.dp.toPx()),
        )

        repeat(3) { index ->
            val lineY = paperTop + (31 + index * 14).dp.toPx()
            drawLine(
                color = StationeryPalette.Rule.copy(alpha = 0.75f),
                start = Offset(paperLeft + 18.dp.toPx(), lineY),
                end = Offset(paperLeft + paperWidth - 13.dp.toPx(), lineY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        drawRoundRect(
            color = StationeryPalette.Blush,
            topLeft = Offset(paperLeft + 27.dp.toPx(), 7.dp.toPx()),
            size = Size(34.dp.toPx(), 14.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        )

        val pencilStart = Offset(91.dp.toPx(), 78.dp.toPx())
        val pencilEnd = Offset(119.dp.toPx(), 49.dp.toPx())
        drawLine(
            color = StationeryPalette.ButterAccent,
            start = pencilStart,
            end = pencilEnd,
            strokeWidth = 9.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = StationeryPalette.Berry,
            start = Offset(116.dp.toPx(), 52.dp.toPx()),
            end = pencilEnd,
            strokeWidth = 9.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val pencilTip = Path().apply {
            moveTo(86.dp.toPx(), 83.dp.toPx())
            lineTo(94.dp.toPx(), 79.dp.toPx())
            lineTo(90.dp.toPx(), 75.dp.toPx())
            close()
        }
        drawPath(pencilTip, color = StationeryPalette.InkMuted)

        fun drawSparkle(center: Offset, radiusPx: Float, color: Color) {
            drawLine(
                color = color,
                start = Offset(center.x - radiusPx, center.y),
                end = Offset(center.x + radiusPx, center.y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(center.x, center.y - radiusPx),
                end = Offset(center.x, center.y + radiusPx),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        drawSparkle(
            center = Offset(113.dp.toPx(), 23.dp.toPx()),
            radiusPx = 6.dp.toPx(),
            color = StationeryPalette.LavenderAccent,
        )
        drawSparkle(
            center = Offset(11.dp.toPx(), 53.dp.toPx()),
            radiusPx = 4.dp.toPx(),
            color = StationeryPalette.Berry,
        )
    }
}
