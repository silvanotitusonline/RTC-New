package za.org.rtc.community.feature.community

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import za.org.rtc.community.core.CommunityComment
import za.org.rtc.community.core.CommunityPost

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial refresh requests only the bounded first page`() = runTest(dispatcher) {
        val repository = FakeCommunityRepository(
            pages = ArrayDeque(
                listOf(
                    Result.success(
                        CommunityFeedPage(
                            items = listOf(post("a", "2026-08-28T12:00:00Z")),
                            nextCursor = CommunityCursor("2026-08-28T12:00:00Z", "a"),
                            hasMore = true,
                        )
                    )
                )
            )
        )

        val viewModel = CommunityViewModel(repository)
        advanceUntilIdle()

        assertEquals(listOf(PageRequest(null, COMMUNITY_FEED_PAGE_SIZE)), repository.requests)
        assertEquals(listOf("a"), viewModel.feedState.value.items.map { it.id })
        assertTrue(viewModel.feedState.value.hasMore)
        assertFalse(viewModel.feedState.value.initialLoading)
    }

    @Test
    fun `next page uses the complete composite cursor and appends without duplicates`() = runTest(dispatcher) {
        val cursor = CommunityCursor("2026-08-28T11:00:00Z", "b")
        val repository = FakeCommunityRepository(
            pages = ArrayDeque(
                listOf(
                    Result.success(
                        CommunityFeedPage(
                            items = listOf(post("a", "2026-08-28T12:00:00Z"), post("b", "2026-08-28T11:00:00Z")),
                            nextCursor = cursor,
                            hasMore = true,
                        )
                    ),
                    Result.success(
                        CommunityFeedPage(
                            items = listOf(post("b", "2026-08-28T11:00:00Z"), post("c", "2026-08-28T10:00:00Z")),
                            nextCursor = null,
                            hasMore = false,
                        )
                    ),
                )
            )
        )
        val viewModel = CommunityViewModel(repository)
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(PageRequest(cursor, COMMUNITY_FEED_PAGE_SIZE), repository.requests.last())
        assertEquals(listOf("a", "b", "c"), viewModel.feedState.value.items.map { it.id })
        assertFalse(viewModel.feedState.value.hasMore)
    }

    @Test
    fun `duplicate like taps are suppressed while authoritative mutation is pending`() = runTest(dispatcher) {
        val repository = FakeCommunityRepository(
            pages = ArrayDeque(listOf(Result.success(CommunityFeedPage(listOf(post("a", "2026-08-28T12:00:00Z")), null, false)))),
            reactions = ArrayDeque(listOf(Result.success(CommunityLikeOutcome(liked = true, reactionCount = 3)))),
        )
        val viewModel = CommunityViewModel(repository)
        advanceUntilIdle()

        viewModel.toggleLike("a")
        viewModel.toggleLike("a")

        assertEquals(setOf("a"), viewModel.pendingLikeIds.value)
        assertTrue(viewModel.feedState.value.items.single().viewerHasLiked)
        assertEquals(1, viewModel.feedState.value.items.single().reactions)

        advanceUntilIdle()

        assertEquals(listOf("a"), repository.toggleRequests)
        assertTrue(viewModel.feedState.value.items.single().viewerHasLiked)
        assertEquals(3, viewModel.feedState.value.items.single().reactions)
        assertTrue(viewModel.pendingLikeIds.value.isEmpty())
    }

    @Test
    fun `failed reaction rolls back only the optimistic state`() = runTest(dispatcher) {
        val repository = FakeCommunityRepository(
            pages = ArrayDeque(listOf(Result.success(CommunityFeedPage(listOf(post("a", "2026-08-28T12:00:00Z")), null, false)))),
            reactions = ArrayDeque(listOf(Result.failure(IllegalStateException("offline")))),
        )
        val viewModel = CommunityViewModel(repository)
        advanceUntilIdle()

        viewModel.toggleLike("a")
        assertTrue(viewModel.feedState.value.items.single().viewerHasLiked)
        advanceUntilIdle()

        assertFalse(viewModel.feedState.value.items.single().viewerHasLiked)
        assertEquals(0, viewModel.feedState.value.items.single().reactions)
        assertEquals("Your reaction could not be saved. Please try again.", viewModel.feedState.value.mutationError)
    }

    @Test
    fun `detail mutations reload only the affected conversation`() = runTest(dispatcher) {
        val initialPost = post("a", "2026-08-28T12:00:00Z")
        val refreshedPost = initialPost.copy(comments = 1)
        val comment = CommunityComment(
            id = "comment-1",
            postId = "a",
            authorId = "resident",
            author = "Resident",
            handle = "@resident",
            content = "Reply",
            createdAt = "2026-08-28T12:01:00Z",
        )
        val repository = FakeCommunityRepository(
            pages = ArrayDeque(listOf(Result.success(CommunityFeedPage(listOf(initialPost), null, false)))),
            posts = ArrayDeque(listOf(Result.success(initialPost), Result.success(refreshedPost))),
            comments = ArrayDeque(listOf(Result.success(emptyList()), Result.success(listOf(comment)))),
            creates = ArrayDeque(listOf(Result.success(Unit))),
        )
        val viewModel = CommunityViewModel(repository)
        advanceUntilIdle()

        viewModel.loadPostDetail("a")
        advanceUntilIdle()
        viewModel.createComment("a", "Reply")
        advanceUntilIdle()

        assertEquals(listOf("a"), repository.createRequests.map { it.first })
        assertEquals(listOf("comment-1"), viewModel.detailState.value.comments.map { it.id })
        assertEquals(1, viewModel.feedState.value.items.single().comments)
        assertEquals("Comment posted.", viewModel.detailState.value.message)
    }

    @Test
    fun `staff moderation hides a comment and reloads the affected conversation`() = runTest(dispatcher) {
        val initialPost = post("a", "2026-08-28T12:00:00Z").copy(comments = 1)
        val comment = CommunityComment(
            id = "comment-1",
            postId = "a",
            authorId = "resident",
            author = "Resident",
            handle = "@resident",
            content = "Unsafe reply",
            createdAt = "2026-08-28T12:01:00Z",
        )
        val repository = FakeCommunityRepository(
            pages = ArrayDeque(listOf(Result.success(CommunityFeedPage(listOf(initialPost), null, false)))),
            posts = ArrayDeque(listOf(Result.success(initialPost), Result.success(initialPost.copy(comments = 0)))),
            comments = ArrayDeque(listOf(Result.success(listOf(comment)), Result.success(emptyList()))),
            moderations = ArrayDeque(listOf(Result.success(Unit))),
        )
        val viewModel = CommunityViewModel(repository)
        advanceUntilIdle()

        viewModel.loadPostDetail("a")
        advanceUntilIdle()
        viewModel.moderateComment("a", "comment-1", "Contains personal information")
        advanceUntilIdle()

        assertEquals(listOf("comment-1" to "Contains personal information"), repository.moderationRequests)
        assertTrue(viewModel.detailState.value.comments.isEmpty())
        assertEquals("Comment removed by moderation.", viewModel.detailState.value.message)
    }

    private data class PageRequest(val cursor: CommunityCursor?, val limit: Int)

    private class FakeCommunityRepository(
        private val pages: ArrayDeque<Result<CommunityFeedPage>>,
        private val reactions: ArrayDeque<Result<CommunityLikeOutcome>> = ArrayDeque(),
        private val emojiReactions: ArrayDeque<Result<CommunityLikeOutcome>> = ArrayDeque(),
        private val posts: ArrayDeque<Result<CommunityPost?>> = ArrayDeque(),
        private val comments: ArrayDeque<Result<List<CommunityComment>>> = ArrayDeque(),
        private val creates: ArrayDeque<Result<Unit>> = ArrayDeque(),
        private val updates: ArrayDeque<Result<Unit>> = ArrayDeque(),
        private val deletes: ArrayDeque<Result<Unit>> = ArrayDeque(),
        private val postDeletes: ArrayDeque<Result<Unit>> = ArrayDeque(),
        private val moderations: ArrayDeque<Result<Unit>> = ArrayDeque(),
    ) : CommunityRepository {
        val requests = mutableListOf<PageRequest>()
        val toggleRequests = mutableListOf<String>()
        val reactionRequests = mutableListOf<Pair<String, String>>()
        val createRequests = mutableListOf<Triple<String, String, String?>>()
        val moderationRequests = mutableListOf<Pair<String, String>>()

        override suspend fun loadFeedPage(cursor: CommunityCursor?, limit: Int): Result<CommunityFeedPage> {
            requests += PageRequest(cursor, limit)
            return pages.removeFirst()
        }

        override suspend fun loadPost(postId: String): Result<CommunityPost?> = posts.removeFirst()

        override suspend fun loadComments(postId: String): Result<List<CommunityComment>> = comments.removeFirst()

        override suspend fun createComment(postId: String, body: String, parentId: String?): Result<Unit> {
            createRequests += Triple(postId, body, parentId)
            return creates.removeFirst()
        }

        override suspend fun updateComment(commentId: String, body: String): Result<Unit> = updates.removeFirst()

        override suspend fun deleteComment(commentId: String): Result<Unit> = deletes.removeFirst()

        override suspend fun deletePost(postId: String): Result<Unit> = postDeletes.removeFirst()

        override suspend fun moderateComment(commentId: String, reason: String): Result<Unit> {
            moderationRequests += commentId to reason
            return moderations.removeFirst()
        }

        override suspend fun toggleLike(postId: String): Result<CommunityLikeOutcome> {
            toggleRequests += postId
            return reactions.removeFirst()
        }

        override suspend fun toggleReaction(postId: String, emoji: String): Result<CommunityLikeOutcome> {
            reactionRequests += postId to emoji
            return emojiReactions.removeFirst()
        }

        override suspend fun refreshMediaUrl(mediaId: String): Result<String?> = Result.success(null)
    }

    private fun post(id: String, createdAt: String) = CommunityPost(
        id = id,
        author = "Resident",
        handle = "@resident",
        content = "Post $id",
        category = "Community",
        createdAt = createdAt,
        reactions = 0,
        comments = 0,
    )
}
