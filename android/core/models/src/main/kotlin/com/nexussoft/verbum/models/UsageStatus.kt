package com.nexussoft.verbum.models
data class UsageRestriction(val code: String, val retryAt: String? = null)
data class UsageStatus(val plan: String, val resetsAt: String, val remaining: Map<String, Int>, val voiceSeconds: Int, val restricted: Boolean)
