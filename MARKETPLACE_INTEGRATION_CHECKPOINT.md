# Marketplace Integration Checkpoint

## Integration base

- Security-hardened `main`: `5821d2e27c0de86b245425e2e930620bda1087e4`
- Marketplace donor branch: `optimize/marketplace-production`
- Reviewed donor checkpoint: `f42c77a4340cb1fe04d4444ee68ce47cc7ed8322`
- Clean integration branch: `integration/marketplace-production-v2`

## Ported in this checkpoint

- timezone-aware recurring Marketplace opening hours;
- special-date hour exceptions in business detail;
- strict Marketplace JSON mappers and mapper tests;
- bounded paginated Marketplace search repository contract;
- debounced/cancellable Marketplace search ViewModel state;
- pagination state and tests;
- donor design/implementation documentation;
- additive detail and search migrations.

## Integration hardening beyond the donor

- appended/replaced Marketplace search pages deduplicate business IDs while preserving first-seen order;
- search query length is bounded server-side to 160 characters;
- locality filter length is bounded server-side to 120 characters;
- page limit remains bounded to 50;
- radius remains bounded to 50 km;
- offset is bounded to 5,000;
- rating is bounded to 0–5;
- sort mode is explicitly allowlisted;
- coordinate pairs must be complete and valid;
- search migration is transactional;
- `SECURITY DEFINER`, fixed `search_path`, authenticated-only execution and the previously merged Marketplace invitation security baseline are protected by source contracts.

## Intentionally not ported

- the donor branch history itself;
- the obsolete duplicate `20260828090000_marketplace_detail_hour_exceptions.sql` donor migration;
- any change that would replace the Security hardening migrations already on `main`;
- production Supabase deployment.

## Remaining Marketplace work

This is a foundation checkpoint, not the complete Marketplace optimisation. The donor had not yet wired the new pagination state through all Marketplace Compose surfaces at this checkpoint. Owner workspace, media recovery, reviews, admin UX, UI decomposition, accessibility and end-to-end Marketplace smoke coverage remain subject to subsequent reviewed donor checkpoints.
