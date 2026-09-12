import ComposableArchitecture
import Foundation
import Models

extension SharedKey where Self == FileStorageKey<PassageReference?>.Default {
    /// Where the reader last was. Written by the reader on every successful
    /// chapter load; read by Home for "Continue reading". Survives launches.
    public static var lastRead: Self {
        Self[.fileStorage(.documentsDirectory.appending(component: "lastRead.json")), default: nil]
    }
}
