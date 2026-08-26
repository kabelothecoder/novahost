import { ReactNode } from 'react';
import { Card } from '@/components/ui/card';
interface AuthLayoutProps {
  children: ReactNode;
}
export function AuthLayout({
  children
}: AuthLayoutProps) {
  return <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <div className="w-full max-w-sm md:w-[480px]">
        <div className="flex justify-center mb-8">
          <img 
            src="/logo.svg" 
            alt="NovaHost Logo" 
            className="h-10 w-auto select-none" 
            draggable={false} 
          />
        </div>
        
        {/* Centered Card */}
        <Card className="p-6 shadow-lg border-border bg-card">
          {children}
        </Card>
      </div>
    </div>;
}