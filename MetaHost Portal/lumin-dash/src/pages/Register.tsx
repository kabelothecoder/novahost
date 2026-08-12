import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthLayout } from '@/components/AuthLayout';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Checkbox } from '@/components/ui/checkbox';
import { TermsModal } from '@/components/TermsModal';
import { Eye, EyeOff } from 'lucide-react';
import { supabase } from '@/integrations/supabase/client';
import { toast } from '@/hooks/use-toast';
export default function Register() {
  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [termsModalOpen, setTermsModalOpen] = useState(false);
  const [agreeToTerms, setAgreeToTerms] = useState(false);
  const [formData, setFormData] = useState({
    fullName: '',
    displayName: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
    instagramLink: '',
    tiktokLink: '',
    telegramGroupLink: '',
    whatsappGroupLink: ''
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const validateForm = () => {
    const newErrors: Record<string, string> = {};

    const isValidUrl = (url: string) => {
      try {
        return /^(https?:\/\/)?([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(\/.*)?$/.test(url);
      } catch {
        return false;
      }
    };

    // Required fields
    if (!formData.fullName.trim()) newErrors.fullName = 'Full name is required';
    if (!formData.displayName.trim()) newErrors.displayName = 'Display name is required';
    if (!formData.email.trim()) newErrors.email = 'Email is required';
    if (!formData.phone.trim()) newErrors.phone = 'Phone is required';
    if (!formData.password) newErrors.password = 'Password is required';
    if (!formData.confirmPassword) newErrors.confirmPassword = 'Confirm password is required';

    // Instagram (Required)
    if (!formData.instagramLink.trim()) {
      newErrors.instagramLink = 'Instagram link is required';
    } else if (!isValidUrl(formData.instagramLink)) {
      newErrors.instagramLink = 'Please enter a valid URL';
    }

    // TikTok (Required)
    if (!formData.tiktokLink.trim()) {
      newErrors.tiktokLink = 'TikTok link is required';
    } else if (!isValidUrl(formData.tiktokLink)) {
      newErrors.tiktokLink = 'Please enter a valid URL';
    }

    // Telegram (Optional)
    if (formData.telegramGroupLink.trim() && !isValidUrl(formData.telegramGroupLink)) {
      newErrors.telegramGroupLink = 'Please enter a valid URL';
    }

    // WhatsApp (Optional)
    if (formData.whatsappGroupLink.trim() && !isValidUrl(formData.whatsappGroupLink)) {
      newErrors.whatsappGroupLink = 'Please enter a valid URL';
    }

    // Email format
    if (formData.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      newErrors.email = 'Invalid email format';
    }

    // Phone regex
    if (formData.phone && !/^\+?\d{9,15}$/.test(formData.phone)) {
      newErrors.phone = 'Phone must be 9-15 digits, optionally starting with +';
    }

    // Password minimum 8 characters
    if (formData.password && formData.password.length < 8) {
      newErrors.password = 'Password must be at least 8 characters';
    }

    // Password confirmation
    if (formData.password !== formData.confirmPassword) {
      newErrors.confirmPassword = 'Passwords do not match';
    }

    // Terms agreement
    if (!agreeToTerms) {
      newErrors.terms = 'You must agree to the Terms & Conditions';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validateForm()) return;
    setIsLoading(true);
    try {
      const redirectUrl = `${window.location.origin}/`;
      
      const { error } = await supabase.auth.signUp({
        email: formData.email,
        password: formData.password,
        options: {
          emailRedirectTo: redirectUrl,
          data: {
            fullName: formData.fullName,
            displayName: formData.displayName,
            phone: formData.phone,
            instagramLink: formData.instagramLink.trim(),
            tiktokLink: formData.tiktokLink.trim(),
            telegramGroupLink: formData.telegramGroupLink.trim() || null,
            whatsappGroupLink: formData.whatsappGroupLink.trim() || null
          }
        }
      });
      
      if (error) throw error;
      
      toast({
        title: "Success",
        description: "Account created, check your inbox to verify"
      });
      navigate('/login');
    } catch (error: any) {
      toast({
        title: "Error",
        description: error.message,
        variant: "destructive"
      });
    } finally {
      setIsLoading(false);
    }
  };
  const handleInputChange = (field: string, value: string) => {
    setFormData(prev => ({
      ...prev,
      [field]: value
    }));
    if (errors[field]) {
      setErrors(prev => ({
        ...prev,
        [field]: ''
      }));
    }
  };
  return <AuthLayout>
      <div className="space-y-6">
        <div className="text-center">
          <h2 className="text-2xl font-bold text-foreground">Create Account</h2>
          <p className="text-muted-foreground mt-1">Join today</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="fullName">Full Name</Label>
            <Input id="fullName" type="text" placeholder="Enter your full name" value={formData.fullName} onChange={e => handleInputChange('fullName', e.target.value)} className={`focus:ring-2 focus:ring-accent ${errors.fullName ? 'border-destructive' : ''}`} aria-describedby={errors.fullName ? 'fullName-error' : undefined} />
            {errors.fullName && <p id="fullName-error" className="text-sm text-destructive">{errors.fullName}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="displayName">Display Name</Label>
            <Input id="displayName" type="text" placeholder="Enter your display name" value={formData.displayName} onChange={e => handleInputChange('displayName', e.target.value)} className={`focus:ring-2 focus:ring-accent ${errors.displayName ? 'border-destructive' : ''}`} aria-describedby={errors.displayName ? 'displayName-error' : undefined} />
            {errors.displayName && <p id="displayName-error" className="text-sm text-destructive">{errors.displayName}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="email">Email</Label>
            <Input id="email" type="email" placeholder="Enter your email" value={formData.email} onChange={e => handleInputChange('email', e.target.value)} className={`focus:ring-2 focus:ring-accent ${errors.email ? 'border-destructive' : ''}`} aria-describedby={errors.email ? 'email-error' : undefined} />
            {errors.email && <p id="email-error" className="text-sm text-destructive">{errors.email}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="phone">Phone</Label>
            <Input id="phone" type="tel" placeholder="+1234567890" value={formData.phone} onChange={e => handleInputChange('phone', e.target.value)} className={`focus:ring-2 focus:ring-accent ${errors.phone ? 'border-destructive' : ''}`} aria-describedby={errors.phone ? 'phone-error' : undefined} />
            {errors.phone && <p id="phone-error" className="text-sm text-destructive">{errors.phone}</p>}
          </div>

          <div className="pt-2 pb-1 border-t border-border/50">
            <h3 className="text-sm font-semibold text-foreground mb-3 flex items-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
              Onboarding Channels
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="instagramLink">Instagram Link <span className="text-destructive">*</span></Label>
                <Input 
                  id="instagramLink" 
                  type="url" 
                  placeholder="https://instagram.com/yourprofile" 
                  value={formData.instagramLink} 
                  onChange={e => handleInputChange('instagramLink', e.target.value)} 
                  className={`focus:ring-2 focus:ring-accent ${errors.instagramLink ? 'border-destructive' : ''}`} 
                  aria-describedby={errors.instagramLink ? 'instagramLink-error' : undefined} 
                />
                {errors.instagramLink && <p id="instagramLink-error" className="text-sm text-destructive">{errors.instagramLink}</p>}
              </div>

              <div className="space-y-2">
                <Label htmlFor="tiktokLink">TikTok Link <span className="text-destructive">*</span></Label>
                <Input 
                  id="tiktokLink" 
                  type="url" 
                  placeholder="https://tiktok.com/@yourprofile" 
                  value={formData.tiktokLink} 
                  onChange={e => handleInputChange('tiktokLink', e.target.value)} 
                  className={`focus:ring-2 focus:ring-accent ${errors.tiktokLink ? 'border-destructive' : ''}`} 
                  aria-describedby={errors.tiktokLink ? 'tiktokLink-error' : undefined} 
                />
                {errors.tiktokLink && <p id="tiktokLink-error" className="text-sm text-destructive">{errors.tiktokLink}</p>}
              </div>

              <div className="space-y-2">
                <Label htmlFor="telegramGroupLink">Telegram Group Link <span className="text-muted-foreground text-xs">(Optional)</span></Label>
                <Input 
                  id="telegramGroupLink" 
                  type="url" 
                  placeholder="https://t.me/yourgroup" 
                  value={formData.telegramGroupLink} 
                  onChange={e => handleInputChange('telegramGroupLink', e.target.value)} 
                  className={`focus:ring-2 focus:ring-accent ${errors.telegramGroupLink ? 'border-destructive' : ''}`} 
                  aria-describedby={errors.telegramGroupLink ? 'telegramGroupLink-error' : undefined} 
                />
                {errors.telegramGroupLink && <p id="telegramGroupLink-error" className="text-sm text-destructive">{errors.telegramGroupLink}</p>}
              </div>

              <div className="space-y-2">
                <Label htmlFor="whatsappGroupLink">WhatsApp Group Link <span className="text-muted-foreground text-xs">(Optional)</span></Label>
                <Input 
                  id="whatsappGroupLink" 
                  type="url" 
                  placeholder="https://chat.whatsapp.com/yourgroup" 
                  value={formData.whatsappGroupLink} 
                  onChange={e => handleInputChange('whatsappGroupLink', e.target.value)} 
                  className={`focus:ring-2 focus:ring-accent ${errors.whatsappGroupLink ? 'border-destructive' : ''}`} 
                  aria-describedby={errors.whatsappGroupLink ? 'whatsappGroupLink-error' : undefined} 
                />
                {errors.whatsappGroupLink && <p id="whatsappGroupLink-error" className="text-sm text-destructive">{errors.whatsappGroupLink}</p>}
              </div>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="password">Password</Label>
            <div className="relative">
              <Input id="password" type={showPassword ? 'text' : 'password'} placeholder="Enter your password" value={formData.password} onChange={e => handleInputChange('password', e.target.value)} className={`focus:ring-2 focus:ring-accent pr-10 ${errors.password ? 'border-destructive' : ''}`} aria-describedby={errors.password ? 'password-error' : undefined} />
              <Button type="button" variant="ghost" size="icon" className="absolute right-0 top-0 h-full px-3" onClick={() => setShowPassword(!showPassword)} aria-label={showPassword ? 'Hide password' : 'Show password'}>
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </Button>
            </div>
            {errors.password && <p id="password-error" className="text-sm text-destructive">{errors.password}</p>}
          </div>

          <div className="space-y-2">
            <Label htmlFor="confirmPassword">Confirm Password</Label>
            <div className="relative">
              <Input id="confirmPassword" type={showConfirmPassword ? 'text' : 'password'} placeholder="Confirm your password" value={formData.confirmPassword} onChange={e => handleInputChange('confirmPassword', e.target.value)} className={`focus:ring-2 focus:ring-accent pr-10 ${errors.confirmPassword ? 'border-destructive' : ''}`} aria-describedby={errors.confirmPassword ? 'confirmPassword-error' : undefined} />
              <Button type="button" variant="ghost" size="icon" className="absolute right-0 top-0 h-full px-3" onClick={() => setShowConfirmPassword(!showConfirmPassword)} aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}>
                {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </Button>
            </div>
            {errors.confirmPassword && <p id="confirmPassword-error" className="text-sm text-destructive">{errors.confirmPassword}</p>}
          </div>

          <div className="space-y-2">
            <div className="flex items-center space-x-2">
              <Checkbox id="terms" checked={agreeToTerms} onCheckedChange={checked => {
              setAgreeToTerms(checked as boolean);
              if (errors.terms) {
                setErrors(prev => ({
                  ...prev,
                  terms: ''
                }));
              }
            }} aria-describedby={errors.terms ? 'terms-error' : undefined} />
              <label htmlFor="terms" className="text-sm text-muted-foreground">
                I agree to{' '}
                <button type="button" onClick={() => setTermsModalOpen(true)} className="text-primary hover:underline">
                  Terms & Conditions
                </button>
              </label>
            </div>
            {errors.terms && <p id="terms-error" className="text-sm text-destructive">{errors.terms}</p>}
          </div>

          <Button type="submit" disabled={isLoading} className="w-full h-12 rounded-xl bg-gradient-to-r from-primary to-purple-600 hover:from-primary/90 hover:to-purple-600/90 font-bold text-primary-foreground transition-all duration-200">
            {isLoading ? 'Creating Account...' : 'Create Account'}
          </Button>
        </form>

        <div className="text-center">
          <p className="text-sm text-muted-foreground">
            Already have an account?{' '}
            <Link to="/login" className="text-primary hover:underline">
              Sign In
            </Link>
          </p>
        </div>
      </div>

      <TermsModal open={termsModalOpen} onOpenChange={setTermsModalOpen} />
    </AuthLayout>;
}