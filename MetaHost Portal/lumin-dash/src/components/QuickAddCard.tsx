import { Plus, Bot } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { useNavigate } from "react-router-dom";
import { useToast } from "@/hooks/use-toast";

export function QuickAddCard() {
  const navigate = useNavigate();
  const { toast } = useToast();

  const handleAddEA = () => {
    navigate("/manage");
    toast({
      title: "Add Expert Advisor",
      description: "Opening EA management to register a new Expert Advisor",
    });
  };

  return (
    <Card 
      onClick={handleAddEA}
      className="bg-gradient-primary border-none text-primary-foreground hover:shadow-hover transition-all duration-300 transform hover:scale-105 cursor-pointer group"
    >
      <CardContent className="p-6">
        <div className="flex flex-col items-center text-center space-y-4">
          <div className="w-16 h-16 bg-white/20 rounded-2xl flex items-center justify-center group-hover:bg-white/30 transition-colors">
            <Plus className="w-8 h-8" />
          </div>
          
          <div className="space-y-2">
            <h3 className="text-xl font-semibold">Add New EA</h3>
            <p className="text-sm text-primary-foreground/80">
              Register a new Expert Advisor in the system
            </p>
          </div>

          <div className="w-full pt-2">
            <Button 
              onClick={(e) => { e.stopPropagation(); handleAddEA(); }}
              variant="secondary" 
              className="w-full bg-white/20 hover:bg-white/30 text-primary-foreground border-none transition-all duration-200 hover:scale-105"
            >
              <Bot className="w-4 h-4 mr-2" />
              Register Expert Advisor
            </Button>
          </div>

          <div className="pt-2 border-t border-white/20 w-full">
            <p className="text-xs text-primary-foreground/60">
              Click to start the EA registration process
            </p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}