"use client";

import { useEffect, useState } from "react";

export interface Session {
  token: string;
  userId: string;
  email: string;
}

const TOKEN_KEY = "cc_token";
const USER_ID_KEY = "cc_user_id";
const EMAIL_KEY = "cc_email";

export function getSession(): Session | null {
  if (typeof window === "undefined") return null;
  const token = sessionStorage.getItem(TOKEN_KEY);
  const userId = sessionStorage.getItem(USER_ID_KEY);
  if (!token || !userId) return null;
  return { token, userId, email: sessionStorage.getItem(EMAIL_KEY) || "" };
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return sessionStorage.getItem(TOKEN_KEY);
}

export function setSession(session: Session): void {
  sessionStorage.setItem(TOKEN_KEY, session.token);
  sessionStorage.setItem(USER_ID_KEY, session.userId);
  sessionStorage.setItem(EMAIL_KEY, session.email);
}

export function clearSession(): void {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_ID_KEY);
  sessionStorage.removeItem(EMAIL_KEY);
}

/**
 * undefined while hydrating (no reliable window/sessionStorage on the server),
 * then either the current Session or null if nobody is logged in.
 */
export function useSession(): Session | null | undefined {
  const [session, setSessionState] = useState<Session | null | undefined>(undefined);

  useEffect(() => {
    setSessionState(getSession());
  }, []);

  return session;
}
