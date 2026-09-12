package za.org.rtc.community.feature.servicecentre.data

import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreActorRole
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBooking
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreBookingStatus
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreCategory
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreMessage
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProvider
import za.org.rtc.community.feature.servicecentre.domain.ServiceCentreProviderProfile

object ServiceCentreMockData {
    private val now = Instant.now()

    fun getSampleCategories(): List<ServiceCentreCategory> = listOf(
        ServiceCentreCategory(id = "cat_sc_plumbing", name = "Plumbing & Geysers", slug = "plumbing", iconKey = "plumbing"),
        ServiceCentreCategory(id = "cat_sc_electrical", name = "Electrical & Solar", slug = "electrical", iconKey = "electrical_services"),
        ServiceCentreCategory(id = "cat_sc_carpentry", name = "Handyman & Carpentry", slug = "handyman", iconKey = "handyman"),
        ServiceCentreCategory(id = "cat_sc_appliance", name = "Appliance Repair", slug = "appliance", iconKey = "home_repair_service"),
        ServiceCentreCategory(id = "cat_sc_cleaning", name = "Home & Deep Cleaning", slug = "cleaning", iconKey = "cleaning_services"),
        ServiceCentreCategory(id = "cat_sc_tutoring", name = "Maths & Science Tutoring", slug = "tutoring", iconKey = "school"),
    )

    fun getSampleProviders(): List<ServiceCentreProvider> = listOf(
        ServiceCentreProvider(
            providerUserId = "usr_prov_001",
            displayName = "Sipho Mthembu",
            avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&q=80",
            categoryId = "cat_sc_plumbing",
            categoryName = "Plumbing & Geysers",
            locality = "Ward 4, Central",
            distanceMetres = 650,
            startingPrice = BigDecimal("350.00"),
            ratingAverage = 4.9,
            reviewCount = 54,
            verified = true,
            active = true,
        ),
        ServiceCentreProvider(
            providerUserId = "usr_prov_002",
            displayName = "Lerato Khanyile",
            avatarUrl = "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=150&q=80",
            categoryId = "cat_sc_electrical",
            categoryName = "Electrical & Solar",
            locality = "West Ward",
            distanceMetres = 1200,
            startingPrice = BigDecimal("450.00"),
            ratingAverage = 4.8,
            reviewCount = 68,
            verified = true,
            active = true,
        ),
        ServiceCentreProvider(
            providerUserId = "usr_prov_003",
            displayName = "Bongani Dube",
            avatarUrl = "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150&q=80",
            categoryId = "cat_sc_carpentry",
            categoryName = "Handyman & Carpentry",
            locality = "Central Ward",
            distanceMetres = 900,
            startingPrice = BigDecimal("280.00"),
            ratingAverage = 4.7,
            reviewCount = 37,
            verified = true,
            active = true,
        ),
        ServiceCentreProvider(
            providerUserId = "usr_prov_004",
            displayName = "Fatima Patel",
            avatarUrl = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=150&q=80",
            categoryId = "cat_sc_tutoring",
            categoryName = "Maths & Science Tutoring",
            locality = "Civic Ward",
            distanceMetres = 1500,
            startingPrice = BigDecimal("220.00"),
            ratingAverage = 5.0,
            reviewCount = 42,
            verified = true,
            active = true,
        ),
        ServiceCentreProvider(
            providerUserId = "usr_prov_005",
            displayName = "Thabo Sithole",
            avatarUrl = "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=150&q=80",
            categoryId = "cat_sc_appliance",
            categoryName = "Appliance Repair",
            locality = "South Ward",
            distanceMetres = 2100,
            startingPrice = BigDecimal("300.00"),
            ratingAverage = 4.8,
            reviewCount = 49,
            verified = true,
            active = true,
        ),
    )

    fun getSampleBookings(): List<ServiceCentreBooking> = listOf(
        ServiceCentreBooking(
            id = "bk_001",
            customerUserId = "usr_cust_me",
            providerUserId = "usr_prov_001",
            actorRole = ServiceCentreActorRole.CUSTOMER,
            counterpartyUserId = "usr_prov_001",
            counterpartyDisplayName = "Sipho Mthembu",
            counterpartyAvatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&q=80",
            categoryId = "cat_sc_plumbing",
            categoryName = "Plumbing & Geysers",
            requestedStartAt = now.plus(1, ChronoUnit.DAYS),
            serviceLocationText = "28 5th Avenue, Ward 4",
            offerAmount = BigDecimal("350.00"),
            currencyCode = "ZAR",
            status = ServiceCentreBookingStatus.CONFIRMED,
            acceptedAt = now.minus(2, ChronoUnit.HOURS),
            confirmedAt = now.minus(2, ChronoUnit.HOURS),
            createdAt = now.minus(5, ChronoUnit.HOURS),
            updatedAt = now.minus(2, ChronoUnit.HOURS),
        ),
        ServiceCentreBooking(
            id = "bk_002",
            customerUserId = "usr_cust_me",
            providerUserId = "usr_prov_002",
            actorRole = ServiceCentreActorRole.CUSTOMER,
            counterpartyUserId = "usr_prov_002",
            counterpartyDisplayName = "Lerato Khanyile",
            counterpartyAvatarUrl = "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?w=150&q=80",
            categoryId = "cat_sc_electrical",
            categoryName = "Electrical & Solar",
            requestedStartAt = now.plus(2, ChronoUnit.DAYS),
            serviceLocationText = "14 Acacia Road, West Ward",
            offerAmount = BigDecimal("500.00"),
            currencyCode = "ZAR",
            status = ServiceCentreBookingStatus.PENDING_PROVIDER,
            createdAt = now.minus(1, ChronoUnit.HOURS),
            updatedAt = now.minus(1, ChronoUnit.HOURS),
        ),
        ServiceCentreBooking(
            id = "bk_003",
            customerUserId = "usr_cust_me",
            providerUserId = "usr_prov_005",
            actorRole = ServiceCentreActorRole.CUSTOMER,
            counterpartyUserId = "usr_prov_005",
            counterpartyDisplayName = "Thabo Sithole",
            counterpartyAvatarUrl = "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?w=150&q=80",
            categoryId = "cat_sc_appliance",
            categoryName = "Appliance Repair",
            requestedStartAt = now.minus(3, ChronoUnit.DAYS),
            serviceLocationText = "28 5th Avenue, Ward 4",
            offerAmount = BigDecimal("420.00"),
            currencyCode = "ZAR",
            status = ServiceCentreBookingStatus.COMPLETED,
            acceptedAt = now.minus(4, ChronoUnit.DAYS),
            confirmedAt = now.minus(4, ChronoUnit.DAYS),
            completedAt = now.minus(3, ChronoUnit.DAYS),
            createdAt = now.minus(5, ChronoUnit.DAYS),
            updatedAt = now.minus(3, ChronoUnit.DAYS),
        ),
    )

    fun getSampleMessages(bookingId: String): List<ServiceCentreMessage> = when (bookingId) {
        "bk_001" -> listOf(
            ServiceCentreMessage(
                id = "msg_bk_1",
                bookingId = bookingId,
                senderUserId = "usr_cust_me",
                senderDisplayName = "You",
                body = "Hi Sipho, our kitchen mixer tap has a constant leak around the base washer. Could you inspect it tomorrow morning?",
                mine = true,
                createdAt = now.minus(4, ChronoUnit.HOURS),
            ),
            ServiceCentreMessage(
                id = "msg_bk_2",
                bookingId = bookingId,
                senderUserId = "usr_prov_001",
                senderDisplayName = "Sipho Mthembu",
                senderAvatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&q=80",
                body = "Hello! Yes, absolutely. I will bring standard ceramic cartridge replacements. I can be there at 09:30.",
                mine = false,
                createdAt = now.minus(3, ChronoUnit.HOURS),
            ),
            ServiceCentreMessage(
                id = "msg_bk_3",
                bookingId = bookingId,
                senderUserId = "usr_cust_me",
                senderDisplayName = "You",
                body = "Perfect, 09:30 works great. Gate intercom code is 1204.",
                mine = true,
                createdAt = now.minus(2, ChronoUnit.HOURS),
            ),
            ServiceCentreMessage(
                id = "msg_bk_4",
                bookingId = bookingId,
                senderUserId = "usr_prov_001",
                senderDisplayName = "Sipho Mthembu",
                senderAvatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150&q=80",
                body = "Confirmed, see you tomorrow at 09:30!",
                mine = false,
                createdAt = now.minus(2, ChronoUnit.HOURS),
            ),
        )
        else -> listOf(
            ServiceCentreMessage(
                id = "msg_default_sc",
                bookingId = bookingId,
                senderUserId = "usr_prov_002",
                senderDisplayName = "Provider",
                body = "Thank you for the booking request! I have received your location details and will respond shortly.",
                mine = false,
                createdAt = now.minus(30, ChronoUnit.MINUTES),
            )
        )
    }

    fun getSampleProviderProfile(userId: String): ServiceCentreProviderProfile = ServiceCentreProviderProfile(
        userId = userId,
        primaryCategoryId = "cat_sc_plumbing",
        categoryName = "Plumbing & Geysers",
        marketplaceBusinessId = null,
        locality = "Ward 4, Central",
        serviceRadiusKm = 25,
        startingPrice = BigDecimal("350.00"),
        currencyCode = "ZAR",
        active = true,
        createdAt = now.minus(30, ChronoUnit.DAYS),
        updatedAt = now.minus(2, ChronoUnit.DAYS),
    )
}
