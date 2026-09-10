package com.ticketproject.webapp.services.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketproject.webapp.exceptions.AccessDeniedException;

/**
 * Unit tests for {@link AccessControlService}, covering the config-driven
 * email allowlist matching rules (exact addresses, bare domains, subdomains,
 * case-insensitivity, whitespace trimming, and the disabled/empty case).
 */
class AccessControlServiceTest
{
    @Nested
    @DisplayName("When the allowlist is empty or unset")
    class DisabledAllowlist
    {
        @Test
        @DisplayName("empty config allows any email")
        void emptyConfigAllowsAnyEmail()
        {
            AccessControlService service = new AccessControlService("");

            assertThat(service.isEmailAllowed("anyone@example.com")).isTrue();
            assertThat(service.isEmailAllowed("other@other.org")).isTrue();
        }

        @Test
        @DisplayName("blank config allows any email")
        void blankConfigAllowsAnyEmail()
        {
            AccessControlService service = new AccessControlService("   ");

            assertThat(service.isEmailAllowed("anyone@example.com")).isTrue();
        }

        @Test
        @DisplayName("null config allows any email")
        void nullConfigAllowsAnyEmail()
        {
            AccessControlService service = new AccessControlService(null);

            assertThat(service.isEmailAllowed("anyone@example.com")).isTrue();
        }

        @Test
        @DisplayName("requireEmailAllowed does not throw when disabled")
        void requireDoesNotThrowWhenDisabled()
        {
            AccessControlService service = new AccessControlService("");

            service.requireEmailAllowed("anyone@example.com");
        }
    }

    @Nested
    @DisplayName("When the allowlist is configured")
    class EnabledAllowlist
    {
        private final AccessControlService service =
            new AccessControlService("allowed.com, me@mydomain.io, sub.allowed.com");

        @Test
        @DisplayName("bare domain matches any address at that domain")
        void bareDomainMatches()
        {
            assertThat(service.isEmailAllowed("alice@allowed.com")).isTrue();
            assertThat(service.isEmailAllowed("bob@allowed.com")).isTrue();
        }

        @Test
        @DisplayName("bare domain does not match a different domain")
        void bareDomainDoesNotMatchOther()
        {
            assertThat(service.isEmailAllowed("alice@notallowed.com")).isFalse();
            assertThat(service.isEmailAllowed("alice@allowed.org")).isFalse();
        }

        @Test
        @DisplayName("exact email entry matches only that address")
        void exactEmailMatches()
        {
            assertThat(service.isEmailAllowed("me@mydomain.io")).isTrue();
            assertThat(service.isEmailAllowed("someoneelse@mydomain.io")).isFalse();
        }

        @Test
        @DisplayName("subdomain of an allowed domain matches")
        void subdomainMatches()
        {
            assertThat(service.isEmailAllowed("carol@dev.allowed.com")).isTrue();
        }

        @Test
        @DisplayName("matching is case-insensitive")
        void caseInsensitive()
        {
            assertThat(service.isEmailAllowed("Alice@ALLOWED.COM")).isTrue();
            assertThat(service.isEmailAllowed("ME@MYDOMAIN.IO")).isTrue();
        }

        @Test
        @DisplayName("surrounding whitespace in the email is trimmed")
        void trimsEmailWhitespace()
        {
            assertThat(service.isEmailAllowed("  alice@allowed.com  ")).isTrue();
        }

        @Test
        @DisplayName("null email is not allowed when the list is enabled")
        void nullEmailNotAllowed()
        {
            assertThat(service.isEmailAllowed(null)).isFalse();
        }

        @Test
        @DisplayName("email without a domain part is not allowed")
        void emailWithoutDomainNotAllowed()
        {
            assertThat(service.isEmailAllowed("alice@")).isFalse();
            assertThat(service.isEmailAllowed("alice")).isFalse();
        }

        @Test
        @DisplayName("requireEmailAllowed throws AccessDeniedException for a disallowed email")
        void requireThrowsForDisallowed()
        {
            assertThatThrownBy(() -> service.requireEmailAllowed("eve@evil.com"))
                .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("requireEmailAllowed does not throw for an allowed email")
        void requireDoesNotThrowForAllowed()
        {
            service.requireEmailAllowed("alice@allowed.com");
        }
    }

    @Nested
    @DisplayName("Config parsing edge cases")
    class ParsingEdgeCases
    {
        @Test
        @DisplayName("whitespace around entries is trimmed")
        void trimsEntries()
        {
            AccessControlService service = new AccessControlService("  allowed.com ,  me@mydomain.io  ");

            assertThat(service.isEmailAllowed("alice@allowed.com")).isTrue();
            assertThat(service.isEmailAllowed("me@mydomain.io")).isTrue();
        }

        @Test
        @DisplayName("empty entries between commas are ignored")
        void ignoresEmptyEntries()
        {
            AccessControlService service = new AccessControlService("allowed.com,, ,me@mydomain.io");

            assertThat(service.isEmailAllowed("alice@allowed.com")).isTrue();
            assertThat(service.isEmailAllowed("me@mydomain.io")).isTrue();
            assertThat(service.isEmailAllowed("eve@evil.com")).isFalse();
        }

        @Test
        @DisplayName("a config of only commas/whitespace disables the allowlist")
        void onlySeparatorsDisables()
        {
            AccessControlService service = new AccessControlService(" , , ");

            assertThat(service.isEmailAllowed("anyone@example.com")).isTrue();
        }
    }
}
