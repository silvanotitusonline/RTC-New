import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const JSON_HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
};

Deno.serve((_request: Request) =>
  new Response(
    JSON.stringify({
      error: "The RTC Service Centre has been retired.",
      errorCode: "SERVICE_CENTRE_RETIRED",
    }),
    { status: 410, headers: JSON_HEADERS },
  )
);
