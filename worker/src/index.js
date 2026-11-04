const SYSTEM = `تو دستیار فارسی اپلیکیشن تومان هستی. پاسخ‌ها را فارسی، روشن و محتاطانه بنویس. اگر سؤال درباره خود برنامه بود، قابلیت‌ها را توضیح بده. اگر تحلیل بازار خواست، فقط از داده بازار ارسالی کاربر استفاده کن و زمان/وضعیت آن را ذکر کن. داده و تحلیل و عدم قطعیت را جدا کن. قیمت یا خبر زنده‌ای که در ورودی نیست جعل نکن. توصیه قطعی خرید و فروش نده و ریسک نوسان ارز و طلا را یادآوری کن.`;

export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": env.ALLOWED_ORIGIN || "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Content-Type": "application/json; charset=utf-8",
    };
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    if (request.method !== "POST") return new Response(JSON.stringify({ error: "Method not allowed" }), { status: 405, headers: cors });
    if (!env.OPENAI_API_KEY) return new Response(JSON.stringify({ error: "Server is not configured" }), { status: 503, headers: cors });
    const ip = request.headers.get("CF-Connecting-IP") || "unknown";
    if (env.RATE_LIMITER) {
      const { success } = await env.RATE_LIMITER.limit({ key: ip });
      if (!success) return new Response(JSON.stringify({ error: "Rate limit exceeded; try again shortly." }), { status: 429, headers: cors });
    }
    try {
      const body = await request.json();
      const message = String(body.message || "").trim();
      if (!message || message.length > 4000) return new Response(JSON.stringify({ error: "Message must be 1–4000 characters" }), { status: 400, headers: cors });
      const market = JSON.stringify({ status: body.market_status, updated_at: body.market_updated_at, rates: body.market || {}, Iran_gold_and_coin: body.gold_market || {} });
      const response = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: { "Authorization": `Bearer ${env.OPENAI_API_KEY}`, "Content-Type": "application/json" },
        body: JSON.stringify({
          model: env.OPENAI_MODEL || "gpt-4.1-mini",
          instructions: SYSTEM,
          input: `داده بازار فعلی برنامه (ممکن است قدیمی باشد): ${market}\n\nپرسش کاربر: ${message}`,
          max_output_tokens: 900,
        }),
      });
      const data = await response.json();
      if (!response.ok) return new Response(JSON.stringify({ error: "OpenAI request failed", detail: data.error?.message || "Unknown error" }), { status: 502, headers: cors });
      const reply = (data.output || []).flatMap(x => x.content || []).filter(x => x.type === "output_text").map(x => x.text).join("\n").trim();
      return new Response(JSON.stringify({ reply }), { headers: cors });
    } catch {
      return new Response(JSON.stringify({ error: "Invalid request or upstream error" }), { status: 400, headers: cors });
    }
  },
};
