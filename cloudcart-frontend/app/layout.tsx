"use client";

import "./globals.css";
import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import Header from "@/components/Header";
import Footer from "@/components/Footer";
import ChatWidget from "@/components/ChatWidget";
import { CartProvider } from "@/lib/CartContext";
import { clearSession, getSession, Session } from "@/lib/session";

const PUBLIC_PATHS = ["/login", "/register"];

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const [session, setSession] = useState<Session | null | undefined>(undefined);
  const isPublicPath = PUBLIC_PATHS.includes(pathname);

  useEffect(() => {
    const current = getSession();
    setSession(current);
    if (!current && !isPublicPath) {
      router.replace("/login");
    }
  }, [pathname, isPublicPath, router]);

  function handleLogout() {
    clearSession();
    setSession(null);
    router.push("/login");
  }

  const userId = session?.userId ?? null;
  const ready = session !== undefined;
  const showAppShell = !isPublicPath && ready && session !== null;

  return (
    <html lang="en">
      <body>
        {isPublicPath ? (
          <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
            {children}
          </main>
        ) : (
          <CartProvider userId={userId}>
            <Header userId={userId} email={session?.email ?? null} onLogout={handleLogout} />
            <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
              {showAppShell ? children : null}
            </main>
            <Footer />
            {showAppShell && <ChatWidget userId={userId} />}
          </CartProvider>
        )}
      </body>
    </html>
  );
}
