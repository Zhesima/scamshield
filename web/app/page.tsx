const CONTRACT = "0xC4a564eb6AE006574c5a6B9A9c6cb1406eaEfaCF";
const DEPLOY_TX =
  "0xd11bb619598c481c6daadd6dd2304579bbaa02af1457310bf00c1ace4ce6eac3";
const LATEST_PUBLISH_TX =
  "0x2c73069a6ec698d1766a6644f02ad9e426ada47579fffebf6fd2063feb2b1370";
const SIGNER = "0x2c7536e3605d9c16a7a3d7b1898e529396a65c23";
const GITHUB = "https://github.com/Zhesima/scamshield";
const TWITTER = "https://twitter.com/MarcoMa_9527";
const BASESCAN_CONTRACT = `https://sepolia.basescan.org/address/${CONTRACT}`;
const BASESCAN_DEPLOY = `https://sepolia.basescan.org/tx/${DEPLOY_TX}`;
const BASESCAN_PUBLISH = `https://sepolia.basescan.org/tx/${LATEST_PUBLISH_TX}`;

export default function Home() {
  return (
    <main className="min-h-screen bg-base-dark text-white">
      {/* ── NAV ───────────────────────────────────────────── */}
      <nav className="mx-auto flex max-w-6xl items-center justify-between px-6 py-6">
        <div className="flex items-center gap-2">
          <span className="text-2xl">🛡️</span>
          <span className="text-lg font-bold tracking-tight">ScamShield</span>
        </div>
        <div className="flex items-center gap-6 text-sm">
          <a href="#how" className="text-zinc-400 hover:text-white">How it works</a>
          <a href="#live" className="text-zinc-400 hover:text-white">Live demo</a>
          <a href={GITHUB} target="_blank" rel="noopener" className="text-zinc-400 hover:text-white">GitHub</a>
          <a
            href={TWITTER}
            target="_blank"
            rel="noopener"
            className="rounded-md bg-white px-3 py-1.5 text-sm font-medium text-black hover:bg-zinc-200"
          >
            Follow on X
          </a>
        </div>
      </nav>

      {/* ── HERO ──────────────────────────────────────────── */}
      <section className="mx-auto max-w-6xl px-6 pt-16 pb-24">
        <div className="mb-6">
          <span className="inline-flex items-center gap-2 rounded-full border border-base-blue/30 bg-base-blue/10 px-3 py-1 text-xs font-medium text-base-blue">
            <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-base-blue" />
            Live on Base Sepolia
          </span>
        </div>

        <h1 className="text-balance text-5xl font-bold leading-tight md:text-7xl">
          The trust layer{" "}
          <span className="text-warn-orange">AI agents</span> call before any swap.
        </h1>

        <p className="mt-6 max-w-2xl text-balance text-lg text-zinc-400 md:text-xl">
          Signed token risk scores on Base. Paid via{" "}
          <span className="font-mono text-base-blue">x402</span> in $0.001 USDC.
          On-chain verifiable, replay-protected, agent-native.
        </p>

        <div className="mt-10 flex flex-wrap items-center gap-4">
          <a
            href={BASESCAN_CONTRACT}
            target="_blank"
            rel="noopener"
            className="rounded-md bg-base-blue px-5 py-3 font-medium text-white hover:bg-base-blue/90"
          >
            View contract on Basescan →
          </a>
          <a
            href={GITHUB}
            target="_blank"
            rel="noopener"
            className="rounded-md border border-zinc-700 px-5 py-3 font-medium text-white hover:border-zinc-500"
          >
            View on GitHub
          </a>
        </div>

        {/* terminal-style "1-line integration" */}
        <div className="mt-16 overflow-x-auto rounded-lg border border-zinc-800 bg-zinc-900/50">
          <div className="flex items-center gap-2 border-b border-zinc-800 px-4 py-2 text-xs text-zinc-500">
            <span className="h-3 w-3 rounded-full bg-red-500/60" />
            <span className="h-3 w-3 rounded-full bg-yellow-500/60" />
            <span className="h-3 w-3 rounded-full bg-green-500/60" />
            <span className="ml-2 font-mono">1-line integration</span>
          </div>
          <pre className="p-6 text-sm leading-relaxed text-zinc-300">
            <code className="font-mono">
              <span className="text-zinc-500">// In your agent's swap handler:</span>{"\n"}
              <span className="text-warn-orange">const</span> score = <span className="text-warn-orange">await</span> ScamShield.<span className="text-base-blue">getScore</span>({"{"}
              {"\n  "}chainId: <span className="text-green-400">8453</span>,
              {"\n  "}token: <span className="text-green-400">{`"0x940181a94...8631"`}</span>,
              {"\n  "}payment: x402Wallet,  <span className="text-zinc-500">{`// pays $0.001 USDC`}</span>
              {"\n"}{"}"});
              {"\n\n"}<span className="text-warn-orange">if</span> (score.value &lt; agent.riskTolerance) <span className="text-warn-orange">return</span> abort();{"\n"}
              <span className="text-zinc-500">// otherwise proceed — signature is verifiable on Base.</span>
            </code>
          </pre>
        </div>
      </section>

      {/* ── HOW IT WORKS ──────────────────────────────────── */}
      <section id="how" className="border-t border-zinc-800 py-24">
        <div className="mx-auto max-w-6xl px-6">
          <p className="text-xs font-medium uppercase tracking-widest text-base-blue">
            How it works
          </p>
          <h2 className="mt-3 text-balance text-4xl font-bold md:text-5xl">
            Three-tier trust model
          </h2>
          <p className="mt-4 max-w-2xl text-zinc-400">
            Each tier strictly stronger than the last. T3 is the on-chain oracle
            anyone can verify with one <span className="font-mono">cast call</span>.
          </p>

          <div className="mt-12 grid gap-6 md:grid-cols-3">
            <TierCard
              tier="T1"
              title="Soft signals"
              accent="text-zinc-400"
              border="border-zinc-700"
              points={[
                "CoinGecko + DefiLlama metadata",
                "GitHub dev activity",
                "Social reach signals",
              ]}
              trust="⭐⭐ Off-chain"
            />
            <TierCard
              tier="T2"
              title="Hard data + signature"
              accent="text-base-blue"
              border="border-base-blue/40"
              points={[
                "Base RPC: totalSupply",
                "DexScreener: liquidity + 24h volume",
                "secp256k1-signed, block-pinned",
              ]}
              trust="⭐⭐⭐⭐ Cryptographic"
            />
            <TierCard
              tier="T3"
              title="On-chain oracle"
              accent="text-warn-orange"
              border="border-warn-orange/40"
              points={[
                "HealthOracle.sol on Base",
                "ecrecover verified on-chain",
                "Replay-protected (timestamp ↑)",
              ]}
              trust="⭐⭐⭐⭐⭐ Contract-grade"
            />
          </div>
        </div>
      </section>

      {/* ── LIVE DEMO ─────────────────────────────────────── */}
      <section id="live" className="border-t border-zinc-800 bg-zinc-950/50 py-24">
        <div className="mx-auto max-w-6xl px-6">
          <p className="text-xs font-medium uppercase tracking-widest text-warn-orange">
            Live on Base Sepolia
          </p>
          <h2 className="mt-3 text-balance text-4xl font-bold md:text-5xl">
            Don't trust. Verify.
          </h2>
          <p className="mt-4 max-w-2xl text-zinc-400">
            Real score, real signature, on a real public chain. Run the{" "}
            <span className="font-mono">cast</span> command — anyone can.
          </p>

          {/* Score card */}
          <div className="mt-12 grid gap-6 lg:grid-cols-2">
            <div className="rounded-xl border border-zinc-800 bg-black p-8">
              <div className="flex items-baseline justify-between">
                <span className="text-xs uppercase tracking-widest text-zinc-500">
                  Token
                </span>
                <span className="rounded-full bg-green-500/10 px-2 py-0.5 text-xs font-medium text-green-400">
                  healthy
                </span>
              </div>
              <p className="mt-2 font-mono text-sm text-zinc-300">
                Aerodrome (AERO) on Base
              </p>
              <p className="break-all font-mono text-xs text-zinc-500">
                0x940181a94A35A4569E4529A3CDfB74e38FD98631
              </p>

              <div className="mt-8 flex items-end gap-3">
                <span className="text-7xl font-bold text-green-400 tabular-nums">
                  100
                </span>
                <span className="pb-2 text-zinc-500">/ 100</span>
              </div>

              <div className="mt-8 space-y-2 text-sm">
                <Row label="Signer" value={`${SIGNER.slice(0, 10)}…${SIGNER.slice(-6)}`} mono />
                <Row label="Chain" value="Base (8453)" />
                <Row label="Block-pinned" value="45762445" />
                <Row label="Signature scheme" value="secp256k1 (EIP-191)" />
              </div>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-black p-8">
              <p className="text-xs uppercase tracking-widest text-zinc-500">
                Verify yourself — run this in any terminal
              </p>
              <pre className="mt-4 overflow-x-auto rounded-md border border-zinc-800 bg-zinc-950 p-4 text-xs leading-relaxed text-zinc-300">
                <code className="font-mono">
                  {`cast call ${CONTRACT.slice(0, 10)}…\n  "getScoreWithAge(uint256,address)\n    (uint8,uint256,address)" \\\n  8453 \\\n  0x940181a9…8631 \\\n  --rpc-url https://sepolia.base.org`}
                </code>
              </pre>
              <p className="mt-4 text-xs text-zinc-500">Returns:</p>
              <pre className="mt-2 rounded-md border border-zinc-800 bg-zinc-950 p-4 text-xs text-zinc-300">
                <code className="font-mono">
                  100{"\n"}
                  &lt;age in seconds&gt;{"\n"}
                  {SIGNER}
                </code>
              </pre>
              <div className="mt-6 flex flex-wrap gap-3 text-sm">
                <a
                  href={BASESCAN_CONTRACT}
                  target="_blank"
                  rel="noopener"
                  className="rounded-md border border-zinc-700 px-3 py-1.5 hover:border-zinc-500"
                >
                  Contract ↗
                </a>
                <a
                  href={BASESCAN_DEPLOY}
                  target="_blank"
                  rel="noopener"
                  className="rounded-md border border-zinc-700 px-3 py-1.5 hover:border-zinc-500"
                >
                  Deploy tx ↗
                </a>
                <a
                  href={BASESCAN_PUBLISH}
                  target="_blank"
                  rel="noopener"
                  className="rounded-md border border-zinc-700 px-3 py-1.5 hover:border-zinc-500"
                >
                  Publish tx ↗
                </a>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── WHY NOW ───────────────────────────────────────── */}
      <section className="border-t border-zinc-800 py-24">
        <div className="mx-auto max-w-4xl px-6 text-center">
          <p className="text-xs font-medium uppercase tracking-widest text-zinc-500">
            Why now
          </p>
          <h2 className="mt-3 text-balance text-3xl font-bold md:text-4xl">
            The agentic economy needs a circuit breaker.
          </h2>
          <p className="mt-6 text-balance text-zinc-400">
            Coinbase, Google, and SKALE launched x402 in early 2026. Kite AI raised
            from Coinbase Ventures for agent infrastructure. What's missing: a
            trust layer agents can verify <em>cryptographically</em>, not just
            trust an API's word. ScamShield is that layer.
          </p>
        </div>
      </section>

      {/* ── FOOTER ────────────────────────────────────────── */}
      <footer className="border-t border-zinc-800 py-12">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 px-6 sm:flex-row">
          <div className="flex items-center gap-2 text-sm text-zinc-500">
            <span>🛡️</span>
            <span>ScamShield · Built on Base · MIT License</span>
          </div>
          <div className="flex items-center gap-5 text-sm">
            <a href={GITHUB} target="_blank" rel="noopener" className="text-zinc-400 hover:text-white">
              GitHub
            </a>
            <a href={TWITTER} target="_blank" rel="noopener" className="text-zinc-400 hover:text-white">
              X / Twitter
            </a>
            <a
              href="https://base.org"
              target="_blank"
              rel="noopener"
              className="text-zinc-400 hover:text-white"
            >
              Base.org
            </a>
          </div>
        </div>
      </footer>
    </main>
  );
}

function TierCard({
  tier,
  title,
  accent,
  border,
  points,
  trust,
}: {
  tier: string;
  title: string;
  accent: string;
  border: string;
  points: string[];
  trust: string;
}) {
  return (
    <div className={`rounded-xl border ${border} bg-zinc-950/50 p-6`}>
      <div className="flex items-baseline justify-between">
        <span className={`font-mono text-3xl font-bold ${accent}`}>{tier}</span>
        <span className="text-xs text-zinc-500">{trust}</span>
      </div>
      <h3 className="mt-3 text-lg font-semibold">{title}</h3>
      <ul className="mt-4 space-y-2 text-sm text-zinc-400">
        {points.map((p) => (
          <li key={p} className="flex items-start gap-2">
            <span className={accent}>›</span>
            <span>{p}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function Row({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex items-center justify-between border-b border-zinc-900 py-1.5">
      <span className="text-zinc-500">{label}</span>
      <span className={mono ? "font-mono text-zinc-300" : "text-zinc-300"}>
        {value}
      </span>
    </div>
  );
}
