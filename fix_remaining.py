with open("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceBusinessScreen.kt", "r") as f:
    content = f.read()

content = content.replace("AsyncImage(\n                                                        model = photoUrl", "MarketplaceProgressiveImage(\n                                                        url = photoUrl")

with open("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceBusinessScreen.kt", "w") as f:
    f.write(content)
