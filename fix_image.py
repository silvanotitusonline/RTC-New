import re

def replace_progressive_image(file_path):
    with open(file_path, "r") as f:
        content = f.read()

    # Add imports
    import_statement = """import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
"""
    if "import coil.compose.SubcomposeAsyncImage" not in content:
        import_idx = content.find("import coil.compose.AsyncImage")
        content = content[:import_idx] + import_statement + content[import_idx:]

    helper_code = """
@Composable
fun MarketplaceProgressiveImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    thumbnailWidth: Int = 200
) {
    val context = LocalContext.current
    val thumbnailUrl = remember(url, thumbnailWidth) {
        url?.replace("/object/sign/", "/render/image/sign/")
            ?.replace("/object/public/", "/render/image/public/")
            ?.let {
                if (it.contains("?")) "$it&width=$thumbnailWidth&quality=50"
                else "$it?width=$thumbnailWidth&quality=50"
            } ?: url
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        },
        error = {
            // fallback gracefully
        }
    )
}
"""

    if "fun MarketplaceProgressiveImage(" not in content and "MarketplaceComponents" in file_path:
        content += helper_code

    with open(file_path, "w") as f:
        f.write(content)

replace_progressive_image("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceComponents.kt")
replace_progressive_image("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceBusinessScreen.kt")
