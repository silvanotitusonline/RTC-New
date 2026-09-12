with open("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceComponents.kt", "r") as f:
    content = f.read()

content = content.replace("imageLoader = loader,\n        imageLoader = loader,", "imageLoader = loader,")

with open("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceComponents.kt", "w") as f:
    f.write(content)
