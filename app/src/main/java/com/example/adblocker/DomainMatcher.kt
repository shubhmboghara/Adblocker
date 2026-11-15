package com.example.adblocker

import java.util.Locale

object DomainMatcher {
    fun normalizeDomain(rawDomain: String): String {
        return rawDomain.trim().trimEnd('.').lowercase(Locale.ROOT)
    }

    fun isDomainOrParentInSet(domain: String, containsDomain: (String) -> Boolean): Boolean {
        val clean = normalizeDomain(domain)
        if (clean.isEmpty()) return false

        var current = clean
        while (true) {
            if (containsDomain(current)) {
                return true
            }
            val dotIndex = current.indexOf('.')
            if (dotIndex < 0 || dotIndex == current.length - 1) {
                break
            }
            current = current.substring(dotIndex + 1)
        }
        return false
    }

    fun shouldBlockDomain(
        domain: String,
        isWhitelisted: (String) -> Boolean,
        isBlocked: (String) -> Boolean
    ): Boolean {
        val clean = normalizeDomain(domain)
        if (clean.isEmpty()) return false
        if (isDomainOrParentInSet(clean, isWhitelisted)) {
            return false
        }
        return isDomainOrParentInSet(clean, isBlocked)
    }
}
