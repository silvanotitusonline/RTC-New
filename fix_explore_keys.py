with open("app/src/main/java/za/org/rtc/community/feature/explore/ExploreScreen.kt", "r") as f:
    content = f.read()

content = content.replace(
    "items(categories, key = { it.title }) { category ->",
    "items(categories, key = { it }) { category ->"
)

with open("app/src/main/java/za/org/rtc/community/feature/explore/ExploreScreen.kt", "w") as f:
    f.write(content)
