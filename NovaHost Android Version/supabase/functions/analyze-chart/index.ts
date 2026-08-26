import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const OPENAI_API_KEY = Deno.env.get('OPENAI_API_KEY')

serve(async (req: Request) => {
  // Handle CORS
  if (req.method === 'OPTIONS') {
    return new Response('ok', { 
      headers: { 
        'Access-Control-Allow-Origin': '*', 
        'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
        'Access-Control-Allow-Methods': 'POST, OPTIONS'
      } 
    })
  }

  try {
    const { image, pair } = await req.json()

    if (!image) {
      return new Response(JSON.stringify({ error: 'No image provided' }), { status: 400 })
    }

    const response = await fetch('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${OPENAI_API_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: "gpt-4o",
        messages: [
          {
            role: "system",
            content: "You are a professional SMC (Smart Money Concepts) trader. Analyze the provided chart screenshot accurately."
          },
          {
            role: "user",
            content: [
              { 
                type: "text", 
                text: `Analyze this ${pair || 'trading'} chart. Identify SMC patterns like Change of Character (ChoCH), Order Blocks, and Liquidity Sweeps. Suggest the most likely trade direction (BUY or SELL), a precise Entry point, Stop Loss (SL), and Take Profit (TP). Return the results in a clean JSON format with keys: action (BUY/SELL), entry, sl, tp, confidence, and patterns (array of strings).` 
              },
              {
                type: "image_url",
                image_url: {
                  url: `data:image/jpeg;base64,${image}`,
                },
              },
            ],
          },
        ],
        response_format: { type: "json_object" }
      }),
    })

    const data = await response.json()
    
    if (data.error) {
        throw new Error(data.error.message)
    }

    const result = JSON.parse(data.choices[0].message.content)

    return new Response(JSON.stringify(result), {
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
    })
  } catch (error) {
    console.error(error)
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' },
    })
  }
})
