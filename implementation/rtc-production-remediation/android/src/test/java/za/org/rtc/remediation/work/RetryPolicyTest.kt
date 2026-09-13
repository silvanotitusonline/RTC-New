package za.org.rtc.remediation.work

import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import za.org.rtc.remediation.network.AuthenticationRequiredException

class RetryPolicyTest {
    private fun http(code:Int)=HttpException(Response.error<Unit>(code,"{}".toResponseBody()))
    @Test fun interruptedConnectionIsRetriedAfterDurableEnqueue() {
        assertEquals(FailureAction.RETRY,RetryPolicy.classify(IOException("connection reset")))
    }
    @Test fun expiredAccountWaitsForSameAccountSignIn() {
        assertEquals(FailureAction.WAIT_FOR_AUTH,RetryPolicy.classify(http(401)))
        assertEquals(FailureAction.WAIT_FOR_AUTH,RetryPolicy.classify(AuthenticationRequiredException()))
    }
    @Test fun validationAndConflictDoNotLoopForever() {
        for(code in listOf(400,403,404,409,413,422)) assertEquals(FailureAction.FAIL,RetryPolicy.classify(http(code)))
    }
    @Test fun transientHttpFailuresRetry() {
        for(code in listOf(408,429,500,502,503,504)) assertEquals(FailureAction.RETRY,RetryPolicy.classify(http(code)))
    }
}
