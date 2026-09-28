import FirebaseAuth
import FirebaseCore
import Foundation
import Models

extension VerbumAPI {
    public func syncPersonalData(_ body: PersonalSyncRequest, uid: String) async throws -> PersonalSyncResponse {
        let request = try await personalRequest(
            method: "POST", path: "/v1/me/sync", uid: uid, body: JSONEncoder().encode(body))
        let data = try await send(request)
        return try JSONDecoder().decode(PersonalSyncResponse.self, from: data)
    }
    public func deletePersonalData(uid: String) async throws {
        let request = try await personalRequest(method: "DELETE", path: "/v1/me/data", uid: uid)
        _ = try await send(request)
    }
    private func personalRequest(method: String, path: String, uid: String, body: Data? = nil) async throws
        -> URLRequest
    {
        guard FirebaseApp.app() != nil, let user = Auth.auth().currentUser, user.uid == uid else {
            throw AccountFailure.credentials
        }
        let token = try await user.getIDToken()
        guard Auth.auth().currentUser?.uid == uid else { throw AccountFailure.credentials }
        var request = URLRequest(url: url(path), timeoutInterval: 20)
        request.httpMethod = method
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        return request
    }
}
public enum PersonalSyncIdentity {
    public static var uid: String? {
        guard FirebaseApp.app() != nil, let user = Auth.auth().currentUser, !user.isAnonymous else { return nil }
        return user.uid
    }
}
