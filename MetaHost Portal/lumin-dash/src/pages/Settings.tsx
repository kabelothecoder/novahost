import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useToast } from '@/hooks/use-toast';
import { Separator } from '@/components/ui/separator';

export default function Settings() {
  const { toast } = useToast();
  const [isLoading, setIsLoading] = useState(false);
  
  const [settings, setSettings] = useState({
    emailNotifications: true,
    smsAlerts: false
  });

  const handleSave = async () => {
    setIsLoading(true);
    
    // Simulate API call
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    toast({
      title: "Settings saved",
      description: "Your preferences have been successfully updated.",
    });
    
    setIsLoading(false);
  };

  const updateSetting = (key: keyof typeof settings, value: boolean | string) => {
    setSettings(prev => ({ ...prev, [key]: value }));
  };

  return (
    <div className="max-w-2xl mx-auto space-y-6 animate-fade-in">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Settings</h1>
        <p className="text-muted-foreground">Manage your application preferences and notifications.</p>
      </div>

      <Card className="hover-scale transition-all duration-300 hover:shadow-lg">
        <CardHeader>
          <CardTitle>Notifications</CardTitle>
          <CardDescription>Configure how you receive alerts and updates.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label className="text-base">Email Notifications</Label>
              <p className="text-sm text-muted-foreground">
                Receive important updates via email
              </p>
            </div>
            <Switch
              checked={settings.emailNotifications}
              onCheckedChange={(checked) => updateSetting('emailNotifications', checked)}
            />
          </div>
          
          <Separator />
          
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label className="text-base">SMS Alerts</Label>
              <p className="text-sm text-muted-foreground">
                Get urgent notifications via text message
              </p>
            </div>
            <Switch
              checked={settings.smsAlerts}
              onCheckedChange={(checked) => updateSetting('smsAlerts', checked)}
            />
          </div>
        </CardContent>
      </Card>




      <Button onClick={handleSave} disabled={isLoading} className="w-full md:w-auto hover-scale transition-all duration-200 hover:shadow-md">
        {isLoading ? "Saving..." : "Save Settings"}
      </Button>
    </div>
  );
}