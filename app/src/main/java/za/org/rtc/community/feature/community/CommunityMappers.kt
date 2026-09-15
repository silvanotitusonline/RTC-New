package za.org.rtc.community.feature.community

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost
import za.org.rtc.community.core.MediaItem
import za.org.rtc.community.core.MediaKind
import za.org.rtc.community.core.MediaTargetType
import za.org.rtc.community.data.local.CachedCommentEntity
import za.org.rtc.community.data.local.CachedPostEntity

fun serializeMediaList(media: List<MediaItem>): String? {
    if (media.isEmpty()) return null
    return buildJsonArray {
        media.forEach { item ->
            add(buildJsonObject {
                put("id", item.id)
                put("targetType", item.targetType.name)
                put("targetId", item.targetId)
                put("storagePath", item.storagePath)
                put("kind", item.kind.name)
                put("mimeType", item.mimeType)
                put("byteSize", item.byteSize)
                item.width?.let { put("width", it) }
                item.height?.let { put("height", it) }
                item.durationSeconds?.let { put("durationSeconds", it) }
                put("position", item.position)
                item.caption?.let { put("caption", it) }
                item.signedUrl?.let { put("signedUrl", it) }
            })
        }
    }.toString()
}

fun deserializeMediaList(mediaJson: String?): List<MediaItem> {
    if (mediaJson.isNullOrBlank()) return emptyList()
    return runCatching {
        val element = Json.parseToJsonElement(mediaJson)
        if (element !is JsonArray) return emptyList()
        element.mapNotNull { jsonItem ->
            if (jsonItem !is JsonObject) return@mapNotNull null
            val id = jsonItem["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val targetTypeStr = jsonItem["targetType"]?.jsonPrimitive?.content ?: MediaTargetType.COMMUNITY_POST.name
            val targetType = runCatching { MediaTargetType.valueOf(targetTypeStr) }.getOrDefault(MediaTargetType.COMMUNITY_POST)
            val targetId = jsonItem["targetId"]?.jsonPrimitive?.content ?: ""
            val storagePath = jsonItem["storagePath"]?.jsonPrimitive?.content ?: ""
            val kindStr = jsonItem["kind"]?.jsonPrimitive?.content ?: MediaKind.IMAGE.name
            val kind = runCatching { MediaKind.valueOf(kindStr) }.getOrDefault(MediaKind.IMAGE)
            val mimeType = jsonItem["mimeType"]?.jsonPrimitive?.content ?: "image/jpeg"
            val byteSize = jsonItem["byteSize"]?.jsonPrimitive?.longOrNull ?: 1024L
            val width = jsonItem["width"]?.jsonPrimitive?.intOrNull
            val height = jsonItem["height"]?.jsonPrimitive?.intOrNull
            val durationSeconds = jsonItem["durationSeconds"]?.jsonPrimitive?.intOrNull
            val position = jsonItem["position"]?.jsonPrimitive?.intOrNull ?: 0
            val caption = jsonItem["caption"]?.jsonPrimitive?.contentOrNull
            val signedUrl = jsonItem["signedUrl"]?.jsonPrimitive?.contentOrNull
            MediaItem(
                id = id,
                targetType = targetType,
                targetId = targetId,
                storagePath = storagePath,
                kind = kind,
                mimeType = mimeType,
                byteSize = byteSize,
                width = width,
                height = height,
                durationSeconds = durationSeconds,
                position = position,
                caption = caption,
                signedUrl = signedUrl,
            )
        }
    }.getOrDefault(emptyList())
}

fun CommunityPost.toCachedEntity(): CachedPostEntity = CachedPostEntity(
    id = id,
    author = author,
    handle = handle,
    content = content,
    category = category,
    createdAt = createdAt,
    reactions = reactions,
    comments = comments,
    viewerHasLiked = viewerHasLiked,
    userReactionsJson = if (userReactions.isEmpty()) null else userReactions.joinToString(","),
    reactionCountsJson = if (reactionCounts.isEmpty()) null else reactionCounts.entries.joinToString(";") { "${it.key}:${it.value}" },
    trendingScore = trendingScore,
    isOfficial = isOfficial,
    authorId = authorId,
    authorAvatarUrl = authorAvatarUrl,
    mediaJson = serializeMediaList(media),
    createdAtEpochMillis = try { Instant.parse(createdAt).toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() },
    repostOfId = repostOfId,
    quotePostId = quotePostId,
    repostCount = repostCount,
    bookmarkCount = bookmarkCount,
    isRepostedByViewer = isRepostedByViewer,
    isBookmarkedByViewer = isBookmarkedByViewer,
    isPendingSync = isPendingSync,
)

fun CachedPostEntity.toCommunityPost(): CommunityPost {
    val reactionsSet = userReactionsJson?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    val countsMap = reactionCountsJson?.split(";")?.mapNotNull { entry ->
        val parts = entry.split(":")
        if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
    }?.toMap() ?: emptyMap()
    val mediaList = deserializeMediaList(mediaJson)

    return CommunityPost(
        id = id,
        author = author,
        handle = handle,
        content = content,
        category = category,
        createdAt = createdAt,
        reactions = reactions,
        comments = comments,
        viewerHasLiked = viewerHasLiked,
        userReactions = reactionsSet,
        reactionCounts = countsMap,
        trendingScore = trendingScore,
        isFollowedTopic = true,
        hasMedia = mediaList.isNotEmpty(),
        media = mediaList,
        isOfficial = isOfficial,
        authorId = authorId ?: "",
        authorAvatarUrl = authorAvatarUrl,
        repostOfId = repostOfId,
        quotePostId = quotePostId,
        repostCount = repostCount,
        bookmarkCount = bookmarkCount,
        isRepostedByViewer = isRepostedByViewer,
        isBookmarkedByViewer = isBookmarkedByViewer,
        isPendingSync = isPendingSync,
    )
}

fun CommunityComment.toCachedEntity(): CachedCommentEntity = CachedCommentEntity(
    id = id,
    postId = postId,
    authorId = authorId,
    author = author,
    handle = handle,
    content = content,
    createdAt = createdAt,
    authorAvatarUrl = authorAvatarUrl,
    createdAtEpochMillis = try { Instant.parse(createdAt).toEpochMilli() } catch (e: Exception) { System.currentTimeMillis() },
    parentId = parentId,
    replyCount = replyCount,
    depth = depth,
)

fun CachedCommentEntity.toCommunityComment(): CommunityComment = CommunityComment(
    id = id,
    postId = postId,
    authorId = authorId,
    author = author,
    handle = handle,
    authorAvatarUrl = authorAvatarUrl,
    content = content,
    createdAt = createdAt,
    isStaff = false,
    parentId = parentId,
    replyCount = replyCount,
    depth = depth,
)
