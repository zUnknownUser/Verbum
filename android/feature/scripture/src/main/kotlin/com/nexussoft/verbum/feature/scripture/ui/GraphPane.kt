package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexussoft.verbum.designsystem.tokens.Radius
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import com.nexussoft.verbum.feature.scripture.GraphFeature
import com.nexussoft.verbum.feature.scripture.R
import com.nexussoft.verbum.models.BibleEntity
import com.nexussoft.verbum.models.BibleEntityType
import com.nexussoft.verbum.models.EntityId
import com.nexussoft.verbum.models.RelationshipType
import kotlin.math.roundToInt

/**
 * The graph page: a ring picture or a list of the same connections (§43), switchable at the
 * top. Nodes are buttons — tap opens, long press expands or refocuses (§8.2). Shape and symbol
 * carry the kind, colour is secondary. Twin of iOS `GraphView`.
 */
@Composable
internal fun GraphPane(state: GraphFeature.State, onBack: () -> Unit, send: (GraphFeature.Action) -> Unit) {
    LaunchedEffect(state.rootId) { send(GraphFeature.Action.Started) }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(
                    (state.content as? GraphFeature.Content.Loaded)?.graph?.root?.name ?: stringResource(R.string.graph),
                    style = VerbumTypography.navigationSerif,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(64.dp))
            }
            when (val content = state.content) {
                GraphFeature.Content.Idle, GraphFeature.Content.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                GraphFeature.Content.Failed -> Column(Modifier.fillMaxSize().padding(Spacing.readingMargin), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.graph_failed), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { send(GraphFeature.Action.RetryTapped) }) { Text(stringResource(R.string.try_again)) }
                }
                is GraphFeature.Content.Loaded -> {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = Spacing.readingMargin, vertical = Spacing.sm)) {
                        GraphFeature.Presentation.entries.forEachIndexed { index, presentation ->
                            SegmentedButton(
                                selected = state.presentation == presentation,
                                onClick = { send(GraphFeature.Action.PresentationChanged(presentation)) },
                                shape = SegmentedButtonDefaults.itemShape(index, GraphFeature.Presentation.entries.size),
                            ) { Text(stringResource(if (presentation == GraphFeature.Presentation.GRAPH) R.string.graph else R.string.list)) }
                        }
                    }
                    if (state.presentation == GraphFeature.Presentation.GRAPH) GraphPicture(content.graph, send) else GraphList(content.graph, send)
                }
            }
        }
    }
}

// MARK: - Picture

private val NODE_SIZE = 64.dp
private val MARGIN = 56.dp

@Composable
private fun GraphPicture(graph: GraphFeature.Graph, send: (GraphFeature.Action) -> Unit) {
    val density = LocalDensity.current
    val ruleColor = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportW = with(density) { maxWidth.toPx() }
        val viewportH = with(density) { maxHeight.toPx() }
        val nodePx = with(density) { NODE_SIZE.toPx() }
        val marginPx = with(density) { MARGIN.toPx() }
        // Scale so the first ring (radius 1 → diameter 2 plus node size) fits the shorter side.
        val xs = graph.nodes.map { it.x }; val ys = graph.nodes.map { it.y }
        val minX = xs.minOrNull() ?: 0.0; val maxX = xs.maxOrNull() ?: 0.0; val minY = ys.minOrNull() ?: 0.0; val maxY = ys.maxOrNull() ?: 0.0
        val usable = minOf(viewportW, viewportH) - 2 * marginPx - nodePx
        val scale = maxOf(usable / 2, with(density) { 90.dp.toPx() })
        val width = maxOf(viewportW, ((maxX - minX) * scale + 2 * marginPx + nodePx).toFloat())
        val height = maxOf(viewportH, ((maxY - minY) * scale + 2 * marginPx + nodePx).toFloat())
        val centreX = width / 2 - ((minX + maxX) / 2 * scale).toFloat()
        val centreY = height / 2 - ((minY + maxY) / 2 * scale).toFloat()
        val points = graph.nodes.associate { it.id to Offset(centreX + (it.x * scale).toFloat(), centreY + (it.y * scale).toFloat()) }

        val hScroll = rememberScrollState((width / 2 - viewportW / 2).roundToInt().coerceAtLeast(0))
        val vScroll = rememberScrollState((height / 2 - viewportH / 2).roundToInt().coerceAtLeast(0))
        Box(Modifier.fillMaxSize().horizontalScroll(hScroll).verticalScroll(vScroll)) {
            Box(Modifier.size(with(density) { width.toDp() }, with(density) { height.toDp() })) {
                Canvas(Modifier.fillMaxSize()) {
                    graph.edges.forEach { edge ->
                        val a = points[edge.sourceId] ?: return@forEach
                        val b = points[edge.targetId] ?: return@forEach
                        drawLine(ruleColor, a, b, strokeWidth = 1.dp.toPx())
                    }
                }
                // Edge labels: small text at midpoints, laid out as composables so they scale with the user's text size.
                graph.edges.forEach { edge ->
                    val a = points[edge.sourceId] ?: return@forEach
                    val b = points[edge.targetId] ?: return@forEach
                    val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
                    EdgeLabel(edge.type, mid)
                }
                graph.nodes.forEach { node ->
                    val p = points.getValue(node.id)
                    NodeButton(
                        node = node,
                        isRoot = node.id == graph.root.id,
                        isExpanded = node.id in graph.expanded,
                        isExpanding = graph.expanding == node.id,
                        connections = graph.connections(node.id).size,
                        canExpand = !graph.atCapacity,
                        send = send,
                        modifier = Modifier.offset { IntOffset((p.x - nodePx / 2).roundToInt(), (p.y - nodePx / 2).roundToInt()) }.size(NODE_SIZE, 88.dp),
                    )
                }
            }
        }
        if (graph.atCapacity) {
            Text(
                stringResource(R.string.graph_full),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.lg)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun EdgeLabel(type: RelationshipType, at: Offset) {
    val label = relationshipLabel(type)
    Text(
        label,
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .offset { IntOffset(at.x.roundToInt() - 40.dp.roundToPx(), at.y.roundToInt() - 6.dp.roundToPx()) }
            .width(80.dp)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f), RoundedCornerShape(2.dp)),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeButton(
    node: GraphFeature.Graph.Node,
    isRoot: Boolean,
    isExpanded: Boolean,
    isExpanding: Boolean,
    connections: Int,
    canExpand: Boolean,
    send: (GraphFeature.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val kind = kindTitle(node.entity.type)
    val description = stringResource(R.string.graph_node_a11y, node.entity.name, kind, connections)
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .semantics { contentDescription = description }
            .combinedClickable(onClick = { send(GraphFeature.Action.NodeTapped(node.entity)) }, onLongClick = { if (!isRoot) menu = true }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        val shape = nodeShape(node.entity.type)
        Box(
            Modifier
                .size(40.dp)
                .clip(shape)
                .background(if (isRoot) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLowest)
                .border(if (isRoot) 2.dp else 1.dp, if (isRoot) accent else MaterialTheme.colorScheme.outlineVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (isExpanding) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(kindIcon(node.entity.type), contentDescription = null, tint = if (isRoot) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Text(
            node.entity.name,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(88.dp),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.graph_expand)) },
                enabled = !isExpanded && canExpand,
                onClick = { menu = false; send(GraphFeature.Action.ExpandTapped(node.id)) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.graph_focus)) },
                onClick = { menu = false; send(GraphFeature.Action.FocusTapped(node.id)) },
            )
        }
    }
}

/** Spec §8.3 shapes. Subtle, and never the only cue — each carries a symbol too. */
private fun nodeShape(kind: BibleEntityType): Shape = when (kind) {
    BibleEntityType.PERSON, BibleEntityType.PROPHECY, BibleEntityType.ORIGINAL_TERM, BibleEntityType.HISTORICAL_PERIOD -> CircleShape
    BibleEntityType.PLACE -> GenericShape { size, _ ->
        moveTo(size.width / 2, 0f); lineTo(size.width, size.height / 2); lineTo(size.width / 2, size.height); lineTo(0f, size.height / 2); close()
    }
    BibleEntityType.EVENT -> RoundedCornerShape(28)
    BibleEntityType.THEME -> RoundedCornerShape(50)
    BibleEntityType.PASSAGE -> RoundedCornerShape(50)
    BibleEntityType.BOOK -> RoundedCornerShape(4.dp)
}

private fun kindIcon(kind: BibleEntityType): ImageVector = when (kind) {
    BibleEntityType.PERSON -> Icons.Outlined.Person
    BibleEntityType.PLACE -> Icons.Outlined.Place
    BibleEntityType.EVENT -> Icons.Outlined.Flag
    BibleEntityType.THEME -> Icons.Outlined.Label
    BibleEntityType.PASSAGE -> Icons.Outlined.FormatQuote
    BibleEntityType.BOOK -> Icons.Outlined.AutoStories
    BibleEntityType.PROPHECY -> Icons.Outlined.Star
    BibleEntityType.ORIGINAL_TERM -> Icons.Outlined.Translate
    BibleEntityType.HISTORICAL_PERIOD -> Icons.Outlined.AccessTime
}

@Composable
private fun kindTitle(kind: BibleEntityType): String = stringResource(
    when (kind) {
        BibleEntityType.PERSON -> R.string.kind_person
        BibleEntityType.PLACE -> R.string.kind_place
        BibleEntityType.EVENT -> R.string.kind_event
        BibleEntityType.THEME -> R.string.kind_theme
        BibleEntityType.PASSAGE -> R.string.kind_passage
        BibleEntityType.BOOK -> R.string.kind_book
        BibleEntityType.PROPHECY -> R.string.kind_prophecy
        BibleEntityType.ORIGINAL_TERM -> R.string.kind_original_term
        BibleEntityType.HISTORICAL_PERIOD -> R.string.kind_period
    },
)

@Composable
private fun relationshipLabel(type: RelationshipType): String = stringResource(
    when (type) {
        RelationshipType.APPEARS_IN -> R.string.rel_appears_in
        RelationshipType.PARTICIPATES_IN -> R.string.rel_participates_in
        RelationshipType.OCCURS_AT -> R.string.rel_occurs_at
        RelationshipType.OCCURS_DURING -> R.string.rel_occurs_during
        RelationshipType.REFERENCES -> R.string.rel_references
        RelationshipType.RELATED_TO_THEME -> R.string.rel_related_to_theme
        RelationshipType.RELATED_TO -> R.string.rel_related_to
        RelationshipType.PRECEDES -> R.string.rel_precedes
        RelationshipType.FOLLOWS -> R.string.rel_follows
        RelationshipType.FULFILLS -> R.string.rel_fulfills
        RelationshipType.QUOTES -> R.string.rel_quotes
    },
)

// MARK: - List

@Composable
private fun GraphList(graph: GraphFeature.Graph, send: (GraphFeature.Action) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Spacing.readingMargin, vertical = Spacing.sm)) {
        item { ListHeading(stringResource(R.string.graph_connected_to, graph.root.name)) }
        items(graph.connections(graph.root.id), key = { it.first.id }) { (edge, other) ->
            ConnectionRow(other, "${graph.root.name} · ${relationshipLabel(edge.type)}") { send(GraphFeature.Action.NodeTapped(other)) }
        }
        graph.nodes.filter { it.id in graph.expanded }.forEach { node ->
            item { ListHeading(stringResource(R.string.graph_connected_to, node.entity.name)) }
            items(graph.connections(node.id).filter { it.second.id != graph.root.id }, key = { node.id + it.first.id }) { (edge, other) ->
                ConnectionRow(other, "${node.entity.name} · ${relationshipLabel(edge.type)}") { send(GraphFeature.Action.NodeTapped(other)) }
            }
        }
        if (!graph.atCapacity) {
            item { ListHeading(stringResource(R.string.graph_expand)) }
            items(graph.nodes.filter { it.id != graph.root.id && it.id !in graph.expanded }, key = { "expand." + it.id }) { node ->
                TextButton(onClick = { send(GraphFeature.Action.ExpandTapped(node.id)) }, enabled = graph.expanding == null) {
                    Text(stringResource(R.string.graph_expand_named, node.entity.name))
                }
            }
        }
    }
}

@Composable
private fun ListHeading(text: String) {
    Text(text.uppercase(), style = VerbumTypography.overline, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.sm).semantics { heading() })
}

@Composable
private fun ConnectionRow(entity: BibleEntity, relation: String, onTap: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onTap).padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(kindIcon(entity.type), contentDescription = kindTitle(entity.type), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(entity.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(relation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}
