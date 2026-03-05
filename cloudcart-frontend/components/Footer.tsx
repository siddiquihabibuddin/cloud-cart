"use client";

import Link from "next/link";

export default function Footer() {
  return (
    <footer
      className="mt-auto w-full"
      style={{ background: "var(--cc-grad-dark)" }}
    >
      {/* Top accent line */}
      <div className="h-px w-full" style={{ background: "var(--cc-grad-brand)" }} />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-10">

          {/* Brand column */}
          <div className="sm:col-span-1">
            <div className="flex items-center gap-2.5 mb-3">
              <div
                className="w-8 h-8 rounded-lg flex items-center justify-center text-white text-base font-black"
                style={{ background: "var(--cc-grad-brand)" }}
                aria-hidden="true"
              >
                C
              </div>
              <span className="text-lg font-black text-white tracking-tight">
                CloudCart
              </span>
            </div>
            <p className="text-sm leading-relaxed" style={{ color: "#a78bfa" }}>
              A cloud-native e-commerce platform built on AWS Lambda, DynamoDB,
              and SQS — fast, serverless, and scalable.
            </p>
          </div>

          {/* Quick links */}
          <div>
            <h3 className="text-xs font-bold uppercase tracking-widest mb-4" style={{ color: "#6366f1" }}>
              Shop
            </h3>
            <ul className="space-y-2.5">
              {[
                { href: "/", label: "All Products" },
                { href: "/cart", label: "My Cart" },
                { href: "/checkout", label: "Checkout" },
                { href: "/orders", label: "My Orders" },
              ].map((link) => (
                <li key={link.href}>
                  <Link
                    href={link.href}
                    className="text-sm transition-colors duration-150"
                    style={{ color: "#c4b5fd" }}
                    onMouseEnter={(e) => {
                      (e.currentTarget as HTMLElement).style.color = "#ffffff";
                    }}
                    onMouseLeave={(e) => {
                      (e.currentTarget as HTMLElement).style.color = "#c4b5fd";
                    }}
                  >
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {/* Tech stack badges */}
          <div>
            <h3 className="text-xs font-bold uppercase tracking-widest mb-4" style={{ color: "#6366f1" }}>
              Powered by
            </h3>
            <div className="flex flex-wrap gap-2">
              {["AWS Lambda", "DynamoDB", "SQS", "Next.js 16", "Tailwind CSS"].map((tech) => (
                <span
                  key={tech}
                  className="px-2.5 py-1 rounded-md text-xs font-medium"
                  style={{
                    background: "rgba(99,102,241,0.15)",
                    color: "#a78bfa",
                    border: "1px solid rgba(99,102,241,0.25)",
                  }}
                >
                  {tech}
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* Bottom bar */}
        <div
          className="mt-10 pt-6 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs"
          style={{ borderTop: "1px solid rgba(99,102,241,0.25)", color: "#9b8ec4" }}
        >
          <p>
            &copy; {new Date().getFullYear()} CloudCart. Demo project.
          </p>
          <p style={{ color: "#7c6fb0" }}>
            Built with serverless architecture on LocalStack
          </p>
        </div>
      </div>
    </footer>
  );
}
