import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const { licenseKey, email, eaName, planName } = await req.json().catch(() => ({}));

    if (!licenseKey || !email || !eaName || !planName) {
      return new Response(JSON.stringify({ error: 'Missing licenseKey, email, eaName, or planName' }), { 
        status: 400, 
        headers: { 'Content-Type': 'application/json', ...corsHeaders } 
      });
    }

    const resendApiKey = Deno.env.get('RESEND_API_KEY');
    const smtpHost = Deno.env.get('SMTP_HOST');

    const htmlContent = `
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="UTF-8">
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <title>Your NovaHost License Key</title>
      <style>
        body {
          margin: 0;
          padding: 0;
          background-color: #0b0f19;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
          color: #f3f4f6;
        }
        .container {
          max-width: 600px;
          margin: 0 auto;
          padding: 40px 20px;
        }
        .header {
          text-align: center;
          margin-bottom: 40px;
        }
        .logo {
          font-size: 28px;
          font-weight: 800;
          letter-spacing: -0.05em;
          background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          margin: 0 0 10px 0;
        }
        .card {
          background-color: #111827;
          border: 1px solid #1f2937;
          border-radius: 24px;
          padding: 40px;
          text-align: center;
          box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.05);
        }
        h1 {
          font-size: 24px;
          font-weight: 700;
          color: #ffffff;
          margin-top: 0;
          margin-bottom: 10px;
        }
        p.subtitle {
          font-size: 14px;
          color: #9ca3af;
          margin-bottom: 30px;
        }
        .ea-badge {
          display: inline-block;
          background: rgba(59, 130, 246, 0.1);
          color: #60a5fa;
          border: 1px solid rgba(59, 130, 246, 0.2);
          border-radius: 12px;
          padding: 6px 16px;
          font-size: 12px;
          font-weight: 600;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          margin-bottom: 20px;
        }
        .key-box {
          background: linear-gradient(90deg, rgba(59, 130, 246, 0.15) 0%, rgba(139, 92, 246, 0.15) 100%);
          border: 1px solid rgba(59, 130, 246, 0.3);
          border-radius: 16px;
          padding: 18px;
          margin: 20px 0;
          font-family: "Courier New", Courier, monospace;
          font-size: 20px;
          font-weight: 700;
          color: #ffffff;
          letter-spacing: 2px;
          word-break: break-all;
        }
        .cta-button {
          display: block;
          background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
          color: #ffffff !important;
          text-decoration: none;
          font-weight: 600;
          font-size: 14px;
          text-transform: uppercase;
          letter-spacing: 0.05em;
          padding: 16px 24px;
          border-radius: 14px;
          margin-top: 30px;
          box-shadow: 0 4px 14px rgba(59, 130, 246, 0.4);
        }
        .footer {
          text-align: center;
          margin-top: 40px;
          font-size: 11px;
          color: #4b5563;
        }
        .footer a {
          color: #4b5563;
          text-decoration: underline;
        }
      </style>
    </head>
    <body>
      <div class="container">
        <div class="header">
          <div class="logo">METAHOST</div>
          <div style="font-size: 12px; color: #4b5563; text-transform: uppercase; letter-spacing: 0.15em;">Expert Advisor Manager</div>
        </div>
        
        <div class="card">
          <span class="ea-badge">${eaName} • ${planName}</span>
          <h1>License Key Provisioned</h1>
          <p class="subtitle">Your professional automated trading access has been generated.</p>
          
          <div style="color: #9ca3af; font-size: 12px; text-transform: uppercase; letter-spacing: 0.1em; margin-bottom: 5px;">Your Activation Key</div>
          <div class="key-box">${licenseKey}</div>
          
          <p style="color: #9ca3af; font-size: 13px; line-height: 1.5; margin: 20px 0;">
            To activate your Expert Advisor, download the NovaHost mobile app, select your broker account, and paste the activation key into the license section.
          </p>
          
          <a href="https://novahost.co/download" class="cta-button">Download App & Activate</a>
        </div>
        
        <div class="footer">
          <p>This email was sent automatically by NovaHost Admin Portal on behalf of your administrator.</p>
          <p>© 2026 NovaHost. All rights reserved.</p>
        </div>
      </div>
    </body>
    </html>
    `;

    if (resendApiKey) {
      console.log('send-license-email: Sending via Resend API');
      const res = await fetch('https://api.resend.com/emails', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${resendApiKey}`,
        },
        body: JSON.stringify({
          from: 'NovaHost <onboarding@resend.dev>', // resend sandbox domain or custom verified domain
          to: [email],
          subject: `Your NovaHost License for ${eaName} is Ready`,
          html: htmlContent,
        }),
      });

      if (!res.ok) {
        const errText = await res.text();
        console.error('send-license-email: Resend API error', errText);
        throw new Error(`Resend transmission failed: ${errText}`);
      }

      const resData = await res.json();
      return new Response(JSON.stringify({ success: true, provider: 'resend', id: resData.id }), {
        status: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    } else if (smtpHost) {
      console.log('send-license-email: Sending via SMTP');
      // Import safe_smtp dynamically to avoid bundling issues
      const { SMTPClient } = await import("https://deno.land/x/safe_smtp/mod.ts");
      
      const client = new SMTPClient({
        connection: {
          hostname: smtpHost,
          port: parseInt(Deno.env.get('SMTP_PORT') || '587'),
          tls: true,
          auth: {
            username: Deno.env.get('SMTP_USER') || '',
            password: Deno.env.get('SMTP_PASS') || '',
          }
        }
      });

      await client.send({
        from: 'NovaHost Admin <no-reply@novahost.co>',
        to: email,
        subject: `Your NovaHost License for ${eaName} is Ready`,
        html: htmlContent,
      });

      await client.close();

      return new Response(JSON.stringify({ success: true, provider: 'smtp' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    } else {
      console.error('send-license-email: No email credentials found. Production environment is not configured.');
      return new Response(JSON.stringify({ 
        error: 'No email service credentials configured. Please set RESEND_API_KEY or SMTP_HOST in Supabase secrets.' 
      }), {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders }
      });
    }

  } catch (e) {
    console.error('send-license-email: Error', e);
    return new Response(JSON.stringify({ error: 'Unexpected error sending email', details: String(e) }), { 
      status: 500, 
      headers: { 'Content-Type': 'application/json', ...corsHeaders } 
    });
  }
});
