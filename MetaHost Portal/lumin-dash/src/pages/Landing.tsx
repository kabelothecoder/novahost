import { Link } from "react-router-dom";
import { motion, useReducedMotion } from "framer-motion";
import {
  ScanLine,
  Radio,
  Layers,
  SlidersHorizontal,
  Building2,
  Fingerprint,
  ArrowRight,
  Check,
  Download,
  Apple,
} from "lucide-react";

/**
 * Where the Android APK is served from.
 *
 * Set VITE_APK_URL in the Vercel project (and .env.local for local dev). Until
 * it is set the download control renders disabled, not as a link to "#".
 * This is the primary conversion control on the site: a button that looks live
 * and silently does nothing is worse than one that admits it is not ready.
 */
const APK_URL: string = import.meta.env.VITE_APK_URL ?? "";

/**
 * The art direction, in one line: the product's own robot mark has a
 * magenta-to-cyan visor, so that gradient is the page's single accent and
 * everything else is a neutral dark ground. It is spent on the wordmark, one
 * phrase in the headline, and the primary button — nowhere else.
 *
 * The mark itself (`/novahost-mark.png`) is a render with a baked-in light
 * ground and no alpha, so it is always presented as a rounded app-icon tile.
 * Floating it on the dark ground would show its own grey square.
 */
const VISOR = "linear-gradient(100deg, #F0439E 0%, #A855F7 48%, #22C9E8 100%)";

const ease = [0.22, 1, 0.36, 1] as const;

const rise = {
  hidden: { opacity: 0, y: 18 },
  visible: (i = 0) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.6, ease, delay: i * 0.08 },
  }),
};

// ─── Small building blocks ───────────────────────────────────────────────────

function Mark({ size = 40 }: { size?: number }) {
  return (
    <img
      src="/novahost-mark.png"
      alt=""
      width={size}
      height={size}
      className="rounded-[22%] object-cover shrink-0"
      style={{ width: size, height: size }}
    />
  );
}

function Wordmark() {
  return (
    <Link to="/landing" className="flex items-center gap-2.5 select-none group">
      <Mark size={32} />
      <span
        className="text-[17px] font-bold tracking-[-0.02em]"
        style={{ fontFamily: "'Bricolage Grotesque', sans-serif" }}
      >
        NovaHost
      </span>
    </Link>
  );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <p
      className="text-[11px] font-semibold uppercase tracking-[0.16em] text-[#7E869A] mb-4"
      style={{ fontFamily: "'JetBrains Mono', monospace" }}
    >
      {children}
    </p>
  );
}

// ─── Page ────────────────────────────────────────────────────────────────────

export default function Landing() {
  const reduce = useReducedMotion();
  const anim = reduce
    ? {}
    : { variants: rise, initial: "hidden", whileInView: "visible", viewport: { once: true, margin: "-80px" } };

  return (
    <div
      className="min-h-screen bg-[#08090C] text-[#F2F4F8] antialiased overflow-x-hidden"
      style={{ fontFamily: "'Figtree', ui-sans-serif, system-ui, sans-serif" }}
    >
      {/* ─── Nav ────────────────────────────────────────────────────────── */}
      <header className="sticky top-0 z-50 border-b border-[#1B1E27] bg-[#08090C]/85 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-[1140px] items-center justify-between px-6">
          <Wordmark />

          <nav className="hidden items-center gap-9 md:flex">
            {[
              ["How it works", "#how-it-works"],
              ["Features", "#features"],
              ["Pricing", "#pricing"],
            ].map(([label, href]) => (
              <a
                key={href}
                href={href}
                className="text-[14px] font-medium text-[#8B92A3] transition-colors hover:text-[#F2F4F8]"
              >
                {label}
              </a>
            ))}
          </nav>

          <div className="flex items-center gap-2">
            <Link
              to="/login"
              className="hidden rounded-full px-4 py-2 text-[14px] font-medium text-[#8B92A3] transition-colors hover:text-[#F2F4F8] sm:block"
            >
              Sign in
            </Link>
            <Link
              to="/register"
              className="rounded-full border border-[#2A2E3A] bg-[#12141B] px-4 py-2 text-[14px] font-semibold text-[#F2F4F8] transition-colors hover:border-[#3B4150] hover:bg-[#171A22]"
            >
              Mentor sign-up
            </Link>
          </div>
        </div>
      </header>

      {/* ─── Hero ───────────────────────────────────────────────────────── */}
      <section className="relative overflow-hidden px-6 pb-24 pt-20 md:pt-28">
        {/* The visor glow, the one piece of colour above the fold. */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute left-1/2 top-[-260px] h-[420px] w-[760px] -translate-x-1/2 opacity-[0.13] blur-[110px]"
          style={{ background: VISOR, borderRadius: "50%" }}
        />

        <div className="relative mx-auto max-w-[1140px]">
          <motion.div
            {...anim}
            custom={0}
            className="mx-auto flex w-fit items-center gap-2 whitespace-nowrap rounded-full border border-[#23262F] bg-[#101218] px-4 py-1.5"
          >
            <span className="h-1.5 w-1.5 shrink-0 rounded-full" style={{ background: "#22C9E8" }} />
            <span className="text-[12.5px] font-medium text-[#A9B0BF]">
              Android now &middot; iOS coming soon
            </span>
          </motion.div>

          <motion.h1
            {...anim}
            custom={1}
            className="mx-auto mt-8 max-w-[15ch] text-center text-[clamp(2.6rem,7vw,4.6rem)] font-bold leading-[1.02] tracking-[-0.035em]"
            style={{ fontFamily: "'Bricolage Grotesque', sans-serif", textWrap: "balance" }}
          >
            Your mentor trades.{" "}
            <span
              className="bg-clip-text text-transparent"
              style={{ backgroundImage: VISOR, WebkitBackgroundClip: "text" }}
            >
              Your account follows.
            </span>
          </motion.h1>

          <motion.p
            {...anim}
            custom={2}
            className="mx-auto mt-6 max-w-[54ch] text-center text-[17px] leading-relaxed text-[#98A0B0]"
          >
            NovaHost links your MT4 or MT5 account to your mentor&rsquo;s trades and runs them
            automatically &mdash; sized to your balance, inside the risk limits you set, from your
            phone.
          </motion.p>

          {/* CTAs */}
          <motion.div
            {...anim}
            custom={3}
            className="mt-10 flex flex-col items-center justify-center gap-3 sm:flex-row"
          >
            {APK_URL ? (
              <a
                href={APK_URL}
                className="group inline-flex items-center gap-2.5 rounded-full px-7 py-3.5 text-[15px] font-semibold text-[#08090C] transition-transform duration-200 hover:scale-[1.02]"
                style={{ backgroundImage: VISOR }}
              >
                <Download size={17} />
                Download for Android
              </a>
            ) : (
              <span
                aria-disabled="true"
                className="inline-flex cursor-not-allowed select-none items-center gap-2.5 rounded-full border border-[#2A2E3A] bg-[#12141B] px-7 py-3.5 text-[15px] font-semibold text-[#6C7484]"
              >
                <Download size={17} />
                Android download coming shortly
              </span>
            )}

            {/*
              iOS is deliberately NOT a download button. It is announced, not
              offered — a live-looking button for an app that does not exist
              produces refund requests, not signups.
            */}
            <span className="inline-flex select-none items-center gap-2.5 rounded-full border border-[#23262F] px-6 py-3.5 text-[15px] font-medium text-[#7E869A]">
              <Apple size={16} />
              iOS &mdash; coming soon
            </span>
          </motion.div>

          {/* The mark, presented as the app icon it is. */}
          <motion.div {...anim} custom={4} className="relative mt-20 flex justify-center">
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-x-0 top-8 mx-auto h-[220px] w-[220px] rounded-full opacity-40 blur-[70px]"
              style={{ background: VISOR }}
            />
            <div className="relative rounded-[30px] border border-[#23262F] bg-[#0D0F14] p-3 shadow-[0_30px_80px_-20px_rgba(0,0,0,0.9)]">
              <Mark size={148} />
            </div>
          </motion.div>
        </div>
      </section>

      {/* ─── How it works ───────────────────────────────────────────────── */}
      <section id="how-it-works" className="border-t border-[#14171E] px-6 py-24">
        <div className="mx-auto max-w-[1140px]">
          <motion.div {...anim} custom={0}>
            <SectionLabel>How it works</SectionLabel>
            <h2
              className="max-w-[20ch] text-[clamp(1.9rem,4vw,2.75rem)] font-bold leading-[1.1] tracking-[-0.03em]"
              style={{ fontFamily: "'Bricolage Grotesque', sans-serif", textWrap: "balance" }}
            >
              Three steps, then it runs itself.
            </h2>
          </motion.div>

          <div className="mt-14 grid gap-5 md:grid-cols-3">
            {[
              {
                n: "01",
                t: "Get your licence key",
                d: "Buy access and your mentor issues a key. It binds to one phone, so a key cannot be passed around.",
              },
              {
                n: "02",
                t: "Link your broker",
                d: "Enter your MT4 or MT5 server, login and password. Any broker — nothing here is tied to one.",
              },
              {
                n: "03",
                t: "Let the robot work",
                d: "When your mentor takes a trade, your account takes it too. Lot size scales to your balance, not theirs.",
              },
            ].map((s, i) => (
              <motion.div
                key={s.n}
                {...anim}
                custom={i + 1}
                className="rounded-2xl border border-[#1D2029] bg-[#0E1015] p-7"
              >
                <span
                  className="text-[12px] font-bold tracking-[0.12em] text-[#5B6272]"
                  style={{ fontFamily: "'JetBrains Mono', monospace" }}
                >
                  {s.n}
                </span>
                <h3
                  className="mt-4 text-[19px] font-semibold tracking-[-0.015em]"
                  style={{ fontFamily: "'Bricolage Grotesque', sans-serif" }}
                >
                  {s.t}
                </h3>
                <p className="mt-2.5 text-[15px] leading-relaxed text-[#8B92A3]">{s.d}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* ─── Features ───────────────────────────────────────────────────── */}
      <section id="features" className="border-t border-[#14171E] px-6 py-24">
        <div className="mx-auto max-w-[1140px]">
          <motion.div {...anim} custom={0}>
            <SectionLabel>What you get</SectionLabel>
            <h2
              className="max-w-[22ch] text-[clamp(1.9rem,4vw,2.75rem)] font-bold leading-[1.1] tracking-[-0.03em]"
              style={{ fontFamily: "'Bricolage Grotesque', sans-serif", textWrap: "balance" }}
            >
              Built for traders who don&rsquo;t sit at a desk.
            </h2>
          </motion.div>

          <div className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {[
              {
                icon: ScanLine,
                t: "AI chart scanner",
                d: "Screenshot any chart and get a structured read — direction, entry, stop and target — scored against the guardrails you set.",
              },
              {
                icon: Radio,
                t: "Mentor signals",
                d: "Market and pending orders reach your account in seconds, with the entry, stop and target your mentor sent.",
              },
              {
                icon: Layers,
                t: "Floating overlay",
                d: "A small bubble that floats over any app, so you can watch your robot without leaving what you were doing.",
              },
              {
                icon: SlidersHorizontal,
                t: "Your risk, your rules",
                d: "Per-symbol lot size, max open trades and hard guardrails that will refuse a trade rather than break your limits.",
              },
              {
                icon: Building2,
                t: "Any MT4 or MT5 broker",
                d: "No broker is hardcoded. Your server, your login, your account — the robot adapts to your broker's symbol names.",
              },
              {
                icon: Fingerprint,
                t: "One device, one licence",
                d: "Every key is bound to your handset. Changed phones? Move it across yourself, no support ticket.",
              },
            ].map((f, i) => (
              <motion.div
                key={f.t}
                {...anim}
                custom={i * 0.5}
                className="group rounded-2xl border border-[#1D2029] bg-[#0E1015] p-7 transition-colors hover:border-[#2B303C]"
              >
                <span className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-[#23262F] bg-[#14171E]">
                  <f.icon size={18} className="text-[#22C9E8]" />
                </span>
                <h3
                  className="mt-5 text-[17.5px] font-semibold tracking-[-0.015em]"
                  style={{ fontFamily: "'Bricolage Grotesque', sans-serif" }}
                >
                  {f.t}
                </h3>
                <p className="mt-2.5 text-[14.5px] leading-relaxed text-[#8B92A3]">{f.d}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* ─── Pricing ────────────────────────────────────────────────────── */}
      <section id="pricing" className="border-t border-[#14171E] px-6 py-24">
        <div className="mx-auto max-w-[1140px]">
          <motion.div {...anim} custom={0}>
            <SectionLabel>Pricing</SectionLabel>
            <h2
              className="max-w-[20ch] text-[clamp(1.9rem,4vw,2.75rem)] font-bold leading-[1.1] tracking-[-0.03em]"
              style={{ fontFamily: "'Bricolage Grotesque', sans-serif", textWrap: "balance" }}
            >
              Pay once. No subscription.
            </h2>
            <p className="mt-4 max-w-[52ch] text-[16px] leading-relaxed text-[#8B92A3]">
              One payment, in Rands, through PayFast. There is no monthly fee and no billing
              token sitting against your card afterwards.
            </p>
          </motion.div>

          <div className="mt-14 grid gap-5 md:grid-cols-3">
            {[
              {
                name: "App access",
                price: "599",
                tag: "Start here",
                featured: true,
                items: [
                  "Lifetime access to the app",
                  "Mentor signals on your account",
                  "Floating overlay + risk controls",
                  "Any MT4 / MT5 broker",
                ],
              },
              {
                name: "AI chart scanner",
                price: "349",
                tag: "Add-on",
                featured: false,
                items: [
                  "Scan any chart screenshot",
                  "Entry, stop and target",
                  "Scored against your guardrails",
                  "Refuses non-chart images",
                ],
              },
              {
                name: "Device move",
                price: "150",
                tag: "When you need it",
                featured: false,
                items: [
                  "Move your licence to a new phone",
                  "Keeps your existing key",
                  "Only if you change handsets",
                  "Not needed to get started",
                ],
              },
            ].map((p, i) => (
              <motion.div
                key={p.name}
                {...anim}
                custom={i + 1}
                className="relative rounded-2xl border bg-[#0E1015] p-7"
                style={{ borderColor: p.featured ? "#2E3442" : "#1D2029" }}
              >
                {p.featured && (
                  <span
                    aria-hidden="true"
                    className="absolute inset-x-7 top-0 h-px"
                    style={{ backgroundImage: VISOR }}
                  />
                )}
                <p
                  className="text-[11px] font-semibold uppercase tracking-[0.14em] text-[#6C7484]"
                  style={{ fontFamily: "'JetBrains Mono', monospace" }}
                >
                  {p.tag}
                </p>
                <h3
                  className="mt-3 text-[20px] font-semibold tracking-[-0.015em]"
                  style={{ fontFamily: "'Bricolage Grotesque', sans-serif" }}
                >
                  {p.name}
                </h3>
                <p className="mt-5 flex items-baseline gap-1.5">
                  <span className="text-[17px] font-medium text-[#8B92A3]">R</span>
                  <span
                    className="text-[42px] font-bold leading-none tracking-[-0.03em]"
                    style={{ fontFamily: "'JetBrains Mono', monospace", fontVariantNumeric: "tabular-nums" }}
                  >
                    {p.price}
                  </span>
                  <span className="ml-1 text-[14px] text-[#6C7484]">once-off</span>
                </p>

                <ul className="mt-7 space-y-3">
                  {p.items.map((it) => (
                    <li key={it} className="flex items-start gap-2.5 text-[14.5px] text-[#98A0B0]">
                      <Check size={15} className="mt-1 shrink-0 text-[#22C9E8]" />
                      <span>{it}</span>
                    </li>
                  ))}
                </ul>
              </motion.div>
            ))}
          </div>

          <motion.p {...anim} custom={4} className="mt-8 text-[13.5px] text-[#6C7484]">
            Prices include VAT where applicable. You buy the app once; your mentor issues the
            licence key that ties it to their robot.
          </motion.p>
        </div>
      </section>

      {/* ─── Closing CTA ────────────────────────────────────────────────── */}
      <section className="relative overflow-hidden border-t border-[#14171E] px-6 py-28">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute left-1/2 top-1/2 h-[380px] w-[780px] -translate-x-1/2 -translate-y-1/2 opacity-[0.16] blur-[100px]"
          style={{ background: VISOR, borderRadius: "50%" }}
        />
        <motion.div {...anim} custom={0} className="relative mx-auto max-w-[640px] text-center">
          <Mark size={56} />
          <h2
            className="mt-7 text-[clamp(1.9rem,4.5vw,2.9rem)] font-bold leading-[1.08] tracking-[-0.03em]"
            style={{ fontFamily: "'Bricolage Grotesque', sans-serif", textWrap: "balance" }}
          >
            Ready when you are.
          </h2>
          <p className="mx-auto mt-5 max-w-[46ch] text-[16.5px] leading-relaxed text-[#98A0B0]">
            Get the app, link your broker, and let your mentor&rsquo;s next trade land on your
            account.
          </p>

          <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
            {APK_URL ? (
              <a
                href={APK_URL}
                className="inline-flex items-center gap-2.5 rounded-full px-7 py-3.5 text-[15px] font-semibold text-[#08090C] transition-transform duration-200 hover:scale-[1.02]"
                style={{ backgroundImage: VISOR }}
              >
                <Download size={17} />
                Download for Android
              </a>
            ) : (
              <span
                aria-disabled="true"
                className="inline-flex cursor-not-allowed select-none items-center gap-2.5 rounded-full border border-[#2A2E3A] bg-[#12141B] px-7 py-3.5 text-[15px] font-semibold text-[#6C7484]"
              >
                <Download size={17} />
                Android download coming shortly
              </span>
            )}
            <Link
              to="/register"
              className="group inline-flex items-center gap-2 rounded-full border border-[#2A2E3A] px-6 py-3.5 text-[15px] font-semibold text-[#F2F4F8] transition-colors hover:border-[#3B4150]"
            >
              I&rsquo;m a mentor
              <ArrowRight size={16} className="transition-transform group-hover:translate-x-0.5" />
            </Link>
          </div>
        </motion.div>
      </section>

      {/* ─── Footer ─────────────────────────────────────────────────────── */}
      <footer className="border-t border-[#14171E] px-6 py-10">
        <div className="mx-auto flex max-w-[1140px] flex-col items-center justify-between gap-5 sm:flex-row">
          <Wordmark />
          <nav className="flex flex-wrap items-center justify-center gap-6">
            {/*
              Privacy / Terms / Contact are deliberately absent rather than
              linked to "#". They need real pages before they go back in — a
              paid product needs published terms, not a link that goes nowhere.
            */}
            {[
              ["How it works", "#how-it-works"],
              ["Features", "#features"],
              ["Pricing", "#pricing"],
            ].map(([label, href]) => (
              <a
                key={href}
                href={href}
                className="text-[13.5px] text-[#6C7484] transition-colors hover:text-[#A9B0BF]"
              >
                {label}
              </a>
            ))}
          </nav>
          <p className="text-[13px] text-[#5B6272]">
            &copy; {new Date().getFullYear()} NovaHost
          </p>
        </div>
      </footer>
    </div>
  );
}
