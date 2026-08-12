import { useState, useEffect } from "react";
import { Card, CardContent } from "@/components/ui/card";
import { TrendingUp, TrendingDown, Users, Key, Shield, Database, Activity, DollarSign } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { supabase } from "@/integrations/supabase/client";
import { motion } from "framer-motion";

interface StatsData {
  total_licenses: number;
  live_fleet: number;
  total_users: number;
  managed_equity: number;
}

interface KPICardProps {
  title: string;
  value: string | number;
  change: string;
  isPositive: boolean;
  icon: React.ReactNode;
  isLoading?: boolean;
  delay?: number;
}

function KPICard({ title, value, change, isPositive, icon, isLoading, delay = 0 }: KPICardProps) {
  if (isLoading) {
    return (
      <Card className="glass-panel border-white/5 bg-black/20 backdrop-blur-xl">
        <CardContent className="p-6">
          <div className="flex items-center justify-between">
            <div className="space-y-2">
              <Skeleton className="h-4 w-24 bg-white/5" />
              <Skeleton className="h-8 w-16 bg-white/10" />
              <Skeleton className="h-3 w-20 bg-white/5" />
            </div>
            <Skeleton className="w-12 h-12 rounded-2xl bg-white/10" />
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay }}
    >
      <Card className="glass-panel group relative overflow-hidden bg-black/40 backdrop-blur-xl border border-white/10 hover:border-primary/30 transition-all duration-500 hover:shadow-[0_0_30px_rgba(59,130,246,0.15)] cursor-pointer">
        {/* Animated background glow */}
        <div className="absolute -right-10 -top-10 w-32 h-32 bg-primary/5 rounded-full blur-3xl group-hover:bg-primary/10 transition-all duration-500" />
        
        <CardContent className="p-6">
          <div className="flex items-center justify-between relative z-10">
            <div className="space-y-2">
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">{title}</p>
              <p className="text-3xl font-mono font-bold text-foreground">
                {typeof value === 'number' && value > 1000 ? (value / 1000).toFixed(1) + 'k' : value}
              </p>
              <div className="flex items-center gap-1.5">
                <div className={`p-0.5 rounded-full ${isPositive ? "bg-success/20" : "bg-destructive/20"}`}>
                  {isPositive ? (
                    <TrendingUp className="w-3 h-3 text-success" />
                  ) : (
                    <TrendingDown className="w-3 h-3 text-destructive" />
                  )}
                </div>
                <span className={`text-xs font-bold ${isPositive ? "text-success" : "text-destructive"}`}>
                  {change}
                </span>
                <span className="text-[10px] text-muted-foreground font-medium uppercase">vs 24h</span>
              </div>
            </div>
            <div className="w-14 h-14 bg-gradient-to-br from-white/5 to-white/[0.02] border border-white/10 rounded-2xl flex items-center justify-center shadow-inner group-hover:scale-110 transition-transform duration-500">
              {icon}
            </div>
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );
}

export function KPICards() {
  const [stats, setStats] = useState<StatsData | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    async function fetchStats() {
      try {
        const { data, error } = await supabase.rpc('get_dashboard_stats');
        if (error) throw error;
        setStats(data);
      } catch (err) {
        console.error("Failed to fetch dashboard stats:", err);
      } finally {
        setIsLoading(false);
      }
    }

    fetchStats();
    const interval = setInterval(fetchStats, 30000); // Update every 30s
    return () => clearInterval(interval);
  }, []);

  const kpiData = [
    {
      title: "Active Licenses",
      value: stats?.total_licenses ?? 0,
      change: "+12.5%",
      isPositive: true,
      icon: <Shield className="w-7 h-7 text-primary drop-shadow-[0_0_8px_rgba(59,130,246,0.5)]" />
    },
    {
      title: "Live Fleet",
      value: stats?.live_fleet ?? 0,
      change: "+4.2%",
      isPositive: true,
      icon: <Activity className="w-7 h-7 text-success drop-shadow-[0_0_8px_rgba(34,197,94,0.5)]" />
    },
    {
      title: "Managed Equity",
      value: stats ? `$${(stats.managed_equity / 1000).toFixed(1)}k` : "$0.0k",
      change: "+8.1%",
      isPositive: true,
      icon: <DollarSign className="w-7 h-7 text-amber-500 drop-shadow-[0_0_8px_rgba(245,158,11,0.5)]" />
    },
    {
      title: "Neural Population",
      value: stats?.total_users ?? 0,
      change: "+2.4%",
      isPositive: true,
      icon: <Users className="w-7 h-7 text-purple-500 drop-shadow-[0_0_8px_rgba(168,85,247,0.5)]" />
    }
  ];

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
      {kpiData.map((kpi, index) => (
        <KPICard
          key={index}
          title={kpi.title}
          value={kpi.value}
          change={kpi.change}
          isPositive={kpi.isPositive}
          icon={kpi.icon}
          isLoading={isLoading}
          delay={index * 0.1}
        />
      ))}
    </div>
  );
}