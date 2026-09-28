package com.nexussoft.verbum.feature.scripture.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.nexussoft.verbum.models.BookLanguage

data class PersonalSyncPresentation(val status: Status = Status.LOCAL, val revision: Int = 0) {
    enum class Status { LOCAL, SYNCING, SYNCED, PENDING }
    val message: String get() {
        val pt = BookLanguage.current == BookLanguage.PORTUGUESE
        return when(status) {
            Status.LOCAL -> if(pt) "Salvo neste aparelho. Entre na sua conta para sincronizar." else "Saved on this device. Sign in to sync."
            Status.SYNCING -> if(pt) "Sincronizando seus dados…" else "Syncing your data…"
            Status.SYNCED -> if(pt) "Sincronizado com sua conta. Disponível offline." else "Synced with your account. Available offline."
            Status.PENDING -> if(pt) "Há dados aguardando sincronização. Suas alterações continuam salvas neste aparelho." else "Sync is pending. Your changes are still saved on this device."
        }
    }
}
val LocalPersonalSync = staticCompositionLocalOf { PersonalSyncPresentation() }
