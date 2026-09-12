import ComposableArchitecture
import DesignSystem
import Models
import SwiftUI

/// The graph page: a ring picture or a list of the same connections (§43),
/// switchable at the top. Nodes are buttons — tap opens, long press expands
/// or refocuses (§8.2). Shape and symbol carry the kind, colour is secondary.
public struct GraphView: View {
    @Bindable var store: StoreOf<GraphFeature>
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(store: StoreOf<GraphFeature>) {
        self.store = store
    }

    public var body: some View {
        Group {
            switch store.content {
            case .idle, .loading:
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            case .failed:
                ContentUnavailableView {
                    Label(L10n.t("Couldn't load the graph"), systemImage: "point.3.connected.trianglepath.dotted")
                } actions: {
                    Button(L10n.t("Try again")) { store.send(.retryTapped) }
                }
            case .loaded(let graph):
                VStack(spacing: 0) {
                    Picker(L10n.t("Presentation"), selection: $store.presentation.sending(\.presentationChanged)) {
                        Text(L10n.t("Graph")).tag(GraphFeature.Presentation.graph)
                        Text(L10n.t("List")).tag(GraphFeature.Presentation.list)
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal, Spacing.readingMargin)
                    .padding(.vertical, Spacing.sm)
                    if store.presentation == .graph {
                        GraphCanvas(graph: graph, reduceMotion: reduceMotion) { store.send($0) }
                    } else {
                        GraphList(graph: graph) { store.send($0) }
                    }
                }
            }
        }
        .background(Palette.paper)
        .navigationTitle(store.state.rootName)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            // Screen readers start on the list: it says the same things, in order.
            if voiceOver && store.presentation == .graph { store.send(.presentationChanged(.list)) }
            await store.send(.task).finish()
        }
    }
}

extension GraphFeature.State {
    var rootName: String {
        if case .loaded(let graph) = content { return graph.root.name }
        return L10n.t("Graph")
    }
}

// MARK: - Picture

private struct GraphCanvas: View {
    let graph: GraphFeature.Graph
    let reduceMotion: Bool
    let send: (GraphFeature.Action) -> Void

    /// Unit → points. The first ring spans most of the shorter side.
    private static let nodeSize: CGFloat = 64
    private static let margin: CGFloat = 56

    var body: some View {
        GeometryReader { proxy in
            let frame = layout(in: proxy.size)
            ScrollView([.horizontal, .vertical], showsIndicators: false) {
                ZStack {
                    Canvas { context, _ in
                        for edge in graph.edges {
                            guard let a = frame.point(edge.sourceId), let b = frame.point(edge.targetId) else { continue }
                            var path = Path()
                            path.move(to: a)
                            path.addLine(to: b)
                            context.stroke(path, with: .color(Palette.rule), lineWidth: 1)
                            let mid = CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
                            context.draw(
                                Text(RelationshipLabel.short(edge.type)).font(Typography.caption2).foregroundStyle(Palette.inkTertiary),
                                at: mid
                            )
                        }
                    }
                    ForEach(graph.nodes) { node in
                        NodeButton(
                            node: node,
                            isRoot: node.id == graph.root.id,
                            isExpanded: graph.expanded.contains(node.id),
                            isExpanding: graph.expanding == node.id,
                            connections: graph.connections(of: node.id).count,
                            canExpand: !graph.atCapacity,
                            send: send
                        )
                        .frame(width: Self.nodeSize, height: Self.nodeSize)
                        .position(frame.point(node.id) ?? .zero)
                        .transition(reduceMotion ? .opacity : .scale.combined(with: .opacity))
                    }
                }
                .frame(width: frame.size.width, height: frame.size.height)
                .animation(reduceMotion ? nil : Motion.standard, value: graph.nodes.map(\.id))
            }
            .defaultScrollAnchor(.center)
            .overlay(alignment: .bottom) {
                if graph.atCapacity {
                    Text(L10n.t("The graph is full. Open a node, or focus on it, to keep exploring."))
                        .font(Typography.footnote)
                        .foregroundStyle(Palette.inkSecondary)
                        .padding(.horizontal, Spacing.lg)
                        .padding(.vertical, Spacing.sm)
                        .background(Palette.paperElevated, in: .capsule)
                        .padding(.bottom, Spacing.lg)
                }
            }
        }
    }

    /// Where every node goes, in points, and how big the scrollable picture is.
    private func layout(in viewport: CGSize) -> Frame {
        let xs = graph.nodes.map(\.x), ys = graph.nodes.map(\.y)
        let minX = xs.min() ?? 0, maxX = xs.max() ?? 0, minY = ys.min() ?? 0, maxY = ys.max() ?? 0
        // Scale so the first ring (radius 1 → diameter 2 plus node size) fits the shorter side.
        let usable = min(viewport.width, viewport.height) - 2 * Self.margin - Self.nodeSize
        let scale = max(usable / 2, 90)
        let width = max(viewport.width, (maxX - minX) * scale + 2 * Self.margin + Self.nodeSize)
        let height = max(viewport.height, (maxY - minY) * scale + 2 * Self.margin + Self.nodeSize)
        let centre = CGPoint(x: width / 2 - (minX + maxX) / 2 * scale, y: height / 2 - (minY + maxY) / 2 * scale)
        var points: [EntityID: CGPoint] = [:]
        for node in graph.nodes {
            points[node.id] = CGPoint(x: centre.x + node.x * scale, y: centre.y + node.y * scale)
        }
        return Frame(size: CGSize(width: width, height: height), points: points)
    }

    private struct Frame {
        let size: CGSize
        let points: [EntityID: CGPoint]
        func point(_ id: EntityID) -> CGPoint? { points[id] }
    }
}

private struct NodeButton: View {
    let node: GraphFeature.Graph.Node
    let isRoot: Bool
    let isExpanded: Bool
    let isExpanding: Bool
    let connections: Int
    let canExpand: Bool
    let send: (GraphFeature.Action) -> Void

    var body: some View {
        Button { send(.nodeTapped(node.entity)) } label: {
            VStack(spacing: Spacing.xxs) {
                ZStack {
                    NodeShape(kind: node.entity.type)
                        .fill(isRoot ? Palette.accent.opacity(0.16) : Palette.paperElevated)
                    NodeShape(kind: node.entity.type)
                        .stroke(isRoot ? Palette.accent : Palette.rule, lineWidth: isRoot ? 2 : 1)
                    if isExpanding {
                        ProgressView().controlSize(.mini)
                    } else {
                        Image(systemName: KindGlyph.symbol(node.entity.type))
                            .font(.system(size: 15, weight: .light))
                            .foregroundStyle(isRoot ? Palette.accent : Palette.inkSecondary)
                    }
                }
                .frame(width: 40, height: 40)
                Text(node.entity.name)
                    .font(Typography.caption2)
                    .foregroundStyle(Palette.ink)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(width: 80)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .contextMenu {
            if !isRoot {
                Button { send(.expandTapped(node.id)) } label: {
                    Label(L10n.t("Expand connections"), systemImage: "plus.circle")
                }
                .disabled(isExpanded || !canExpand)
                Button { send(.focusTapped(node.id)) } label: {
                    Label(L10n.t("Focus here"), systemImage: "scope")
                }
            }
        }
        .accessibilityLabel(accessibilityLabel)
        .accessibilityHint(node.entity.type == .passage ? L10n.t("Opens the passage") : L10n.t("Opens the entity"))
        .accessibilityAddTraits(isRoot ? .isHeader : [])
    }

    private var accessibilityLabel: String {
        let kind = KindGlyph.title(node.entity.type)
        return L10n.t("\(node.entity.name), \(kind), \(connections) connections")
    }
}

/// Spec §8.3 shapes. Subtle, and never the only cue — each carries a symbol too.
private struct NodeShape: Shape {
    let kind: BibleEntityType

    func path(in rect: CGRect) -> Path {
        switch kind {
        case .person, .prophecy, .originalTerm, .historicalPeriod:
            return Circle().path(in: rect)
        case .place:
            var p = Path()
            p.move(to: CGPoint(x: rect.midX, y: rect.minY))
            p.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
            p.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
            p.addLine(to: CGPoint(x: rect.minX, y: rect.midY))
            p.closeSubpath()
            return p
        case .event:
            return RoundedRectangle(cornerRadius: rect.width * 0.28, style: .continuous).path(in: rect)
        case .theme:
            return Ellipse().path(in: rect.insetBy(dx: 0, dy: rect.height * 0.12))
        case .passage:
            return Capsule().path(in: rect.insetBy(dx: 0, dy: rect.height * 0.18))
        case .book:
            var p = RoundedRectangle(cornerRadius: 4).path(in: rect.insetBy(dx: rect.width * 0.1, dy: 0))
            p.move(to: CGPoint(x: rect.minX + rect.width * 0.26, y: rect.minY))
            p.addLine(to: CGPoint(x: rect.minX + rect.width * 0.26, y: rect.maxY))
            return p
        }
    }
}

// MARK: - List

private struct GraphList: View {
    let graph: GraphFeature.Graph
    let send: (GraphFeature.Action) -> Void

    var body: some View {
        List {
            Section {
                ForEach(graph.connections(of: graph.root.id), id: \.edge.id) { connection in
                    row(connection.other, relation: RelationshipLabel.sentence(connection.edge.type, from: graph.root.name))
                }
            } header: {
                Text(L10n.t("Connected to \(graph.root.name)")).overline()
            }
            ForEach(graph.nodes.filter { graph.expanded.contains($0.id) }) { node in
                Section {
                    ForEach(graph.connections(of: node.id).filter { $0.other.id != graph.root.id }, id: \.edge.id) { connection in
                        row(connection.other, relation: RelationshipLabel.sentence(connection.edge.type, from: node.entity.name))
                    }
                } header: {
                    Text(L10n.t("Connected to \(node.entity.name)")).overline()
                }
            }
            if !graph.atCapacity {
                Section {
                    ForEach(graph.nodes.filter { $0.id != graph.root.id && !graph.expanded.contains($0.id) }) { node in
                        Button { send(.expandTapped(node.id)) } label: {
                            Label(L10n.t("Expand \(node.entity.name)"), systemImage: "plus.circle")
                                .font(Typography.subheadline)
                        }
                        .disabled(graph.expanding != nil)
                    }
                } header: {
                    Text(L10n.t("Expand")).overline()
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private func row(_ entity: BibleEntity, relation: String) -> some View {
        Button { send(.nodeTapped(entity)) } label: {
            HStack(spacing: Spacing.md) {
                Image(systemName: KindGlyph.symbol(entity.type))
                    .font(.system(size: 16, weight: .light))
                    .foregroundStyle(Palette.accent)
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: Spacing.xxs) {
                    Text(entity.name).font(Typography.subheadline).foregroundStyle(Palette.ink)
                    Text(relation).font(Typography.footnote).foregroundStyle(Palette.inkSecondary)
                }
                Spacer()
                Image(systemName: "chevron.right").font(Typography.caption).foregroundStyle(Palette.inkTertiary).accessibilityHidden(true)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(entity.name), \(KindGlyph.title(entity.type)). \(relation)")
    }
}

// MARK: - Kinds and relations

enum KindGlyph {
    static func symbol(_ type: BibleEntityType) -> String {
        switch type {
        case .person: "person"
        case .place: "mappin.and.ellipse"
        case .event: "flag"
        case .theme: "tag"
        case .passage: "text.quote"
        case .book: "book.closed"
        case .prophecy: "sparkles"
        case .originalTerm: "character.book.closed"
        case .historicalPeriod: "clock"
        }
    }

    static func title(_ type: BibleEntityType) -> String {
        switch type {
        case .person: L10n.t("Person")
        case .place: L10n.t("Place")
        case .event: L10n.t("Event")
        case .theme: L10n.t("Theme")
        case .passage: L10n.t("Passage")
        case .book: L10n.t("Book")
        case .prophecy: L10n.t("Prophecy")
        case .originalTerm: L10n.t("Original term")
        case .historicalPeriod: L10n.t("Period")
        }
    }
}

enum RelationshipLabel {
    /// Two or three words on an edge.
    static func short(_ type: RelationshipType) -> String {
        switch type {
        case .appearsIn: L10n.t("appears in")
        case .participatesIn: L10n.t("takes part in")
        case .occursAt: L10n.t("happens at")
        case .occursDuring: L10n.t("happens during")
        case .references: L10n.t("references")
        case .relatedToTheme: L10n.t("touches on")
        case .relatedTo: L10n.t("related to")
        case .precedes: L10n.t("comes before")
        case .follows: L10n.t("comes after")
        case .fulfills: L10n.t("fulfils")
        case .quotes: L10n.t("quotes")
        }
    }

    /// `David appears in` — the list row shows the other end on its own line.
    static func sentence(_ type: RelationshipType, from name: String) -> String {
        "\(name) · \(short(type))"
    }
}
