import axios from "axios";

const authApi = axios.create({
  baseURL: process.env.NEXT_PUBLIC_AUTH_API,
});

export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
}

export async function register(email: string, password: string): Promise<AuthResponse> {
  const { data } = await authApi.post("/register", { email, password });
  return data;
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const { data } = await authApi.post("/login", { email, password });
  return data;
}
