package com.nexussoft.verbum.feature.scripture.ui
import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import com.nexussoft.verbum.models.UsageRestriction
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
internal fun usageDate(raw: String): String? = runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)) }.getOrNull()
@Composable internal fun UsageNotice(restriction: UsageRestriction) {
    val title = when(restriction.code) {
        "plan_required" -> "limitPlanTitle"
        "budget_exhausted", "usage_unavailable" -> "limitTemporaryTitle"
        "session_limit" -> "limitSessionTitle"
        "rate_limited", "request_in_progress" -> "limitRateTitle"
        else -> "limitTitle"
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(accountText(title), style = VerbumTypography.editorialHeadline)
        Text(accountText(if(restriction.code == "plan_required") "limitPlanBody" else "limitBody"), style = MaterialTheme.typography.bodyMedium)
        restriction.retryAt?.let(::usageDate)?.let { Text(accountText("usageReset") + " " + it, style = MaterialTheme.typography.bodySmall) }
    }
}
