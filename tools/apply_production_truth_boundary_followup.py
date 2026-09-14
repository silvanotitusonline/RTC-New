from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RTC = ROOT / "app/src/main/java/za/org/rtc/community/data/RtcRepository.kt"


def replace_between(text: str, start: str, end: str, replacement: str) -> str:
    i = text.find(start)
    if i < 0:
        raise RuntimeError(f"missing start marker: {start}")
    j = text.find(end, i)
    if j < 0:
        raise RuntimeError(f"missing end marker after {start}: {end}")
    return text[:i] + replacement.rstrip() + "\n\n" + text[j:]


rtc = RTC.read_text(encoding="utf-8")
rtc = rtc.replace("import za.org.rtc.community.data.local.CachedReportEntity\n", "")

rtc = replace_between(
    rtc,
    "    suspend fun toggleEventRsvp",
    "    private val _session",
    '''    suspend fun toggleEventRsvp(eventId: String): Result<Unit> =
        communityEventsRepository.toggleRsvp(eventId)''',
)

rtc = replace_between(
    rtc,
    "    suspend fun updateProfile",
    "    suspend fun setNotificationPreference",
    '''    suspend fun updateProfile(displayName: String, bio: String, interests: List<String>): Result<Unit> = runCatching {
        val cleanName = displayName.trim()
        val cleanBio = bio.trim()
        val cleanInterests = interests.map(String::trim).filter(String::isNotBlank).distinct().take(8)
        require(cleanName.length in 2..120) { "Enter a display name between 2 and 120 characters." }
        require(cleanBio.length <= 600) { "Keep the bio to 600 characters or fewer." }

        when (_session.value.authority) {
            SessionAuthority.SUPABASE_AUTH -> {
                productionUxRepository.saveOwnProfile(cleanName, cleanBio, cleanInterests).getOrThrow()
                hydrateSupabaseSession()
                refreshLiveContent()
                _communityPostDetail.value?.id?.let { postId -> loadCommunityPostDetail(postId) }
            }
            SessionAuthority.DEVELOPMENT_ADAPTER -> {
                val currentSession = _session.value
                database.cachedUserProfileDao().insertProfile(
                    CachedUserProfileEntity(
                        userId = currentSession.id,
                        email = currentSession.authenticatedEmail,
                        displayName = cleanName,
                        bio = cleanBio,
                        interestsJson = cleanInterests.joinToString(","),
                        avatarUrl = currentSession.avatarUrl,
                        role = currentSession.role.name,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    )
                )
                _session.value = currentSession.copy(
                    displayName = cleanName,
                    bio = cleanBio,
                    interests = cleanInterests,
                )
            }
            SessionAuthority.PUBLIC -> error("Sign in before changing your profile.")
        }
    }''',
)

rtc = replace_between(
    rtc,
    "    suspend fun reportCommunityPost",
    "    suspend fun registerFcmDevice",
    '''    suspend fun reportCommunityPost(postId: String, reason: ModerationReason, detail: String): Result<Unit> {
        require(_session.value.authority == SessionAuthority.SUPABASE_AUTH) { "Sign in before reporting Community content." }
        return productionUxRepository.reportCommunityPost(postId, reason, detail)
    }''',
)

for forbidden in (
    "RtcMockData",
    "CommunityMockData",
    "SampleCommunityEvents",
    "report_${UUID.randomUUID()}",
):
    if forbidden in rtc:
        raise RuntimeError(f"forbidden production truth token remains in RtcRepository: {forbidden}")

RTC.write_text(rtc, encoding="utf-8")
