# Toman AI Worker

Deploy from this directory with Wrangler. Never commit the OpenAI key.

```sh
npm install -g wrangler@latest
wrangler login
wrangler secret put OPENAI_API_KEY
wrangler deploy
```

Copy the deployed HTTPS Worker URL into the AI tab in the Android app. Optional: set `OPENAI_MODEL` in Worker variables and `ALLOWED_ORIGIN` if you have a trusted web origin. Android native apps do not send browser Origin headers, so CORS is not an authentication mechanism. For public distribution, add rate limiting and abuse protection (for example Cloudflare Turnstile or a gateway policy) before exposing the endpoint widely.


The rate-limit binding is configured for 10 requests per 60 seconds per client IP. Cloudflare's Worker rate limits are approximate and location-scoped, not a strict global billing cap; users behind the same mobile carrier/NAT may share an IP bucket. Choose a positive integer `namespace_id` that is unique in your Cloudflare account if `1001` is already in use. Wrangler 4.36.0 or newer is required for the rate-limit binding.
