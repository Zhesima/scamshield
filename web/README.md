# ScamShield Landing

Next.js 15 + Tailwind 3 static landing page for ScamShield.

## Local dev

```bash
cd web
npm install
npm run dev
# open http://localhost:3000
```

## Deploy to Vercel

### Option A: from GitHub (recommended, auto deploy on push)

1. Login to https://vercel.com (use "Continue with GitHub")
2. Click **Add New → Project**
3. Pick `Zhesima/scamshield` repo
4. Vercel detects Next.js automatically
5. **Important**: set "Root Directory" to `web` (this is a monorepo subdir)
6. Leave Build Command / Output Directory at defaults
7. Click **Deploy** — first build takes ~1 min

Production URL: `https://scamshield-xxx.vercel.app` (Vercel auto-assigns)

To use a custom domain (`scamshield.xyz`):

1. Project Settings → Domains → Add
2. Buy domain on Namecheap / Cloudflare / etc.
3. Point DNS A/CNAME per Vercel instructions

### Option B: Vercel CLI

```bash
npm i -g vercel
cd web
vercel
# follow prompts
```

## Customize

All editable content is in `app/page.tsx`. Top-level constants:

```typescript
const CONTRACT = "0xC4a564eb6AE006574c5a6B9A9c6cb1406eaEfaCF";
const GITHUB   = "https://github.com/Zhesima/scamshield";
const TWITTER  = "https://twitter.com/MarcoMa_9527";
```

Update these when:
- Contract redeployed to mainnet
- GitHub repo renamed
- New social handle

## Stack

| Tool | Why |
|---|---|
| Next.js 15 App Router | Static export ready, Vercel-native |
| Tailwind 3 | Utility-first, small bundle |
| TypeScript | Catch typos in handles / addresses |
| No DB / no API routes | Pure static, no runtime cost |

## Why static (no live API call)

Backend runs on your laptop (`localhost:8086`). Vercel can't reach it. Live demo
uses **hardcoded real data** (AERO scored 100, published on Base Sepolia) +
links to Basescan so judges can verify on-chain. That's stronger than a "spinner
that calls localhost" anyway — it shows the data is **already on-chain and
public**.

Future: deploy backend to Railway/Fly.io/Render and replace hardcoded demo
section with live API call (1-line change in `page.tsx`).
