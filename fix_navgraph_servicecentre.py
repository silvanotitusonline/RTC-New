with open("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt", "r") as f:
    content = f.read()

# Update enterTransition
old_enter = """        enterTransition = {
            if (targetState.destination.route?.startsWith("community_post/") == true) {
                fadeIn(animationSpec = tween(300))
            } else {"""
new_enter = """        enterTransition = {
            if (targetState.destination.route?.startsWith("community_post/") == true ||
                targetState.destination.route?.startsWith("service_booking/") == true) {
                fadeIn(animationSpec = tween(300))
            } else {"""
content = content.replace(old_enter, new_enter)

# Update exitTransition
old_exit = """        exitTransition = {
            if (targetState.destination.route?.startsWith("community_post/") == true) {
                fadeOut(animationSpec = tween(300))
            } else {"""
new_exit = """        exitTransition = {
            if (targetState.destination.route?.startsWith("community_post/") == true ||
                targetState.destination.route?.startsWith("service_booking/") == true) {
                fadeOut(animationSpec = tween(300))
            } else {"""
content = content.replace(old_exit, new_exit)

# Update popEnterTransition
old_pop_enter = """        popEnterTransition = {
            if (initialState.destination.route?.startsWith("community_post/") == true) {
                fadeIn(animationSpec = tween(300))
            } else {"""
new_pop_enter = """        popEnterTransition = {
            if (initialState.destination.route?.startsWith("community_post/") == true ||
                initialState.destination.route?.startsWith("service_booking/") == true) {
                fadeIn(animationSpec = tween(300))
            } else {"""
content = content.replace(old_pop_enter, new_pop_enter)

# Update popExitTransition
old_pop_exit = """        popExitTransition = {
            if (initialState.destination.route?.startsWith("community_post/") == true) {
                fadeOut(animationSpec = tween(300))
            } else {"""
new_pop_exit = """        popExitTransition = {
            if (initialState.destination.route?.startsWith("community_post/") == true ||
                initialState.destination.route?.startsWith("service_booking/") == true) {
                fadeOut(animationSpec = tween(300))
            } else {"""
content = content.replace(old_pop_exit, new_pop_exit)

with open("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt", "w") as f:
    f.write(content)
