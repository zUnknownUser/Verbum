package com.nexussoft.verbum

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.nexussoft.verbum.models.BookLanguage
import java.util.Locale

/** One locale policy for app resources, notifications, content and voice. */
internal fun localizedContext(base: Context): Context {
    val requested = base.resources.configuration.locales[0]
    val locale = if (BookLanguage.of(requested) == BookLanguage.PORTUGUESE) Locale.forLanguageTag("pt-BR") else Locale.ENGLISH
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}

class VerbumApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(localizedContext(base))
    }
}
