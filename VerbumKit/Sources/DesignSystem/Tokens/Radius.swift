import Foundation

/// Corner radii (spec §64). iOS 26 favours container-concentric corners, so
/// prefer `.rect(cornerRadius: .containerConcentric)` for anything nested inside
/// a system container and reach for these fixed values only at the top level.
public enum Radius {
    public static let sm: CGFloat = 8
    public static let md: CGFloat = 12
    public static let lg: CGFloat = 16
}
