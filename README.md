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
    │   POST /orders → DynamoDB (PENDING) → SQS       │
    └──────────────────────────┬──────────────────────┘
                               │ OrderPlacedEvent
                    ┌──────────▼──────────┐
                    │   payment-service   │
                    │  SQS trigger → 80% PAID / 20% FAILED
                    │  → UpdateItem DynamoDB             │
                    └─────────────────────┘
```

## Services

| Service | Runtime | Trigger | Table |
|---|---|---|---|
| `cloudcart-product-catalog-java` | Java 21 Lambda | REST API | `ProductsTableDev` |
| `cloudcart-cart-service` | Java 21 Lambda | REST API | `CartTableDev` |
| `cloudcart-order-service` | Java 21 Lambda | REST API | `OrdersTableDev` + SQS |
| `cloudcart-payment-service` | Java 21 Lambda | SQS | `OrdersTableDev` |
| `cloudcart-frontend` | Next.js 16 | — | — |

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
| `POST` | `/orders` | Place order (→ SQS) |
| `GET` | `/orders/{orderId}` | Get order status |
| `GET` | `/orders?userId=X` | List user's orders |

## Order Flow

1. Customer adds items to cart and navigates to `/checkout`
2. "Place Order" sends `POST /orders` → order saved as **PENDING**
3. `OrderPlacedEvent` published to SQS `OrderPlacedQueueDev`
4. Payment Lambda consumes the event → **80% PAID / 20% FAILED**
5. Checkout page polls `GET /orders/{id}` every 2s and displays the final status

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

This builds all four JARs, uploads them to S3, and deploys the CloudFormation stacks in dependency order (order stack before payment stack).

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
- **Frontend**: Next.js 16, TypeScript, Tailwind CSS, Axios
- **Infrastructure**: AWS CloudFormation, LocalStack Pro
- **Build**: Maven (Shade plugin for fat JARs)
