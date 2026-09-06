with open("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt", "r") as f:
    content = f.read()

# Add fadeIn and fadeOut imports
if "import androidx.compose.animation.fadeIn" not in content:
    import_idx = content.find("import androidx.compose.animation.")
    if import_idx != -1:
        content = content[:import_idx] + "import androidx.compose.animation.fadeIn\nimport androidx.compose.animation.fadeOut\n" + content[import_idx:]

original_transitions = """        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(400)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(400),
                targetOffset = { it / 3 } // Background slides slower (parallax)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(400),
                initialOffset = { it / 3 } // Background slides in slower (parallax)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(400)
            )
        }"""

new_transitions = """        enterTransition = {
            if (targetState.destination.route?.startsWith("community_post/") == true) {
                fadeIn(animationSpec = tween(300))
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(400)
                )
            }
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("community_post/") == true) {
                fadeOut(animationSpec = tween(300))
            } else {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(400),
                    targetOffset = { it / 3 } // Background slides slower (parallax)
                )
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("community_post/") == true) {
                fadeIn(animationSpec = tween(300))
            } else {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(400),
                    initialOffset = { it / 3 } // Background slides in slower (parallax)
                )
            }
        },
        popExitTransition = {
            if (initialState.destination.route?.startsWith("community_post/") == true) {
                fadeOut(animationSpec = tween(300))
            } else {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(400)
                )
            }
        }"""

if original_transitions in content:
    content = content.replace(original_transitions, new_transitions)
else:
    print("Could not find original transitions block.")

with open("app/src/main/java/za/org/rtc/community/ui/navigation/RtcCommunityNavGraph.kt", "w") as f:
    f.write(content)

