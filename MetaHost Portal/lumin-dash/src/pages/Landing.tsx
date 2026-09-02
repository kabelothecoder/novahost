import { useRef } from "react";
import { Link } from "react-router-dom";
import { motion, useScroll, useTransform } from "framer-motion";
import {
  Zap,
  Shield,
  Server,
  ChevronRight,
  ArrowRight,
  Smartphone,
  Download,
  Activity,
} from "lucide-react";

/**
 * Where the Android APK is served from.
 *
 * Set VITE_APK_URL in the Vercel project (and .env.local for local dev). Until
 * it is set the download controls render as disabled, not as links to "#".
 * This is the primary conversion control on the site: a button that looks live
 * and silently does nothing is worse than one that admits it is not ready.
 */
const APK_URL: string = import.meta.env.VITE_APK_URL ?? "";

// ─── Animation Variants ──────────────────────────────────────────────────────

const fadeUp = {
  hidden: { opacity: 0, y: 32 },
  visible: (i = 0) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.65, ease: [0.22, 1, 0.36, 1], delay: i * 0.12 },
  }),
};

const fadeIn = {
  hidden: { opacity: 0 },
  visible: (i = 0) => ({
    opacity: 1,
    transition: { duration: 0.5, ease: "easeOut", delay: i * 0.1 },
  }),
};

// ─── Sub-components ───────────────────────────────────────────────────────────

/** Transparent sticky navbar */
function Navbar() {
  return (
    <header className="fixed top-0 left-0 right-0 z-50 flex items-center justify-between px-6 md:px-12 py-4 bg-white/70 backdrop-blur-xl border-b border-slate-100/80">
      {/* Logo */}
      <Link to="/landing" className="flex items-center gap-2 select-none">
        <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-gray-950 text-white text-xs font-black tracking-tight">
          MH
        </span>
        <span className="text-lg font-bold tracking-tight text-gray-900">NovaHost</span>
      </Link>

      {/* Center links */}
      <nav className="hidden md:flex items-center gap-8">
        {["Features", "How it Works", "Pricing"].map((label) => (
          <a
            key={label}
            href={`#${label.toLowerCase().replace(/\s/g, "-")}`}
            className="text-sm font-medium text-gray-500 hover:text-gray-900 transition-colors duration-200"
          >
            {label}
          </a>
        ))}
      </nav>

      {/* CTAs */}
      <div className="flex items-center gap-3">
        <Link
          to="/login"
          className="hidden sm:inline-flex items-center px-4 py-2 rounded-full text-sm font-semibold text-gray-700 border border-gray-200 hover:border-gray-400 hover:text-gray-900 transition-all duration-200"
        >
          Mentor Login
        </Link>
        <Link
          to="/register"
          className="inline-flex items-center px-4 py-2 rounded-full text-sm font-semibold bg-gray-950 text-white hover:bg-gray-800 transition-all duration-200 shadow-sm"
        >
          Mentor Sign Up
        </Link>
      </div>
    </header>
  );
}

/** Trusted brokers strip */
function BrokerLogos() {
  const brokers = ["Exness", "IC Markets", "XM", "Pepperstone", "FP Markets"];
  return (
    <motion.div
      className="mt-20 pt-10 border-t border-slate-100"
      variants={fadeIn}
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true }}
    >
      <p className="text-center text-xs font-semibold tracking-widest text-slate-400 uppercase mb-6">
        Trusted Brokers
      </p>
      <div className="flex flex-wrap items-center justify-center gap-8 md:gap-12">
        {brokers.map((b) => (
          <span
            key={b}
            className="text-slate-300 font-bold text-lg tracking-tight select-none grayscale hover:grayscale-0 hover:text-slate-500 transition-all duration-300"
          >
            {b}
          </span>
        ))}
      </div>
    </motion.div>
  );
}

/** Floating background glow orbs for CTA section */
function FloatingNodes() {
  const nodes = [
    { top: "10%", left: "8%", size: 56, delay: 0 },
    { top: "20%", right: "10%", size: 44, delay: 0.3 },
    { top: "65%", left: "5%", size: 36, delay: 0.6 },
    { top: "55%", right: "6%", size: 52, delay: 0.2 },
    { top: "80%", left: "25%", size: 32, delay: 0.8 },
    { top: "75%", right: "22%", size: 40, delay: 0.5 },
  ];

  const icons = [Activity, Zap, Shield, Server, Smartphone, ArrowRight];

  return (
    <>
      {/* Decorative SVG lines */}
      <svg
        className="absolute inset-0 w-full h-full pointer-events-none opacity-10"
        xmlns="http://www.w3.org/2000/svg"
      >
        <line x1="12%" y1="15%" x2="50%" y2="50%" stroke="#6366f1" strokeWidth="1" strokeDasharray="4 6" />
        <line x1="88%" y1="22%" x2="50%" y2="50%" stroke="#6366f1" strokeWidth="1" strokeDasharray="4 6" />
        <line x1="8%" y1="68%" x2="50%" y2="50%" stroke="#a78bfa" strokeWidth="1" strokeDasharray="4 6" />
        <line x1="92%" y1="58%" x2="50%" y2="50%" stroke="#a78bfa" strokeWidth="1" strokeDasharray="4 6" />
        <line x1="28%" y1="82%" x2="50%" y2="50%" stroke="#818cf8" strokeWidth="1" strokeDasharray="4 6" />
        <line x1="75%" y1="78%" x2="50%" y2="50%" stroke="#818cf8" strokeWidth="1" strokeDasharray="4 6" />
      </svg>

      {nodes.map((n, i) => {
        const Icon = icons[i];
        const style: React.CSSProperties = {
          top: n.top,
          left: (n as any).left,
          right: (n as any).right,
          width: n.size,
          height: n.size,
        };
        return (
          <motion.div
            key={i}
            className="absolute flex items-center justify-center rounded-full bg-white border border-indigo-100 shadow-lg shadow-indigo-100/40"
            style={style}
            initial={{ opacity: 0, scale: 0 }}
            whileInView={{ opacity: 1, scale: 1 }}
            viewport={{ once: true }}
            transition={{ duration: 0.5, delay: n.delay, ease: "backOut" }}
            animate={{ y: [0, -8, 0] }}
          >
            <Icon size={n.size * 0.4} className="text-indigo-400" strokeWidth={1.5} />
          </motion.div>
        );
      })}
    </>
  );
}

// ─── Main Page ─────────────────────────────────────────────────────────────────

export default function Landing() {
  const stepsRef = useRef<HTMLDivElement>(null);
  const { scrollYProgress } = useScroll({
    target: stepsRef,
    offset: ["start start", "end end"],
  });
  const phoneMockupY = useTransform(scrollYProgress, [0, 1], ["0%", "0%"]);

  return (
    <div className="min-h-screen bg-white text-gray-900 overflow-x-hidden font-sans">
      <Navbar />

      {/* ─── SECTION 1 (embedded in navbar above) ─── */}

      {/* ─── SECTION 2: Hero ──────────────────────────────────────────────── */}
      <section
        id="features"
        className="relative min-h-screen flex flex-col justify-center px-6 md:px-16 xl:px-28 pt-28 pb-16 overflow-hidden"
      >
        {/* Pastel glow backdrop */}
        <div className="absolute -top-32 -left-32 w-[600px] h-[600px] rounded-full bg-indigo-50 blur-[120px] opacity-60 pointer-events-none" />
        <div className="absolute top-1/2 right-0 w-[400px] h-[400px] rounded-full bg-violet-50 blur-[100px] opacity-50 pointer-events-none" />

        <div className="relative grid grid-cols-1 lg:grid-cols-2 gap-16 items-center max-w-7xl mx-auto w-full">
          {/* Left — copy */}
          <div className="space-y-8">
            <motion.div
              variants={fadeIn}
              initial="hidden"
              animate="visible"
              custom={0}
              className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-600 text-xs font-semibold tracking-wide"
            >
              <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse" />
              Now live — 50k+ robots hosted
            </motion.div>

            <motion.h1
              variants={fadeUp}
              initial="hidden"
              animate="visible"
              custom={1}
              className="text-5xl md:text-6xl xl:text-7xl font-extrabold tracking-tight leading-[1.05] text-gray-950"
            >
              Effortlessly{" "}
              <span className="bg-gradient-to-r from-indigo-500 to-violet-500 bg-clip-text text-transparent">
                Automate
              </span>{" "}
              and Simplify Your Trading.
            </motion.h1>

            <motion.p
              variants={fadeUp}
              initial="hidden"
              animate="visible"
              custom={2}
              className="text-lg text-slate-500 leading-relaxed max-w-lg"
            >
              Automatically run your Expert Advisors 24/7 with ultra-low latency directly from your mobile device.
            </motion.p>

            {/* Buttons */}
            <motion.div
              variants={fadeUp}
              initial="hidden"
              animate="visible"
              custom={3}
              className="flex flex-wrap gap-3"
            >
              <a
                href="#download"
                className="inline-flex items-center gap-2 px-6 py-3.5 rounded-full bg-gray-950 text-white font-semibold text-sm hover:bg-gray-800 transition-all duration-200 shadow-lg shadow-gray-900/20"
              >
                <Download size={16} />
                Download App
              </a>
              <a
                href="#how-it-works"
                className="inline-flex items-center gap-2 px-6 py-3.5 rounded-full border border-gray-200 text-gray-700 font-semibold text-sm hover:border-gray-400 hover:text-gray-900 transition-all duration-200"
              >
                Explore Platform
                <ChevronRight size={16} />
              </a>
            </motion.div>

            {/* Trust badge */}
            <motion.div
              variants={fadeUp}
              initial="hidden"
              animate="visible"
              custom={4}
              className="flex items-center gap-3"
            >
              <div className="flex -space-x-2">
                {[
                  "bg-indigo-400",
                  "bg-violet-400",
                  "bg-pink-400",
                  "bg-sky-400",
                  "bg-emerald-400",
                ].map((bg, i) => (
                  <div
                    key={i}
                    className={`w-8 h-8 rounded-full ${bg} border-2 border-white flex items-center justify-center text-white text-[10px] font-bold`}
                  >
                    {["K", "J", "A", "M", "T"][i]}
                  </div>
                ))}
              </div>
              <div>
                <p className="text-sm font-semibold text-gray-800">50k+ Active Robots Hosted</p>
                <p className="text-xs text-slate-400">Trusted by mentors worldwide</p>
              </div>
            </motion.div>
          </div>

          {/* Right — phone mockup */}
          <motion.div
            variants={fadeUp}
            initial="hidden"
            animate="visible"
            custom={2}
            className="flex justify-center lg:justify-end"
          >
            <div className="relative">
              {/* Glow halo */}
              <div className="absolute inset-0 scale-110 rounded-[3rem] bg-gradient-to-br from-indigo-200 via-violet-100 to-transparent blur-3xl opacity-60" />
              {/* Phone frame */}
              <div className="relative w-64 h-[500px] rounded-[3rem] bg-gradient-to-b from-gray-100 to-gray-200 border border-slate-200 shadow-2xl shadow-slate-300/50 flex flex-col items-center justify-center overflow-hidden">
                <div className="w-24 h-5 rounded-full bg-gray-300 absolute top-4" />
                <div className="px-6 w-full space-y-3 mt-8">
                  {[80, 60, 70, 55, 65].map((w, i) => (
                    <div
                      key={i}
                      className="h-3 rounded-full bg-gradient-to-r from-indigo-200 to-violet-200"
                      style={{ width: `${w}%` }}
                    />
                  ))}
                  <div className="mt-4 h-20 w-full rounded-2xl bg-gradient-to-br from-indigo-100 to-violet-100 flex items-center justify-center">
                    <Activity size={28} className="text-indigo-400" strokeWidth={1.5} />
                  </div>
                  {[50, 75, 45].map((w, i) => (
                    <div
                      key={i}
                      className="h-2 rounded-full bg-slate-200"
                      style={{ width: `${w}%` }}
                    />
                  ))}
                </div>
                <p className="absolute bottom-8 text-[10px] text-slate-400 font-medium tracking-widest uppercase">
                  App Mockup
                </p>
              </div>
            </div>
          </motion.div>
        </div>

        {/* Broker logos */}
        <div className="max-w-7xl mx-auto w-full">
          <BrokerLogos />
        </div>
      </section>

      {/* ─── SECTION 3: Features Grid ────────────────────────────────────────── */}
      <section id="features" className="py-28 px-6 md:px-16 xl:px-28 bg-[#fafafa]">
        <div className="max-w-7xl mx-auto">
          <motion.div
            className="text-center mb-16 space-y-3"
            variants={fadeUp}
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
          >
            <p className="text-xs font-semibold tracking-widest text-indigo-500 uppercase">Platform Capabilities</p>
            <h2 className="text-4xl md:text-5xl font-extrabold tracking-tight text-gray-950">
              Exclusive hosting,{" "}
              <span className="bg-gradient-to-r from-indigo-500 to-violet-500 bg-clip-text text-transparent">
                seamless execution.
              </span>
            </h2>
          </motion.div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {[
              {
                title: "Low Latency Execution",
                desc: "Sub-millisecond order routing to your broker with co-located servers near major liquidity pools.",
                icon: Zap,
                gradient: "from-indigo-50 to-blue-50",
                iconBg: "bg-indigo-500",
                accent: "indigo",
                delay: 0,
              },
              {
                title: "Advanced Cybersecurity",
                desc: "Military-grade AES-256 encryption and isolated sandboxed containers protect every EA session.",
                icon: Shield,
                gradient: "from-violet-50 to-purple-50",
                iconBg: "bg-violet-500",
                accent: "violet",
                delay: 0.12,
              },
              {
                title: "24/7 Server Uptime",
                desc: "99.99% SLA with redundant power, automatic failover, and real-time uptime monitoring alerts.",
                icon: Server,
                gradient: "from-pink-50 to-rose-50",
                iconBg: "bg-pink-500",
                accent: "pink",
                delay: 0.24,
              },
            ].map((card) => {
              const Icon = card.icon;
              return (
                <motion.div
                  key={card.title}
                  variants={fadeUp}
                  initial="hidden"
                  whileInView="visible"
                  viewport={{ once: true }}
                  custom={card.delay}
                  className={`group relative flex flex-col rounded-3xl bg-gradient-to-br ${card.gradient} border border-white/80 shadow-xl shadow-slate-100/80 p-8 overflow-hidden hover:shadow-2xl hover:-translate-y-1 transition-all duration-300 cursor-default`}
                >
                  {/* Dark icon top-right */}
                  <div
                    className={`absolute top-6 right-6 w-10 h-10 rounded-full ${card.iconBg} flex items-center justify-center shadow-lg`}
                  >
                    <Icon size={18} className="text-white" strokeWidth={2} />
                  </div>

                  {/* Placeholder visual area */}
                  <div className="w-full h-48 rounded-2xl bg-white/70 border border-white mb-8 flex items-center justify-center backdrop-blur-sm">
                    <Icon size={48} className="text-slate-200" strokeWidth={1} />
                  </div>

                  <h3 className="text-xl font-bold text-gray-900 mb-3">{card.title}</h3>
                  <p className="text-sm text-slate-500 leading-relaxed">{card.desc}</p>

                  {/* Subtle corner glow */}
                  <div
                    className={`absolute -bottom-16 -right-16 w-48 h-48 rounded-full opacity-20 blur-2xl`}
                    style={{
                      background: card.accent === "indigo" ? "#6366f1" : card.accent === "violet" ? "#8b5cf6" : "#ec4899",
                    }}
                  />
                </motion.div>
              );
            })}
          </div>
        </div>
      </section>

      {/* ─── SECTION 4: Step-by-Step Guide ──────────────────────────────────── */}
      <section id="how-it-works" className="py-28 px-6 md:px-16 xl:px-28 bg-white overflow-hidden">
        <div className="max-w-7xl mx-auto">
          <motion.div
            className="mb-14 space-y-3"
            variants={fadeUp}
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
          >
            <p className="text-xs font-semibold tracking-widest text-indigo-500 uppercase">Getting Started</p>
            <h2 className="text-4xl md:text-5xl font-extrabold tracking-tight text-gray-950 max-w-xl leading-tight">
              Simple Steps to Your Automated Trading Experience.
            </h2>
          </motion.div>

          <div ref={stepsRef} className="grid grid-cols-1 lg:grid-cols-2 gap-16 items-start">
            {/* Left — steps */}
            <div className="space-y-5 lg:pr-8">
              {[
                {
                  num: "01",
                  title: "Get Access",
                  desc: "Choose your hosting tier that matches your trading volume and EA requirements.",
                  color: "bg-indigo-500",
                },
                {
                  num: "02",
                  title: "Buy License Key",
                  desc: "Purchase a valid license key directly from your certified NovaHost mentor.",
                  color: "bg-violet-500",
                },
                {
                  num: "03",
                  title: "Link Your Account",
                  desc: "Connect your Expert Advisor to our secure VPS with one-tap broker integration.",
                  color: "bg-pink-500",
                },
                {
                  num: "04",
                  title: "Profit On-The-Go",
                  desc: "Manage, monitor, and scale your automated trades from anywhere on your phone.",
                  color: "bg-emerald-500",
                },
              ].map((step, i) => (
                <motion.div
                  key={step.num}
                  variants={fadeUp}
                  initial="hidden"
                  whileInView="visible"
                  viewport={{ once: true, margin: "-60px" }}
                  custom={i * 0.1}
                  className="group flex items-start gap-5 p-6 rounded-2xl bg-gradient-to-r from-gray-50 to-white border border-slate-100 hover:border-indigo-100 hover:shadow-lg hover:shadow-indigo-50/60 transition-all duration-300 cursor-default"
                >
                  <div
                    className={`shrink-0 flex items-center justify-center w-12 h-12 rounded-2xl ${step.color} text-white font-black text-base shadow-md`}
                  >
                    {step.num}
                  </div>
                  <div>
                    <h3 className="font-bold text-gray-900 mb-1">{step.title}</h3>
                    <p className="text-sm text-slate-500 leading-relaxed">{step.desc}</p>
                  </div>
                  <ArrowRight
                    size={16}
                    className="shrink-0 ml-auto text-slate-200 group-hover:text-indigo-400 transition-colors duration-200 mt-1"
                  />
                </motion.div>
              ))}
            </div>

            {/* Right — sticky phone */}
            <div className="hidden lg:flex justify-center">
              <motion.div
                className="sticky top-28 self-start"
                style={{ y: phoneMockupY }}
              >
                <div className="relative">
                  <div className="absolute inset-0 scale-110 rounded-[3rem] bg-gradient-to-br from-indigo-100 via-violet-100 to-pink-100 blur-3xl opacity-50" />
                  <div className="relative w-64 h-[500px] rounded-[3rem] bg-gradient-to-b from-slate-50 to-slate-100 border border-slate-200 shadow-2xl shadow-slate-200/60 flex flex-col items-center justify-start overflow-hidden pt-10 px-5">
                    <div className="w-20 h-4 rounded-full bg-slate-300 mb-6" />
                    <div className="w-full space-y-3">
                      <div className="h-24 w-full rounded-2xl bg-gradient-to-br from-indigo-100 to-violet-100 flex items-center justify-center">
                        <Activity size={32} className="text-indigo-400" strokeWidth={1.5} />
                      </div>
                      <div className="grid grid-cols-2 gap-2">
                        {[["Lat", "0.4ms"], ["Up", "99.99%"], ["EAs", "12"], ["PnL", "+18%"]].map(([l, v]) => (
                          <div key={l} className="rounded-xl bg-white border border-slate-100 p-3 shadow-sm">
                            <p className="text-[10px] text-slate-400 font-medium">{l}</p>
                            <p className="text-sm font-bold text-gray-900">{v}</p>
                          </div>
                        ))}
                      </div>
                      <div className="h-16 w-full rounded-2xl bg-white border border-slate-100 shadow-sm flex items-center px-4 gap-3">
                        <div className="w-8 h-8 rounded-full bg-emerald-100 flex items-center justify-center">
                          <Zap size={14} className="text-emerald-500" />
                        </div>
                        <div>
                          <p className="text-[10px] text-slate-400">Status</p>
                          <p className="text-xs font-bold text-emerald-600">All Systems Active</p>
                        </div>
                      </div>
                    </div>
                    <p className="absolute bottom-8 text-[10px] text-slate-400 font-medium tracking-widest uppercase">
                      Live Dashboard
                    </p>
                  </div>
                </div>
              </motion.div>
            </div>
          </div>
        </div>
      </section>

      {/* ─── SECTION 5: Final CTA ─────────────────────────────────────────────── */}
      <section
        id="download"
        className="relative py-36 px-6 md:px-16 xl:px-28 overflow-hidden bg-gradient-to-b from-slate-50 to-white"
      >
        {/* Ambient glow */}
        <div className="absolute inset-0 pointer-events-none">
          <div className="absolute top-0 left-1/2 -translate-x-1/2 w-[800px] h-[400px] rounded-full bg-indigo-100 blur-[120px] opacity-50" />
        </div>

        {/* Floating node icons */}
        <FloatingNodes />

        {/* Center content */}
        <div className="relative max-w-3xl mx-auto text-center space-y-8 z-10">
          <motion.div
            variants={fadeIn}
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
            className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-50 border border-indigo-100 text-indigo-600 text-xs font-semibold tracking-wide"
          >
            <span className="w-1.5 h-1.5 rounded-full bg-indigo-500 animate-pulse" />
            Available on Android & iOS
          </motion.div>

          <motion.h2
            variants={fadeUp}
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
            className="text-4xl md:text-6xl font-extrabold tracking-tight text-gray-950 leading-tight"
          >
            Transform Your Experience.{" "}
            <span className="bg-gradient-to-r from-indigo-500 to-violet-500 bg-clip-text text-transparent">
              Indulge in the World
            </span>{" "}
            of Mobile Automated Trading!
          </motion.h2>

          <motion.p
            variants={fadeUp}
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
            custom={0.1}
            className="text-lg text-slate-500 leading-relaxed max-w-xl mx-auto"
          >
            Download NovaHost directly to your device for exclusive strategies and a seamless luxury trading experience.
          </motion.p>

          {/* Download buttons */}
          <motion.div
            variants={fadeUp}
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
            custom={0.2}
            className="flex flex-col sm:flex-row items-center justify-center gap-4"
          >
            {/*
              The iOS button that used to sit beside this one has been removed.
              iOS is not in scope, and advertising a download for a platform we
              do not ship produces refund requests, not signups.
            */}
            {APK_URL ? (
              <a
                href={APK_URL}
                className="inline-flex items-center gap-3 px-8 py-4 rounded-full bg-gray-950 text-white font-semibold text-sm hover:bg-gray-800 transition-all duration-200 shadow-xl shadow-gray-900/20 group"
              >
                <Download size={18} className="group-hover:-translate-y-0.5 transition-transform duration-200" />
                Download for Android (.apk)
              </a>
            ) : (
              <span
                aria-disabled="true"
                className="inline-flex items-center gap-3 px-8 py-4 rounded-full bg-gray-200 text-gray-500 font-semibold text-sm cursor-not-allowed select-none"
              >
                <Download size={18} />
                Android download coming shortly
              </span>
            )}
          </motion.div>
        </div>

        {/* Phone mockup peeking from bottom */}
        <motion.div
          className="relative mt-20 flex justify-center"
          variants={fadeUp}
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true }}
          custom={0.3}
        >
          <div className="relative">
            <div className="absolute inset-0 scale-110 rounded-[3rem] bg-gradient-to-br from-indigo-100 via-violet-100 to-transparent blur-3xl opacity-60" />
            <div className="relative w-72 h-48 overflow-hidden rounded-t-[3rem] bg-gradient-to-b from-slate-800 to-slate-900 border border-slate-700 shadow-2xl shadow-indigo-900/20 flex items-start justify-center pt-6 px-6">
              <div className="w-full space-y-2">
                {[
                  { w: "100%", color: "from-indigo-500 to-violet-500" },
                  { w: "75%", color: "from-slate-600 to-slate-700" },
                  { w: "55%", color: "from-slate-600 to-slate-700" },
                ].map((bar, i) => (
                  <div
                    key={i}
                    className={`h-2.5 rounded-full bg-gradient-to-r ${bar.color} opacity-80`}
                    style={{ width: bar.w }}
                  />
                ))}
                <div className="pt-2 flex gap-2">
                  {["XAUUSD +2.1%", "EURUSD -0.4%"].map((t) => (
                    <div key={t} className="text-[9px] text-slate-400 bg-slate-700/60 rounded-full px-2 py-1">
                      {t}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </motion.div>
      </section>

      {/* ─── Footer ──────────────────────────────────────────────────────────── */}
      <footer className="py-10 px-6 md:px-16 border-t border-slate-100 bg-white">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-gray-950 text-white text-[10px] font-black">
              NH
            </span>
            <span className="text-sm font-bold text-gray-900">NovaHost</span>
          </div>
          <p className="text-xs text-slate-400">© {new Date().getFullYear()} NovaHost. All rights reserved.</p>
          <div className="flex gap-6">
            {["Privacy", "Terms", "Contact"].map((l) => (
              <a key={l} href="#" className="text-xs text-slate-400 hover:text-gray-700 transition-colors">
                {l}
              </a>
            ))}
          </div>
        </div>
      </footer>
    </div>
  );
}
