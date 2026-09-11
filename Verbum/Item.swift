//
//  Item.swift
//  Verbum
//
//  Created by Lucas de Amorim on 11/09/26.
//

import Foundation
import SwiftData

@Model
final class Item {
    var timestamp: Date
    
    init(timestamp: Date) {
        self.timestamp = timestamp
    }
}
