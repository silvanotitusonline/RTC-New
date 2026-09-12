import re

# MarketplaceHomeScreen
with open("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceHomeScreen.kt", "r") as f:
    content = f.read()

content = content.replace(
    "items(categories) { (categoryName, icon) ->",
    "items(categories, key = { it.first }) { (categoryName, icon) ->"
)

with open("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceHomeScreen.kt", "w") as f:
    f.write(content)

# MarketplaceComponents
with open("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceComponents.kt", "r") as f:
    content = f.read()

content = content.replace(
    "items(attachedPhotos) { photoUrl ->",
    "items(attachedPhotos, key = { it }) { photoUrl ->"
)

with open("app/src/main/java/za/org/rtc/community/feature/marketplace/presentation/MarketplaceComponents.kt", "w") as f:
    f.write(content)

# ExploreScreen
with open("app/src/main/java/za/org/rtc/community/feature/explore/ExploreScreen.kt", "r") as f:
    content = f.read()

content = content.replace(
    "items(categories) { category ->",
    "items(categories, key = { it.title }) { category ->"
)

with open("app/src/main/java/za/org/rtc/community/feature/explore/ExploreScreen.kt", "w") as f:
    f.write(content)

# InteractiveMunicipalCanvasMap
with open("app/src/main/java/za/org/rtc/community/feature/explore/InteractiveMunicipalCanvasMap.kt", "r") as f:
    content = f.read()

content = content.replace(
    "items(MapLayerFilter.entries) { filter ->",
    "items(MapLayerFilter.entries, key = { it.name }) { filter ->"
)

with open("app/src/main/java/za/org/rtc/community/feature/explore/InteractiveMunicipalCanvasMap.kt", "w") as f:
    f.write(content)

# ServiceCentreBookingDetailScreen
with open("app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreBookingDetailScreen.kt", "r") as f:
    content = f.read()

content = content.replace(
    "items(quickPrompts) { prompt ->",
    "items(quickPrompts, key = { it }) { prompt ->"
)

with open("app/src/main/java/za/org/rtc/community/feature/servicecentre/presentation/ServiceCentreBookingDetailScreen.kt", "w") as f:
    f.write(content)
