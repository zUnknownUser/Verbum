import CryptoKit
import FirebaseAuth
import FirebaseCore
import Foundation

/// No UID is used as a path. Existing installation data is assigned once, at startup,
/// to the already signed-in identity (or the local guest), never on a later login.
public enum LocalAccountData {
    public static var owner: String {
        let user = FirebaseApp.app() == nil ? nil : Auth.auth().currentUser
        let uid = user?.isAnonymous == false ? user?.uid : nil
        return key(uid)
    }
    public static func key(_ uid: String?) -> String {
        SHA256.hash(data: Data((uid.map { "uid:" + $0 } ?? "local-guest:" + (UserDefaults.standard.string(forKey: "guest-generation") ?? "initial")).utf8)).map { String(format: "%02x", $0) }.joined()
    }
    static var base: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Verbum/accounts", isDirectory: true)
    }
    public static func url(_ name: String, owner: String? = nil) -> URL {
        var directory = base.appendingPathComponent(owner ?? self.owner, isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try? directory.setResourceValues(values)
        return directory.appendingPathComponent(name)
    }
    static func isDeleted(_ owner: String) -> Bool { UserDefaults.standard.bool(forKey: "deleted-local-account." + owner) }
    public static func prepare() throws {
        let fm = FileManager.default
        let marker = base.appendingPathComponent("migration-v1")
        guard !fm.fileExists(atPath: marker.path) else { return }
        try fm.createDirectory(at: base, withIntermediateDirectories: true)
        let ownerFile = base.appendingPathComponent("migration-owner")
        let migrationOwner: String
        if fm.fileExists(atPath: ownerFile.path) {
            migrationOwner = try String(contentsOf: ownerFile, encoding: .utf8)
        } else {
            migrationOwner = owner
            try Data(migrationOwner.utf8).write(to: ownerFile, options: .atomic)
        }
        let documents = fm.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let oldAnnotations = base.deletingLastPathComponent().appendingPathComponent("reader-annotations.json")
        for (source, name) in [(oldAnnotations, "reader-annotations.json"), (documents.appendingPathComponent("reading-activity.json"), "reading-activity.json"), (documents.appendingPathComponent("lastRead.json"), "lastRead.json")] {
            let destination = url(name, owner: migrationOwner)
            if fm.fileExists(atPath: source.path), !fm.fileExists(atPath: destination.path) { try fm.moveItem(at: source, to: destination) }
        }
        try fm.createDirectory(at: base, withIntermediateDirectories: true)
        try Data().write(to: marker, options: .atomic)
    }
    static func promoteGuest(to uid: String) throws {
        let guest = key(nil)
        for name in ["reader-annotations.json", "reading-activity.json", "lastRead.json"] {
            let source = url(name, owner: guest), destination = url(name, owner: key(uid))
            if FileManager.default.fileExists(atPath: source.path), !FileManager.default.fileExists(atPath: destination.path) {
                try FileManager.default.copyItem(at: source, to: destination)
            }
        }
        UserDefaults.standard.set(true, forKey: "deleted-local-account." + guest)
        UserDefaults.standard.set(UUID().uuidString, forKey: "guest-generation")
        let directory = base.appendingPathComponent(guest)
        if FileManager.default.fileExists(atPath: directory.path) { try FileManager.default.removeItem(at: directory) }
        // Leave an empty, non-writable tombstone so delayed @Shared history writes
        // from a discarded screen cannot recreate deleted personal data.
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o500])
    }
    static func delete(uid: String) throws {
        let owner = key(uid)
        UserDefaults.standard.set(true, forKey: "deleted-local-account." + owner)
        let directory = base.appendingPathComponent(owner)
        if FileManager.default.fileExists(atPath: directory.path) { try FileManager.default.removeItem(at: directory) }
        // Leave an empty, non-writable tombstone so delayed @Shared history writes
        // from a discarded screen cannot recreate deleted personal data.
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o500])
    }
}
