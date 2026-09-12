
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

const SUPABASE_URL = Deno.env.get('SUPABASE_URL') || "";
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') || "";

const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
  auth: {
    autoRefreshToken: false,
    persistSession: false
  }
});

serve(async (req) => {
  try {
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

    const { data: pendingDeletions, error: fetchError } = await supabase
      .from('account_deletion_requests')
      .select('user_id')
      .lt('requested_at', thirtyDaysAgo.toISOString());

    if (fetchError) throw fetchError;

    if (!pendingDeletions || pendingDeletions.length === 0) {
      return new Response(JSON.stringify({ message: "No pending deletions." }), { status: 200 });
    }

    const results = [];
    for (const request of pendingDeletions) {
      const userId = request.user_id;
      
      await supabase.storage.from('community').remove([`posts/${userId}`]);
      const { error: dbError } = await supabase.from('profiles').delete().eq('id', userId);

      if (dbError) {
        results.push({ userId, status: 'failed', error: dbError.message });
      } else {
        results.push({ userId, status: 'deleted' });
      }
    }

    return new Response(JSON.stringify({ processed: results }), { status: 200 });
  } catch (err) {
    return new Response(JSON.stringify({ error: err.message }), { status: 500 });
  }
});
