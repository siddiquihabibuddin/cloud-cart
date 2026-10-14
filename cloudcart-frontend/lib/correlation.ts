import type { AxiosInstance, InternalAxiosRequestConfig } from "axios";

/**
 * Gives every browser -> API call an X-Correlation-Id. The backend (API Gateway Lambdas, the agent,
 * Groq and MCP calls, the SQS saga) logs under this same id, so one id finds a whole request in
 * Grafana. Set NEXT_PUBLIC_LOG_HTTP=true to also print one console line per call (method, URL,
 * status, duration, id). Bodies and headers are never logged in the browser.
 */
const LOG_HTTP = process.env.NEXT_PUBLIC_LOG_HTTP === "true";

let lastCorrelationId: string | null = null;

/** The id of the most recent API call, for showing in error messages ("ref: ..."). */
export function getLastCorrelationId(): string | null {
  return lastCorrelationId;
}

function newCorrelationId(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`;
}

type TimedConfig = InternalAxiosRequestConfig & { __startedAt?: number; __correlationId?: string };

export function attachCorrelation(instance: AxiosInstance): AxiosInstance {
  instance.interceptors.request.use((config) => {
    const timed = config as TimedConfig;
    timed.__correlationId = newCorrelationId();
    timed.__startedAt = Date.now();
    lastCorrelationId = timed.__correlationId;
    config.headers.set("X-Correlation-Id", timed.__correlationId);
    return config;
  });

  const log = (config: TimedConfig | undefined, status: number | string) => {
    if (!LOG_HTTP || !config) return;
    const ms = config.__startedAt ? Date.now() - config.__startedAt : undefined;
    console.debug(
      `[http] ${config.method?.toUpperCase()} ${config.baseURL ?? ""}${config.url ?? ""} -> ${status} ${ms ?? "?"}ms id=${config.__correlationId}`
    );
  };

  instance.interceptors.response.use(
    (res) => {
      log(res.config as TimedConfig, res.status);
      return res;
    },
    (error) => {
      log(error.config as TimedConfig | undefined, error.response?.status ?? "network-error");
      return Promise.reject(error);
    }
  );

  return instance;
}
