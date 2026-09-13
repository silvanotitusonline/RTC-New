package za.org.rtc.remediation.data

import android.content.Context
import androidx.room.withTransaction
import coil.ImageLoader
import coil.disk.DiskCache
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import za.org.rtc.remediation.model.*
import za.org.rtc.remediation.network.*
import za.org.rtc.remediation.work.*
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Room is the durable source; flows expose cached, optimistic and committed changes. */
@OptIn(ExperimentalCoroutinesApi::class)
class RtcRepository(
    private val context: Context, private val database: RtcDatabase,
    private val network: RtcNetwork, private val sessions: SessionProvider,
    private val connectivity: Connectivity, private val scheduler: () -> OutboxScheduler,
    private val scope: CoroutineScope,
) {
    private val dao = database.dao()
    private val _baseFeed = MutableStateFlow<LoadState<List<FeedPost>>>(LoadState.Loading)
    private val optimistic = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val feed: StateFlow<LoadState<List<FeedPost>>> = combine(_baseFeed,optimistic) { state,changes ->
        if(state is LoadState.Content) LoadState.Content(state.value.map { post ->
            changes[post.id]?.let { desired -> post.copy(votedByMe=desired,
                voteCount=(post.voteCount + (if(desired) 1 else 0) - (if(post.votedByMe) 1 else 0)).coerceAtLeast(0)) } ?: post
        }) else state
    }.stateIn(scope,SharingStarted.Eagerly,LoadState.Loading)
    private val _reports = MutableStateFlow<LoadState<List<Report>>>(LoadState.Loading)
    val reports: StateFlow<LoadState<List<Report>>> = _reports.asStateFlow()
    private val _providers = MutableStateFlow<LoadState<List<Provider>>>(LoadState.Loading)
    val providers: StateFlow<LoadState<List<Provider>>> = _providers.asStateFlow()
    private val _timeline = MutableStateFlow<LoadState<List<FeedPost>>>(LoadState.Loading)
    val timeline: StateFlow<LoadState<List<FeedPost>>> = _timeline.asStateFlow()
    private val _outbox = MutableStateFlow<List<OutboxItem>>(emptyList())
    val outbox: StateFlow<List<OutboxItem>> = _outbox.asStateFlow()
    @Volatile var imageLoader: ImageLoader = newImageLoader("signed-out")
        private set
    private fun newImageLoader(owner: String): ImageLoader {
        val key=MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString("") { "%02x".format(it) }
        return ImageLoader.Builder(context.applicationContext).okHttpClient(network.imageClient)
            .diskCache { DiskCache.Builder().directory(File(context.cacheDir,"rtc-images-$key")).maxSizeBytes(50L*1024*1024).build() }
            .build()
    }
    private val voteLocks = ConcurrentHashMap<String,Mutex>()
    private val feedLock = Mutex()
    private val feedEpoch = AtomicLong()
    private val accountEpoch = AtomicLong()
    private var nextFeedCursor: String? = null
    private val hasMoreFeed = MutableStateFlow(false)
    val canLoadMoreFeed: StateFlow<Boolean> = hasMoreFeed.asStateFlow()
    private val queueDirectory = File(context.filesDir,"rtc-pending-uploads").apply { mkdirs() }

    private var started = false
    fun start() {
        check(!started) { "RtcRepository.start must be called once per process." }
        started = true
        scope.launch {
            var previous: String? = null
            sessions.session.map { it?.userId }.distinctUntilChanged().collectLatest { owner ->
                previous?.let { scheduler().cancel(it) }
                previous=owner
                accountEpoch.incrementAndGet()
                imageLoader.shutdown()
                imageLoader=newImageLoader(owner ?: "signed-out")
                feedEpoch.incrementAndGet(); optimistic.value=emptyMap()
                _baseFeed.value=LoadState.Loading; _reports.value=LoadState.Loading
                _providers.value=LoadState.Loading; _timeline.value=LoadState.Loading
                _outbox.value=emptyList(); nextFeedCursor=null; hasMoreFeed.value=false
                if(owner==null) {
                    _baseFeed.value=LoadState.Content(emptyList()); _reports.value=LoadState.Content(emptyList())
                    _providers.value=LoadState.Content(emptyList()); _timeline.value=LoadState.Content(emptyList())
                    return@collectLatest
                }
                coroutineScope {
                    launch { dao.observe(owner,"FEED").collect { rows ->
                        if(!stillOwner(owner)) return@collect
                        val data=rows.map { rtcJson.decodeFromString<FeedPost>(it.payload) }
                        if(data.isNotEmpty() || _baseFeed.value !is LoadState.Loading) _baseFeed.value=LoadState.Content(data)
                    } }
                    launch { dao.observe(owner,"REPORT").collect { rows ->
                        if(!stillOwner(owner)) return@collect
                        val data=rows.map { rtcJson.decodeFromString<Report>(it.payload) }
                        if(data.isNotEmpty() || _reports.value !is LoadState.Loading) _reports.value=LoadState.Content(data)
                    } }
                    launch { dao.observe(owner,"PROVIDER").collect { rows ->
                        if(!stillOwner(owner)) return@collect
                        val data=rows.map { rtcJson.decodeFromString<Provider>(it.payload) }
                        if(data.isNotEmpty() || _providers.value !is LoadState.Loading) _providers.value=LoadState.Content(data)
                    } }
                    launch { dao.observe(owner,"TIMELINE").collect { rows ->
                        if(!stillOwner(owner)) return@collect
                        val data=rows.map { rtcJson.decodeFromString<FeedPost>(it.payload) }
                        if(data.isNotEmpty() || _timeline.value !is LoadState.Loading) _timeline.value=LoadState.Content(data)
                    } }
                    launch { dao.observeOutbox(owner).collect { rows -> if(!stillOwner(owner)) return@collect
                        _outbox.value=rows.map { item ->
                        val preview=if(item.kind=="POST") rtcJson.decodeFromString<PostInput>(item.payload).body
                            else rtcJson.decodeFromString<ReportInput>(item.payload).body
                        OutboxItem(item.id,item.kind,item.state,item.lastError,preview,item.imagePath,item.createdAt)
                    } } }
                    launch { refreshFeed() }; launch { refreshReports() }; launch { refreshProviders() }
                }
            }
        }
        scope.launch {
            sessions.session.filterNotNull().distinctUntilChanged().collect { session ->
                dao.resumeAuth(session.userId)
                scheduler().reconcilePeriodically(session.userId)
                scheduler().enqueue(session.userId)
            }
        }
    }
    fun isOnline(): Boolean = connectivity.isOnline()
    private fun owner(): String = sessions.session.value?.userId ?: throw AuthenticationRequiredException()
    private fun stillOwner(id: String): Boolean = sessions.session.value?.userId==id
    fun apiForCurrentAccount(): RtcApi = network.apiFor(owner())

    suspend fun refreshFeed() {
        val owner=owner(); val epoch=feedEpoch.get(); val account=accountEpoch.get()
        try {
            val response=network.apiFor(owner).feed()
            if(!response.isSuccessful) throw HttpException(response)
            val rows=requireNotNull(response.body()) { "The feed response was empty." }
            feedLock.withLock {
                if(!stillOwner(owner) || epoch!=feedEpoch.get()) return
                dao.replace(owner,"FEED",rows.map { record(owner,"FEED",it) })
                if(!stillOwner(owner) || account!=accountEpoch.get()) return
                _baseFeed.value=LoadState.Content(rows)
                nextFeedCursor=response.headers()["X-Next-Cursor"]
                hasMoreFeed.value=!nextFeedCursor.isNullOrBlank()
            }
        } catch(error: CancellationException) { throw error }
          catch(error: Exception) { if(stillOwner(owner) && _baseFeed.value !is LoadState.Content) _baseFeed.value=LoadState.Failure(message(error)) }
    }
    suspend fun loadMoreFeed() = feedLock.withLock {
        val cursor=nextFeedCursor ?: return
        val owner=owner(); val account=accountEpoch.get()
        val response=network.apiFor(owner).feed(before=cursor)
        if(!response.isSuccessful) throw HttpException(response)
        val page=requireNotNull(response.body())
        if(!stillOwner(owner)) return
        dao.put(page.map { record(owner,"FEED",it) })
        if(!stillOwner(owner) || account!=accountEpoch.get()) return
        nextFeedCursor=response.headers()["X-Next-Cursor"]
        hasMoreFeed.value=!nextFeedCursor.isNullOrBlank()
    }
    suspend fun refreshTimeline() {
        val owner=owner(); val account=accountEpoch.get()
        try {
            val response=network.apiFor(owner).timeline()
            if(!response.isSuccessful) throw HttpException(response)
            val data=requireNotNull(response.body())
            if(stillOwner(owner)) { dao.replace(owner,"TIMELINE",data.map { record(owner,"TIMELINE",it) }); if(stillOwner(owner) && account==accountEpoch.get()) _timeline.value=LoadState.Content(data) }
        } catch(error: CancellationException) { throw error }
          catch(error: Exception) { if(stillOwner(owner) && _timeline.value !is LoadState.Content) _timeline.value=LoadState.Failure(message(error)) }
    }
    suspend fun refreshReports() {
        val owner=owner(); val account=accountEpoch.get()
        try {
            val data=network.apiFor(owner).reports()
            if(stillOwner(owner)) { dao.replace(owner,"REPORT",data.map { CachedRecord(owner,"REPORT",it.id,rtcJson.encodeToString(it),it.createdAt) }); if(stillOwner(owner) && account==accountEpoch.get()) _reports.value=LoadState.Content(data) }
        } catch(error: CancellationException) { throw error }
          catch(error: Exception) { if(stillOwner(owner) && _reports.value !is LoadState.Content) _reports.value=LoadState.Failure(message(error)) }
    }
    suspend fun refreshProviders() {
        val owner=owner(); val account=accountEpoch.get()
        try {
            val data=network.apiFor(owner).providers()
            if(stillOwner(owner)) { dao.replace(owner,"PROVIDER",data.map { CachedRecord(owner,"PROVIDER",it.id,rtcJson.encodeToString(it),it.name) }); if(stillOwner(owner) && account==accountEpoch.get()) _providers.value=LoadState.Content(data) }
        } catch(error: CancellationException) { throw error }
          catch(error: Exception) { if(stillOwner(owner) && _providers.value !is LoadState.Content) _providers.value=LoadState.Failure(message(error)) }
    }
    suspend fun setVote(postId: String, voted: Boolean) {
        val owner=owner(); val account=accountEpoch.get()
        val mutex=voteLocks.getOrPut("$owner:$postId") { Mutex() }
        if(!mutex.tryLock()) return
        try {
            feedEpoch.incrementAndGet()
            optimistic.update { it + (postId to voted) }
            val result=network.apiFor(owner).setVote(postId,VoteInput(voted))
            feedLock.withLock {
                if(!stillOwner(owner)) return
                val current=(_baseFeed.value as? LoadState.Content)?.value.orEmpty()
                val updated=current.map { if(it.id==postId) it.copy(voteCount=result.voteCount,votedByMe=result.votedByMe) else it }
                dao.put(updated.filter { it.id==postId }.map { record(owner,"FEED",it) })
                if(stillOwner(owner) && account==accountEpoch.get()) _baseFeed.value=LoadState.Content(updated)
            }
        } finally {
            feedEpoch.incrementAndGet()
            if(stillOwner(owner) && account==accountEpoch.get()) optimistic.update { it-postId }
            mutex.unlock()
        }
    }
    suspend fun enqueuePost(body: String, image: PreparedImage?, key: String = UUID.randomUUID().toString()): String = withContext(Dispatchers.IO) {
        val owner=owner(); val text=body.trim()
        require(text.codePointCount(0,text.length) in 1..4000) { "Enter a post of 1 to 4,000 characters." }
        val id=UUID.fromString(key).toString()
        dao.findOutbox(id,owner)?.let { return@withContext it.id }
        val copy=image?.let { prepared ->
            val source=File(prepared.path).canonicalFile
            require(source.isFile && source.parentFile==File(context.filesDir,"rtc-outbox-media").canonicalFile)
            require(source.length() in 1..10L*1024*1024)
            require(prepared.mimeType in setOf("image/jpeg","image/png","image/webp"))
            source.copyTo(File(queueDirectory,"$id.upload"),overwrite=false)
        }
        try {
            dao.enqueue(OutboxEntity(id,owner,"POST",rtcJson.encodeToString(PostInput(text)),copy?.absolutePath,image?.mimeType,createdAt=Instant.now().toString()))
        } catch(error: Throwable) {
            withContext(NonCancellable) { if(dao.findOutbox(id,owner)==null) copy?.delete() }
            throw error
        }
        scheduler().enqueue(owner)
        id
    }
    suspend fun enqueueReport(input: ReportInput, key: String = UUID.randomUUID().toString()): String {
        val owner=owner(); val trimmed=input.body.trim()
        require(trimmed.codePointCount(0,trimmed.length) in 20..4000) { "Describe the issue using at least 20 characters." }
        require(input.category in setOf("APP_SUPPORT","INFRASTRUCTURE"))
        require(input.priority in setOf("LOW","NORMAL","HIGH","URGENT"))
        require((input.latitude==null)==(input.longitude==null))
        input.latitude?.let { require(it.isFinite() && it in -90.0..90.0) }
        input.longitude?.let { require(it.isFinite() && it in -180.0..180.0) }
        val id=UUID.fromString(key).toString()
        dao.findOutbox(id,owner)?.let { return it.id }
        dao.enqueue(OutboxEntity(id,owner,"REPORT",rtcJson.encodeToString(input.copy(body=trimmed)),createdAt=Instant.now().toString()))
        scheduler().enqueue(owner)
        return id
    }
    suspend fun releasePreparedImage(image: PreparedImage) = withContext(Dispatchers.IO) {
        val file=File(image.path).canonicalFile
        if(file.parentFile==File(context.filesDir,"rtc-outbox-media").canonicalFile) file.delete()
    }
    suspend fun book(input: BookingInput, key: String): Booking {
        UUID.fromString(key)
        return network.apiFor(owner()).book(key,input)
    }
    suspend fun retryOutbox(id: String) {
        val owner=owner(); dao.retry(id,owner); scheduler().enqueue(owner)
    }
    /** A worker is bound to one owner; it cannot submit an old account's draft as a new user. */
    suspend fun drain(owner: String): DrainResult {
        if(!stillOwner(owner)) return DrainResult.WAIT_FOR_AUTH
        repeat(30) {
            if(!stillOwner(owner)) return DrainResult.WAIT_FOR_AUTH
            val item=dao.claim(owner,System.currentTimeMillis()) ?: return DrainResult.COMPLETE
            if(item.attempts>10) { dao.mark(item.id,"FAILED","Automatic retries exhausted. Review and retry this item."); return@repeat }
            try {
                val api=network.apiFor(owner)
                if(item.kind=="POST") {
                    val input=rtcJson.decodeFromString<PostInput>(item.payload)
                    val photo=item.imagePath?.let { path ->
                        val file=File(path).canonicalFile
                        require(file.parentFile==queueDirectory.canonicalFile && file.isFile) { "Saved photo is unavailable. Recreate this post." }
                        MultipartBody.Part.createFormData("image","photo",file.asRequestBody(requireNotNull(item.imageMime).toMediaType()))
                    }
                    val result=api.createPost(item.id,input.body.toRequestBody("text/plain; charset=utf-8".toMediaType()),photo)
                    feedLock.withLock {
                        feedEpoch.incrementAndGet()
                        database.withTransaction {
                            dao.put(listOf(record(owner,"FEED",result),record(owner,"TIMELINE",result)))
                            dao.remove(item.id,owner)
                        }
                    }
                } else {
                    val result=api.createReport(item.id,rtcJson.decodeFromString(item.payload))
                    database.withTransaction {
                        dao.put(listOf(CachedRecord(owner,"REPORT",result.id,rtcJson.encodeToString(result),result.createdAt)))
                        dao.remove(item.id,owner)
                    }
                }
                item.imagePath?.let { File(it).delete() }
            } catch(error: CancellationException) {
                withContext(NonCancellable) { dao.mark(item.id,"PENDING",null) }
                throw error
            } catch(error: Exception) {
                when(RetryPolicy.classify(error)) {
                    FailureAction.WAIT_FOR_AUTH -> { dao.mark(item.id,"NEEDS_AUTH","Sign in again to send this saved item."); return DrainResult.WAIT_FOR_AUTH }
                    FailureAction.RETRY -> {
                        dao.mark(item.id,if(item.attempts>=10) "FAILED" else "PENDING",message(error))
                        // Leave this request for WorkManager's backoff; never retry in a tight loop.
                        return if(item.attempts>=10 && dao.remaining(owner)==0) DrainResult.COMPLETE else DrainResult.RETRY
                    }
                    FailureAction.FAIL -> dao.mark(item.id,"FAILED",message(error))
                }
            }
        }
        return if(dao.remaining(owner)>0) DrainResult.RETRY else DrainResult.COMPLETE
    }
    private fun record(owner: String,kind: String,post: FeedPost)=CachedRecord(owner,kind,post.id,rtcJson.encodeToString(post),post.createdAt)
    private fun message(error: Throwable): String = when(error) {
        is OfflineException -> "Network offline. Saved for later."
        is HttpException -> when(error.code()) { 401 -> "Sign in again."; 409 -> "This request conflicts with an existing submission."; 413 -> "The selected image is too large."; 429 -> "Service busy. The saved request will retry."; in 500..599 -> "Service unavailable. The saved request will retry."; else -> "Request rejected. Review the form and try again." }
        is java.io.IOException -> "Connection interrupted. Saved requests will retry."
        is IllegalArgumentException -> error.message ?: "Review the submitted data."
        else -> "The request could not be completed. Your saved data is retained."
    }
}
