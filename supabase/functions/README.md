# RTC Community Supabase Edge Functions

The connected Production deployment contains six RTC Edge Functions:

| Function | JWT | Production SHA-256 |
|---|---:|---|
| `rtc-admin-ai` | required | `15591900f2a2985185729168da9fae0e72bdece16ace9ddb68ec49eaf2cd352e` |
| `rtc-fcm-dispatch` | required | `dbdb03e8baed131131f296098f3b10384636fbfdb8d692e668e5b2ab3899b13e` |
| `community-media-url` | required | `e3b231df33b6b519f49ca3240d99d0b5ff33538b318541d0bedf5d132a9a09dc` |
| `rtc-privacy-requests` | required | `7c45c7bde518fd065b4bfd5344ebcbbaca0c7137340201cd54a212b9d29159eb` |
| `report-community-post` | required | `3537bc985ef629459e3694c29aceb1d30534989480a70793915d01edc5a7ab1f` |
| `dispatch-community-alerts` | scheduler-secret authentication | `27586a6763ffcb5c31ff1bb4cd2534f66d803c45cc54e33ad16a248e1ba2fee8` |

The same hashes were verified in the RTC Non-Production environment during release reconciliation. Environment secrets and service-account material are deliberately not source controlled.
