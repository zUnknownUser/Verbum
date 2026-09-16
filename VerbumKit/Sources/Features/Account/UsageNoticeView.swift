import DesignSystem
import Models
import SwiftUI

struct UsageNoticeView: View {
    let restriction: UsageRestriction
    private var title: String {
        switch restriction.code {
        case "plan_required": "limitPlanTitle"
        case "budget_exhausted", "usage_unavailable": "limitTemporaryTitle"
        case "session_limit": "limitSessionTitle"
        case "rate_limited", "request_in_progress": "limitRateTitle"
        default: "limitTitle"
        }
    }
    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(AccountCopy.text(title)).font(Typography.editorialHeadline)
            Text(AccountCopy.text(restriction.code == "plan_required" ? "limitPlanBody" : "limitBody")).font(Typography.subheadline)
            if let date = restriction.resetDate {
                Text(AccountCopy.text("usageReset") + " " + date.formatted(Date.FormatStyle(date: .abbreviated, time: .shortened).locale(BookLanguage.current.locale))).font(Typography.footnote)
            }
        }
    }
}
