import re

def replace_async_image(file_path):
    with open(file_path, "r") as f:
        content = f.read()

    # We need to replace AsyncImage( with MarketplaceProgressiveImage(
    # but only for those that use `model = ...`
    # Let's just use regex or manual replace for the specific blocks.
    
    content = content.replace("AsyncImage(\n                                        model = photoUrl", "MarketplaceProgressiveImage(\n                                        url = photoUrl")
    content = content.replace("AsyncImage(\n                        model = heroImageUrl,", "MarketplaceProgressiveImage(\n                        url = heroImageUrl,")
    content = content.replace("AsyncImage(\n                model = logoUrl,", "MarketplaceProgressiveImage(\n                url = logoUrl,")
    content = content.replace("AsyncImage(\n                                                    model = photoUrl,", "MarketplaceProgressiveImage(\n                                                    url = photoUrl,")
    content = content.replace("AsyncImage(\n                        model = photoUrl,", "MarketplaceProgressiveImage(\n                        url = photoUrl,")
    content = content.replace("AsyncImage(\n                            model = photoUrl,", "MarketplaceProgressiveImage(\n                            url = photoUrl,")
    content = content.replace("AsyncImage(\n                    model = photos.getOrNull(currentIndex),", "MarketplaceProgressiveImage(\n                    url = photos.getOrNull(currentIndex),")

    with open(file_path, "w") as f:
        f.write(content)

replace_async_image("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceComponents.kt")
replace_async_image("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceBusinessScreen.kt")
