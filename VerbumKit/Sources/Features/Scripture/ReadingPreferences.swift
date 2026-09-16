import ComposableArchitecture
import Models

extension SharedKey where Self == AppStorageKey<ReadingMode>.Default {
    public static var readingMode: Self { Self[.appStorage("readingMode"), default: .pages] }
}
extension SharedKey where Self == AppStorageKey<Bool>.Default {
    public static var readerFocusMode: Self { Self[.appStorage("readerFocusMode"), default: false] }
}
