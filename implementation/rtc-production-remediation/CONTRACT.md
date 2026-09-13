# RTC Kotlin / Express implementation contract

This is an independently runnable integration package. RTC-New currently uses Supabase
RPC/Storage, not Express. Existing application routes and live Supabase services are
not switched by this package. Supply a deployed HTTPS API URL and the existing
identity-provider access token to use the Kotlin library. A data migration/adaptor
is required before directing existing RTC traffic to the new server schema.

All endpoints require `Authorization: Bearer <access token>` verified by the server.
JSON uses camelCase; timestamps are ISO-8601 UTC; money uses integer cents.
Errors: `{ "error": { "code": "...", "message": "..." } }`.

- POST /posts: multipart fields `body` and optional `image` (JPEG/PNG/WebP, max 10 MiB).
  Required UUID Idempotency-Key. Returns 201 FeedPost; repeat returns same response.
- GET /feed and GET /timeline: 200 JSON array of FeedPost; [] is successful empty data.
  limit default 30, max 100; optional `before` opaque cursor; X-Next-Cursor response header.
  /timeline is the authenticated user's own posts. Feed excludes private support reports.
- PUT /posts/:id/vote: JSON `{voted:Boolean}`; returns `{postId:String,voteCount:Int,votedByMe:Boolean}`.
  The operation sets desired state, not toggle; safe to retry after ambiguous network failure.
- GET /media/:id: authenticated media bytes, only for a visible community post.
  Feed imageUrl is an absolute HTTPS URL to this endpoint; Coil must use the authenticated client.
- POST /reports: required UUID Idempotency-Key; JSON ReportInput. Returns 201 Report.
- GET /reports: authenticated user's report array, newest first; [] is success.
- GET /dashboard: authenticated user's ReportSummary from same report table/scope.
- GET /providers: array of Provider; [] is success, no mock fallback.
- POST /bookings: required UUID Idempotency-Key; JSON BookingInput; returns 201 Booking.
  Same user/key/payload replays response; changed payload returns 409.
  A provider/startAt uniqueness constraint prevents double-booking across different keys.
- GET /locations/route?fromLat=&fromLon=&toLat=&toLon=: TomTom road distance/duration;
  returns RouteDistance. Location permission denial/no route is unavailable, never fake zero.
- GET /locations/search?q=&lat=&lon=: bounded TomTom POI search; returns Place array.

FeedPost: id, authorId, body, imageUrl:String?, imageWidth:Int?, imageHeight:Int?,
createdAt:String, voteCount:Int, votedByMe:Boolean.
ReportInput: body:String (trimmed 20..4000 characters), category:APP_SUPPORT|INFRASTRUCTURE,
priority:LOW|NORMAL|HIGH|URGENT, latitude:Double?, longitude:Double?. Coordinates supplied together.
The server accepts display labels 'App Support' and 'Infrastructure' and normalizes them.
Report: id, body, category, priority, status:PENDING|WORKING|RESOLVED, createdAt,
latitude:Double?, longitude:Double?. No invented timeline events.
ReportSummary: pending:Int, working:Int, resolved:Int, total:Int (sum of all three).
Provider: id, name, imageUrl:String?, rateCents:Long, latitude:Double?, longitude:Double?.
BookingInput: providerId:String, startsAt:String (future UTC ISO instant), notes:String.
Booking: id, providerId, startsAt, notes, status:PENDING, createdAt.
RouteDistance: distanceMeters:Long, travelTimeSeconds:Long, calculatedAt:String.
Place: id:String, name:String, latitude:Double, longitude:Double.

Android writes user-scoped outbox entries before sending posts/reports. Persistent
image files belong in filesDir, not a temporary content URI or cache. Each queued
operation retains the original idempotency key across attempts/process death.
Only the matching signed-in account may drain its queue. Connectivity prediction
is advisory; IOException still queues/retries. 401 waits for sign-in; 4xx validation
is terminal and visible; 429/5xx retry with bounded backoff. Cancellation is rethrown.

All code files must contain complete implementations, no TODO bodies. Tests should
exercise real routes/database constraints where available and report runtime limits.
