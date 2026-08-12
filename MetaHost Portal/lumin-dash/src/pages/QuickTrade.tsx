import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import { useToast } from "@/hooks/use-toast";
import { Send, TrendingUp, TrendingDown, Activity, Loader2, Bot, Users, Radio, CheckCircle2, Zap } from "lucide-react";
import { supabase } from "@/integrations/supabase/client";
import { useAuth } from "@/contexts/AuthContext";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";

interface Product {
  id: string;
  name: string;
  code: string;
}

const AVAILABLE_PAIRS = [
  "EURUSD", "GBPUSD", "USDJPY", "XAUUSD", "XAGUSD", 
  "NAS100", "US30", "SPX500", "BTCUSD", "ETHUSD", "VIX"
];

export default function QuickTrade() {
  const { user } = useAuth();
  const { toast } = useToast();
  const [products, setProducts] = useState<Product[]>([]);
  const [targetId, setTargetId] = useState<string>("ALL");
  const [pair, setPair] = useState<string>("EURUSD");
  const [side, setSide] = useState<"buy" | "sell">("buy");
  const [volume, setVolume] = useState<string>("0.1");
  const [sl, setSl] = useState<string>("");
  const [tp, setTp] = useState<string>("");
  const [isSending, setIsSending] = useState(false);
  const [activeTerminals, setActiveTerminals] = useState(0);
  const [clientsReached, setClientsReached] = useState(0);
  const [lastSignalSent, setLastSignalSent] = useState<Date | null>(null);

  useEffect(() => {
    if (!user) return;

    async function loadProducts() {
      const { data } = await supabase
        .from("expert_advisors")
        .select("id, name, code")
        .eq("user_id", user.id)
        .order("name");
      if (data) setProducts(data);
    }
    loadProducts();

    async function fetchActiveTerminals() {
      const fifteenMinutesAgo = new Date(Date.now() - 15 * 60 * 1000).toISOString();
      const { count, error } = await supabase
        .from("device_activations")
        .select("*", { count: "exact", head: true })
        .gt("last_seen_at", fifteenMinutesAgo)
        .eq("status", "active");
      
      if (!error && count !== null) {
        setActiveTerminals(count); 
      }
    }

    fetchActiveTerminals();
    const interval = setInterval(fetchActiveTerminals, 10000);
    return () => clearInterval(interval);
  }, [user]);

  const handleSendSignal = async () => {
    if (!pair || !volume || !sl || !tp) {
      toast({ title: "Validation Error", description: "All fields are required.", variant: "destructive" });
      return;
    }

    if (!AVAILABLE_PAIRS.includes(pair)) {
      toast({ title: "Invalid Pair", description: "The selected trading pair is not supported.", variant: "destructive" });
      return;
    }

    if (parseFloat(volume) <= 0 || parseFloat(volume) > 50) {
      toast({ title: "Volume Error", description: "Lot size must be between 0.01 and 50.", variant: "destructive" });
      return;
    }

    if (!user) {
      toast({ title: "Auth Error", description: "You must be logged in.", variant: "destructive" });
      return;
    }

    setIsSending(true);
    try {
      const payload = {
        ea_id: targetId === "ALL" ? "MASTER_OVERRIDE" : targetId,
        pair,
        lot: parseFloat(volume),
        price: 0,
        sl: parseFloat(sl),
        tp: parseFloat(tp),
        type: side.toUpperCase()
      };

      const { data, error } = await supabase.functions.invoke('broadcast-signal', {
        body: payload
      });
      
      if (error) throw error;

      toast({ 
        title: "Signal Broadcasted", 
        description: `${side.toUpperCase()} signal sent for ${pair} to ${targetId === 'ALL' ? 'All EAs' : products.find(p => p.id === targetId)?.name}` 
      });

      setSl("");
      setTp("");
      setLastSignalSent(new Date());
      setClientsReached(activeTerminals);

    } catch (e: any) {
      console.error(e);
      toast({ title: "Broadcast Failed", description: e.message || "Could not route dispatch", variant: "destructive" });
    } finally {
      setIsSending(false);
    }
  };

  return (
    <div className="space-y-8 animate-fade-in max-w-4xl mx-auto">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-bold text-foreground flex items-center gap-2">
            <Activity className="w-6 h-6 text-primary" />
            Quick Trade
          </h1>
          <p className="text-muted-foreground">Construct and execute instant market orders to all client EAs.</p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <Card className="glass-card shadow-2xl border-white/10 dark:border-white/5 relative overflow-hidden group">
          <div className="absolute -inset-1 bg-gradient-to-r from-primary/10 via-secondary/10 to-primary/10 rounded-2xl blur opacity-20 group-hover:opacity-40 transition duration-1000 group-hover:duration-200"></div>
          <CardHeader className="relative">
            <CardTitle className="text-xl">Quick Trade parameters</CardTitle>
            <CardDescription>Setup parameters for instantaneous MT5 deployment</CardDescription>
          </CardHeader>
          <CardContent className="space-y-6 relative z-10">
            
            {/* Side selector */}
            <div className="space-y-2">
              <Label>Trade Action</Label>
              <Tabs value={side} onValueChange={(val) => setSide(val as any)} className="w-full">
                <TabsList className="grid grid-cols-2 bg-black/40 border border-white/10 rounded-xl p-1 h-11">
                  <TabsTrigger 
                    value="buy" 
                    className="rounded-lg text-xs font-semibold data-[state=active]:bg-emerald-500/20 data-[state=active]:text-emerald-400 data-[state=active]:border-emerald-500/30 border border-transparent"
                  >
                    <TrendingUp className="w-4 h-4 mr-2" /> BUY POSITION
                  </TabsTrigger>
                  <TabsTrigger 
                    value="sell" 
                    className="rounded-lg text-xs font-semibold data-[state=active]:bg-rose-500/20 data-[state=active]:text-rose-400 data-[state=active]:border-rose-500/30 border border-transparent"
                  >
                    <TrendingDown className="w-4 h-4 mr-2" /> SELL POSITION
                  </TabsTrigger>
                </TabsList>
              </Tabs>
            </div>

            <div className="space-y-2">
              <Label>Target Execution Units</Label>
              <Select value={targetId} onValueChange={setTargetId}>
                <SelectTrigger className="bg-white/5 backdrop-blur-md border-white/10">
                  <SelectValue placeholder="Select target EA" />
                </SelectTrigger>
                <SelectContent className="glass-modal border-white/10">
                  <SelectItem value="ALL">
                    <div className="flex items-center gap-2 text-primary font-bold">
                      <Send className="w-4 h-4" /> All Active EAs
                    </div>
                  </SelectItem>
                  {products.map(p => (
                    <SelectItem key={p.id} value={p.id}>
                      <div className="flex items-center gap-2">
                        <Bot className="w-4 h-4 text-muted-foreground" />
                        {p.name}
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Trading Pair</Label>
                <Select value={pair} onValueChange={setPair}>
                  <SelectTrigger className="bg-white/5 backdrop-blur-md border-white/10">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent className="glass-modal border-white/10">
                    {AVAILABLE_PAIRS.map(p => (
                      <SelectItem key={p} value={p}>{p}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label>Volume (Lots)</Label>
                <Input 
                  type="number" step="0.01" min="0.01"
                  value={volume} onChange={(e) => setVolume(e.target.value)}
                  className="bg-white/5 backdrop-blur-md border-white/10"
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label className="text-rose-500">Stop Loss</Label>
                <Input 
                  type="number" step="0.00001"
                  value={sl} onChange={(e) => setSl(e.target.value)}
                  placeholder="e.g. 1.05400"
                  className="bg-white/5 backdrop-blur-md border-white/10"
                />
              </div>

              <div className="space-y-2">
                <Label className="text-emerald-500">Take Profit</Label>
                <Input 
                  type="number" step="0.00001"
                  value={tp} onChange={(e) => setTp(e.target.value)}
                  placeholder="e.g. 1.06000"
                  className="bg-white/5 backdrop-blur-md border-white/10"
                />
              </div>
            </div>
            
            {/* Prominent Quick Trade Entry Button */}
            <div className="space-y-4 pt-4 border-t border-white/5 mt-6">
              <Button 
                onClick={handleSendSignal}
                disabled={isSending}
                className={`w-full h-12 rounded-xl text-white font-bold flex items-center justify-center gap-2 shadow-lg hover:scale-[1.01] transition-all duration-300 ${
                  side === "buy" 
                    ? "bg-emerald-600 hover:bg-emerald-700 shadow-emerald-600/20" 
                    : "bg-rose-600 hover:bg-rose-700 shadow-rose-600/20"
                }`}
              >
                {isSending ? (
                  <Loader2 className="w-5 h-5 animate-spin" />
                ) : (
                  <>
                    <Zap className="w-5 h-5 fill-current animate-pulse" />
                    Quick Trade Entry
                  </>
                )}
              </Button>
            </div>

          </CardContent>
        </Card>
        
        <div className="space-y-6">
          {/* Heartbeat & Performance Dashboard */}
          <Card className="glass-card shadow-lg border-white/10 dark:border-white/5 bg-gradient-to-br from-background/90 to-black/95 overflow-hidden">
            <CardHeader className="border-b border-white/5 pb-4">
              <CardTitle className="text-xl flex items-center justify-between text-white">
                <span className="flex items-center gap-2">
                  <Radio className="w-5 h-5 text-emerald-400 animate-pulse" /> Network Heartbeat
                </span>
                <Badge variant="outline" className="bg-emerald-500/10 text-emerald-400 border-emerald-500/20">Live</Badge>
              </CardTitle>
            </CardHeader>
            <CardContent className="pt-6 space-y-6">
              
              {/* Active MT5 Terminals KPI */}
              <div className="flex flex-col items-center justify-center p-6 bg-black/40 rounded-2xl border border-white/5">
                <p className="text-sm text-muted-foreground uppercase tracking-wider mb-2 font-semibold">Active MT5 Terminals</p>
                <div className="text-5xl font-mono font-bold text-white tracking-widest drop-shadow-[0_0_15px_rgba(255,255,255,0.3)]">
                  {activeTerminals.toLocaleString()}
                </div>
              </div>

              {/* Clients Reached / Delivery Progress */}
              <div className="space-y-3 p-5 glass-card bg-primary/5 rounded-xl border-primary/20">
                <div className="flex justify-between items-end">
                  <h3 className="text-sm font-semibold text-primary/80 uppercase tracking-wide flex items-center gap-2">
                    <Users className="w-4 h-4" /> Clients Reached
                  </h3>
                  <span className="font-mono text-xl text-primary font-bold">{clientsReached.toLocaleString()}</span>
                </div>
                
                {/* Progress bar */}
                <div className="h-3 w-full bg-black/50 rounded-full overflow-hidden border border-white/5">
                  <div 
                    className="h-full bg-gradient-to-r from-primary to-cyan-400 transition-all duration-[50ms]"
                    style={{ width: `${activeTerminals > 0 ? (clientsReached / activeTerminals) * 100 : 0}%`, boxShadow: '0 0 10px rgba(56,189,248,0.5)' }}
                  />
                </div>
                
                {clientsReached === activeTerminals && clientsReached > 0 && lastSignalSent && (
                  <p className="text-xs text-emerald-400 flex items-center gap-1 justify-end animate-in fade-in slide-in-from-right-2">
                    <CheckCircle2 className="w-3 h-3" /> Synchronized 
                  </p>
                )}
              </div>

              {/* System Status Info */}
              <div className="flex items-center justify-between p-4 bg-emerald-500/5 border border-emerald-500/20 rounded-xl">
                <span className="text-sm font-medium text-emerald-400 flex items-center gap-2">
                  <span className="relative flex h-2.5 w-2.5">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
                  </span>
                  System Status
                </span>
                <span className="text-sm font-bold text-emerald-400 uppercase tracking-wider font-mono">
                  Online
                </span>
              </div>

            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
