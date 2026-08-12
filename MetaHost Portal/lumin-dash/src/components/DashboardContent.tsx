import { useState, useEffect } from "react";
import { KPICards } from "@/components/KPICards";
import { DateRangeSelector } from "@/components/DateRangeSelector";
import { LicenseChart } from "@/components/LicenseChart";
import { RecentRequestsTable } from "@/components/RecentRequestsTable";
import { QuickAddCard } from "@/components/QuickAddCard";
import { DateRange } from "react-day-picker";
import { useAuth } from "@/contexts/AuthContext";
import { supabase } from "@/integrations/supabase/client";
import { Button } from "@/components/ui/button";
import { Play } from "lucide-react";
import { toast } from "@/hooks/use-toast";
import { playMechanicalThud, playWelcomeSwoosh } from "@/lib/notify";

export function DashboardContent() {
  const { user } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [dateRange, setDateRange] = useState<DateRange | undefined>();
  const [licenseStatus, setLicenseStatus] = useState<"loading" | "valid" | "invalid">("loading");
  
  useEffect(() => {
    async function checkLicense() {
      if (!user) return;
      try {
        // Fetch user's licenses
        const { data: licenses } = await supabase
          .from('licenses')
          .select('license_key')
          .eq('user_id', user.id)
          .limit(1);

        const currentLicense = licenses?.[0]?.license_key;
        if (!currentLicense) {
          setLicenseStatus("invalid");
          setIsLoading(false);
          return;
        }

        // Get/set device ID
        let deviceId = localStorage.getItem('device_id');
        if (!deviceId) {
          deviceId = crypto.randomUUID();
          localStorage.setItem('device_id', deviceId);
        }

        // Validate via Edge Function
        const { data, error } = await supabase.functions.invoke('validate-license', {
          body: { licenseKey: currentLicense, deviceId }
        });

        if (error || !data?.valid) {
          setLicenseStatus("invalid");
        } else {
          setLicenseStatus("valid");
        }
      } catch (err) {
        console.error("Validation error:", err);
        setLicenseStatus("invalid");
      } finally {
        setIsLoading(false);
      }
    }
    
    checkLicense();
  }, [user]);



  const handleDateRangeChange = (newDateRange: DateRange | undefined) => {
    setDateRange(newDateRange);
    setIsLoading(true);
    setTimeout(() => setIsLoading(false), 1000);
  };

  return (
    <div className="space-y-8 relative z-10">
      {/* Header Section */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold text-foreground">Command Center</h1>
          <p className="text-muted-foreground">
            Monitor your system status and robot execution
          </p>
        </div>
        
        <div className="shrink-0 flex items-center gap-4">
          {licenseStatus === "valid" ? (
            <Button 
              size="lg" 
              className="gap-2 bg-red-600 hover:bg-red-700 text-white shadow-[0_0_20px_rgba(220,38,38,0.6)] border border-red-500 transition-all duration-300 animate-pulse"
              onClick={() => toast({ title: "Robot Started", description: "Executing trades..." })}
            >
              <Play className="w-5 h-5 fill-current" />
              START
            </Button>
          ) : licenseStatus === "invalid" ? (
            <Button size="lg" disabled className="gap-2 bg-muted text-muted-foreground opacity-50">
              <Play className="w-5 h-5" />
              START
            </Button>
          ) : null}
          <DateRangeSelector onDateRangeChange={handleDateRangeChange} />
        </div>
      </div>



      {/* KPI Cards */}
      <KPICards isLoading={isLoading} />

      {/* Widgets Grid */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        {/* License Chart - spans 2 columns on large screens */}
        <div className="xl:col-span-2 order-1">
          <LicenseChart isLoading={isLoading} />
        </div>
        
        {/* Quick Add Card */}
        <div className="xl:col-span-1 order-2">
          <QuickAddCard />
        </div>
      </div>

      {/* Recent Requests Table */}
      <RecentRequestsTable isLoading={isLoading} />
    </div>
  );
}