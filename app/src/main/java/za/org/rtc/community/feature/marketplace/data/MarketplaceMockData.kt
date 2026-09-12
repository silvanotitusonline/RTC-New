package za.org.rtc.community.feature.marketplace.data

import java.time.Instant
import java.time.temporal.ChronoUnit
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminMetrics
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminQueue
import za.org.rtc.community.feature.marketplace.domain.MarketplaceAdminQueueItem
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessCard
import za.org.rtc.community.feature.marketplace.domain.MarketplaceBusinessDetail
import za.org.rtc.community.feature.marketplace.domain.MarketplaceCategory
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHome
import za.org.rtc.community.feature.marketplace.domain.MarketplaceHoursInterval
import za.org.rtc.community.feature.marketplace.domain.MarketplaceLocation
import za.org.rtc.community.feature.marketplace.domain.MarketplaceMediaAsset
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOffering
import za.org.rtc.community.feature.marketplace.domain.MarketplaceOwnerResponse
import za.org.rtc.community.feature.marketplace.domain.MarketplaceRating
import za.org.rtc.community.feature.marketplace.domain.MarketplaceReview
import za.org.rtc.community.feature.marketplace.domain.defaultGalleryPhotosForCategory

object MarketplaceMockData {
    private val now = Instant.now()

    fun getSampleCategories(): List<MarketplaceCategory> = listOf(
        MarketplaceCategory(id = "cat_food", name = "Food & Dining", slug = "food-dining", iconKey = "restaurant"),
        MarketplaceCategory(id = "cat_auto", name = "Auto & Mechanics", slug = "auto-mechanics", iconKey = "car"),
        MarketplaceCategory(id = "cat_beauty", name = "Health & Beauty", slug = "health-beauty", iconKey = "spa"),
        MarketplaceCategory(id = "cat_trade", name = "Home & Trade Services", slug = "home-trade", iconKey = "build"),
        MarketplaceCategory(id = "cat_retail", name = "Retail & Crafts", slug = "retail-crafts", iconKey = "shopping_bag"),
        MarketplaceCategory(id = "cat_tech", name = "Tech & Digital Services", slug = "tech-digital", iconKey = "devices"),
    )

    fun getSampleBusinesses(): List<MarketplaceBusinessCard> = listOf(
        MarketplaceBusinessCard(
            id = "biz_001",
            slug = "mam-bongis-kitchen",
            displayName = "Mam' Bongi's Kitchen & Catering",
            tagline = "Authentic home-cooked African dishes, fresh dombolo, and hearty stews.",
            category = "Food & Dining",
            locality = "Ward 4, Central",
            ratingAverage = 4.9,
            reviewCount = 84,
            weightedScore = 4.92,
            distanceMetres = 450,
            logoPath = "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=200&q=80",
            verified = true,
            featured = true,
        ),
        MarketplaceBusinessCard(
            id = "biz_002",
            slug = "ubuntu-solar-electrical",
            displayName = "Ubuntu Solar & Electrical Solutions",
            tagline = "Certified residential solar installations, inverter setups, and electrical compliance.",
            category = "Home & Trade Services",
            locality = "West Ward",
            ratingAverage = 4.8,
            reviewCount = 52,
            weightedScore = 4.81,
            distanceMetres = 1200,
            logoPath = "https://images.unsplash.com/photo-1509391365360-2e959784a276?w=200&q=80",
            verified = true,
            featured = true,
        ),
        MarketplaceBusinessCard(
            id = "biz_003",
            slug = "zandis-hair-sanctuary",
            displayName = "Zandi's Hair & Beauty Sanctuary",
            tagline = "Specialist protective hair braiding, loc maintenance, and organic scalp treatments.",
            category = "Health & Beauty",
            locality = "Civic Centre Mall",
            ratingAverage = 4.9,
            reviewCount = 116,
            weightedScore = 4.95,
            distanceMetres = 600,
            logoPath = "https://images.unsplash.com/photo-1560750588-73207b1ef5b8?w=200&q=80",
            verified = true,
            featured = true,
        ),
        MarketplaceBusinessCard(
            id = "biz_004",
            slug = "precision-auto-care",
            displayName = "Precision Auto Care & Tyre Centre",
            tagline = "Minor & major vehicle servicing, brake pads, computerized wheel alignment, and punctures.",
            category = "Auto & Mechanics",
            locality = "East Industrial Zone",
            ratingAverage = 4.6,
            reviewCount = 67,
            weightedScore = 4.58,
            distanceMetres = 2800,
            logoPath = "https://images.unsplash.com/photo-1617814076367-b759c7d7e738?w=200&q=80",
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz_005",
            slug = "kasi-creatives-print-lab",
            displayName = "Kasi Creatives Print & Brand Lab",
            tagline = "High-definition custom vinyl printing, corporate signage, uniforms, and embroidery.",
            category = "Tech & Digital Services",
            locality = "Ward 4, Central",
            ratingAverage = 4.8,
            reviewCount = 38,
            weightedScore = 4.75,
            distanceMetres = 850,
            logoPath = "https://images.unsplash.com/photo-1521791136064-7986c2920216?w=200&q=80",
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz_006",
            slug = "green-leaf-plant-nursery",
            displayName = "Green Leaf Community Plant Nursery",
            tagline = "Indigenous garden shrubs, fruit trees, organic compost, and vegetable seedlings.",
            category = "Retail & Crafts",
            locality = "Green Valley",
            ratingAverage = 4.8,
            reviewCount = 29,
            weightedScore = 4.70,
            distanceMetres = 3400,
            logoPath = "https://images.unsplash.com/photo-1585320806297-9794b3e4eeae?w=200&q=80",
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz_007",
            slug = "sizwes-plumbing-repairs",
            displayName = "Sizwe's Plumbing & Leak Detection",
            tagline = "24/7 emergency geyser replacements, drain unclogging, and pressure valve repairs.",
            category = "Home & Trade Services",
            locality = "South Ward",
            ratingAverage = 4.7,
            reviewCount = 44,
            weightedScore = 4.65,
            distanceMetres = 1900,
            logoPath = "https://images.unsplash.com/photo-1581092918056-0c4c3acd3789?w=200&q=80",
            verified = true,
            featured = false,
        ),
        MarketplaceBusinessCard(
            id = "biz_008",
            slug = "sunrise-bakery-patisserie",
            displayName = "Sunrise Bakery & Patisserie",
            tagline = "Artisan sourdough, fresh morning croissants, birthday celebration cakes, and pies.",
            category = "Food & Dining",
            locality = "Market Square",
            ratingAverage = 4.7,
            reviewCount = 92,
            weightedScore = 4.68,
            distanceMetres = 550,
            logoPath = "https://images.unsplash.com/photo-1509440159596-0249088772ff?w=200&q=80",
            verified = true,
            featured = false,
        ),
    )

    fun getSampleHome(): MarketplaceHome {
        val all = getSampleBusinesses()
        return MarketplaceHome(
            featured = all.filter { it.featured },
            nearby = all,
            newest = all.takeLast(4),
            topRated = all.sortedByDescending { it.ratingAverage },
            categories = getSampleCategories(),
        )
    }

    fun getSampleSavedBusinesses(): List<MarketplaceBusinessCard> =
        getSampleBusinesses().take(2)

    fun getSampleDetail(idOrSlug: String): MarketplaceBusinessDetail {
        val card = getSampleBusinesses().firstOrNull { it.id == idOrSlug || it.slug == idOrSlug }
            ?: getSampleBusinesses().first()

        val sampleHours = listOf(
            MarketplaceHoursInterval(dayOfWeek = 1, intervalOrder = 0, state = "OPEN", opensAt = "08:00", closesAt = "17:00"),
            MarketplaceHoursInterval(dayOfWeek = 2, intervalOrder = 0, state = "OPEN", opensAt = "08:00", closesAt = "17:00"),
            MarketplaceHoursInterval(dayOfWeek = 3, intervalOrder = 0, state = "OPEN", opensAt = "08:00", closesAt = "17:00"),
            MarketplaceHoursInterval(dayOfWeek = 4, intervalOrder = 0, state = "OPEN", opensAt = "08:00", closesAt = "17:00"),
            MarketplaceHoursInterval(dayOfWeek = 5, intervalOrder = 0, state = "OPEN", opensAt = "08:00", closesAt = "17:00"),
            MarketplaceHoursInterval(dayOfWeek = 6, intervalOrder = 0, state = "OPEN", opensAt = "09:00", closesAt = "14:00"),
        )

        val locations = listOf(
            MarketplaceLocation(
                id = "loc_${card.id}_1",
                label = "Main Branch",
                locality = card.locality,
                municipality = "RTC Metropolitan",
                province = "Gauteng",
                address = "14 Main Commercial Road, ${card.locality}",
                visibility = "PUBLIC",
                latitude = -26.2041,
                longitude = 28.0473,
                timezone = "Africa/Johannesburg",
                accessibilityFeatures = listOf("Wheelchair ramp", "Ground floor access", "Dedicated parking"),
                parkingNote = "Customer parking available on street front and rear courtyard.",
                hours = sampleHours,
            )
        )

        val offerings = when (card.category) {
            "Food & Dining" -> listOf(
                MarketplaceOffering(
                    id = "off_1",
                    type = "SERVICE",
                    title = "Hearty Beef & Dumpling Platter",
                    description = "Slow-braised beef brisket stew with steamed dombolo and chakalaka.",
                    priceType = "FIXED",
                    currencyCode = "ZAR",
                    priceMin = "85",
                    priceMax = null,
                    durationMinutes = 15,
                    availabilityNote = "Available daily from 11:30",
                ),
                MarketplaceOffering(
                    id = "off_2",
                    type = "SERVICE",
                    title = "Event & Family Catering Service",
                    description = "Buffet catering for weddings, birthdays, and community meetings up to 100 guests.",
                    priceType = "QUOTE",
                    currencyCode = "ZAR",
                    priceMin = null,
                    priceMax = null,
                    durationMinutes = null,
                    availabilityNote = "Book at least 3 days in advance",
                ),
            )
            "Home & Trade Services" -> listOf(
                MarketplaceOffering(
                    id = "off_solar_1",
                    type = "SERVICE",
                    title = "5kVA Inverter & Solar Backup Installation",
                    description = "Complete home installation including lithium battery, inverter, and municipal safety certificate.",
                    priceType = "FROM",
                    currencyCode = "ZAR",
                    priceMin = "48000",
                    priceMax = null,
                    durationMinutes = 480,
                    availabilityNote = "Free site inspection included",
                ),
                MarketplaceOffering(
                    id = "off_solar_2",
                    type = "SERVICE",
                    title = "Electrical Compliance Certificate (CoC)",
                    description = "Inspection and certification of residential and commercial electrical installations.",
                    priceType = "FIXED",
                    currencyCode = "ZAR",
                    priceMin = "950",
                    priceMax = null,
                    durationMinutes = 60,
                    availabilityNote = "Same-day inspection available",
                ),
            )
            else -> listOf(
                MarketplaceOffering(
                    id = "off_gen_1",
                    type = "SERVICE",
                    title = "Consultation & Service Estimate",
                    description = "One-on-one consultation to assess your specific requirements with itemised pricing.",
                    priceType = "FREE",
                    currencyCode = "ZAR",
                    priceMin = "0",
                    priceMax = null,
                    durationMinutes = 30,
                    availabilityNote = "Walk-ins welcome or book via WhatsApp",
                ),
                MarketplaceOffering(
                    id = "off_gen_2",
                    type = "SERVICE",
                    title = "Standard Service Package",
                    description = "Comprehensive service delivery with certified parts and workmanship warranty.",
                    priceType = "RANGE",
                    currencyCode = "ZAR",
                    priceMin = "350",
                    priceMax = "850",
                    durationMinutes = 90,
                    availabilityNote = "Daily appointments",
                ),
            )
        }

        val galleryPhotos = defaultGalleryPhotosForCategory(card.category)

        return MarketplaceBusinessDetail(
            card = card,
            description = "${card.displayName} has been serving our community with integrity and dedication since 2021. " +
                "We pride ourselves on friendly, professional customer service, verified quality standards, and supporting the local economy. " +
                "Contact us directly or stop by during operating hours.",
            phone = "+27 (0)82 555 0192",
            whatsappEnabled = true,
            email = "info@${card.slug}.rtc.org.za",
            websiteUrl = "https://rtc.org.za/marketplace/${card.slug}",
            locations = locations,
            offerings = offerings,
            media = galleryPhotos.mapIndexed { index, url ->
                MarketplaceMediaAsset(
                    id = "media_${card.id}_$index",
                    type = "IMAGE",
                    path = url,
                    altText = "${card.displayName} photo $index",
                    displayOrder = index,
                )
            },
            rating = MarketplaceRating(
                average = card.ratingAverage,
                count = card.reviewCount,
                distribution = mapOf("5" to 68, "4" to 22, "3" to 8, "2" to 2, "1" to 0),
            ),
            saved = false,
            galleryImages = galleryPhotos,
        )
    }

    fun getSampleReviews(businessId: String): Pair<List<MarketplaceReview>, MarketplaceRating> {
        val reviews = listOf(
            MarketplaceReview(
                id = "rev_001",
                rating = 5,
                title = "Exceptional quality and fast turnaround!",
                body = "Really impressed by how quickly they attended to us. Friendly staff and transparent pricing from the start. Would definitely recommend to anyone in Ward 4.",
                createdAt = now.minus(3, ChronoUnit.DAYS).toString(),
                updatedAt = now.minus(3, ChronoUnit.DAYS).toString(),
                isMine = false,
                helpfulCount = 14,
                response = MarketplaceOwnerResponse(
                    id = "resp_001",
                    body = "Thank you so much for the kind words! It was our absolute pleasure to assist you.",
                    createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
                ),
            ),
            MarketplaceReview(
                id = "rev_002",
                rating = 5,
                title = "A true neighborhood gem",
                body = "Top tier service right on our doorstep! Supporting our local entrepreneurs is so important and these guys make it easy.",
                createdAt = now.minus(7, ChronoUnit.DAYS).toString(),
                updatedAt = now.minus(7, ChronoUnit.DAYS).toString(),
                isMine = false,
                helpfulCount = 9,
                response = null,
            ),
            MarketplaceReview(
                id = "rev_003",
                rating = 4,
                title = "Great experience, will use again",
                body = "Everything was handled professionally. Slight delay during peak afternoon rush, but the quality of the final result made up for it.",
                createdAt = now.minus(14, ChronoUnit.DAYS).toString(),
                updatedAt = now.minus(14, ChronoUnit.DAYS).toString(),
                isMine = false,
                helpfulCount = 5,
                response = null,
            ),
        )

        val rating = MarketplaceRating(
            average = 4.8,
            count = reviews.size,
            distribution = mapOf("5" to 2, "4" to 1, "3" to 0, "2" to 0, "1" to 0),
        )

        return Pair(reviews, rating)
    }

    fun getSampleAdminMetrics(): MarketplaceAdminMetrics = MarketplaceAdminMetrics(
        pendingListings = 3,
        changesRequested = 1,
        publishedBusinesses = 48,
        activeLocations = 56,
        flaggedReviews = 2,
        suspendedListings = 0,
    )

    fun getSampleAdminQueue(): MarketplaceAdminQueue = MarketplaceAdminQueue(
        items = listOf(
            MarketplaceAdminQueueItem(
                id = "queue_001",
                businessId = "biz_005",
                revisionId = "rev_005_1",
                displayName = "Kasi Creatives Print & Brand Lab",
                state = "PENDING_REVIEW",
                assignedTo = null,
                createdAt = now.minus(1, ChronoUnit.DAYS).toString(),
            ),
            MarketplaceAdminQueueItem(
                id = "queue_002",
                businessId = "biz_006",
                revisionId = "rev_006_1",
                displayName = "Green Leaf Community Plant Nursery",
                state = "PENDING_REVIEW",
                assignedTo = "staff@rtc.org.za",
                createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
            ),
        ),
        metrics = getSampleAdminMetrics(),
    )
}
