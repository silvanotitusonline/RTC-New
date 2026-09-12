import re

with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "r") as f:
    content = f.read()

# I want to replace `coroutineScope.launch { snackbarHostState.showSnackbar("No ID token found in Google Sign-In response.")`
# with `coroutineScope.launch { snackbarHostState.showSnackbar("No ID token found in Google Sign-In response.") }`
content = re.sub(r'(coroutineScope\.launch\s*\{\s*snackbarHostState\.showSnackbar\([^)]*\))', r'\1 }', content)

with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "w") as f:
    f.write(content)
