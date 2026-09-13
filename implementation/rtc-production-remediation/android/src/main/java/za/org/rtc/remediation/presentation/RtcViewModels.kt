package za.org.rtc.remediation.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import za.org.rtc.remediation.data.RtcRepository
import za.org.rtc.remediation.model.Booking
import za.org.rtc.remediation.model.BookingInput
import za.org.rtc.remediation.model.LoadState
import za.org.rtc.remediation.model.PreparedImage
import za.org.rtc.remediation.model.ReportInput

data class ReportFormState(
    val body: String = "",
    val category: String? = null,
    val priority: String = "NORMAL",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val submitting: Boolean = false,
    val attempted: Boolean = false,
    val error: String? = null,
    val queuedId: String? = null,
) {
    val bodyError: String? get() = PresentationRules.reportBodyError(body)
    val isDirty: Boolean get() = body.isNotBlank() || category != null || priority != "NORMAL" || latitude != null
    val canSubmit: Boolean get() = bodyError == null && category != null && !submitting
}

class ReportFormViewModel(private val saved: SavedStateHandle, private val repository: RtcRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ReportFormState(
        body = saved["report.body"] ?: "",
        category = saved["report.category"],
        priority = saved["report.priority"] ?: "NORMAL",
        latitude = saved["report.latitude"],
        longitude = saved["report.longitude"],
    ))
    val state = mutableState.asStateFlow()

    private fun edit(change: (ReportFormState) -> ReportFormState) {
        if (state.value.submitting) return
        val old = state.value
        val next = change(old).copy(error = null, queuedId = null)
        if (next.body != old.body || next.category != old.category || next.priority != old.priority ||
            next.latitude != old.latitude || next.longitude != old.longitude) saved.remove<String>("report.key")
        saved["report.body"] = next.body
        saved["report.category"] = next.category
        saved["report.priority"] = next.priority
        saved["report.latitude"] = next.latitude
        saved["report.longitude"] = next.longitude
        mutableState.value = next
    }

    fun setBody(value: String) = edit { it.copy(body = value) }
    fun setCategory(value: String) {
        require(value in setOf("APP_SUPPORT", "INFRASTRUCTURE"))
        edit { it.copy(category = value) }
    }
    fun setPriority(value: String) {
        require(value in setOf("LOW", "NORMAL", "HIGH", "URGENT"))
        edit { it.copy(priority = value) }
    }
    fun setLocation(latitude: Double?, longitude: Double?) {
        require((latitude == null) == (longitude == null))
        require(latitude == null || (latitude.isFinite() && latitude in -90.0..90.0))
        require(longitude == null || (longitude.isFinite() && longitude in -180.0..180.0))
        edit { it.copy(latitude = latitude, longitude = longitude) }
    }
    fun discard() = edit { ReportFormState() }
    fun dismissQueued() = mutableState.update { it.copy(queuedId = null) }

    fun submit() {
        val current = state.value
        if (current.submitting) return
        if (!current.canSubmit) {
            mutableState.update { it.copy(attempted = true) }
            return
        }
        val key = saved.get<String>("report.key") ?: UUID.randomUUID().toString().also { saved["report.key"] = it }
        mutableState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val id = repository.enqueueReport(ReportInput(
                    body = current.body.trim(), category = requireNotNull(current.category),
                    priority = current.priority, latitude = current.latitude, longitude = current.longitude,
                ), key)
                mutableState.update { it.copy(submitting = false) }
                discard()
                mutableState.update { it.copy(queuedId = id) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(error = error.message ?: "Could not save this report. Please retry.") }
            } finally {
                mutableState.update { it.copy(submitting = false) }
            }
        }
    }

    companion object {
        fun factory(repository: RtcRepository) = viewModelFactory {
            initializer { ReportFormViewModel(createSavedStateHandle(), repository) }
        }
    }
}

data class PostComposerState(
    val body: String = "",
    val image: PreparedImage? = null,
    val preparing: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val queuedId: String? = null,
) {
    val isDirty: Boolean get() = body.isNotBlank() || image != null
    val canSubmit: Boolean get() = PresentationRules.characterCount(body.trim()) in 1..4_000 && !preparing && !submitting
}

class PostComposerViewModel(private val saved: SavedStateHandle, private val repository: RtcRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(PostComposerState(
        body = saved["post.body"] ?: "",
        image = saved.get<String>("post.imagePath")?.let { PreparedImage(it, saved["post.imageType"] ?: "image/jpeg") },
    ))
    val state = mutableState.asStateFlow()
    fun setBody(body: String) {
        if (state.value.submitting) return
        if (body != state.value.body) saved.remove<String>("post.key")
        saved["post.body"] = body
        mutableState.update { it.copy(body = body, error = null) }
    }
    private fun setImage(image: PreparedImage?) {
        val previous = state.value.image
        if (previous != image) saved.remove<String>("post.key")
        saved["post.imagePath"] = image?.path
        saved["post.imageType"] = image?.mimeType
        mutableState.update { it.copy(image = image, error = null) }
        if (previous != null && previous.path != image?.path) viewModelScope.launch {
            try { repository.releasePreparedImage(previous) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Cleanup failure must not undo the durable submission or the new draft.
            }
        }
    }
    fun clearImage() { if (!state.value.submitting && !state.value.preparing) setImage(null) }
    fun prepare(uri: Uri, prepareImage: suspend (Uri) -> PreparedImage) {
        if (state.value.preparing || state.value.submitting) return
        mutableState.update { it.copy(preparing = true, error = null) }
        viewModelScope.launch {
            try { setImage(prepareImage(uri)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(error = error.message ?: "Could not read this image.") } }
            finally { mutableState.update { it.copy(preparing = false) } }
        }
    }
    fun discard() {
        if (state.value.submitting || state.value.preparing) return
        setBody("")
        setImage(null)
    }
    fun dismissQueued() = mutableState.update { it.copy(queuedId = null) }
    fun submit() {
        val current = state.value
        if (!current.canSubmit) return
        val key = saved.get<String>("post.key") ?: UUID.randomUUID().toString().also { saved["post.key"] = it }
        mutableState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val id = repository.enqueuePost(current.body.trim(), current.image, key)
                mutableState.update { it.copy(submitting = false) }
                discard()
                mutableState.update { it.copy(queuedId = id) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(error = error.message ?: "Could not save this post. Please retry.") } }
            finally { mutableState.update { it.copy(submitting = false) } }
        }
    }
    companion object {
        fun factory(repository: RtcRepository) = viewModelFactory {
            initializer { PostComposerViewModel(createSavedStateHandle(), repository) }
        }
    }
}

class CommunityViewModel(private val repository: RtcRepository) : ViewModel() {
    val feed = repository.feed
    val reports = repository.reports
    val providers = repository.providers
    val outbox = repository.outbox
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = mutableMessages.asSharedFlow()
    val dashboard: StateFlow<LoadState<DashboardSummary>> = reports.map { reports ->
        when (reports) {
            is LoadState.Loading -> LoadState.Loading
            is LoadState.Failure -> reports
            is LoadState.Content -> LoadState.Content(PresentationRules.summary(reports.value))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoadState.Loading)

    init { refresh() }
    fun refresh() {
        launchRequest { repository.refreshFeed() }
        launchRequest { repository.refreshReports() }
        launchRequest { repository.refreshProviders() }
    }
    fun vote(postId: String, voted: Boolean) = launchRequest { repository.setVote(postId, voted) }
    fun retryOutbox(id: String) = launchRequest { repository.retryOutbox(id) }
    private fun launchRequest(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableMessages.emit(error.message ?: "Could not update. Please retry.") }
        }
    }
    companion object {
        fun factory(repository: RtcRepository) = viewModelFactory { initializer { CommunityViewModel(repository) } }
    }
}

data class BookingState(
    val providerId: String = "",
    val startsAt: String = "",
    val notes: String = "",
    val idempotencyKey: String = UUID.randomUUID().toString(),
    val submitting: Boolean = false,
    val error: String? = null,
    val booking: Booking? = null,
) {
    val input: BookingInput get() = BookingInput(providerId, startsAt.trim(), notes.trim())
    val canSubmit: Boolean get() = !submitting && booking == null && PresentationRules.bookingError(input, Instant.now()) == null
}

/** The key survives retries and process restoration. Editing the payload starts a new operation. */
class BookingViewModel(private val saved: SavedStateHandle, private val repository: RtcRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(BookingState(
        providerId = saved["booking.provider"] ?: "", startsAt = saved["booking.startsAt"] ?: "",
        notes = saved["booking.notes"] ?: "", idempotencyKey = saved["booking.key"] ?: UUID.randomUUID().toString(),
    ))
    val state = mutableState.asStateFlow()
    init { saved["booking.key"] = state.value.idempotencyKey }

    private fun edit(change: (BookingState) -> BookingState) {
        if (state.value.submitting) return
        val old = state.value
        val candidate = change(old)
        if (candidate == old) return
        val next = if (candidate.input != old.input)
            candidate.copy(idempotencyKey = UUID.randomUUID().toString(), booking = null, error = null)
        else candidate // Keep ordinary typing spaces without creating a different server operation.
        saved["booking.provider"] = next.providerId
        saved["booking.startsAt"] = next.startsAt
        saved["booking.notes"] = next.notes
        saved["booking.key"] = next.idempotencyKey
        mutableState.value = next
    }
    fun selectProvider(id: String) = edit { it.copy(providerId = id) }
    fun setStartsAt(value: String) = edit { it.copy(startsAt = value) }
    fun setNotes(value: String) = edit { it.copy(notes = value) }
    fun submit() {
        val current = state.value
        if (current.submitting || current.booking != null) return
        PresentationRules.bookingError(current.input, Instant.now())?.let { error ->
            mutableState.update { it.copy(error = error) }
            return
        }
        mutableState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val booking = repository.book(current.input, current.idempotencyKey)
                mutableState.update { it.copy(booking = booking) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.update { it.copy(error = error.message ?: "Booking failed. Retry with the same request.") } }
            finally { mutableState.update { it.copy(submitting = false) } }
        }
    }
    companion object {
        fun factory(repository: RtcRepository) = viewModelFactory {
            initializer { BookingViewModel(createSavedStateHandle(), repository) }
        }
    }
}
