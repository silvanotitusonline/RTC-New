import re

# 1. Update CommunityPostCard.kt
with open("app/src/main/java/za/org/rtc/community/feature/community/CommunityPostCard.kt", "r") as f:
    card_content = f.read()

card_content = card_content.replace("SharedTransitionScope.ResizeMode.ScaleToBounds()", "SharedTransitionScope.ResizeMode.RemeasureToBounds")
card_content = card_content.replace("SharedTransitionScope.ResizeMode.ScaleToBounds", "SharedTransitionScope.ResizeMode.RemeasureToBounds")

with open("app/src/main/java/za/org/rtc/community/feature/community/CommunityPostCard.kt", "w") as f:
    f.write(card_content)

# 2. Update CommunityPostDetailScreen.kt
with open("app/src/main/java/za/org/rtc/community/feature/community/CommunityPostDetailScreen.kt", "r") as f:
    content = f.read()

# Add imports if missing
if "import androidx.compose.foundation.background" not in content:
    import_idx = content.find("import androidx.compose.foundation.")
    if import_idx != -1:
        imports_to_add = "import androidx.compose.foundation.background\nimport androidx.compose.foundation.shape.CircleShape\nimport androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.foundation.layout.height\n"
        content = content[:import_idx] + imports_to_add + content[import_idx:]

start_str = "    if (activePost == null) {"
start_idx = content.find(start_str)
end_str = "    LazyColumn(contentPadding"
end_idx = content.find(end_str)

if start_idx != -1 and end_idx != -1:
    head = content[:start_idx]
    tail = content[end_idx:]
    
    replacement = """
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "post_card_${postId}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spring(stiffness = Spring.StiffnessMediumLow) },
                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                renderInOverlayDuringTransition = false,
                zIndexInOverlay = 1f,
            ).skipToLookaheadSize()
        }
    } else {
        Modifier
    }

    if (activePost == null) {
        if (detailState.isLoading || postId.isNotBlank()) {
            if (detailState.message == null) {
                LazyColumn(contentPadding = PaddingValues(RtcSpacing.standard), verticalArrangement = Arrangement.spacedBy(RtcSpacing.small)) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth().then(sharedModifier)) {
                            Column(modifier = Modifier.padding(RtcSpacing.small), verticalArrangement = Arrangement.spacedBy(RtcSpacing.compact)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(RtcSize.avatarStandard).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                                    Spacer(Modifier.width(RtcSpacing.compact))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Box(modifier = Modifier.size(width = 120.dp, height = 16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                                        Spacer(Modifier.height(4.dp))
                                        Box(modifier = Modifier.size(width = 80.dp, height = 12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                                    }
                                }
                                Spacer(Modifier.height(RtcSpacing.compact))
                                Box(modifier = Modifier.fillMaxWidth().height(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                                Box(modifier = Modifier.fillMaxWidth().height(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                                Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                                Spacer(Modifier.height(RtcSpacing.compact))
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Box(modifier = Modifier.size(width = 40.dp, height = 24.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                                    Box(modifier = Modifier.size(width = 40.dp, height = 24.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                                    Box(modifier = Modifier.size(width = 40.dp, height = 24.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(RtcSpacing.small))
                        Box(modifier = Modifier.size(width = 100.dp, height = 24.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                    }
                    items(3) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(RtcSpacing.small), verticalAlignment = Alignment.Top) {
                                Box(modifier = Modifier.size(RtcSize.avatarCompact).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
                                Spacer(Modifier.width(RtcSpacing.compact))
                                Column(modifier = Modifier.weight(1f)) {
                                    Box(modifier = Modifier.size(width = 100.dp, height = 14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                                    Spacer(Modifier.height(4.dp))
                                    Box(modifier = Modifier.fillMaxWidth().height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                                    Spacer(Modifier.height(4.dp))
                                    Box(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PurposefulEmptyState(detailState.message ?: "Community conversation unavailable.", "Retry") {
                        communityViewModel.loadPostDetail(postId)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PurposefulEmptyState("This Community post is no longer available.", "Return to Community", {})
            }
        }
        return
    }

"""
    with open("app/src/main/java/za/org/rtc/community/feature/community/CommunityPostDetailScreen.kt", "w") as f:
        f.write(head + replacement[1:] + tail)
