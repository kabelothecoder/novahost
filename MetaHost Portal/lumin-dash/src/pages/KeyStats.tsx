import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { TrendingUp, TrendingDown, Users, Key, Shield, Database } from "lucide-react";
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend
} from "recharts";

interface KPICardProps {
  title: string;
  value: string | number;
  change: string;
  isPositive: boolean;
  icon: React.ReactNode;
  isLoading?: boolean;
}

function KPICard({ title, value, change, isPositive, icon, isLoading }: KPICardProps) {
  if (isLoading) {
    return (
      <Card className="bg-gradient-card border-border hover:shadow-hover transition-all duration-300 transform hover:scale-105">
        <CardContent className="p-6">
          <div className="flex items-center justify-between">
            <div className="space-y-2">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-8 w-16" />
              <Skeleton className="h-3 w-20" />
            </div>
            <Skeleton className="w-12 h-12 rounded-xl" />
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="bg-gradient-card border-border hover:shadow-hover transition-all duration-300 transform hover:scale-105 cursor-pointer">
      <CardContent className="p-6">
        <div className="flex items-center justify-between">
          <div className="space-y-2">
            <p className="text-sm font-medium text-muted-foreground">{title}</p>
            <p className="text-3xl font-bold text-foreground">{value}</p>
            <div className="flex items-center gap-1">
              {isPositive ? (
                <TrendingUp className="w-4 h-4 text-success" />
              ) : (
                <TrendingDown className="w-4 h-4 text-destructive" />
              )}
              <span className={`text-sm font-medium ${
                isPositive ? "text-success" : "text-destructive"
              }`}>
                {change}
              </span>
              <span className="text-sm text-muted-foreground">vs last month</span>
            </div>
          </div>
          <div className="w-12 h-12 bg-accent rounded-xl flex items-center justify-center">
            {icon}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

const activationData: Array<{ month: string; activations: number; reactivations: number }> = [];

const planDistributionData: Array<{ name: string; value: number; count: number; color: string }> = [];

export default function KeyStats() {
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => setIsLoading(false), 2000);
    return () => clearTimeout(timer);
  }, []);

  const kpiData = [
    {
      title: "Total Keys Generated",
      value: "—",
      change: "0%",
      isPositive: true,
      icon: <Key className="w-6 h-6 text-primary" />
    },
    {
      title: "Active Licenses",
      value: "—",
      change: "0%",
      isPositive: true,
      icon: <Shield className="w-6 h-6 text-success" />
    },
    {
      title: "Revenue This Month",
      value: "—",
      change: "0%",
      isPositive: true,
      icon: <TrendingUp className="w-6 h-6 text-warning" />
    },
    {
      title: "Churn Rate",
      value: "—",
      change: "0%",
      isPositive: true,
      icon: <Database className="w-6 h-6 text-destructive" />
    }
  ];

  if (isLoading) {
    return (
      <div className="space-y-8 animate-fade-in">
        <div className="animate-scale-in">
          <Skeleton className="h-8 w-48 mb-2" />
          <Skeleton className="h-4 w-96" />
        </div>
        
        {/* KPI Skeletons */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="animate-scale-in" style={{ animationDelay: `${150 + (i * 75)}ms` }}>
              <KPICard
                title=""
                value=""
                change=""
                isPositive={true}
                icon={null}
                isLoading={true}
              />
            </div>
          ))}
        </div>

        {/* Chart Skeletons */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Card className="animate-scale-in" style={{ animationDelay: "500ms" }}>
            <CardHeader>
              <Skeleton className="h-6 w-32" />
            </CardHeader>
            <CardContent>
              <Skeleton className="h-80 w-full" />
            </CardContent>
          </Card>
          <Card className="animate-scale-in" style={{ animationDelay: "650ms" }}>
            <CardHeader>
              <Skeleton className="h-6 w-32" />
            </CardHeader>
            <CardContent>
              <Skeleton className="h-80 w-full" />
            </CardContent>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Header */}
      <div>
        <h1 className="text-2xl sm:text-3xl font-bold text-foreground">Key Statistics</h1>
        <p className="text-muted-foreground">
          Comprehensive analytics and insights for your license management
        </p>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {kpiData.map((kpi, index) => (
          <KPICard
            key={index}
            title={kpi.title}
            value={kpi.value}
            change={kpi.change}
            isPositive={kpi.isPositive}
            icon={kpi.icon}
            isLoading={false}
          />
        ))}
      </div>

      {/* Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* License Activations Chart */}
        <Card className="bg-gradient-card border-border">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <TrendingUp className="w-5 h-5 text-primary" />
              License Activations
            </CardTitle>
            <p className="text-sm text-muted-foreground">
              Monthly license activations and reactivations
            </p>
          </CardHeader>
          <CardContent>
            <div className="h-80">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={activationData}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                  <XAxis 
                    dataKey="month" 
                    className="text-muted-foreground"
                    fontSize={12}
                  />
                  <YAxis 
                    className="text-muted-foreground"
                    fontSize={12}
                  />
                  <Tooltip 
                    contentStyle={{
                      backgroundColor: 'hsl(var(--card))',
                      border: '1px solid hsl(var(--border))',
                      borderRadius: '8px'
                    }}
                  />
                  <Legend />
                  <Line 
                    type="monotone" 
                    dataKey="activations" 
                    stroke="hsl(var(--primary))" 
                    strokeWidth={2}
                    name="New Activations"
                    dot={{ fill: 'hsl(var(--primary))', strokeWidth: 2, r: 4 }}
                  />
                  <Line 
                    type="monotone" 
                    dataKey="reactivations" 
                    stroke="hsl(var(--success))" 
                    strokeWidth={2}
                    name="Reactivations"
                    dot={{ fill: 'hsl(var(--success))', strokeWidth: 2, r: 4 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>

        {/* Plan Distribution Chart */}
        <Card className="bg-gradient-card border-border">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Users className="w-5 h-5 text-primary" />
              Plan Distribution
            </CardTitle>
            <p className="text-sm text-muted-foreground">
              Current active licenses by plan type
            </p>
          </CardHeader>
          <CardContent>
            <div className="h-80">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={planDistributionData}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={100}
                    paddingAngle={2}
                    dataKey="value"
                    label={({ name, value }) => `${name}: ${value}%`}
                    labelLine={false}
                    fontSize={12}
                  >
                    {planDistributionData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip 
                    formatter={(value: any, name: any, props: any) => [
                      `${value}% (${props.payload.count} licenses)`,
                      name
                    ]}
                    contentStyle={{
                      backgroundColor: 'hsl(var(--card))',
                      border: '1px solid hsl(var(--border))',
                      borderRadius: '8px'
                    }}
                  />
                  <Legend 
                    wrapperStyle={{ fontSize: '12px' }}
                    formatter={(value, entry) => (
                      <span style={{ color: entry.color }}>
                        {value} ({planDistributionData.find(item => item.name === value)?.count} licenses)
                      </span>
                    )}
                  />
                </PieChart>
              </ResponsiveContainer>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Additional Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card className="bg-gradient-card border-border hover:shadow-hover transition-all duration-300">
          <CardContent className="p-6 text-center">
            <div className="w-12 h-12 bg-primary/10 rounded-lg flex items-center justify-center mx-auto mb-4">
              <Key className="w-6 h-6 text-primary" />
            </div>
            <h3 className="font-semibold text-lg mb-2">Average Revenue Per User</h3>
            <p className="text-3xl font-bold text-primary mb-1">—</p>
            <p className="text-sm text-muted-foreground">—</p>
          </CardContent>
        </Card>

        <Card className="bg-gradient-card border-border hover:shadow-hover transition-all duration-300">
          <CardContent className="p-6 text-center">
            <div className="w-12 h-12 bg-success/10 rounded-lg flex items-center justify-center mx-auto mb-4">
              <Shield className="w-6 h-6 text-success" />
            </div>
            <h3 className="font-semibold text-lg mb-2">License Utilization</h3>
            <p className="text-3xl font-bold text-success mb-1">—</p>
            <p className="text-sm text-muted-foreground">—</p>
          </CardContent>
        </Card>

        <Card className="bg-gradient-card border-border hover:shadow-hover transition-all duration-300">
          <CardContent className="p-6 text-center">
            <div className="w-12 h-12 bg-warning/10 rounded-lg flex items-center justify-center mx-auto mb-4">
              <TrendingUp className="w-6 h-6 text-warning" />
            </div>
            <h3 className="font-semibold text-lg mb-2">Growth Rate</h3>
            <p className="text-3xl font-bold text-warning mb-1">—</p>
            <p className="text-sm text-muted-foreground">—</p>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}