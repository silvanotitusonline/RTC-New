
-- PILLAR 2: REAL-TIME & ENGAGEMENT SYSTEMS
BEGIN;

-- 1. Direct Messaging System
CREATE TABLE IF NOT EXISTS public.conversations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.conversation_members (
    conversation_id uuid REFERENCES public.conversations(id) ON DELETE CASCADE,
    user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
    joined_at timestamptz DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_id uuid REFERENCES auth.users(id),
    content text NOT NULL,
    is_read boolean DEFAULT false,
    created_at timestamptz DEFAULT now()
);

-- 2. Repost & Quote Engine
ALTER TABLE public.community_posts 
ADD COLUMN IF NOT EXISTS original_post_id uuid REFERENCES public.community_posts(id) ON DELETE CASCADE,
ADD COLUMN IF NOT EXISTS post_type text DEFAULT 'original' CHECK (post_type IN ('original', 'repost', 'quote'));

-- 3. Real-time Notifications
CREATE TABLE IF NOT EXISTS public.notifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid REFERENCES auth.users(id) ON DELETE CASCADE,
    actor_id uuid REFERENCES auth.users(id),
    type text NOT NULL CHECK (type IN ('like', 'repost', 'reply', 'mention', 'dm')),
    post_id uuid REFERENCES public.community_posts(id) ON DELETE CASCADE,
    is_read boolean DEFAULT false,
    created_at timestamptz DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_messages_conv_created ON public.messages (conversation_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON public.notifications (user_id, is_read);

COMMIT;
