import { 
  BarChart3, 
  Key, 
  Users, 
  RotateCcw, 
  TrendingUp,
  Home,
  Send,
  Server,
  Layout,
  MessageSquare,
  Shield,
  ChevronDown,
  Zap
} from "lucide-react";
import { NavLink, useLocation } from "react-router-dom";
import { useIsMobile } from "@/hooks/use-mobile";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { SidebarMenuSub, SidebarMenuSubItem } from "@/components/ui/sidebar";

import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";

const menuItems = [
  { title: "Dashboard", url: "/", icon: Home },
  { title: "Generate Key", url: "/generate", icon: Key },
  { title: "Manage EA", url: "/manage", icon: Users },
];

export function AppSidebar() {
  const { state } = useSidebar();
  const location = useLocation();
  const isMobile = useIsMobile();
  const isCollapsed = state === "collapsed";

  return (
    <Sidebar 
      className={`${isCollapsed ? "w-16" : "w-64"} transition-all duration-300 border-r border-border/50 bg-gradient-to-b from-card/95 to-card backdrop-blur-sm`}
      collapsible={isMobile ? "offcanvas" : "icon"}
      variant={isMobile ? "floating" : "sidebar"}
    >
      <SidebarContent className="p-4">
        {/* Brand Section */}
        <div className="mb-8 animate-fade-in">
          <div className="flex items-center gap-3 p-3 rounded-xl bg-gradient-to-r from-primary/10 to-primary/5 border border-primary/20">
            <div className="w-10 h-10 bg-gradient-primary rounded-xl flex items-center justify-center shadow-lg transform hover:scale-110 transition-all duration-300">
              <BarChart3 className="w-5 h-5 text-primary-foreground" />
            </div>
            {(!isCollapsed || isMobile) && (
              <div className="transition-all duration-300">
                <h1 className="font-bold text-lg text-foreground">Nova Edge</h1>
                <p className="text-xs text-muted-foreground">Admin Portal</p>
              </div>
            )}
          </div>
        </div>

        <SidebarGroup>
          <SidebarGroupLabel className={`${isCollapsed && !isMobile ? "sr-only" : ""} transition-all duration-300 text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3`}>
            Navigation
          </SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu className="space-y-2">
              {menuItems.map((item, index) => {
                const isActive = location.pathname === item.url;
                return (
                  <SidebarMenuItem 
                    key={item.title} 
                    className="animate-fade-in" 
                    style={{ animationDelay: `${100 * index}ms` }}
                  >
                    <NavLink 
                      to={item.url}
                      className={`
                        flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium 
                        transition-all duration-300 ease-in-out transform hover:scale-[1.02]
                        ${isActive 
                          ? 'bg-gradient-to-r from-primary to-primary/80 text-primary-foreground shadow-lg shadow-primary/25 border border-primary/30' 
                          : 'hover:bg-accent/70 hover:text-accent-foreground text-muted-foreground hover:shadow-md border border-transparent hover:border-border/50'
                        }
                      `}
                    >
                      <item.icon className={`
                        w-5 h-5 flex-shrink-0 transition-all duration-300 
                        ${isActive ? 'text-primary-foreground scale-110' : 'group-hover:scale-105'}
                      `} />
                      {(!isCollapsed || isMobile) && (
                        <span className="transition-all duration-300 font-medium">
                          {item.title}
                        </span>
                      )}
                      {isActive && (!isCollapsed || isMobile) && (
                        <div className="ml-auto w-2 h-2 bg-primary-foreground rounded-full animate-pulse" />
                      )}
                    </NavLink>
                  </SidebarMenuItem>
                );
              })}

              {/* Trade Dispatcher Collapsible Group */}
              <Collapsible defaultOpen={true} className="group/collapsible animate-fade-in" style={{ animationDelay: '300ms' }}>
                <SidebarMenuItem>
                  <CollapsibleTrigger asChild>
                    <button
                      className={`
                        w-full flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium 
                        transition-all duration-300 ease-in-out transform hover:scale-[1.02]
                        hover:bg-accent/70 hover:text-accent-foreground text-muted-foreground border border-transparent hover:border-border/50
                        ${location.pathname.startsWith('/dispatcher') ? 'text-primary bg-accent/30' : ''}
                      `}
                    >
                      <Send className="w-5 h-5 flex-shrink-0 text-muted-foreground group-data-[state=open]/collapsible:text-primary transition-colors" />
                      {(!isCollapsed || isMobile) && (
                        <>
                          <span className="font-medium text-left flex-1">Trade Dispatcher</span>
                          <ChevronDown className="w-4 h-4 transition-transform duration-200 group-data-[state=open]/collapsible:rotate-180 text-muted-foreground" />
                        </>
                      )}
                    </button>
                  </CollapsibleTrigger>
                  <CollapsibleContent className="pt-1">
                    <SidebarMenuSub className="space-y-1 border-l border-white/10 ml-6 pl-4 flex flex-col">
                      <SidebarMenuSubItem>
                        <NavLink
                          to="/dispatcher/quick-trade"
                          className={({ isActive }) => `
                            flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs font-semibold transition-all duration-300
                            ${isActive 
                              ? 'bg-primary/20 text-primary border border-primary/20 shadow-md shadow-primary/5' 
                              : 'text-muted-foreground hover:bg-white/5 hover:text-foreground border border-transparent'
                            }
                          `}
                        >
                          <Zap className="w-3.5 h-3.5" />
                          <span>Quick Trade</span>
                        </NavLink>
                      </SidebarMenuSubItem>
                      <SidebarMenuSubItem>
                        <NavLink
                          to="/dispatcher/licenses"
                          className={({ isActive }) => `
                            flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs font-semibold transition-all duration-300
                            ${isActive 
                              ? 'bg-primary/20 text-primary border border-primary/20 shadow-md shadow-primary/5' 
                              : 'text-muted-foreground hover:bg-white/5 hover:text-foreground border border-transparent'
                            }
                          `}
                        >
                          <Shield className="w-3.5 h-3.5" />
                          <span>Licenses</span>
                        </NavLink>
                      </SidebarMenuSubItem>
                    </SidebarMenuSub>
                  </CollapsibleContent>
                </SidebarMenuItem>
              </Collapsible>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        {/* Bottom Decoration */}
        {(!isCollapsed || isMobile) && (
          <div className="mt-auto space-y-6 pt-6">
            <div className="p-4 rounded-xl bg-gradient-to-r from-primary/5 to-secondary/5 border border-border/50">
              <div className="text-xs text-muted-foreground text-center">
                <p className="font-medium">Nova Edge EA Manager</p>
                <p>Professional Edition</p>
              </div>
            </div>
          </div>
        )}
      </SidebarContent>
    </Sidebar>
  );
}