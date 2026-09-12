package com.nexussoft.verbum.models

/**
 * Identifier aliases used by the client interfaces (docs/PRODUCT.md §38).
 *
 * They stay `String` on purpose: spec §22 declares every model id as `String`,
 * and these aliases only name the intent at call sites.
 */
typealias BookId = String
typealias EntityId = String
