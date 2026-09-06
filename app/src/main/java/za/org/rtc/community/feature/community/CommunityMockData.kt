package za.org.rtc.community.feature.community

import android.content.Context
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import za.org.rtc.community.R
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.core.MediaItem
import za.org.rtc.community.core.MediaKind
import za.org.rtc.community.core.MediaTargetType

object CommunityMockData {

    fun getSamplePosts(context: Context? = null): List<CommunityPost> {
        val now = Instant.now()
        val pkg = context?.packageName ?: "za.org.rtc.community"
        val gardenUri = "android.resource://$pkg/${R.drawable.community_park_garden}"
        val solarUri = "android.resource://$pkg/${R.drawable.community_solar_hub}"

        val gardenMedia = listOf(
            MediaItem(
                id = "mock_media_garden",
                targetType = MediaTargetType.COMMUNITY_POST,
                targetId = "mock_post_garden",
                storagePath = "community/garden.jpg",
                kind = MediaKind.IMAGE,
                mimeType = "image/jpeg",
                byteSize = 1256834L,
                position = 0,
                caption = "Community garden spring planting harvest and seedling exchange",
                signedUrl = gardenUri,
            )
        )

        val solarMedia = listOf(
            MediaItem(
                id = "mock_media_solar",
                targetType = MediaTargetType.COMMUNITY_POST,
                targetId = "mock_post_solar",
                storagePath = "community/solar.jpg",
                kind = MediaKind.IMAGE,
                mimeType = "image/jpeg",
                byteSize = 985243L,
                position = 0,
                caption = "Rooftop solar energy system powering the RTC youth tech lab",
                signedUrl = solarUri,
            )
        )

        return listOf(
            CommunityPost(
                id = "mock_post_garden",
                author = "Thandi Khumalo",
                handle = "@thandi_rtc",
                content = "🌱 Spring Harvest & Community Planting Day! We have set up 8 new organic raised garden beds today. Fresh spinach, herbs, and heirloom tomatoes are in the ground! Come visit us this Saturday at 09:00 for the community seed exchange.",
                category = "Environment & Gardening",
                createdAt = now.minus(2, ChronoUnit.HOURS).toString(),
                reactions = 24,
                comments = 3,
                viewerHasLiked = false,
                trendingScore = 92,
                isFollowedTopic = true,
                hasMedia = true,
                media = gardenMedia,
                isOfficial = false,
                authorId = "author_thandi",
            ),
            CommunityPost(
                id = "mock_post_solar",
                author = "RTC Sustainability Team",
                handle = "@rtc_green",
                content = "☀️⚡ Clean Energy Milestone: New 15kW Rooftop Solar Installation is officially live at our Community Hub! Powering the digital tech laboratory and after-school coding classrooms with 100% renewable energy.",
                category = "Infrastructure",
                createdAt = now.minus(5, ChronoUnit.HOURS).toString(),
                reactions = 42,
                comments = 2,
                viewerHasLiked = true,
                trendingScore = 88,
                isFollowedTopic = true,
                hasMedia = true,
                media = solarMedia,
                isOfficial = true,
                authorId = "author_rtc_official",
            ),
            CommunityPost(
                id = "mock_post_coding",
                author = "Sipho Ndlovu",
                handle = "@sipho_dev",
                content = "🚀 Registrations are now open for the Term 2 Youth Coding & Robotics Bootcamp! Laptops and mentors are provided free of charge for learners in Grades 8-12 every Tuesday and Thursday afternoon.",
                category = "Youth & Education",
                createdAt = now.minus(1, ChronoUnit.DAYS).toString(),
                reactions = 19,
                comments = 3,
                viewerHasLiked = false,
                trendingScore = 75,
                isFollowedTopic = false,
                hasMedia = false,
                media = emptyList(),
                isOfficial = false,
                authorId = "author_sipho",
            ),
            CommunityPost(
                id = "mock_post_cleanup",
                author = "Maria Van Zyl",
                handle = "@maria_vz",
                content = "🙌 A huge thank you to all 45 neighborhood volunteers who joined our Riverwalk Clean-Up drive yesterday! We collected over 60 bags of recyclables and restored the walking trails for the whole community.",
                category = "Community Life",
                createdAt = now.minus(2, ChronoUnit.DAYS).toString(),
                reactions = 35,
                comments = 2,
                viewerHasLiked = false,
                trendingScore = 64,
                isFollowedTopic = false,
                hasMedia = false,
                media = emptyList(),
                isOfficial = false,
                authorId = "author_maria",
            )
        )
    }

    fun getSampleComments(postId: String): List<CommunityComment> {
        val now = Instant.now()
        return when (postId) {
            "mock_post_garden" -> listOf(
                CommunityComment(
                    id = "comment_garden_1",
                    postId = postId,
                    authorId = "author_david",
                    author = "David Sithole",
                    handle = "@davids",
                    content = "Wonderful initiative! Is there rainwater irrigation available or should we bring watering cans?",
                    createdAt = now.minus(100, ChronoUnit.MINUTES).toString(),
                ),
                CommunityComment(
                    id = "comment_garden_2",
                    postId = postId,
                    authorId = "author_rtc_staff",
                    author = "RTC Coordinator",
                    handle = "@rtc_staff",
                    content = "Hi David! Yes, the 5,000L rainwater catchment tanks are fully plumbed and we have hoses ready for everyone.",
                    createdAt = now.minus(85, ChronoUnit.MINUTES).toString(),
                    isStaff = true,
                ),
                CommunityComment(
                    id = "comment_garden_3",
                    postId = postId,
                    authorId = "author_lerato",
                    author = "Lerato Mokoena",
                    handle = "@lerato_m",
                    content = "I will bring indigenous herbs and rosemary cuttings to share with the neighborhood.",
                    createdAt = now.minus(45, ChronoUnit.MINUTES).toString(),
                ),
            )
            "mock_post_solar" -> listOf(
                CommunityComment(
                    id = "comment_solar_1",
                    postId = postId,
                    authorId = "author_jason",
                    author = "Jason Adams",
                    handle = "@jason_a",
                    content = "Incredible milestone! Will there be a public energy monitor screen in the community foyer?",
                    createdAt = now.minus(4, ChronoUnit.HOURS).toString(),
                ),
                CommunityComment(
                    id = "comment_solar_2",
                    postId = postId,
                    authorId = "author_rtc_staff",
                    author = "RTC Engineering",
                    handle = "@rtc_engineering",
                    content = "Yes Jason! The live telemetry screen is installed by the reception area showing real-time kW production.",
                    createdAt = now.minus(3, ChronoUnit.HOURS).toString(),
                    isStaff = true,
                ),
            )
            "mock_post_coding" -> listOf(
                CommunityComment(
                    id = "comment_coding_1",
                    postId = postId,
                    authorId = "author_nomsa",
                    author = "Nomsa Dlamini",
                    handle = "@nomsad",
                    content = "My son attended last year's workshop and it completely sparked his interest in software engineering.",
                    createdAt = now.minus(20, ChronoUnit.HOURS).toString(),
                ),
                CommunityComment(
                    id = "comment_coding_2",
                    postId = postId,
                    authorId = "author_kevin",
                    author = "Kevin Patel",
                    handle = "@kevin_p",
                    content = "Can university computer science students volunteer as teaching assistants?",
                    createdAt = now.minus(18, ChronoUnit.HOURS).toString(),
                ),
                CommunityComment(
                    id = "comment_coding_3",
                    postId = postId,
                    authorId = "author_rtc_staff",
                    author = "RTC Youth Lead",
                    handle = "@rtc_youth",
                    content = "Absolutely Kevin! Please reach out to youth@rtc.org.za or visit the centre office.",
                    createdAt = now.minus(16, ChronoUnit.HOURS).toString(),
                    isStaff = true,
                ),
            )
            else -> listOf(
                CommunityComment(
                    id = "comment_${UUID.randomUUID()}",
                    postId = postId,
                    authorId = "author_community_member",
                    author = "Community Member",
                    handle = "@community",
                    content = "Thank you for sharing this update with our neighborhood!",
                    createdAt = now.minus(30, ChronoUnit.MINUTES).toString(),
                )
            )
        }
    }
}
