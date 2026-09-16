// swift-tools-version: 6.2

import PackageDescription

// Module layering (spec §36, §37, §60 Task 1). Arrows point at what a module may import.
//
//   App (Xcode target) ─▶ Features ─▶ Clients ─▶ Models
//                            │            │
//                            ├─▶ DesignSystem
//                            └─▶ Core ──▶ Models
//
// Rules:
// - Models has no dependencies. Pure domain types, Codable/Equatable/Sendable.
// - Core holds shared, non-UI infrastructure (errors, analytics abstraction, parsers).
// - Clients are TCA dependency interfaces. Features depend on them, never on concrete backends.
// - DesignSystem is tokens + reusable views only. No domain knowledge.
// - Features never import networking or persistence directly.

let package = Package(
    name: "VerbumKit",
    defaultLocalization: "en",
    platforms: [.iOS(.v17)],
    products: [
        .library(name: "Models", targets: ["Models"]),
        .library(name: "Core", targets: ["Core"]),
        .library(name: "DesignSystem", targets: ["DesignSystem"]),
        .library(name: "Clients", targets: ["Clients"]),
        .library(name: "Features", targets: ["Features"]),
    ],
    dependencies: [
        .package(url: "https://github.com/firebase/firebase-ios-sdk.git", exact: "12.17.0"),
        .package(
            url: "https://github.com/pointfreeco/swift-composable-architecture",
            from: "1.26.2"
        ),
    ],
    targets: [
        .target(name: "Models"),
        .target(name: "Core", dependencies: ["Models"]),
        .target(name: "DesignSystem"),
        .target(
            name: "Clients",
            dependencies: [
                .product(name: "FirebaseAuth", package: "firebase-ios-sdk"),
                .product(name: "FirebaseAppCheck", package: "firebase-ios-sdk"),
                .product(name: "FirebaseCore", package: "firebase-ios-sdk"),
                "Models",
                "Core",
                .product(name: "ComposableArchitecture", package: "swift-composable-architecture"),
            ],
            resources: [.copy("Resources/web.tsv")]
        ),
        .target(
            name: "Features",
            dependencies: [
                "Models",
                "Core",
                "Clients",
                "DesignSystem",
                .product(name: "ComposableArchitecture", package: "swift-composable-architecture"),
            ],
            resources: [.process("Resources")]
        ),
        .testTarget(name: "ModelsTests", dependencies: ["Models"]),
        .testTarget(name: "CoreTests", dependencies: ["Core"]),
        .testTarget(
            name: "ClientsTests",
            dependencies: [
                "Clients",
                .product(name: "ComposableArchitecture", package: "swift-composable-architecture"),
            ],
            resources: [.copy("Samples")]
        ),
        .testTarget(
            name: "FeaturesTests",
            dependencies: [
                "Features",
                .product(name: "ComposableArchitecture", package: "swift-composable-architecture"),
            ]
        ),
    ],
    swiftLanguageModes: [.v6]
)
