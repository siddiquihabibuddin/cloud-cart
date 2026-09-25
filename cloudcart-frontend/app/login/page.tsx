"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { login } from "@/lib/auth";
import { setSession } from "@/lib/session";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setPending(true);
    try {
      const result = await login(email, password);
      setSession(result);
      router.push("/");
    } catch {
      setError("Invalid email or password.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="flex items-center justify-center py-12">
      <div
        className="w-full max-w-sm rounded-3xl overflow-hidden cc-scale-in"
        style={{ boxShadow: "var(--cc-shadow-lg)" }}
      >
        <div className="h-2 w-full" style={{ background: "var(--cc-grad-brand)" }} aria-hidden="true" />
        <div className="p-8" style={{ background: "var(--cc-surface-card)" }}>
          <div
            className="w-14 h-14 rounded-2xl flex items-center justify-center text-white text-2xl font-black mb-5 shadow-lg"
            style={{ background: "var(--cc-grad-brand)" }}
            aria-hidden="true"
          >
            C
          </div>

          <h1 className="text-2xl font-black mb-2" style={{ color: "var(--cc-text-primary)" }}>
            Welcome back to <span className="cc-gradient-text">CloudCart</span>
          </h1>
          <p className="text-sm mb-6" style={{ color: "var(--cc-text-secondary)" }}>
            Log in to see your cart and orders.
          </p>

          <form onSubmit={handleSubmit} className="space-y-3">
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="Email"
              required
              autoFocus
              className="w-full px-4 py-3 text-sm rounded-xl border-2 transition-colors duration-150"
              style={{ borderColor: "var(--cc-border)", outline: "none", color: "var(--cc-text-primary)" }}
            />
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Password"
              required
              className="w-full px-4 py-3 text-sm rounded-xl border-2 transition-colors duration-150"
              style={{ borderColor: "var(--cc-border)", outline: "none", color: "var(--cc-text-primary)" }}
            />

            {error && (
              <p className="text-sm px-1" style={{ color: "#dc2626" }} role="alert">
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={pending}
              className="w-full py-3 rounded-xl text-sm font-bold text-white transition-all duration-150 disabled:opacity-40 disabled:cursor-not-allowed hover:scale-[1.02] hover:-translate-y-0.5 active:scale-[0.98]"
              style={{ background: "var(--cc-grad-brand)", boxShadow: "var(--cc-shadow-btn)" }}
            >
              {pending ? "Logging in…" : "Log In"}
            </button>
          </form>

          <p className="text-sm mt-5" style={{ color: "var(--cc-text-secondary)" }}>
            Don&rsquo;t have an account?{" "}
            <Link href="/register" className="font-semibold underline" style={{ color: "var(--cc-violet)" }}>
              Create one
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
