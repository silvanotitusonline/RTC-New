package za.org.rtc.community.feature.servicecentre.domain

import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceCentreValidationTest {
    @Test
    fun providerValidationRequiresCategoryAreaBoundedRadiusAndSafePrice() {
        assertNotNull(ServiceCentreValidation.provider("", "Postmasburg", BigDecimal("800"), 25))
        assertNotNull(ServiceCentreValidation.provider("category", " ", BigDecimal("800"), 25))
        assertNotNull(ServiceCentreValidation.provider("category", "Postmasburg", BigDecimal("-0.01"), 25))
        assertNotNull(ServiceCentreValidation.provider("category", "Postmasburg", BigDecimal("1000000.00"), 25))
        assertNotNull(ServiceCentreValidation.provider("category", "Postmasburg", BigDecimal("800"), 0))
        assertNotNull(ServiceCentreValidation.provider("category", "Postmasburg", BigDecimal("800"), 51))

        assertNull(ServiceCentreValidation.provider("category", "Postmasburg", BigDecimal("0.00"), 1))
        assertNull(ServiceCentreValidation.provider("category", "Postmasburg", BigDecimal("999999.99"), 50))
    }

    @Test
    fun bookingValidationRejectsSelfBookingPastTimeUnsafeLocationAndOffer() {
        val now = Instant.parse("2026-08-28T14:00:00Z")
        val future = now.plusSeconds(3600)

        assertNotNull(ServiceCentreValidation.booking("same", "same", future, "Postmasburg", BigDecimal("500"), now))
        assertNotNull(ServiceCentreValidation.booking("customer", "provider", now, "Postmasburg", BigDecimal("500"), now))
        assertNotNull(ServiceCentreValidation.booking("customer", "provider", future, "ab", BigDecimal("500"), now))
        assertNotNull(ServiceCentreValidation.booking("customer", "provider", future, "x".repeat(241), BigDecimal("500"), now))
        assertNotNull(ServiceCentreValidation.booking("customer", "provider", future, "Postmasburg", BigDecimal("0.99"), now))
        assertNotNull(ServiceCentreValidation.booking("customer", "provider", future, "Postmasburg", BigDecimal("1000000.00"), now))

        assertNull(ServiceCentreValidation.booking("customer", "provider", future, "Postmasburg", BigDecimal("1.00"), now))
        assertNull(ServiceCentreValidation.booking("customer", "provider", future, "Postmasburg", BigDecimal("999999.99"), now))
    }

    @Test
    fun messageValidationAcceptsTrimmedTextAndRejectsBlankOrOversizedText() {
        assertNotNull(ServiceCentreValidation.message("   "))
        assertNotNull(ServiceCentreValidation.message("x".repeat(1001)))
        assertNull(ServiceCentreValidation.message(" Hello, can we discuss transport? "))
        assertNull(ServiceCentreValidation.message("x".repeat(1000)))
    }
}
