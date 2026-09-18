# xKiro API demo

This repository includes [`tools/xkiro_demo.py`](../tools/xkiro_demo.py), a dependency-free Python smoke-test and example generator for xKiro.

## Capability brief

xKiro is a model gateway at `https://api.xkiro.com/v1`. It supports both OpenAI Chat Completions and Anthropic Messages formats, so existing SDK integrations can usually be migrated by changing the base URL, API key, and model ID. Models are addressed as `vendor/model`.

The documented feature set includes ordinary text generation, server-sent-event streaming, function/tool calling, reasoning controls, vision inputs, text-to-speech, asynchronous image generation and editing jobs, model discovery, usage/quota inspection, smart routing, and fallback across upstream providers. The public site describes access to models from providers including OpenAI, Anthropic, Google, DeepSeek, xAI, Kimi, Qwen, GLM, MiniMax, Mistral, NVIDIA, Meta, and others; the exact catalog is returned by the live `/v1/models` endpoint.

## Run the demo

The script uses only the Python standard library and never prints the API key.

```bash
cd /home/ubuntu/RTC-New

# Network/API availability smoke test; no key required.
python3 tools/xkiro_demo.py --smoke

# Print request templates for the supported features; no API calls are made.
python3 tools/xkiro_demo.py --examples

# Run an authenticated minimal chat request.
export XKIRO_API_KEY='sk-xt-...'
python3 tools/xkiro_demo.py --live
```

`XKIRO_API_KEY` is preferred. For compatibility with this Manus session, the script also accepts the existing `xKiro` environment variable. Keep keys server-side and do not commit them.

## What the script tests

| Mode | Endpoint or behavior | Purpose |
| --- | --- | --- |
| `--smoke` | `GET /v1/models` | Separates DNS/network failures from authentication failures; the endpoint is public. |
| `--live` | `POST /v1/chat/completions` | Sends a minimal authenticated OpenAI-compatible request using the first discovered model, or `--model vendor/model`. |
| `--examples` | No network call | Prints templates for streaming, tools, vision, speech, image jobs, and usage. |

Optional production features are shown as templates rather than invoked automatically because speech and image generation can consume quota and image calls are asynchronous jobs that need polling.

## Test result in this session

The xKiro host was reached, but `GET /v1/models` returned **Cloudflare HTTP 403 Error 1010 (`browser_signature_banned`)**. This means the sandbox client was blocked at the edge before the request could reach xKiro’s API authentication layer. It does **not** prove that the configured value is invalid. The demo reports this condition without revealing the secret.

To complete the authenticated test, create or copy a real key from the [xKiro API Keys page](https://xkiro.com/dashboard/api/keys), export it as `XKIRO_API_KEY`, and rerun `--live`. If the public catalog remains blocked but you know a valid model ID, bypass discovery with `python3 tools/xkiro_demo.py --live --model vendor/model`; the resulting status will test the authenticated generation endpoint directly.

## API notes

Authentication accepts either `Authorization: Bearer <key>` or `x-api-key: <key>`. The documented base URL for OpenAI-compatible clients includes `/v1`; the raw API reference describes the host as `https://api.xkiro.com` and lists paths under `/v1`. Blocking requests have a documented 95-second cutoff, so streaming is recommended for long or reasoning-heavy responses.

References: [xKiro documentation](https://docs.xkiro.com/), [API overview](https://docs.xkiro.com/api/overview/), [Quickstart](https://docs.xkiro.com/guides/quickstart/), and [xKiro product site](https://xkiro.com/).
