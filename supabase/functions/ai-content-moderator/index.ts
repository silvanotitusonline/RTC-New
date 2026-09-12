
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const OPENAI_API_KEY = Deno.env.get('OPENAI_API_KEY') || "";

serve(async (req) => {
  try {
    const { content, userId } = await req.json();
    
    // AI Analysis: Check for toxicity, hate speech, or spam
    const response = await fetch('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: { 
        'Authorization': `Bearer ${OPENAI_API_KEY}`, 
        'Content-Type': 'application/json' 
      },
      body: JSON.stringify({
        model: "gpt-4",
        messages: [
          { role: "system", content: "You are a community moderator. Analyze the following text for toxicity, hate speech, or spam. Respond in JSON: { "action": "ALLOW" | "FLAG" | "BLOCK", "reason": "string" }" },
          { role: "user", content: content }
        ],
        response_format: { type: "json_object" }
      })
    });

    const result = await response.json();
    const decision = JSON.parse(result.choices[0].message.content);

    return new Response(JSON.stringify(decision), { status: 200 });
  } catch (err) {
    return new Response(JSON.stringify({ action: "ALLOW", reason: "fallback" }), { status: 200 });
  }
});
