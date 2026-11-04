/**
 * TomanRates AI - Cloudflare Worker
 *
 * Required binding:
 *   Settings -> Bindings -> Add -> Workers AI
 *   Variable name: AI
 *
 * Uses Cloudflare Workers AI (no OpenAI API key required).
 * Model: @cf/zai-org/glm-4.7-flash
 */

const MODEL = "@cf/zai-org/glm-4.7-flash";
const MAX_BODY_BYTES = 32 * 1024;

function response(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type"
    }
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
          "Access-Control-Allow-Headers": "Content-Type",
          "Access-Control-Max-Age": "86400"
        }
      });
    }

    if (request.method === "GET") {
      return response({
        status: "ok",
        service: "TomanRates AI",
        provider: "Cloudflare Workers AI",
        model: MODEL
      });
    }

    if (request.method !== "POST") {
      return response({ error: "Method not allowed" }, 405);
    }

    if (!env.AI || typeof env.AI.run !== "function") {
      return response({
        error: "Workers AI binding is missing. Add a Workers AI binding named AI, then redeploy."
      }, 503);
    }

    try {
      const contentLength = Number(request.headers.get("Content-Length") || 0);
      if (contentLength > MAX_BODY_BYTES) {
        return response({ error: "Request is too large" }, 413);
      }

      const raw = await request.text();
      if (new TextEncoder().encode(raw).length > MAX_BODY_BYTES) {
        return response({ error: "Request is too large" }, 413);
      }

      const body = JSON.parse(raw);
      const message = String(body.message || "").trim();

      if (!message) {
        return response({ error: "Message is empty" }, 400);
      }
      if (message.length > 5000) {
        return response({ error: "Message is too long" }, 413);
      }

      const marketContext = {
        currencies: body.market || {},
        gold: body.gold_market || {},
        market_updated_at: body.market_updated_at || null,
        market_status: body.market_status || "unknown"
      };

      const system = `
تو دستیار هوشمند مالی اپلیکیشن TomanRates هستی.
همیشه فارسی روان، دقیق و قابل‌فهم بنویس؛ مگر کاربر زبان دیگری بخواهد.

وظایف:
- تحلیل آموزشی بازار ارز، طلا و اقتصاد ایران
- توضیح عوامل مؤثر بر قیمت ارز و طلا
- بررسی سناریوهای احتمالی و ریسک‌ها
- مقایسه گزینه‌های مالی با بیان مزایا، معایب و عدم‌قطعیت‌ها
- توضیح داده‌های بازار برنامه و کمک به فهم روندها

قواعد:
- داده‌های بازار زیر را فقط به‌عنوان آخرین داده دریافتی برنامه در نظر بگیر، نه قیمت لحظه‌ای تضمین‌شده.
- اگر داده قدیمی، ناقص یا ناموجود است، صریح بگو.
- قیمت، خبر، منبع یا آمار ساختگی تولید نکن.
- پیش‌بینی را احتمال بدان، نه قطعیت.
- توصیه قطعی خرید یا فروش نده؛ ریسک و افق زمانی را توضیح بده.
- اگر سؤال به خبر یا داده زنده نیاز دارد و داده‌ای در اختیار نداری، این محدودیت را اعلام کن.
- پاسخ‌ها را متناسب با سؤال، کاربردی و بدون حاشیه بنویس.

داده‌های فعلی برنامه:
${JSON.stringify(marketContext)}
`;

      const history = Array.isArray(body.history) ? body.history.slice(-10) : [];
      const messages = [{ role: "system", content: system }];

      for (const item of history) {
        const role = item && item.role === "assistant" ? "assistant" : "user";
        const content = String(item && item.content || "").slice(0, 3000);
        if (content) messages.push({ role, content });
      }
      messages.push({ role: "user", content: message });

      const result = await env.AI.run(MODEL, {
        messages,
        max_tokens: 1100,
        temperature: 0.45
      });

      const reply = String(result && (result.response || result.result && result.result.response) || "").trim();

      if (!reply) {
        return response({ error: "AI returned an empty response" }, 502);
      }

      return response({ reply });
    } catch (error) {
      return response({
        error: error && error.message ? error.message : "Internal server error"
      }, 500);
    }
  }
};
