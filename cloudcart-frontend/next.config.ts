import type { NextConfig } from "next";

const PRODUCTS_API =
  process.env.NEXT_PUBLIC_PRODUCTS_API_INTERNAL ||
  "http://localhost:4566/restapis/26xqmiqwkx/dev/_user_request_";

const CART_API =
  process.env.NEXT_PUBLIC_CART_API_INTERNAL ||
  "http://localhost:4566/restapis/gppxzj7nt9/dev/_user_request_";

// Order API ID is set after first deploy via NEXT_PUBLIC_ORDER_API_INTERNAL env var
const ORDER_API =
  process.env.NEXT_PUBLIC_ORDER_API_INTERNAL ||
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
        destination: `${PRODUCTS_API}/:path*`,
      },
      {
        source: "/api-cart/:path*",
        destination: `${CART_API}/:path*`,
      },
      {
        source: "/api-orders/:path*",
        destination: `${ORDER_API}/:path*`,
      },
    ];
  },
};

export default nextConfig;
