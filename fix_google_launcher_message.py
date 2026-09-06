import re

with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "r") as f:
    content = f.read()

pattern = r'coroutineScope\.launch \{ snackbarHostState\.showSnackbar\("Google sign-in failed\. Error Code: \$\{e\.statusCode\}"\) \}'
replacement = """val errorMsg = if (e.statusCode == 10) "Code 10: App SHA-1 fingerprint not registered in Google Cloud Console" else "Google sign-in failed. Error Code: ${e.statusCode}"
                coroutineScope.launch { snackbarHostState.showSnackbar(errorMsg) }"""

content = re.sub(pattern, replacement, content)

with open("app/src/main/java/za/org/rtc/community/ui/auth/SignInScreen.kt", "w") as f:
    f.write(content)
