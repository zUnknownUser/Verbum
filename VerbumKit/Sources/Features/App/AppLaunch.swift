import Clients

/// What the app target must do before launch finishes. Kept here so the app
/// stays a thin shell that only imports `Features` (spec §36).
public enum AppLaunch {
    /// Installs the notification delegate: the system only hands a
    /// notification tap to a delegate that was in place at launch.
    public static func prepare() {
        FirebaseBootstrap.configure()
        NotificationClient.installNotificationDelegate()
    }
}
