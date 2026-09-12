/// The verse of the day (spec §14: a short daily touch that must not become
/// the centre of the product — one verse, no streak, no pseudo-prophetic frame).
///
/// The pick is deterministic per calendar day so the morning notification, the
/// Home card and every device agree on the same verse. Within each cycle of
/// `pool.count` days no verse repeats: the pool is shuffled once per cycle with
/// a seeded generator, then walked in order. The same algorithm runs on Android.
public enum DailyVerses {
    /// Single, widely known verses across the canon. Curated, not generated.
    public static let pool: [PassageReference] = [
        .init(bookId: "Gen", chapter: 1, verses: 1...1),
        .init(bookId: "Gen", chapter: 28, verses: 15...15),
        .init(bookId: "Exod", chapter: 14, verses: 14...14),
        .init(bookId: "Num", chapter: 6, verses: 24...24),
        .init(bookId: "Deut", chapter: 31, verses: 6...6),
        .init(bookId: "Josh", chapter: 1, verses: 9...9),
        .init(bookId: "1Sam", chapter: 16, verses: 7...7),
        .init(bookId: "2Sam", chapter: 22, verses: 31...31),
        .init(bookId: "1Chr", chapter: 16, verses: 34...34),
        .init(bookId: "2Chr", chapter: 7, verses: 14...14),
        .init(bookId: "Neh", chapter: 8, verses: 10...10),
        .init(bookId: "Job", chapter: 19, verses: 25...25),
        .init(bookId: "Ps", chapter: 16, verses: 11...11),
        .init(bookId: "Ps", chapter: 18, verses: 2...2),
        .init(bookId: "Ps", chapter: 19, verses: 14...14),
        .init(bookId: "Ps", chapter: 23, verses: 1...1),
        .init(bookId: "Ps", chapter: 27, verses: 1...1),
        .init(bookId: "Ps", chapter: 34, verses: 8...8),
        .init(bookId: "Ps", chapter: 37, verses: 4...4),
        .init(bookId: "Ps", chapter: 46, verses: 1...1),
        .init(bookId: "Ps", chapter: 46, verses: 10...10),
        .init(bookId: "Ps", chapter: 51, verses: 10...10),
        .init(bookId: "Ps", chapter: 55, verses: 22...22),
        .init(bookId: "Ps", chapter: 56, verses: 3...3),
        .init(bookId: "Ps", chapter: 62, verses: 1...1),
        .init(bookId: "Ps", chapter: 73, verses: 26...26),
        .init(bookId: "Ps", chapter: 90, verses: 12...12),
        .init(bookId: "Ps", chapter: 91, verses: 1...1),
        .init(bookId: "Ps", chapter: 103, verses: 2...2),
        .init(bookId: "Ps", chapter: 118, verses: 24...24),
        .init(bookId: "Ps", chapter: 119, verses: 105...105),
        .init(bookId: "Ps", chapter: 121, verses: 1...1),
        .init(bookId: "Ps", chapter: 139, verses: 14...14),
        .init(bookId: "Ps", chapter: 143, verses: 8...8),
        .init(bookId: "Ps", chapter: 147, verses: 3...3),
        .init(bookId: "Prov", chapter: 3, verses: 5...5),
        .init(bookId: "Prov", chapter: 3, verses: 6...6),
        .init(bookId: "Prov", chapter: 4, verses: 23...23),
        .init(bookId: "Prov", chapter: 16, verses: 9...9),
        .init(bookId: "Prov", chapter: 18, verses: 10...10),
        .init(bookId: "Eccl", chapter: 3, verses: 1...1),
        .init(bookId: "Isa", chapter: 26, verses: 3...3),
        .init(bookId: "Isa", chapter: 40, verses: 31...31),
        .init(bookId: "Isa", chapter: 41, verses: 10...10),
        .init(bookId: "Isa", chapter: 43, verses: 2...2),
        .init(bookId: "Isa", chapter: 53, verses: 5...5),
        .init(bookId: "Isa", chapter: 55, verses: 8...8),
        .init(bookId: "Jer", chapter: 29, verses: 11...11),
        .init(bookId: "Lam", chapter: 3, verses: 22...22),
        .init(bookId: "Lam", chapter: 3, verses: 23...23),
        .init(bookId: "Mic", chapter: 6, verses: 8...8),
        .init(bookId: "Nah", chapter: 1, verses: 7...7),
        .init(bookId: "Hab", chapter: 3, verses: 19...19),
        .init(bookId: "Zeph", chapter: 3, verses: 17...17),
        .init(bookId: "Matt", chapter: 5, verses: 16...16),
        .init(bookId: "Matt", chapter: 6, verses: 33...33),
        .init(bookId: "Matt", chapter: 11, verses: 28...28),
        .init(bookId: "Matt", chapter: 28, verses: 20...20),
        .init(bookId: "Mark", chapter: 10, verses: 27...27),
        .init(bookId: "Luke", chapter: 1, verses: 37...37),
        .init(bookId: "John", chapter: 1, verses: 5...5),
        .init(bookId: "John", chapter: 3, verses: 16...16),
        .init(bookId: "John", chapter: 8, verses: 12...12),
        .init(bookId: "John", chapter: 10, verses: 10...10),
        .init(bookId: "John", chapter: 14, verses: 6...6),
        .init(bookId: "John", chapter: 14, verses: 27...27),
        .init(bookId: "John", chapter: 15, verses: 5...5),
        .init(bookId: "John", chapter: 16, verses: 33...33),
        .init(bookId: "Acts", chapter: 1, verses: 8...8),
        .init(bookId: "Rom", chapter: 5, verses: 8...8),
        .init(bookId: "Rom", chapter: 8, verses: 28...28),
        .init(bookId: "Rom", chapter: 8, verses: 38...38),
        .init(bookId: "Rom", chapter: 12, verses: 2...2),
        .init(bookId: "Rom", chapter: 12, verses: 12...12),
        .init(bookId: "Rom", chapter: 15, verses: 13...13),
        .init(bookId: "1Cor", chapter: 10, verses: 13...13),
        .init(bookId: "1Cor", chapter: 13, verses: 4...4),
        .init(bookId: "1Cor", chapter: 16, verses: 14...14),
        .init(bookId: "2Cor", chapter: 5, verses: 17...17),
        .init(bookId: "2Cor", chapter: 12, verses: 9...9),
        .init(bookId: "Gal", chapter: 2, verses: 20...20),
        .init(bookId: "Gal", chapter: 5, verses: 22...22),
        .init(bookId: "Eph", chapter: 2, verses: 8...8),
        .init(bookId: "Eph", chapter: 2, verses: 10...10),
        .init(bookId: "Eph", chapter: 3, verses: 20...20),
        .init(bookId: "Phil", chapter: 1, verses: 6...6),
        .init(bookId: "Phil", chapter: 4, verses: 6...6),
        .init(bookId: "Phil", chapter: 4, verses: 8...8),
        .init(bookId: "Phil", chapter: 4, verses: 13...13),
        .init(bookId: "Col", chapter: 3, verses: 23...23),
        .init(bookId: "1Thess", chapter: 5, verses: 16...16),
        .init(bookId: "1Thess", chapter: 5, verses: 18...18),
        .init(bookId: "2Tim", chapter: 1, verses: 7...7),
        .init(bookId: "Heb", chapter: 4, verses: 16...16),
        .init(bookId: "Heb", chapter: 11, verses: 1...1),
        .init(bookId: "Heb", chapter: 12, verses: 2...2),
        .init(bookId: "Heb", chapter: 13, verses: 8...8),
        .init(bookId: "Jas", chapter: 1, verses: 5...5),
        .init(bookId: "1Pet", chapter: 5, verses: 7...7),
        .init(bookId: "1John", chapter: 4, verses: 19...19),
        .init(bookId: "Rev", chapter: 21, verses: 4...4)
    ]

    /// The verse for a civil date. `epochDay` counts days since 1970-01-01.
    public static func verse(epochDay: Int) -> PassageReference {
        let count = pool.count
        let cycle = epochDay >= 0 ? epochDay / count : (epochDay - count + 1) / count
        let position = ((epochDay % count) + count) % count
        return pool[permutation(seed: UInt64(bitPattern: Int64(cycle)))[position]]
    }

    /// The verse for a year/month/day in the proleptic Gregorian calendar.
    public static func verse(year: Int, month: Int, day: Int) -> PassageReference {
        verse(epochDay: epochDay(year: year, month: month, day: day))
    }

    /// Days since 1970-01-01 for a civil date (Howard Hinnant's `days_from_civil`),
    /// identical to `java.time.LocalDate.toEpochDay` so both platforms agree.
    public static func epochDay(year: Int, month: Int, day: Int) -> Int {
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let mp = (month + 9) % 12
        let doy = (153 * mp + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    /// Fisher–Yates over the pool indices, driven by splitmix64 from `seed`.
    static func permutation(seed: UInt64) -> [Int] {
        var indices = Array(0..<pool.count)
        var state = seed
        var i = indices.count - 1
        while i > 0 {
            let j = Int(splitmix64(&state) % UInt64(i + 1))
            indices.swapAt(i, j)
            i -= 1
        }
        return indices
    }

    private static func splitmix64(_ state: inout UInt64) -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}

import Foundation

extension DailyVerses {
    /// The verse for the calendar day containing `date`, in `calendar`'s time zone.
    public static func verse(on date: Date, calendar: Calendar) -> PassageReference {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return verse(year: c.year ?? 1970, month: c.month ?? 1, day: c.day ?? 1)
    }
}
