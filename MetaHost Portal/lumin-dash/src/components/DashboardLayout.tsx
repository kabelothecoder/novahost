import { Outlet } from "react-router-dom";
import { SidebarProvider } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/AppSidebar";
import { TopNavbar } from "@/components/TopNavbar";
import { useIsMobile } from "@/hooks/use-mobile";
import { useAuth } from "@/contexts/AuthContext";
import { useEffect, useState } from "react";
import { supabase } from "@/integrations/supabase/client";

export function DashboardLayout() {
  const isMobile = useIsMobile();
  const { user } = useAuth();
  const [avatarBg, setAvatarBg] = useState<string | null>(null);

  useEffect(() => {
    async function loadVibeSync() {
      if (!user?.id) return;
      const { data } = await supabase
        .from('profiles')
        .select('avatar_url')
        .eq('id', user.id)
        .maybeSingle();
      
      if (data?.avatar_url) {
        setAvatarBg(data.avatar_url);
      } else if (user?.user_metadata?.avatar_url) {
        setAvatarBg(user.user_metadata.avatar_url);
      }
    }
    loadVibeSync();
  }, [user]);

  return (
    <SidebarProvider defaultOpen={!isMobile}>
      <div 
        className="min-h-screen flex w-full bg-background relative overflow-hidden"
      >
        {/* Vibe Sync Background */}
        {avatarBg && (
          <div 
            className="absolute inset-0 pointer-events-none z-0"
            style={{
              backgroundImage: `url(${avatarBg})`,
              backgroundSize: 'cover',
              backgroundPosition: 'center',
              filter: 'blur(100px) brightness(0.3)',
              opacity: 1 // Full opacity as requested, but blurred and dimmed for usage as bg
            }}
            aria-hidden="true" 
          />
        )}
        <div className="animated-bg pointer-events-none z-0" aria-hidden="true" />
        
        <div className="z-10 bg-transparent flex h-screen">
          <AppSidebar />
        </div>
        
        <div className="flex-1 flex flex-col min-w-0 z-10 relative h-screen overflow-y-auto">
          <TopNavbar />
          
          <main className="flex-1 p-4 md:p-6 animate-enter">
            <Outlet />
          </main>
        </div>
      </div>
    </SidebarProvider>
  );
}