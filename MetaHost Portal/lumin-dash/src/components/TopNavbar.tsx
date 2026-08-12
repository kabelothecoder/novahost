import { Bell, Plus, ChevronDown, User, Sun, Moon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { useAuth } from "@/contexts/AuthContext";
import { useNavigate } from "react-router-dom";
import { playNotificationSound } from "@/lib/notify";
import { Switch } from "@/components/ui/switch";
import { useTheme } from "next-themes";

export function TopNavbar() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const { theme, setTheme } = useTheme();

  const handleNavigate = (path: string) => {
    navigate(path);
  };

  const handleSignOut = async () => {
    try {
      await signOut();
    } catch (error) {
      console.error('Error signing out:', error);
    }
  };

  return (
    <header className="h-16 border-b border-border bg-card/50 backdrop-blur-sm sticky top-0 z-10">
      <div className="flex items-center justify-between h-full px-4 md:px-6">
        <div className="flex items-center gap-4">
          <SidebarTrigger className="p-2 hover:bg-accent rounded-lg transition-colors" />
          
          <div className="hidden sm:block">
            <h2 className="font-semibold text-foreground">Dashboard</h2>
            <p className="text-sm text-muted-foreground">Welcome back, Admin</p>
          </div>
        </div>

        <div className="flex items-center gap-2 md:gap-3">
          {/* New Menu */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button 
                variant="outline" 
                size="sm"
                className="gap-2 bg-gradient-primary text-primary-foreground border-none hover:opacity-90 transition-opacity"
              >
                <Plus className="w-4 h-4" />
                <span className="hidden sm:inline">New</span>
                <ChevronDown className="w-4 h-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-48">
              <DropdownMenuLabel>Quick Actions</DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => handleNavigate('/generate')}>
                Generate License Key
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleNavigate('/manage')}>
                Add EA
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          {/* Theme Toggle */}
          <div className="flex items-center gap-2 mr-2 bg-secondary/50 p-1.5 px-3 rounded-xl border border-border/30">
            <Sun className="h-4 w-4 text-amber-500 dark:text-muted-foreground" />
            <Switch
              id="theme-toggle"
              checked={theme === 'dark'}
              onCheckedChange={(checked) => setTheme(checked ? 'dark' : 'light')}
              className="data-[state=checked]:bg-primary"
            />
            <Moon className="h-4 w-4 text-muted-foreground dark:text-indigo-400" />
          </div>

          {/* Notifications */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" size="icon" className="relative" onClick={() => playNotificationSound()}>
                <Bell className="w-5 h-5" />
                <Badge 
                  variant="destructive" 
                  className="absolute -top-1 -right-1 w-5 h-5 flex items-center justify-center p-0 text-xs"
                >
                  3
                </Badge>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-72">
              <DropdownMenuLabel>Notifications</DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => { playNotificationSound(); handleNavigate('/generate'); }}>
                New license request pending approval
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => { playNotificationSound(); handleNavigate('/manage'); }}>
                New Expert Advisor added successfully
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => { playNotificationSound(); handleNavigate('/key-stats'); }}>
                License expiring soon — review stats
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          {/* User Avatar */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="gap-2 md:gap-3 p-2">
                <Avatar className="w-8 h-8">
                  <AvatarImage src={user?.user_metadata?.avatar_url} />
                  <AvatarFallback>
                    {user?.email?.charAt(0).toUpperCase() || <User className="w-4 h-4" />}
                  </AvatarFallback>
                </Avatar>
                <div className="text-left hidden lg:block">
                  <p className="text-sm font-medium">{user?.user_metadata?.displayName || user?.email}</p>
                  <p className="text-xs text-muted-foreground">User</p>
                </div>
                <ChevronDown className="w-4 h-4 hidden sm:block" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-56">
              <DropdownMenuLabel>My Account</DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={() => handleNavigate('/profile')}>Profile</DropdownMenuItem>
              <DropdownMenuItem onClick={() => handleNavigate('/settings')}>Settings</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem onClick={handleSignOut}>Log out</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </header>
  );
}