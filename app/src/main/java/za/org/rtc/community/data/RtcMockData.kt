package za.org.rtc.community.data

import java.time.Instant
import java.time.temporal.ChronoUnit
import za.org.rtc.community.core.AdminAnalyticsDashboard
import za.org.rtc.community.core.AdminAnalyticsMetric
import za.org.rtc.community.core.AdminAnalyticsPeriod
import za.org.rtc.community.core.AdminAuditTrailEvent
import za.org.rtc.community.core.AdminLocalitySummary
import za.org.rtc.community.core.AdministrativeActivityEvent
import za.org.rtc.community.core.AssignedSupportCase
import za.org.rtc.community.core.CaseStage
import za.org.rtc.community.core.CentreRecord
import za.org.rtc.community.core.CommunityAlert
import za.org.rtc.community.core.CommunityAlertCategory
import za.org.rtc.community.core.CommunityAlertDashboardItem
import za.org.rtc.community.core.CommunityAlertState
import za.org.rtc.community.core.DashboardMetrics
import za.org.rtc.community.core.EditorialNoticeRecord
import za.org.rtc.community.core.HelpArticle
import za.org.rtc.community.core.MainDestination
import za.org.rtc.community.core.ModerationAppeal
import za.org.rtc.community.core.ModerationQueueItem
import za.org.rtc.community.core.NoticeStatus
import za.org.rtc.community.core.OfficialNotice
import za.org.rtc.community.core.OperationalIncident
import za.org.rtc.community.core.OperationsWorkItem
import za.org.rtc.community.core.OpportunityRecord
import za.org.rtc.community.core.ProjectRecord
import za.org.rtc.community.core.PublicSearchResult
import za.org.rtc.community.core.RtcNotification
import za.org.rtc.community.core.SupportCase
import za.org.rtc.community.core.SupportCaseMessage
import za.org.rtc.community.core.SystemHealthStatus

object RtcMockData {
    private val now = Instant.now()

    fun getSampleMetrics(): DashboardMetrics = DashboardMetrics(
        overallProjectProgress = 78.5,
        activeProjectCount = 14,
        centreCount = 8,
        opportunityCount = 12,
    )

    fun getSampleProjects(): List<ProjectRecord> = listOf(
        ProjectRecord(
            id = "proj_001",
            slug = "ward4-solar-microgrid",
            title = "Ward 4 Community Solar Microgrid Phase 2",
            summary = "Installation of a 45kW rooftop solar array and 120kWh battery storage bank supplying clean, uninterruptible power to community clinic and youth labs.",
            details = "Phase 2 expands solar generation to cover the community library, municipal clinic, and streetlighting circuits. Reduces dependency on municipal grid outages by 85%.",
            sector = "Renewable Energy & Infrastructure",
            projectStatus = "IN_PROGRESS",
            budgetAmount = 4500000.0,
            budgetCurrency = "ZAR",
            sourceUrl = "https://rtc.org.za/projects/solar-microgrid",
            sourceAsOf = "2026-09-01",
            updatedAt = now.minus(2, ChronoUnit.DAYS).toString(),
        ),
        ProjectRecord(
            id = "proj_002",
            slug = "central-clinic-expansion",
            title = "Central Community Clinic & Maternity Wing Expansion",
            summary = "Modernisation of consultation rooms, digital pharmacy dispensing, and an expanded 24-hour maternal healthcare ward.",
            details = "Upgrading capacity from 120 to 350 patient consultations per day. Includes emergency power backup and cold-chain vaccine refrigeration.",
            sector = "Public Health",
            projectStatus = "CONSTRUCTION",
            budgetAmount = 12800000.0,
            budgetCurrency = "ZAR",
            sourceUrl = "https://rtc.org.za/projects/clinic-expansion",
            sourceAsOf = "2026-08-15",
            updatedAt = now.minus(5, ChronoUnit.DAYS).toString(),
        ),
        ProjectRecord(
            id = "proj_003",
            slug = "khanya-youth-tech-lab",
            title = "Khanya Youth Digital Innovation & Robotics Lab",
            summary = "High-speed fibre-connected learning space equipped with 40 workstations, 3D printers, and robotics training kits for high school learners.",
            details = "Providing free accredited coding, digital literacy, and STEM training courses in partnership with provincial education and civic tech sponsors.",
            sector = "Youth & Education",
            projectStatus = "COMPLETED",
            budgetAmount = 1850000.0,
            budgetCurrency = "ZAR",
            sourceUrl = "https://rtc.org.za/projects/youth-tech-lab",
            sourceAsOf = "2026-07-20",
            updatedAt = now.minus(10, ChronoUnit.DAYS).toString(),
        ),
        ProjectRecord(
            id = "proj_004",
            slug = "urban-agriculture-rainwater",
            title = "Community Food Security & Rainwater Harvesting",
            summary = "Construction of 12 communal organic vegetable tunnels and 60,000L subterranean rainwater catchment tanks.",
            details = "Empowers 45 local families with sustainable agro-ecological vegetable farming, organic seedling nursery, and weekly community distribution.",
            sector = "Food Security & Environment",
            projectStatus = "ACTIVE",
            budgetAmount = 920000.0,
            budgetCurrency = "ZAR",
            sourceUrl = "https://rtc.org.za/projects/food-security",
            sourceAsOf = "2026-09-02",
            updatedAt = now.minus(1, ChronoUnit.DAYS).toString(),
        ),
        ProjectRecord(
            id = "proj_005",
            slug = "stormwater-culvert-upgrade",
            title = "Main Road Stormwater Culvert & Flood Mitigation",
            summary = "Rehabilitation of stormwater drainage channels and culvert widening along Main Road to prevent seasonal flash flooding.",
            details = "Re-engineering 2.4km of concrete drainage, stormwater retention ponds, and permeable gravel sidewalk buffers.",
            sector = "Civic Infrastructure",
            projectStatus = "PLANNING",
            budgetAmount = 6200000.0,
            budgetCurrency = "ZAR",
            sourceUrl = "https://rtc.org.za/projects/stormwater-upgrade",
            sourceAsOf = "2026-08-30",
            updatedAt = now.minus(7, ChronoUnit.DAYS).toString(),
        ),
        ProjectRecord(
            id = "proj_006",
            slug = "smart-led-streetlighting",
            title = "Neighborhood Smart LED Streetlighting Network",
            summary = "Replacing high-pressure sodium streetlamps with energy-efficient solar LED fixtures along transit routes and school zones.",
            details = "Over 380 new fixtures with integrated solar panels and battery storage to ensure lit streets during power outages.",
            sector = "Public Safety & Energy",
            projectStatus = "IN_PROGRESS",
            budgetAmount = 2400000.0,
            budgetCurrency = "ZAR",
            sourceUrl = "https://rtc.org.za/projects/streetlighting",
            sourceAsOf = "2026-09-05",
            updatedAt = now.minus(3, ChronoUnit.DAYS).toString(),
        ),
    )

    fun getSampleCentres(): List<CentreRecord> = listOf(
        CentreRecord(
            id = "centre_001",
            slug = "rtc-civic-centre",
            name = "RTC Central Civic & Community Centre",
            summary = "Primary municipal service hub, auditorium, resident advisory desks, and multi-purpose community hall.",
            category = "Municipal & Civic Hub",
            address = "42 Mandela Boulevard, Ward 4, RTC",
            locality = "Central Ward",
            phone = "+27 (0)11 555 0142",
            email = "civic@rtc.org.za",
            openingHours = "Mon-Fri: 07:30 - 16:30 | Sat: 08:30 - 12:30",
            directionsUrl = "https://maps.google.com/?q=RTC+Civic+Centre",
            updatedAt = now.minus(4, ChronoUnit.DAYS).toString(),
        ),
        CentreRecord(
            id = "centre_002",
            slug = "khanya-youth-hub",
            name = "Khanya Youth Tech & Innovation Hub",
            summary = "Free community computer lab, study desks, coding classrooms, and digital creative studio.",
            category = "Education & Youth",
            address = "18 Khanya Street, West Ward, RTC",
            locality = "West Ward",
            phone = "+27 (0)11 555 0188",
            email = "youth@rtc.org.za",
            openingHours = "Mon-Thu: 08:00 - 21:00 | Fri-Sat: 08:00 - 18:00",
            directionsUrl = "https://maps.google.com/?q=Khanya+Youth+Hub",
            updatedAt = now.minus(6, ChronoUnit.DAYS).toString(),
        ),
        CentreRecord(
            id = "centre_003",
            slug = "green-valley-centre",
            name = "Green Valley Environmental & Agro Centre",
            summary = "Community garden demonstration site, seedling nursery, composting depot, and environmental education centre.",
            category = "Environment & Agriculture",
            address = "Plot 7 Riverside Road, Green Valley, RTC",
            locality = "Green Valley",
            phone = "+27 (0)11 555 0199",
            email = "environment@rtc.org.za",
            openingHours = "Tue-Sun: 08:00 - 16:00",
            directionsUrl = "https://maps.google.com/?q=Green+Valley+Centre",
            updatedAt = now.minus(8, ChronoUnit.DAYS).toString(),
        ),
        CentreRecord(
            id = "centre_004",
            slug = "mandela-sports-complex",
            name = "Nelson Mandela Community Sports Complex",
            summary = "Floodlit synthetic turf soccer fields, netball courts, outdoor gymnasium, and athletics track.",
            category = "Sports & Recreation",
            address = "Corner 12th Avenue & Stadium Way, RTC",
            locality = "South Ward",
            phone = "+27 (0)11 555 0165",
            email = "sports@rtc.org.za",
            openingHours = "Mon-Sun: 06:00 - 20:00",
            directionsUrl = "https://maps.google.com/?q=Mandela+Sports+Complex",
            updatedAt = now.minus(12, ChronoUnit.DAYS).toString(),
        ),
        CentreRecord(
            id = "centre_005",
            slug = "ward4-clinic",
            name = "Ward 4 Primary Healthcare & Wellness Centre",
            summary = "Primary medical clinic, infant immunisations, prenatal care, and chronic medication dispensary.",
            category = "Healthcare & Wellness",
            address = "88 Clinic Road, Ward 4, RTC",
            locality = "Central Ward",
            phone = "+27 (0)11 555 0111",
            email = "clinic@rtc.org.za",
            openingHours = "Mon-Fri: 07:00 - 17:00 | 24/7 Emergency Triage",
            directionsUrl = "https://maps.google.com/?q=Ward4+Clinic",
            updatedAt = now.minus(3, ChronoUnit.DAYS).toString(),
        ),
        CentreRecord(
            id = "centre_006",
            slug = "impumelelo-skills-hub",
            name = "Impumelelo Vocational & Artisan Skills Hub",
            summary = "Accredited trade workshops for electrical, plumbing, carpentry, welding, and entrepreneurship training.",
            category = "Skills & Enterprise",
            address = "5 Industry Road, East Industrial, RTC",
            locality = "East Ward",
            phone = "+27 (0)11 555 0177",
            email = "skills@rtc.org.za",
            openingHours = "Mon-Fri: 08:00 - 17:00",
            directionsUrl = "https://maps.google.com/?q=Impumelelo+Skills+Hub",
            updatedAt = now.minus(15, ChronoUnit.DAYS).toString(),
        ),
    )

    fun getSampleOpportunities(): List<OpportunityRecord> = listOf(
        OpportunityRecord(
            id = "opp_001",
            slug = "gis-data-analyst-intern",
            title = "Youth GIS & Municipal Data Analyst Intern",
            summary = "6-month paid internship with the RTC Planning Department working on spatial mapping of community infrastructure assets.",
            opportunityType = "Internship",
            organisation = "RTC Planning & Development",
            locality = "Central Ward",
            closingAt = "2026-10-15T17:00:00Z",
            applicationUrl = "https://rtc.org.za/careers/gis-intern",
            contactEmail = "careers@rtc.org.za",
            updatedAt = now.minus(1, ChronoUnit.DAYS).toString(),
        ),
        OpportunityRecord(
            id = "opp_002",
            slug = "community-environmental-ambassador",
            title = "Community Environmental & Recycling Ambassadors",
            summary = "Stipend-supported community facilitators to lead door-to-door waste separation awareness and recycling bag distribution.",
            opportunityType = "Community Work",
            organisation = "EcoCycle Resident Network",
            locality = "All Wards",
            closingAt = "2026-09-30T17:00:00Z",
            applicationUrl = "https://rtc.org.za/careers/ambassadors",
            contactEmail = "recruitment@rtc.org.za",
            updatedAt = now.minus(2, ChronoUnit.DAYS).toString(),
        ),
        OpportunityRecord(
            id = "opp_003",
            slug = "volunteer-coding-mentor",
            title = "Volunteer Coding Mentors for Youth Bootcamp",
            summary = "Passionate programmers and computer science students invited to mentor high school learners during afternoon labs.",
            opportunityType = "Volunteer",
            organisation = "Khanya Tech Hub",
            locality = "West Ward",
            closingAt = "2026-10-31T17:00:00Z",
            applicationUrl = "https://rtc.org.za/volunteer/coding-mentor",
            contactEmail = "volunteer@rtc.org.za",
            updatedAt = now.minus(4, ChronoUnit.DAYS).toString(),
        ),
        OpportunityRecord(
            id = "opp_004",
            slug = "smme-business-incubation",
            title = "Ward 4 Small Business Incubation Programme 2026",
            summary = "Comprehensive mentorship, accounting tools, and seed grant funding of up to R50,000 for qualifying neighborhood micro-enterprises.",
            opportunityType = "Grant & Mentorship",
            organisation = "Local Economic Development Office",
            locality = "Central Ward",
            closingAt = "2026-11-15T17:00:00Z",
            applicationUrl = "https://rtc.org.za/smme/incubation",
            contactEmail = "smme@rtc.org.za",
            updatedAt = now.minus(3, ChronoUnit.DAYS).toString(),
        ),
        OpportunityRecord(
            id = "opp_005",
            slug = "community-health-traineeship",
            title = "Community Health Worker Certification Traineeships",
            summary = "Accredited 12-month foundational healthcare traineeship covering home-based care, vital signs monitoring, and wellness referral.",
            opportunityType = "Traineeship",
            organisation = "Department of Health & Wellness",
            locality = "South Ward",
            closingAt = "2026-10-20T17:00:00Z",
            applicationUrl = "https://rtc.org.za/careers/health-traineeship",
            contactEmail = "health-careers@rtc.org.za",
            updatedAt = now.minus(5, ChronoUnit.DAYS).toString(),
        ),
        OpportunityRecord(
            id = "opp_006",
            slug = "solar-installation-apprenticeship",
            title = "Photovoltaic Solar Technician Apprenticeship",
            summary = "Hands-on technical apprenticeship leading to PV GreenCard certification. Tools, safety gear, and monthly stipend provided.",
            opportunityType = "Apprenticeship",
            organisation = "RTC Renewable Energy Taskforce",
            locality = "East Ward",
            closingAt = "2026-10-10T17:00:00Z",
            applicationUrl = "https://rtc.org.za/careers/solar-apprentice",
            contactEmail = "energy@rtc.org.za",
            updatedAt = now.minus(6, ChronoUnit.DAYS).toString(),
        ),
    )

    fun getSampleNotices(): List<OfficialNotice> = listOf(
        OfficialNotice(
            id = "notice_001",
            title = "Water Infrastructure Scheduled Pressure Maintenance",
            summary = "Municipal water technicians will conduct scheduled isolation valve inspections at Reservoir 2 on Wednesday between 22:00 and 04:00. High-lying areas may experience reduced pressure.",
            status = NoticeStatus.PUBLISHED,
            publishedAt = now.minus(1, ChronoUnit.DAYS).toString(),
            requiresSafetyReview = false,
        ),
        OfficialNotice(
            id = "notice_002",
            title = "Annual Integrated Development Plan (IDP) 2026 Public Participation",
            summary = "Residents are invited to review the draft ward capital budget allocations and submit civic project proposals. Public hearings commence next Tuesday at the Civic Centre Auditorium.",
            status = NoticeStatus.PUBLISHED,
            publishedAt = now.minus(3, ChronoUnit.DAYS).toString(),
            requiresSafetyReview = false,
        ),
        OfficialNotice(
            id = "notice_003",
            title = "Substation B Transformer Upgrades & Load Reduction Contingency",
            summary = "Energy infrastructure upgrade works scheduled for Sunday morning. Standby generators will power the Community Clinic and Police Station without interruption.",
            status = NoticeStatus.PUBLISHED,
            publishedAt = now.minus(5, ChronoUnit.DAYS).toString(),
            requiresSafetyReview = true,
        ),
        OfficialNotice(
            id = "notice_004",
            title = "Revision of Waste Management & Recycling Collection Days",
            summary = "Refuse collection for Ward 4 will move to Tuesday mornings starting next month to accommodate dedicated recyclables separation trucks.",
            status = NoticeStatus.PUBLISHED,
            publishedAt = now.minus(7, ChronoUnit.DAYS).toString(),
            requiresSafetyReview = false,
        ),
        OfficialNotice(
            id = "notice_005",
            title = "Seasonal Wildfire & Veld Fire Precaution Advisory",
            summary = "Emergency services advise all property owners along the northern ridge buffer to maintain clear firebreaks and report any uncontrolled smoke immediately.",
            status = NoticeStatus.PUBLISHED,
            publishedAt = now.minus(10, ChronoUnit.DAYS).toString(),
            requiresSafetyReview = true,
        ),
    )

    fun getSampleHelpArticles(): List<HelpArticle> = listOf(
        HelpArticle(
            id = "help_001",
            slug = "log-service-request",
            title = "How to Log and Track a Community Service Request",
            summary = "Step-by-step guide to reporting potholes, water leaks, and street light faults through the RTC Support tab.",
            body = """
                ### Logging a Municipal Service Request
                
                Residents can report civic infrastructure issues quickly and directly through the RTC mobile application:
                
                1. **Navigate to the Support Tab**: Tap on the 'Support' icon on the bottom navigation bar.
                2. **Create New Ticket**: Tap on 'New Request' and select the appropriate category (e.g. Water & Sanitation, Roads, Electricity).
                3. **Provide Precise Location**: Enter the street address, closest intersection, or landmark to help response teams locate the issue quickly.
                4. **Attach Photographic Evidence**: High-resolution photos provide valuable context for dispatching the correct repair equipment.
                5. **Track Progress**: You will receive notifications as your ticket moves from 'Submitted' to 'Action Planned' and 'Resolved'.
            """.trimIndent(),
            category = "Service Delivery",
            publishedAt = now.minus(14, ChronoUnit.DAYS).toString(),
        ),
        HelpArticle(
            id = "help_002",
            slug = "solar-contingency-plan",
            title = "Community Solar Hubs & Power Outage Assistance",
            summary = "Where to access charging stations, emergency Wi-Fi, and cold storage for medicines during extended electrical outages.",
            body = """
                ### Community Power Hubs
                
                During periods of load shedding or unplanned electrical outages, the following facilities remain fully operational on solar microgrid power:
                
                - **RTC Central Civic Centre**: Mobile device charging stations and free Wi-Fi in the main foyer.
                - **Ward 4 Community Clinic**: Uninterrupted emergency care and refrigerated medicine storage.
                - **Khanya Youth Hub**: Study desks and internet access powered by solar battery backup.
            """.trimIndent(),
            category = "Infrastructure",
            publishedAt = now.minus(18, ChronoUnit.DAYS).toString(),
        ),
        HelpArticle(
            id = "help_003",
            slug = "waste-sorting-guide",
            title = "Neighborhood Waste Separation & Recycling Guide",
            summary = "A comprehensive visual guide to separating household recyclables, compostable greens, and hazardous e-waste.",
            body = """
                ### Household Recycling Guidelines
                
                Our community participates in the two-bag separation system:
                
                - **Clear Bags (Recyclables)**: Paper, clean cardboard, plastic bottles (PET), glass jars, beverage cans, and foil.
                - **Black Bags (General Waste)**: Food contaminated packaging, hygiene products, and non-recyclable items.
                - **E-Waste & Batteries**: Drop off old cellphones, laptop batteries, and appliance cords at the dedicated bins inside the Community Library.
            """.trimIndent(),
            category = "Environment",
            publishedAt = now.minus(21, ChronoUnit.DAYS).toString(),
        ),
        HelpArticle(
            id = "help_004",
            slug = "local-business-permits",
            title = "Registering a Small Business or Informal Trading Permit",
            summary = "Guidance for micro-enterprises, street traders, and food vendors wishing to trade in designated civic zones.",
            body = """
                ### Business Registration & Permits
                
                The Local Economic Development desk assists neighborhood entrepreneurs with:
                
                - Informal trader zoning permits for Civic Square and transport interchanges.
                - Health and safety certificates of acceptability for food and beverage vendors.
                - SMME directory listings on the RTC Marketplace tab.
            """.trimIndent(),
            category = "Local Economy",
            publishedAt = now.minus(25, ChronoUnit.DAYS).toString(),
        ),
    )

    fun getSampleAlerts(): List<CommunityAlert> = listOf(
        CommunityAlert(
            id = "alert_001",
            notificationId = "notif_alert_001",
            category = CommunityAlertCategory.COMMUNITY_UPDATE,
            state = CommunityAlertState.ORIGINAL,
            title = "Ward 4 High-Speed Fibre & Public Wi-Fi Expansion",
            summary = "Fibre trenching along 4th to 10th Avenue is completed. Free public Wi-Fi hotspots are now activated at Civic Park and the Library square.",
            body = "Civic telecommunications teams have finalized the public broadband ring. Residents and local businesses can now connect to the 'RTC-Community-Free' Wi-Fi network with 1GB daily high-speed browsing allowance.",
            linkedNoticeId = "notice_002",
            publishedAt = now.minus(2, ChronoUnit.HOURS).toString(),
            expiresAt = now.plus(14, ChronoUnit.DAYS).toString(),
            createdAt = now.minus(2, ChronoUnit.HOURS).toString(),
            readAt = null,
        ),
        CommunityAlert(
            id = "alert_002",
            notificationId = "notif_alert_002",
            category = CommunityAlertCategory.SERVICE_DISRUPTION,
            state = CommunityAlertState.ORIGINAL,
            title = "Reservoir 2 Valve Replacement & Water Pressure Notice",
            summary = "Scheduled water pressure reduction tonight from 22:00 to 04:00. Please store adequate water for evening household use.",
            body = "Municipal engineers are replacing isolation valves at the main reservoir. Tankers on standby for essential medical facilities. Normal flow expected by 05:00 tomorrow morning.",
            linkedNoticeId = "notice_001",
            publishedAt = now.minus(6, ChronoUnit.HOURS).toString(),
            expiresAt = now.plus(2, ChronoUnit.DAYS).toString(),
            createdAt = now.minus(6, ChronoUnit.HOURS).toString(),
            readAt = now.minus(4, ChronoUnit.HOURS).toString(),
        ),
        CommunityAlert(
            id = "alert_003",
            notificationId = "notif_alert_003",
            category = CommunityAlertCategory.SAFETY_EMERGENCY,
            state = CommunityAlertState.ORIGINAL,
            title = "Flash Flood Precaution: Lower River Crossing Road Closure",
            summary = "Heavy downpours upstream have swollen the river crossing. The low-water bridge on River Road is temporarily closed to vehicular traffic.",
            body = "Metropolitan traffic and emergency services have cordoned off the river crossing. Please use Main Road or 14th Avenue bridge as alternative routes. Do not attempt to cross submerged roads.",
            linkedNoticeId = null,
            publishedAt = now.minus(12, ChronoUnit.HOURS).toString(),
            expiresAt = now.plus(1, ChronoUnit.DAYS).toString(),
            createdAt = now.minus(12, ChronoUnit.HOURS).toString(),
            readAt = null,
        ),
        CommunityAlert(
            id = "alert_004",
            notificationId = "notif_alert_004",
            category = CommunityAlertCategory.EVENT,
            state = CommunityAlertState.ORIGINAL,
            title = "Annual Spring Youth Sports & Wellness Carnival",
            summary = "Join us this Saturday at Mandela Sports Complex for athletics, youth soccer matches, wellness stalls, and music.",
            body = "Over 400 local youth athletes competing in junior soccer, netball, and track events. Free wellness screenings, healthy cooking demonstrations, and food stalls.",
            linkedNoticeId = null,
            publishedAt = now.minus(1, ChronoUnit.DAYS).toString(),
            expiresAt = now.plus(4, ChronoUnit.DAYS).toString(),
            createdAt = now.minus(1, ChronoUnit.DAYS).toString(),
            readAt = null,
        ),
        CommunityAlert(
            id = "alert_005",
            notificationId = "notif_alert_005",
            category = CommunityAlertCategory.OPPORTUNITY,
            state = CommunityAlertState.ORIGINAL,
            title = "Applications Open: Youth GIS & Data Internship Programme",
            summary = "6-month paid digital skills internship with RTC Municipal Planning. Open to residents aged 18-28.",
            body = "Gain accredited hands-on experience in geographical information systems, spatial asset mapping, and public data visualization. Stipend and laptops provided.",
            linkedNoticeId = null,
            publishedAt = now.minus(2, ChronoUnit.DAYS).toString(),
            expiresAt = now.plus(20, ChronoUnit.DAYS).toString(),
            createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
            readAt = now.minus(1, ChronoUnit.DAYS).toString(),
        ),
    )

    fun getSampleAlertDashboard(): List<CommunityAlertDashboardItem> = listOf(
        CommunityAlertDashboardItem(
            id = "alert_001",
            category = CommunityAlertCategory.COMMUNITY_UPDATE,
            state = CommunityAlertState.ORIGINAL,
            title = "Ward 4 High-Speed Fibre & Public Wi-Fi Expansion",
            summary = "Fibre trenching completed. Hotspots active at Civic Park.",
            status = "PUBLISHED",
            scheduledAt = null,
            publishedAt = now.minus(2, ChronoUnit.HOURS).toString(),
            expiresAt = now.plus(14, ChronoUnit.DAYS).toString(),
            dispatchState = "DELIVERED",
            intendedRecipients = 1420,
            eligibleDevices = 1380,
            fcmAccepted = 1365,
            fcmFailed = 15,
            readCount = 942,
            createdAt = now.minus(2, ChronoUnit.HOURS).toString(),
        ),
        CommunityAlertDashboardItem(
            id = "alert_002",
            category = CommunityAlertCategory.SERVICE_DISRUPTION,
            state = CommunityAlertState.ORIGINAL,
            title = "Reservoir 2 Valve Replacement & Water Pressure Notice",
            summary = "Scheduled water pressure reduction tonight.",
            status = "PUBLISHED",
            scheduledAt = null,
            publishedAt = now.minus(6, ChronoUnit.HOURS).toString(),
            expiresAt = now.plus(2, ChronoUnit.DAYS).toString(),
            dispatchState = "DELIVERED",
            intendedRecipients = 2850,
            eligibleDevices = 2780,
            fcmAccepted = 2740,
            fcmFailed = 40,
            readCount = 2190,
            createdAt = now.minus(6, ChronoUnit.HOURS).toString(),
        ),
        CommunityAlertDashboardItem(
            id = "alert_003",
            category = CommunityAlertCategory.SAFETY_EMERGENCY,
            state = CommunityAlertState.ORIGINAL,
            title = "Flash Flood Precaution: Lower River Crossing Road Closure",
            summary = "Low-water bridge temporarily closed to vehicular traffic.",
            status = "PUBLISHED",
            scheduledAt = null,
            publishedAt = now.minus(12, ChronoUnit.HOURS).toString(),
            expiresAt = now.plus(1, ChronoUnit.DAYS).toString(),
            dispatchState = "DELIVERED",
            intendedRecipients = 3400,
            eligibleDevices = 3320,
            fcmAccepted = 3310,
            fcmFailed = 10,
            readCount = 3120,
            createdAt = now.minus(12, ChronoUnit.HOURS).toString(),
        ),
    )

    fun getSampleSupportCases(): List<SupportCase> = listOf(
        SupportCase(
            id = "case_101",
            title = "Water Pipe Leak on 7th Street Verge",
            stage = CaseStage.ACTION_PLANNED,
            updatedAt = now.minus(3, ChronoUnit.HOURS).toString(),
            actionRequired = false,
            category = "WATER_SANITATION",
            priority = 2,
            locationLabel = "Corner 7th St & Palm Ave, Ward 4",
        ),
        SupportCase(
            id = "case_102",
            title = "Non-Functioning Streetlight Outside Community Clinic",
            stage = CaseStage.REVIEWING,
            updatedAt = now.minus(1, ChronoUnit.DAYS).toString(),
            actionRequired = true,
            category = "ELECTRICITY",
            priority = 3,
            locationLabel = "Clinic Rd Gate 2, Ward 4",
        ),
        SupportCase(
            id = "case_103",
            title = "Pothole Remediation on Oliver Tambo Drive",
            stage = CaseStage.RESOLVED,
            updatedAt = now.minus(4, ChronoUnit.DAYS).toString(),
            actionRequired = false,
            category = "ROADS_INFRASTRUCTURE",
            priority = 3,
            locationLabel = "Oliver Tambo Dr near School Zone",
        ),
        SupportCase(
            id = "case_104",
            title = "Illegal Refuse Dumping in Public Park Buffer",
            stage = CaseStage.ACTION_PLANNED,
            updatedAt = now.minus(2, ChronoUnit.DAYS).toString(),
            actionRequired = false,
            category = "WASTE_MANAGEMENT",
            priority = 2,
            locationLabel = "Civic Park North Boundary",
        ),
        SupportCase(
            id = "case_105",
            title = "Tree Branch Trimming Request Near Overhead Lines",
            stage = CaseStage.SUBMITTED,
            updatedAt = now.minus(5, ChronoUnit.HOURS).toString(),
            actionRequired = false,
            category = "PARKS_FACILITIES",
            priority = 4,
            locationLabel = "14 Acacia Road, West Ward",
        ),
    )

    fun getSampleAssignedCases(): List<AssignedSupportCase> = listOf(
        AssignedSupportCase(
            id = "case_101",
            title = "Water Pipe Leak on 7th Street Verge",
            category = "WATER_SANITATION",
            state = "IN_PROGRESS",
            priority = 2,
            locationLabel = "Corner 7th St & Palm Ave, Ward 4",
            updatedAt = now.minus(3, ChronoUnit.HOURS).toString(),
        ),
        AssignedSupportCase(
            id = "case_102",
            title = "Non-Functioning Streetlight Outside Community Clinic",
            category = "ELECTRICITY",
            state = "IN_REVIEW",
            priority = 3,
            locationLabel = "Clinic Rd Gate 2, Ward 4",
            updatedAt = now.minus(1, ChronoUnit.DAYS).toString(),
        ),
        AssignedSupportCase(
            id = "case_104",
            title = "Illegal Refuse Dumping in Public Park Buffer",
            category = "WASTE_MANAGEMENT",
            state = "IN_PROGRESS",
            priority = 2,
            locationLabel = "Civic Park North Boundary",
            updatedAt = now.minus(2, ChronoUnit.DAYS).toString(),
        ),
    )

    fun getSampleSupportCaseMessages(caseId: String): List<SupportCaseMessage> = when (caseId) {
        "case_101" -> listOf(
            SupportCaseMessage(
                id = "msg_001",
                caseId = caseId,
                authorId = "author_resident",
                body = "Hello, water has been bubbling up from under the sidewalk paving since yesterday morning. It is beginning to pool near the driveway entrance.",
                createdAt = now.minus(1, ChronoUnit.DAYS).toString(),
            ),
            SupportCaseMessage(
                id = "msg_002",
                caseId = caseId,
                authorId = "author_staff",
                body = "Good day. A technical inspection team has surveyed the line. A damaged 50mm municipal branch pipe was identified. A repair crew with replacement couplings is scheduled for dispatch today.",
                createdAt = now.minus(8, ChronoUnit.HOURS).toString(),
            ),
            SupportCaseMessage(
                id = "msg_003",
                caseId = caseId,
                authorId = "author_staff",
                body = "Update: Crew is currently on site excavating the trench. Work is estimated to complete within 3 hours.",
                createdAt = now.minus(3, ChronoUnit.HOURS).toString(),
            ),
        )
        "case_102" -> listOf(
            SupportCaseMessage(
                id = "msg_101",
                caseId = caseId,
                authorId = "author_resident",
                body = "The streetlight directly opposite the pedestrian gate of the clinic has been dark for 3 nights, creating a safety issue for staff on night duty.",
                createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
            ),
            SupportCaseMessage(
                id = "msg_102",
                caseId = caseId,
                authorId = "author_staff",
                body = "Thank you for alerting us. We have logged this under high-priority public lighting. Could you confirm if the adjacent pole number is LP-402?",
                createdAt = now.minus(1, ChronoUnit.DAYS).toString(),
            ),
        )
        else -> listOf(
            SupportCaseMessage(
                id = "msg_default_1",
                caseId = caseId,
                authorId = "author_staff",
                body = "Your service request has been received by municipal operations and assigned to the relevant depot supervisor.",
                createdAt = now.minus(6, ChronoUnit.HOURS).toString(),
            ),
        )
    }

    fun getSampleNotifications(): List<RtcNotification> = listOf(
        RtcNotification(
            id = "notif_001",
            title = "Community Alert: High-Speed Fibre & Public Wi-Fi Expansion",
            message = "Free community Wi-Fi hotspots are now activated at Civic Park.",
            createdAt = now.minus(2, ChronoUnit.HOURS).toString(),
            unread = true,
            route = MainDestination.HOME,
            alertId = "alert_001",
        ),
        RtcNotification(
            id = "notif_002",
            title = "Support Ticket Update: Water Pipe Leak on 7th St",
            message = "Stage changed to 'Action planned'. Repair team dispatched.",
            createdAt = now.minus(3, ChronoUnit.HOURS).toString(),
            unread = true,
            route = MainDestination.SUPPORT,
            alertId = null,
        ),
        RtcNotification(
            id = "notif_003",
            title = "Community Alert: Water Pressure Notice",
            message = "Scheduled water pressure maintenance tonight between 22:00 and 04:00.",
            createdAt = now.minus(6, ChronoUnit.HOURS).toString(),
            unread = false,
            route = MainDestination.HOME,
            alertId = "alert_002",
        ),
        RtcNotification(
            id = "notif_004",
            title = "Community Alert: Flash Flood Warning",
            message = "Lower river crossing bridge temporarily closed.",
            createdAt = now.minus(12, ChronoUnit.HOURS).toString(),
            unread = false,
            route = MainDestination.HOME,
            alertId = "alert_003",
        ),
    )

    fun getSampleOperationsWorkQueue(): List<OperationsWorkItem> = listOf(
        OperationsWorkItem(
            id = "work_001",
            sourceType = "SUPPORT_CASE",
            sourceId = "case_101",
            title = "Urgent Water Pipe Leak on 7th Street",
            description = "High-priority municipal water leak affecting residential access. Repair team dispatched.",
            priority = "HIGH",
            state = "IN_PROGRESS",
            assignedToMe = true,
            isUnassigned = false,
            dueAt = now.plus(4, ChronoUnit.HOURS).toString(),
            createdAt = now.minus(1, ChronoUnit.DAYS).toString(),
        ),
        OperationsWorkItem(
            id = "work_002",
            sourceType = "EDITORIAL_NOTICE",
            sourceId = "notice_draft_004",
            title = "Review: Spring Storm Warning & Drainage Notice",
            description = "Editorial draft submitted by Civil Protection team for safety review prior to mass push dispatch.",
            priority = "MEDIUM",
            state = "OPEN",
            assignedToMe = false,
            isUnassigned = true,
            dueAt = now.plus(1, ChronoUnit.DAYS).toString(),
            createdAt = now.minus(4, ChronoUnit.HOURS).toString(),
        ),
        OperationsWorkItem(
            id = "work_003",
            sourceType = "MODERATION_REPORT",
            sourceId = "report_882",
            title = "Community Post Content Review (Potential Commercial Spam)",
            description = "Post reported by 3 community members for unverified promotional claims.",
            priority = "LOW",
            state = "OPEN",
            assignedToMe = false,
            isUnassigned = true,
            dueAt = now.plus(2, ChronoUnit.DAYS).toString(),
            createdAt = now.minus(2, ChronoUnit.HOURS).toString(),
        ),
    )

    fun getSampleSystemHealth(): List<SystemHealthStatus> = listOf(
        SystemHealthStatus(
            serviceKey = "POSTGRES_DB",
            status = "OPERATIONAL",
            category = "Database",
            affectedCount = 0,
            lastSuccessfulAt = now.minus(1, ChronoUnit.MINUTES).toString(),
            detail = "Primary database cluster latency is 14ms. Replication healthy.",
        ),
        SystemHealthStatus(
            serviceKey = "SUPABASE_AUTH",
            status = "OPERATIONAL",
            category = "Authentication",
            affectedCount = 0,
            lastSuccessfulAt = now.minus(2, ChronoUnit.MINUTES).toString(),
            detail = "OAuth2 and MFA authentication endpoints responding normally.",
        ),
        SystemHealthStatus(
            serviceKey = "STORAGE_CDN",
            status = "OPERATIONAL",
            category = "Storage",
            affectedCount = 0,
            lastSuccessfulAt = now.minus(3, ChronoUnit.MINUTES).toString(),
            detail = "Media asset buckets and signed URL caching operating smoothly.",
        ),
        SystemHealthStatus(
            serviceKey = "FCM_PUSH",
            status = "OPERATIONAL",
            category = "Notifications",
            affectedCount = 0,
            lastSuccessfulAt = now.minus(5, ChronoUnit.MINUTES).toString(),
            detail = "Push dispatch queue latency 180ms. Delivery success rate 99.4%.",
        ),
    )

    fun getSampleAdministrativeActivity(): List<AdministrativeActivityEvent> = listOf(
        AdministrativeActivityEvent(
            id = "act_001",
            actorEmail = "admin@rtc.org.za",
            category = "SECURITY",
            eventType = "MFA_POLICY_VERIFICATION",
            outcome = "SUCCESS",
            occurredAt = now.minus(1, ChronoUnit.HOURS).toString(),
            targetLabel = "Security Policy Engine",
            details = "Routine multi-factor authentication audit passed with zero anomalies.",
        ),
        AdministrativeActivityEvent(
            id = "act_002",
            actorEmail = "editor@rtc.org.za",
            category = "EDITORIAL",
            eventType = "NOTICE_PUBLISHED",
            outcome = "SUCCESS",
            occurredAt = now.minus(3, ChronoUnit.HOURS).toString(),
            targetLabel = "Notice #001 (Water Maintenance)",
            details = "Published and scheduled community alert dispatch.",
        ),
        AdministrativeActivityEvent(
            id = "act_003",
            actorEmail = "operations@rtc.org.za",
            category = "OPERATIONS",
            eventType = "WORK_ITEM_ASSIGNED",
            outcome = "SUCCESS",
            occurredAt = now.minus(6, ChronoUnit.HOURS).toString(),
            targetLabel = "Case #101 (Water Leak)",
            details = "Assigned to Ward 4 Water Depot Field Unit.",
        ),
    )

    fun getSampleOperationalIncidents(): List<OperationalIncident> = listOf(
        OperationalIncident(
            id = "inc_001",
            title = "Temporary SMS Notification Provider Latency",
            impactSummary = "One-time SMS verification codes experienced a 45-second delivery delay between 08:15 and 08:40.",
            severity = "LOW",
            state = "RESOLVED",
            openedAt = now.minus(1, ChronoUnit.DAYS).toString(),
            resolvedAt = now.minus(22, ChronoUnit.HOURS).toString(),
            closingSummary = "SMS gateway provider rerouted carrier traffic. Latency restored to sub-3 seconds.",
        ),
    )

    fun getSampleModerationQueue(): List<ModerationQueueItem> = listOf(
        ModerationQueueItem(
            reportId = "mod_rep_001",
            postId = "post_spam_01",
            reasonCode = "SPAM",
            reportDetail = "Repeated commercial advertising for unregulated cryptocurrency loan scheme.",
            reportState = "PENDING_REVIEW",
            reportedAt = now.minus(4, ChronoUnit.HOURS).toString(),
            postBody = "Earn guaranteed R5,000 daily from home with no experience! Click here to join our WhatsApp group.",
            postState = "FLAGGED",
            reportCount = 4,
            authorDisplayName = "Fast Cash Loans",
            isAutoLimited = true,
        ),
        ModerationQueueItem(
            reportId = "mod_rep_002",
            postId = "post_priv_02",
            reasonCode = "PRIVACY_CONCERN",
            reportDetail = "Resident posted neighbor's vehicle registration number and address in argument over street parking.",
            reportState = "PENDING_REVIEW",
            reportedAt = now.minus(8, ChronoUnit.HOURS).toString(),
            postBody = "Silver sedan CA 123-456 parked across 14 Oak Road driveway again. Calling tow truck.",
            postState = "ACTIVE",
            reportCount = 2,
            authorDisplayName = "Resident J",
            isAutoLimited = false,
        ),
    )

    fun getSampleModerationAppeals(): List<ModerationAppeal> = listOf(
        ModerationAppeal(
            appealId = "appeal_001",
            subjectType = "COMMUNITY_POST",
            subjectId = "post_garden_promo",
            reason = "My post sharing our community nursery seedling sale was incorrectly flagged as spam. We are a registered non-profit community project.",
            state = "PENDING",
            createdAt = now.minus(1, ChronoUnit.DAYS).toString(),
            postBody = "Organic spinach and cabbage seedlings available for R2 each at the community nursery this Saturday!",
        ),
    )

    fun getSampleEditorialNotices(): List<EditorialNoticeRecord> = listOf(
        EditorialNoticeRecord(
            id = "ed_notice_001",
            title = "Water Infrastructure Scheduled Pressure Maintenance",
            body = "Municipal water technicians will conduct scheduled isolation valve inspections at Reservoir 2 on Wednesday between 22:00 and 04:00.",
            category = "Infrastructure",
            status = "PUBLISHED",
            safetySensitive = false,
            createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
            publishedAt = now.minus(1, ChronoUnit.DAYS).toString(),
        ),
        EditorialNoticeRecord(
            id = "ed_notice_002",
            title = "Annual Integrated Development Plan (IDP) 2026 Public Participation",
            body = "Residents are invited to review the draft ward capital budget allocations.",
            category = "Civic",
            status = "PUBLISHED",
            safetySensitive = false,
            createdAt = now.minus(4, ChronoUnit.DAYS).toString(),
            publishedAt = now.minus(3, ChronoUnit.DAYS).toString(),
        ),
        EditorialNoticeRecord(
            id = "ed_notice_003",
            title = "Upcoming Ward 4 Townhall Agenda & Guest Speakers",
            body = "Draft agenda for next month's civic townhall meeting covering renewable solar power and local road maintenance.",
            category = "Civic",
            status = "UNDER_REVIEW",
            safetySensitive = false,
            createdAt = now.minus(12, ChronoUnit.HOURS).toString(),
        ),
    )

    fun getSampleAdminAnalyticsDashboard(): AdminAnalyticsDashboard = AdminAnalyticsDashboard(
        period = AdminAnalyticsPeriod.LAST_30_DAYS,
        metrics = listOf(
            AdminAnalyticsMetric("active_users", "Active Residents", 4820L),
            AdminAnalyticsMetric("community_posts", "Community Posts", 638L),
            AdminAnalyticsMetric("community_comments", "Community Comments", 2410L),
            AdminAnalyticsMetric("support_tickets_resolved", "Resolved Support Cases", 312L),
            AdminAnalyticsMetric("directory_searches", "Directory Searches", 8940L),
            AdminAnalyticsMetric("alerts_dispatched", "Official Alerts Sent", 24L),
        ),
    )

    fun getSampleAdminLocalities(): List<AdminLocalitySummary> = listOf(
        AdminLocalitySummary("ACTIVE_RESIDENTS", "Central Ward", 1840L),
        AdminLocalitySummary("ACTIVE_RESIDENTS", "West Ward", 1210L),
        AdminLocalitySummary("ACTIVE_RESIDENTS", "South Ward", 980L),
        AdminLocalitySummary("ACTIVE_RESIDENTS", "East Ward", 790L),
    )

    fun getSampleAdminAuditEvents(): List<AdminAuditTrailEvent> = listOf(
        AdminAuditTrailEvent(
            id = "aud_001",
            actorEmail = "admin@rtc.org.za",
            eventType = "ROLE_ASSIGNMENT",
            entityType = "USER_ACCOUNT",
            result = "SUCCESS",
            targetEmail = "staff.thabo@rtc.org.za",
            occurredAt = now.minus(2, ChronoUnit.HOURS).toString(),
            source = "ADMIN_CONSOLE",
            details = "Assigned CASE_STAFF role to Thabo Sithole.",
        ),
        AdminAuditTrailEvent(
            id = "aud_002",
            actorEmail = "editor@rtc.org.za",
            eventType = "NOTICE_DISPATCH",
            entityType = "COMMUNITY_ALERT",
            result = "SUCCESS",
            targetEmail = null,
            occurredAt = now.minus(6, ChronoUnit.HOURS).toString(),
            source = "EDITORIAL_PORTAL",
            details = "Dispatched Community Alert #001 to 1420 active devices.",
        ),
    )

    fun getSamplePublicSearchResults(query: String): List<PublicSearchResult> {
        val q = query.trim().lowercase()
        val allResults = listOf(
            PublicSearchResult("PROJECT", "proj_001", "Ward 4 Solar Microgrid Phase 2", "45kW clean solar and 120kWh battery storage bank supplying civic facilities.", "explore/projects/proj_001", now.minus(2, ChronoUnit.DAYS).toString(), 1),
            PublicSearchResult("PROJECT", "proj_002", "Central Community Clinic Expansion", "Modern consultation rooms and 24-hour maternal healthcare ward.", "explore/projects/proj_002", now.minus(5, ChronoUnit.DAYS).toString(), 2),
            PublicSearchResult("CENTRE", "centre_001", "RTC Central Civic & Community Centre", "Primary municipal service hub, auditorium, and resident advisory desks.", "explore/centres/centre_001", now.minus(4, ChronoUnit.DAYS).toString(), 3),
            PublicSearchResult("CENTRE", "centre_002", "Khanya Youth Tech & Innovation Hub", "Free community computer lab, study desks, and coding classrooms.", "explore/centres/centre_002", now.minus(6, ChronoUnit.DAYS).toString(), 4),
            PublicSearchResult("OPPORTUNITY", "opp_001", "Youth GIS & Municipal Data Analyst Intern", "6-month paid internship with the RTC Planning Department.", "explore/opportunities/opp_001", now.minus(1, ChronoUnit.DAYS).toString(), 5),
            PublicSearchResult("OPPORTUNITY", "opp_003", "Volunteer Coding Mentors for Youth Bootcamp", "Passionate programmers invited to mentor high school learners.", "explore/opportunities/opp_003", now.minus(4, ChronoUnit.DAYS).toString(), 6),
            PublicSearchResult("NOTICE", "notice_001", "Water Infrastructure Scheduled Pressure Maintenance", "Municipal water technicians conducting isolation valve inspections at Reservoir 2.", "home/notices/notice_001", now.minus(1, ChronoUnit.DAYS).toString(), 7),
            PublicSearchResult("HELP", "help_001", "How to Log and Track a Community Service Request", "Step-by-step guide to reporting potholes, water leaks, and street light faults.", "support/help/help_001", now.minus(14, ChronoUnit.DAYS).toString(), 8),
            PublicSearchResult("ALERT", "alert_001", "Ward 4 High-Speed Fibre & Public Wi-Fi Expansion", "Free community Wi-Fi hotspots activated at Civic Park.", "inbox/alert_001", now.minus(2, ChronoUnit.HOURS).toString(), 9),
        )

        return if (q.isBlank()) {
            allResults
        } else {
            allResults.filter { it.title.lowercase().contains(q) || it.summary.lowercase().contains(q) }
        }
    }
}
