
-- Migration: Security MFA Hardening for Administrative Roles
-- Objective: Ensure that users with ADMIN or STAFF roles have MFA enabled to perform sensitive operations.

begin;

-- 1. Create a helper function to validate MFA status for high-privilege roles
create or replace function private.validate_mfa_status() 
returns boolean as $$
declare
    user_role text;
    mfa_enabled boolean;
begin
    -- Get the role of the current user from the profiles/roles table
    select role into user_role from public.profiles where id = auth.uid();
    
    -- Check if MFA is enabled in the Supabase auth.users metadata
    -- Note: Supabase stores MFA status in the app_metadata
    mfa_enabled := (auth.jwt() -> 'app_metadata' ->> 'amfa_enabled')::boolean;

    -- If the user is an ADMIN or STAFF, and MFA is NOT enabled, deny access
    if (user_role in ('ADMIN', 'STAFF')) and (mfa_enabled is false or mfa_enabled is null) then
        return false;
    end if;

    return true;
end;
$$ language plpgsql security definer;

-- 2. Apply this check to the existing Admin AI and Dispatch functions
-- We wrap the logic in existing RPCs or create a trigger-based guard.
-- For this implementation, we create a standard 'guard' function that other RPCs must call.

create or replace function public.admin_access_guard()
returns void as $$
begin
    if not private.validate_mfa_status() then
        raise exception 'MFA_REQUIRED: Administrative access requires Multi-Factor Authentication to be enabled.';
    end if;
end;
$$ language plpgsql security definer;

commit;
