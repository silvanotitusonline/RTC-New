package za.org.rtc.remediation.work

import java.io.IOException
import retrofit2.HttpException
import za.org.rtc.remediation.network.AuthenticationRequiredException

enum class FailureAction { RETRY, WAIT_FOR_AUTH, FAIL }
object RetryPolicy {
    fun classify(error: Throwable): FailureAction = when {
        error is AuthenticationRequiredException -> FailureAction.WAIT_FOR_AUTH
        error is HttpException && error.code() == 401 -> FailureAction.WAIT_FOR_AUTH
        error is HttpException && (error.code() == 408 || error.code() == 429 || error.code() >= 500) -> FailureAction.RETRY
        error is IOException -> FailureAction.RETRY
        else -> FailureAction.FAIL
    }
}
