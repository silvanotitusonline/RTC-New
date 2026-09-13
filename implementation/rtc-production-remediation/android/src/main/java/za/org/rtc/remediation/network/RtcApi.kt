package za.org.rtc.remediation.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import za.org.rtc.remediation.model.*

interface RtcApi {
    @Multipart @POST("posts")
    suspend fun createPost(@Header("Idempotency-Key") key: String, @Part("body") body: RequestBody,
        @Part image: MultipartBody.Part?): FeedPost
    @GET("feed") suspend fun feed(@Query("before") before: String? = null, @Query("limit") limit: Int = 30): Response<List<FeedPost>>
    @GET("timeline") suspend fun timeline(@Query("before") before: String? = null, @Query("limit") limit: Int = 30): Response<List<FeedPost>>
    @PUT("posts/{id}/vote") suspend fun setVote(@Path("id") id: String, @Body input: VoteInput): VoteResult
    @POST("reports") suspend fun createReport(@Header("Idempotency-Key") key: String, @Body input: ReportInput): Report
    @GET("reports") suspend fun reports(): List<Report>
    @GET("dashboard") suspend fun dashboard(): ReportSummary
    @GET("providers") suspend fun providers(): List<Provider>
    @POST("bookings") suspend fun book(@Header("Idempotency-Key") key: String, @Body input: BookingInput): Booking
    @GET("locations/route") suspend fun route(@Query("fromLat") fromLat: Double, @Query("fromLon") fromLon: Double,
        @Query("toLat") toLat: Double, @Query("toLon") toLon: Double): RouteDistance
    @GET("locations/search") suspend fun search(@Query("q") query: String, @Query("lat") lat: Double?, @Query("lon") lon: Double?): List<Place>
}
