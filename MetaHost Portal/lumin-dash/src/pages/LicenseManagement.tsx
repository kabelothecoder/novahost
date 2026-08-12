import { useEffect, useState, useCallback } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/hooks/use-toast";
import { supabase } from "@/integrations/supabase/client";
import { 
  Shield, 
  Key, 
  Copy, 
  Smartphone, 
  CheckCircle2, 
  AlertTriangle, 
  RefreshCw,
  Search,
  ExternalLink
} from "lucide-react";
import { Input } from "@/components/ui/input";
import { useNavigate } from "react-router-dom";

interface DBActivation {
  device_id: string;
}

interface DBLicense {
  id: string;
  license_key: string;
  owner_email: string | null;
  status: string;
  metadata: any;
  created_at: string;
  expert_advisors: { name: string } | null;
  device_activations: DBActivation[];
}

export default function LicenseManagement() {
  const [licenses, setLicenses] = useState<DBLicense[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const { toast } = useToast();
  const navigate = useNavigate();

  const fetchLicenses = useCallback(async () => {
    setIsLoading(true);
    try {
      const { data: sessionData } = await supabase.auth.getSession();
      if (!sessionData.session) {
        setLicenses([]);
        setIsLoading(false);
        return;
      }

      // Fetch all licenses, products, and device activations
      const { data, error } = await supabase
        .from("licenses")
        .select(`
          id,
          license_key,
          owner_email,
          status,
          metadata,
          created_at,
          expert_advisors:expert_advisors!licenses_ea_id_fkey (
            name
          ),
          device_activations (
            device_id
          )
        `)
        .eq("user_id", sessionData.session.user.id)
        .order("created_at", { ascending: false });

      if (error) {
        if (error.code === 'PGRST301' || error.message?.includes('JWT') || error.message?.includes('unauthorized')) {
          setLicenses([]);
          return;
        }
        throw error;
      }

      setLicenses((data as any) ?? []);
    } catch (e: any) {
      console.warn("Gracefully handled license fetch error:", e);
      setLicenses([]);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLicenses();
  }, [fetchLicenses]);

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    toast({
      title: "Key Copied",
      description: "License key successfully copied to clipboard.",
    });
  };

  // Filter licenses based on search query (by key, email, or bot name)
  const filteredLicenses = licenses.filter(lic => {
    const keyMatch = lic.license_key.toLowerCase().includes(searchQuery.toLowerCase());
    const emailVal = lic.owner_email || (lic.metadata as any)?.username || "";
    const emailMatch = emailVal.toLowerCase().includes(searchQuery.toLowerCase());
    const botNameVal = lic.expert_advisors?.name || (lic.metadata as any)?.name || (lic.metadata as any)?.robot_name || "";
    const botMatch = botNameVal.toLowerCase().includes(searchQuery.toLowerCase());
    
    return keyMatch || emailMatch || botMatch;
  });

  return (
    <div className="space-y-8 animate-fade-in max-w-6xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold text-white flex items-center gap-2">
            <Shield className="w-8 h-8 text-primary" /> License Management
          </h1>
          <p className="text-muted-foreground">
            Track and monitor all generated activation licenses and mobile connection states
          </p>
        </div>
        <Button 
          variant="outline" 
          size="sm" 
          onClick={fetchLicenses}
          disabled={isLoading}
          className="bg-white/5 border-white/10 hover:bg-white/10 text-white shrink-0 self-start sm:self-auto gap-2"
        >
          <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
          Refresh Data
        </Button>
      </div>

      {/* Control bar */}
      <div className="flex items-center relative max-w-md w-full">
        <Search className="w-4 h-4 absolute left-3.5 text-muted-foreground" />
        <Input
          placeholder="Search by license key, owner, or robot..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="pl-10 bg-white/5 border-white/10 text-white rounded-xl placeholder:text-muted-foreground/60 focus-visible:ring-primary"
        />
      </div>

      {/* Content */}
      <Card className="bg-gradient-card border-border glass-card overflow-hidden shadow-xl">
        <CardHeader>
          <CardTitle className="text-lg flex items-center gap-2">
            <Key className="w-5 h-5 text-primary" /> Active Deployments ({filteredLicenses.length})
          </CardTitle>
        </CardHeader>
        <CardContent className="p-0 sm:p-6">
          {isLoading ? (
            <div className="p-6 space-y-4">
              {[...Array(4)].map((_, i) => (
                <div key={i} className="flex gap-4 items-center">
                  <Skeleton className="h-4 w-1/4" />
                  <Skeleton className="h-4 w-1/4" />
                  <Skeleton className="h-4 w-1/4" />
                  <Skeleton className="h-6 w-20" />
                </div>
              ))}
            </div>
          ) : (
            <>
              {/* Mobile View Layout (Stack cards vertically, no horizontal scrolling) */}
              <div className="grid grid-cols-1 gap-4 p-4 md:hidden">
                {filteredLicenses.length === 0 ? (
                  <div className="text-center py-12 border border-dashed border-white/10 rounded-2xl">
                    <AlertTriangle className="w-10 h-10 text-muted-foreground mx-auto mb-2" />
                    <p className="text-muted-foreground text-sm">No matching licenses found.</p>
                  </div>
                ) : (
                  filteredLicenses.map((lic) => {
                    const ownerEmail = lic.owner_email || (lic.metadata as any)?.username || "Unknown Owner";
                    const botName = lic.expert_advisors?.name || (lic.metadata as any)?.name || (lic.metadata as any)?.robot_name || "Unknown Bot";
                    const activations = lic.device_activations || [];
                    const isConnected = activations.length > 0;
                    const primaryColor = (lic.metadata as any)?.primary_color || "#3b82f6";

                    return (
                      <div 
                        key={lic.id} 
                        className="p-5 rounded-2xl border border-white/10 bg-black/40 flex flex-col justify-between space-y-4"
                        style={{ borderLeft: `4px solid ${primaryColor}` }}
                      >
                        <div className="space-y-3">
                          <div className="flex items-start justify-between gap-2">
                            <div>
                              <h3 className="text-white font-bold text-base">{botName}</h3>
                              <p className="text-xs text-muted-foreground font-mono mt-0.5">{ownerEmail}</p>
                            </div>
                            {isConnected ? (
                              <Badge className="bg-success/15 text-success border border-success/30 flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold">
                                <span className="w-1.5 h-1.5 bg-success rounded-full animate-pulse" />
                                Connected
                              </Badge>
                            ) : (
                              <Badge className="bg-warning/10 text-warning/90 border border-warning/20 flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold">
                                <span className="w-1.5 h-1.5 bg-warning rounded-full" />
                                Pending Link
                              </Badge>
                            )}
                          </div>

                          <div className="flex items-center gap-2 bg-white/5 border border-white/10 rounded-xl px-3.5 py-2.5 justify-between">
                            <code className="text-xs font-mono font-bold text-white/95 tracking-wider break-all">{lic.license_key}</code>
                            <Button 
                              variant="ghost" 
                              size="icon" 
                              className="w-8 h-8 shrink-0 hover:bg-white/10"
                              onClick={() => copyToClipboard(lic.license_key)}
                            >
                              <Copy className="w-4 h-4 text-muted-foreground hover:text-white" />
                            </Button>
                          </div>
                        </div>

                        {isConnected && (
                          <div className="text-[10px] text-muted-foreground flex items-center gap-1.5 pt-1 font-mono">
                            <Smartphone className="w-3.5 h-3.5 shrink-0" />
                            <span>Device: {activations[0].device_id}</span>
                          </div>
                        )}
                        
                        <div className="flex justify-end pt-1">
                          <Button 
                            variant="ghost" 
                            size="sm" 
                            className="text-xs text-primary hover:text-primary-glow font-bold gap-1 p-0 hover:bg-transparent"
                            onClick={() => navigate(`/license-details/${lic.license_key}`)}
                          >
                            View Details <ExternalLink className="w-3 h-3" />
                          </Button>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>

              {/* Desktop Table View Layout (md and larger) */}
              <div className="hidden md:block">
                <Table>
                  <TableHeader>
                    <TableRow className="bg-muted/30 hover:bg-muted/30 border-white/10">
                      <TableHead className="font-medium pl-6">License Key</TableHead>
                      <TableHead className="font-medium">Assigned User (Email)</TableHead>
                      <TableHead className="font-medium">Associated Bot (EA)</TableHead>
                      <TableHead className="font-medium">Device Connection</TableHead>
                      <TableHead className="font-medium pr-6 text-right">Action</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredLicenses.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={5} className="text-center py-10 text-muted-foreground">
                          No matching licenses found
                        </TableCell>
                      </TableRow>
                    ) : (
                      filteredLicenses.map((lic) => {
                        const ownerEmail = lic.owner_email || (lic.metadata as any)?.username || "Unknown Owner";
                        const botName = lic.expert_advisors?.name || (lic.metadata as any)?.name || (lic.metadata as any)?.robot_name || "Unknown Bot";
                        const activations = lic.device_activations || [];
                        const isConnected = activations.length > 0;
                        const primaryColor = (lic.metadata as any)?.primary_color || "#3b82f6";

                        return (
                          <TableRow key={lic.id} className="hover:bg-white/5 transition-colors border-white/10">
                            <TableCell className="pl-6 font-mono text-sm font-semibold py-4">
                              <div className="flex items-center gap-2">
                                <code>{lic.license_key}</code>
                                <Button 
                                  variant="ghost" 
                                  size="icon" 
                                  className="w-7 h-7 hover:bg-white/10 rounded-lg"
                                  onClick={() => copyToClipboard(lic.license_key)}
                                >
                                  <Copy className="w-3.5 h-3.5 text-muted-foreground" />
                                </Button>
                              </div>
                            </TableCell>
                            <TableCell className="text-white/80 font-medium">{ownerEmail}</TableCell>
                            <TableCell>
                              <div className="flex items-center gap-2">
                                <div 
                                  className="w-2.5 h-2.5 rounded-full shrink-0" 
                                  style={{ backgroundColor: primaryColor, boxShadow: `0 0 8px ${primaryColor}` }}
                                />
                                <span className="font-semibold text-white/90">{botName}</span>
                              </div>
                            </TableCell>
                            <TableCell>
                              {isConnected ? (
                                <div className="flex flex-col gap-1">
                                  <Badge className="bg-success/15 text-success border border-success/30 flex items-center gap-1.5 w-fit rounded-full px-2.5 py-0.5 text-xs font-semibold">
                                    <span className="w-1.5 h-1.5 bg-success rounded-full animate-pulse" />
                                    Connected
                                  </Badge>
                                  <span className="text-[10px] font-mono text-muted-foreground">ID: {activations[0].device_id}</span>
                                </div>
                              ) : (
                                <Badge className="bg-white/5 text-muted-foreground border border-white/10 flex items-center gap-1.5 w-fit rounded-full px-2.5 py-0.5 text-xs font-semibold">
                                  <span className="w-1.5 h-1.5 bg-muted-foreground/60 rounded-full" />
                                  Pending Device Link
                                </Badge>
                              )}
                            </TableCell>
                            <TableCell className="pr-6 text-right">
                              <Button 
                                variant="ghost" 
                                size="sm" 
                                className="text-xs text-primary hover:text-primary-glow font-bold gap-1 hover:bg-white/5"
                                onClick={() => navigate(`/license-details/${lic.license_key}`)}
                              >
                                Details <ExternalLink className="w-3.5 h-3.5" />
                              </Button>
                            </TableCell>
                          </TableRow>
                        );
                      })
                    )}
                  </TableBody>
                </Table>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
