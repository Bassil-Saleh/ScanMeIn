package com.ticketproject.webapp.services.access;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ticketproject.webapp.exceptions.AccessDeniedException;

/**
 * AccessControlService enforces an optional, config-driven email allowlist.
 *
 * <p>The allowlist is configured via the {@code app.access.allowed-email-domains}
 * property (injected from the {@code ALLOWED_EMAIL_DOMAINS} environment variable
 * in the {@code docker} profile). Each comma-separated entry is either:</p>
 * <ul>
 *   <li>a full email address (contains {@code @}) which must match exactly
 *       (case-insensitive), or</li>
 *   <li>a bare domain (no {@code @}) which matches any address whose domain
 *       equals the entry or is a subdomain of it.</li>
 * </ul>
 *
 * <p>When the property is empty or unset the allowlist is disabled and every
 * email address is allowed, preserving backwards compatibility for the local
 * development setup and the home-network Docker deployment.</p>
 */
@Service
public class AccessControlService
{
    /** Set of exact email addresses (lower-cased) allowed by the list. */
    private final Set<String> allowedEmails;

    /** Set of domains (lower-cased) allowed by the list. */
    private final Set<String> allowedDomains;

    /** True when the allowlist is disabled (empty config value). */
    private final boolean allowlistDisabled;

    /**
     * Constructs a new AccessControlService.
     *
     * @param allowedEmailDomainsRaw comma-separated list of allowed email
     *        addresses and/or domains; empty or blank disables the allowlist
     */
    public AccessControlService(@Value("${app.access.allowed-email-domains:}") String allowedEmailDomainsRaw)
    {
        if (allowedEmailDomainsRaw == null || allowedEmailDomainsRaw.isBlank())
        {
            this.allowlistDisabled = true;
            this.allowedEmails = Set.of();
            this.allowedDomains = Set.of();
            return;
        }

        List<String> entries = Stream.of(allowedEmailDomainsRaw.split(","))
            .map(entry -> entry.trim().toLowerCase(Locale.ROOT))
            .filter(entry -> !entry.isEmpty())
            .toList();

        // In case the allowlist only consists of commas and whitespace.
        if (entries.isEmpty())
        {
            this.allowlistDisabled = true;
            this.allowedEmails = Set.of();
            this.allowedDomains = Set.of();
            return;
        }

        this.allowlistDisabled = false;

        this.allowedEmails = entries.stream()
            .filter(entry -> entry.contains("@"))
            .collect(Collectors.toSet());

        this.allowedDomains = entries.stream()
            .filter(entry -> !entry.contains("@"))
            .collect(Collectors.toSet());
    }

    /**
     * Checks whether the given email address is permitted by the allowlist.
     *
     * @param email the email address to check
     * @return true if the allowlist is disabled or the email is allowed
     */
    public boolean isEmailAllowed(String email)
    {
        if (allowlistDisabled)
        {
            return true;
        }

        if (email == null)
        {
            return false;
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);

        if (allowedEmails.contains(normalized))
        {
            return true;
        }

        int atIndex = normalized.lastIndexOf('@');
        if (atIndex < 0 || atIndex == normalized.length() - 1)
        {
            return false;
        }

        String domain = normalized.substring(atIndex + 1);

        return allowedDomains.stream().anyMatch(
            allowedDomain -> domain.equals(allowedDomain) || domain.endsWith("." + allowedDomain));
    }

    /**
     * Asserts that the given email address is permitted by the allowlist,
     * throwing {@link AccessDeniedException} otherwise.
     *
     * @param email the email address to check
     * @throws AccessDeniedException if the email address is not allowed
     */
    public void requireEmailAllowed(String email)
    {
        if (!isEmailAllowed(email))
        {
            throw new AccessDeniedException("Access is restricted to approved email addresses.");
        }
    }
}
