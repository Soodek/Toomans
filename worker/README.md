# Toman AI Worker

Deploy from this directory with Wrangler. Never commit the OpenAI key.

```sh
npm install -g wrangler
wrangler login
wrangler secret put OPENAI_API_KEY
wrangler deploy
```

Copy the deployed HTTPS Worker URL into the AI tab in the Android app. Optional: set `OPENAI_MODEL` in Worker variables and `ALLOWED_ORIGIN` if you have a trusted web origin. Android native apps do not send browser Origin headers, so CORS is not an authentication mechanism. For public distribution, add rate limiting and abuse protection (for example Cloudflare Turnstile or a gateway policy) before exposing the endpoint widely.
