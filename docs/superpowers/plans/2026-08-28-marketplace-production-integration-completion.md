# Marketplace Production Integration Completion Plan

## Goal

Complete PR #13 by reconciling the finished `optimize/marketplace-production` donor checkpoint into the Security-hardened integration branch without weakening checkpoint-1 search isolation or checkpoint-2 replay/idempotency guarantees.

## Integration rules

- Keep `SupabaseMarketplaceRepository` from PR #13 as the hardened core repository.
- Keep the checkpoint-2 forward migrations and non-production validation evidence.
- Reuse donor route-family Compose screens byte-for-byte where they do not alter security/data contracts.
- Split `MarketplaceViewModels.kt` into scoped ViewModels.
- Preserve search-generation and selected-area inheritance protections in Discovery.
- Preserve idempotency keys for review reporting and all retryable mutations.
- Add the remaining helpful-vote and admin-detail RPCs through a narrow additive capability adapter.
- Route all Marketplace administration through centralized `ProtectedRoute` / `RouteAccessPolicy`.
- Do not deploy to production Supabase.

## Completion gates

1. Source regression contracts.
2. Shared Edge authorization Deno tests.
3. JVM unit tests including Marketplace route policy.
4. Android lint.
5. Debug APK assembly.
6. AndroidTest APK compilation.
7. Non-production RPC checks for helpful voting and admin detail.
8. Exact-head artifact recording.
9. Final integration report and report-head CI.
10. Mark PR #13 ready and merge only the exact verified head.
