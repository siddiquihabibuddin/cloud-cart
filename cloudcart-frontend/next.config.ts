import type { NextConfig } from "next";

// Single unified API gateway — all service routes go through one endpoint.
// Set NEXT_PUBLIC_UNIFIED_API_INTERNAL after deploying cloudcart-gateway-dev stack.
const UNIFIED_API =
  process.env.NEXT_PUBLIC_UNIFIED_API_INTERNAL ||
  "http://localhost:4566/restapis/PLACEHOLDER/dev/_user_request_";

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      { protocol: "https", hostname: "picsum.photos" },
    ],
  },
  async rewrites() {
    return [
      {
        source: "/api-products/:path*",
        destination: `${UNIFIED_API}/:path*`,
      },
      {
        source: "/api-cart/:path*",
        destination: `${UNIFIED_API}/:path*`,
      },
      {
        source: "/api-orders/:path*",
        destination: `${UNIFIED_API}/:path*`,
      },
    ];
  },
};

export default nextConfig;
