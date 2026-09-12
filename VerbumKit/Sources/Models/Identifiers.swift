/// Identifier aliases used by the client interfaces (spec §38).
///
/// They stay `String` on purpose: spec §22 declares every model id as `String`,
/// and these aliases only name the intent at call sites.
public typealias BookID = String
public typealias EntityID = String
