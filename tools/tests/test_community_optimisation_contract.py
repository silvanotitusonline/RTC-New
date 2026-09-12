from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMMUNITY = ROOT / "app/src/main/java/za/org/rtc/community/feature/community"
CORE_MEDIA = ROOT / "app/src/main/java/za/org/rtc/community/core/media"


class CommunityOptimisationContractTest(unittest.TestCase):
    def read(self, name: str) -> str:
        return (COMMUNITY / name).read_text(encoding="utf-8")

    def test_feed_is_bounded_and_community_owned(self):
        state = self.read("CommunityFeedState.kt")
        vm = self.read("CommunityViewModel.kt")
        repo = self.read("SupabaseCommunityRepository.kt")
        screen = self.read("CommunityFeedScreen.kt")
        self.assertIn("COMMUNITY_FEED_PAGE_SIZE = 20", state)
        self.assertIn("CommunityCursor", state)
        self.assertIn("community_post_page_v2", repo)
        self.assertIn("pendingLikeIds", vm)
        self.assertNotIn("refreshLiveContent()", vm)
        self.assertIn("communityViewModel::loadNextPage", screen)

    def test_signed_urls_are_bounded_expiry_aware_and_shared(self):
        cache = (CORE_MEDIA / "SignedUrlCache.kt").read_text(encoding="utf-8")
        compatibility = self.read("SignedUrlCache.kt")
        repo = self.read("SupabaseCommunityRepository.kt")
        media = self.read("CommunityMedia.kt")
        self.assertIn("LinkedHashMap", cache)
        self.assertIn("refreshSkew", cache)
        self.assertIn("inFlight", cache)
        self.assertIn("za.org.rtc.community.core.media.SignedUrlCache", compatibility)
        self.assertIn("za.org.rtc.community.core.media.SignedUrlValue", compatibility)
        self.assertIn("SIGNED_URL_CACHE_CAPACITY", repo)
        self.assertIn("refreshMediaUrl(mediaId", repo)
        self.assertIn("automaticRefreshAttempted", media)
        self.assertIn("Retry", media)

    def test_composer_and_media_preparation_are_recoverable_and_shared(self):
        composer = self.read("CommunityComposerState.kt")
        sheet = self.read("PostComposer.kt")
        preparation = (CORE_MEDIA / "MediaPreparation.kt").read_text(encoding="utf-8")
        compatibility = (ROOT / "app/src/main/java/za/org/rtc/community/data/local/MediaPreparation.kt").read_text(encoding="utf-8")
        production = (ROOT / "app/src/main/java/za/org/rtc/community/supabase/ProductionUxRepository.kt").read_text(encoding="utf-8")
        self.assertIn("CommunityUploadPhase.FAILED", composer)
        self.assertIn("removeAttachment", composer)
        self.assertIn("Selected media", sheet)
        self.assertIn("Dispatchers.IO", preparation)
        self.assertIn("za.org.rtc.community.core.media.MediaPreparation", compatibility)
        self.assertIn("forDraftForOwner", production)
        self.assertIn('state = "RETRY"', production)

    def test_detail_mutations_are_scoped(self):
        vm = self.read("CommunityViewModel.kt")
        detail = self.read("CommunityPostDetailScreen.kt")
        self.assertIn("CommunityDetailState", vm)
        self.assertIn("refreshDetailAfterMutation", vm)
        self.assertNotIn("refreshLiveContent()", vm)
        self.assertIn("communityViewModel.createComment", detail)
        self.assertIn("communityViewModel.updateComment", detail)
        self.assertIn("communityViewModel.deleteComment", detail)
        self.assertIn("communityViewModel.toggleLike", detail)

    def test_donor_ui_package_is_not_reintroduced(self):
        duplicate = ROOT / "app/src/main/java/za/org/rtc/community/feature/communityhub/ui"
        self.assertFalse(duplicate.exists())


if __name__ == "__main__":
    unittest.main()
