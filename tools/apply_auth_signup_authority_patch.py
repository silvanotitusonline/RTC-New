from pathlib import Path

path = Path("app/src/main/java/za/org/rtc/community/data/RtcRepository.kt")
source = path.read_text()
start = source.index("    suspend fun signUpWithEmail")
end = source.index("\n    /** The result is intentionally generic", start)
replacement = '''    suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<Unit> = runCatching {
        val cleanEmail = email.trim()
        val cleanDisplayName = displayName.trim()
        require(cleanDisplayName.isNotEmpty()) { "Enter your name to create an account." }
        requireStrongPassword(password)

        // Supabase remains authoritative for account creation. With email confirmation enabled,
        // a successful sign-up is confirmation-pending and must not manufacture a local
        // authenticated session before Supabase establishes one on a later sign-in.
        supabase.auth.signUpWith(Email, "rtc://community") {
            this.email = cleanEmail
            this.password = password
            data = buildJsonObject { put("full_name", cleanDisplayName) }
        }
    }
'''
path.write_text(source[:start] + replacement + source[end:])
