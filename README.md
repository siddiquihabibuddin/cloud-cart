# CloudCart

A serverless e-commerce platform built with AWS Lambda, DynamoDB, SQS, and Next.js — running locally via LocalStack.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      Next.js Frontend                   │
│         Products → Cart → Checkout → Order Status       │
└────────────┬──────────────────────────┬─────────────────┘
             │                          │
    ┌────────▼────────┐      ┌──────────▼──────────┐
    │  product-catalog│      │    cart-service      │
    │  Lambda + DDB   │      │    Lambda + DDB      │
    └─────────────────┘      └─────────────────────┘

    ┌─────────────────────────────────────────────────┐
    │                  order-service                  │
    │  stock check → reserve inventory → DynamoDB     │
    │  (PENDING) → OrderPlacedEvent → SQS             │
    └──────────────────────────┬──────────────────────┘
                               │ OrderPlacedQueueDev
                    ┌──────────▼──────────┐
                    │   payment-service   │
                    │  80% PAID / 20% FAILED           │
                    │  → UpdateItem DynamoDB            │
                    │  If PAID → PaymentSuccessEvent   │
                    └──────────┬──────────┘
                               │ PaymentSuccessQueueDev
                    ┌──────────▼──────────┐
                    │  shipment-service   │
                    │  generate trackingId             │
                    │  → UpdateItem SHIPPED            │
                    └─────────────────────┘
```

## Services

| Service | Runtime | Trigger | Table |
|---|---|---|---|
| `cloudcart-product-catalog-java` | Java 21 Lambda | REST API | `ProductsTableDev` |
| `cloudcart-cart-service` | Java 21 Lambda | REST API | `CartTableDev` |
| `cloudcart-order-service` | Java 21 Lambda | REST API | `OrdersTableDev` + SQS |
| `cloudcart-payment-service` | Java 21 Lambda | SQS (`OrderPlacedQueueDev`) | `OrdersTableDev` |
| `cloudcart-shipment-service` | Java 21 Lambda | SQS (`PaymentSuccessQueueDev`) | `OrdersTableDev` |
| `cloudcart-frontend` | Next.js | — | — |

## API Routes

### Product Catalog
| Method | Path | Description |
|---|---|---|
| `GET` | `/products` | List products (paginated) |
| `POST` | `/products` | Create product |
| `GET` | `/products/{id}` | Get product |
| `PATCH` | `/products/{id}/stock` | Update stock |

### Cart
| Method | Path | Description |
|---|---|---|
| `POST` | `/cart` | Add item to cart |
| `GET` | `/cart/{userId}` | View cart |
| `PATCH` | `/cart/{userId}/{productId}` | Update quantity |
| `DELETE` | `/cart/{userId}/{productId}` | Remove item |

### Orders
| Method | Path | Description |
|---|---|---|
| `POST` | `/orders` | Place order — reserves stock, returns 409 if insufficient |
| `GET` | `/orders/{orderId}` | Get order (includes `trackingId` and `shippedAt` once shipped) |
| `GET` | `/orders?userId=X` | List user's orders |

## Order Flow

1. Customer adds items to cart and navigates to `/checkout`
2. "Place Order" sends `POST /orders`
   - Stock is checked per item (`GetItem` on `ProductsTableDev`)
   - If any item is out of stock → **409** `{"error":"Insufficient stock","items":[...]}`
   - Stock is atomically decremented via conditional `UpdateItem` (race-condition safe)
   - Order saved as **PENDING**, `OrderPlacedEvent` published to `OrderPlacedQueueDev`
3. Payment Lambda consumes the event → **80% PAID / 20% FAILED**
   - If PAID → publishes `PaymentSuccessEvent` to `PaymentSuccessQueueDev`
4. Shipment Lambda consumes the payment event → generates `TRK-XXXXXXXX` tracking ID
   - Order updated to **SHIPPED** with `trackingId` and `shippedAt`
5. Checkout page polls `GET /orders/{id}` every 2s — status progresses PENDING → PAID → SHIPPED

## Prerequisites

- Docker
- Java 21 + Maven
- Node.js 20+
- LocalStack Pro (auth token required)
- `awslocal` CLI (`pip install awscli-local`)

## Getting Started

### 1. Start LocalStack

```bash
docker run -d \
  --name localstack \
  -p 4566:4566 \
  -e LOCALSTACK_AUTH_TOKEN=<your-token> \
  -v /var/run/docker.sock:/var/run/docker.sock \
  localstack/localstack-pro:latest
```

### 2. Deploy all stacks

```bash
bash deploy-localstack.sh
```

Builds all five JARs, uploads them to S3, and deploys CloudFormation stacks in dependency order:
`cart` + `products` → `order` → `payment` → `shipment`

### 3. Configure the frontend

After deploy, grab the API IDs from the stack outputs and add them to `cloudcart-frontend/.env.local`:

```env
NEXT_PUBLIC_PRODUCTS_API=/api-products
NEXT_PUBLIC_CART_API=/api-cart
NEXT_PUBLIC_ORDER_API=/api-orders
NEXT_PUBLIC_PRODUCTS_API_INTERNAL=http://localhost:4566/restapis/<products-api-id>/dev/_user_request_
NEXT_PUBLIC_CART_API_INTERNAL=http://localhost:4566/restapis/<cart-api-id>/dev/_user_request_
NEXT_PUBLIC_ORDER_API_INTERNAL=http://localhost:4566/restapis/<order-api-id>/dev/_user_request_
```

### 4. Start the frontend

```bash
cd cloudcart-frontend
npm install
npm run dev
```

Open **http://localhost:3000**

### 5. Seed products (optional)

```bash
PRODUCTS_API=http://localhost:4566/restapis/<products-api-id>/dev/_user_request_ \
  bash seed-products.sh
```

## Force-refreshing Lambda code

CloudFormation only redeploys when the template changes. After a code-only change, force a Lambda update:

```bash
awslocal lambda update-function-code \
  --function-name <FunctionName> \
  --s3-bucket sid-mysourcecode \
  --s3-key <jar-name>
```

## Tech Stack

- **Backend**: AWS Lambda (Java 21), DynamoDB, SQS, API Gateway (REST v1)
- **Frontend**: Next.js, TypeScript, Tailwind CSS, Axios
- **Infrastructure**: AWS CloudFormation, LocalStack Pro
- **Build**: Maven (Shade plugin for fat JARs)
