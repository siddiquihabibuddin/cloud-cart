# CloudCart

A serverless e-commerce platform built with AWS Lambda, DynamoDB, SQS, Step Functions, and Next.js — running locally via LocalStack.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      Next.js Frontend                   │
│         Products → Cart → Checkout → Order Status       │
└───────────────────────────┬─────────────────────────────┘
                            │ /api-products, /api-cart, /api-orders
                ┌───────────▼───────────┐
                │   Unified API Gateway  │
                │   (UnifiedApiDev)      │
                └──┬──────────┬─────────┘
                   │          │           │
    ┌──────────────▼──┐  ┌────▼────┐  ┌──▼──────────────┐
    │  product-catalog│  │  cart   │  │  order-service   │
    │  Lambda + DDB   │  │  Lambda │  │  Lambda + DDB    │
    └─────────────────┘  │  + DDB  │  └─────────────────┘
                         └─────────┘
```

### Order Saga (Choreography)

```
order-service
  validate → idempotency check
  → reserve stock via PATCH /products/{id}/stock × N
  → DynamoDB PENDING
  → SagaTableDev (STARTED / STOCK_RESERVED)
  → OrderPlacedEvent → OrderPlacedQueueDev
          │ (DLQ: OrderPlacedDLQDev)
          ▼
  payment-service   80% PAID / 20% FAILED
  → UpdateItem OrdersTableDev (PENDING → PAID or FAILED)
  If PAID:
    → SagaTableDev (STARTED / PAYMENT_COMPLETED)
    → PaymentSuccessEvent → PaymentSuccessQueueDev
          │ (DLQ: PaymentSuccessDLQDev)
          ▼
    shipment-service
    → UpdateItem OrdersTableDev (PAID → SHIPMENT_CREATED)
    → SagaTableDev (COMPLETED / SHIPMENT_CREATED)

  EventBridge (rate 5 min) → OrderShippingStateMachine
    → ScanShipmentCreatedFunction
    → scans OrdersTableDev for SHIPMENT_CREATED orders
    → publishes OrderShippedEvent → OrderShippedQueueDev
          │ (DLQ: OrderShippedDLQDev)
          ▼
    ProcessOrderShippedFunction
    → UpdateItem OrdersTableDev (SHIPMENT_CREATED → SHIPPED)
    → sets trackingId (TRK-XXXXXXXX) + shippedAt

  If FAILED:
    → SagaTableDev (COMPENSATING / PAYMENT_FAILED)
    → StockCompensationEvent → StockCompensationQueueDev
          │ (DLQ: StockCompensationDLQDev)
          ▼
    compensation-service (ProcessCompensationFunctionDev)
    → PATCH /products/{id}/stock {"release":N} × N
    → SagaTableDev (COMPENSATION_COMPLETED / STOCK_RELEASED)
```

## Services

| Service | Runtime | Trigger | Tables |
|---|---|---|---|
| `cloudcart-product-catalog-java` | Java 21 Lambda | REST API | `ProductsTableDev` |
| `cloudcart-cart-service` | Java 21 Lambda | REST API | `CartTableDev` |
| `cloudcart-order-service` | Java 21 Lambda | REST API | `OrdersTableDev`, `IdempotencyTableDev`, `SagaTableDev` |
| `cloudcart-payment-service` | Java 21 Lambda | SQS (`OrderPlacedQueueDev`) | `OrdersTableDev`, `SagaTableDev` |
| `ProcessShipmentFunctionDev` | Java 21 Lambda | SQS (`PaymentSuccessQueueDev`) | `OrdersTableDev`, `SagaTableDev` |
| `ScanShipmentCreatedFunctionDev` | Java 21 Lambda | Step Functions (EventBridge schedule) | `OrdersTableDev` |
| `ProcessOrderShippedFunctionDev` | Java 21 Lambda | SQS (`OrderShippedQueueDev`) | `OrdersTableDev` |
| `ProcessCompensationFunctionDev` | Java 21 Lambda | SQS (`StockCompensationQueueDev`) | `SagaTableDev` |
| `cloudcart-frontend` | Next.js | — | — |

## API Routes

### Orders Page

Customers can view all their past orders at `/orders`. The page lists each order with its status badge, total amount, date, and — once shipped — the tracking number. A "My Orders" link in the header provides quick navigation.

### Product Catalog
| Method | Path | Description |
|---|---|---|
| `GET` | `/products?limit=N&lastKey=X` | List products (paginated, limit capped 1–100) |
| `POST` | `/products` | Create product |
| `GET` | `/products/{id}` | Get product |
| `PATCH` | `/products/{id}/stock` | Update stock — body: `{"stock":N}` (absolute), `{"reserve":N}` (conditional decrement, 409 if insufficient), or `{"release":N}` (increment) |

### Cart
| Method | Path | Description |
|---|---|---|
| `POST` | `/cart` | Add item to cart |
| `GET` | `/cart/{userId}` | View cart |
| `PATCH` | `/cart/{userId}/{productId}` | Update quantity |
| `DELETE` | `/cart/{userId}/{productId}` | Remove item |

### Orders
All order endpoints require `x-api-key: cloudcart-dev-key-2024`.

| Method | Path | Description |
|---|---|---|
| `POST` | `/orders` | Place order — atomic stock reservation, returns 409 if insufficient |
| `GET` | `/orders/{orderId}?userId=X` | Get order — `userId` required; returns 403 if it doesn't match the order owner |
| `GET` | `/orders?userId=X` | List user's orders — queries `userId-index` GSI |

`POST /orders` accepts an optional `Idempotency-Key` header — repeated requests with the same key return the cached response for 24 hours.

## Order Flow

1. Customer adds items to cart and navigates to `/checkout`
2. "Place Order" sends `POST /orders` with `x-api-key` header
   - Input is validated (userId required, quantity ≥ 1, price ≥ 0)
   - Idempotency key is checked against `IdempotencyTableDev` (24h TTL)
   - Stock is reserved via sequential `PATCH /products/{id}/stock {"reserve":N}` calls; on any 409 or error, already-reserved items are released before returning the error
   - If any item is out of stock → **409** `{"error":"Insufficient stock","items":[...]}`
   - Order saved as **PENDING**, saga record created in `SagaTableDev` (`STARTED / STOCK_RESERVED`), `OrderPlacedEvent` published to `OrderPlacedQueueDev`
3. Payment Lambda consumes the event → **80% PAID / 20% FAILED**
   - Uses conditional `UpdateItem` (`attribute_exists(orderId) AND status = PENDING`) — idempotent on retry
   - **If PAID** → saga updated (`STARTED / PAYMENT_COMPLETED`), `PaymentSuccessEvent` published to `PaymentSuccessQueueDev`
   - **If FAILED** → saga updated (`COMPENSATING / PAYMENT_FAILED`), `StockCompensationEvent` published to `StockCompensationQueueDev` for reliable async stock release
   - Failed records reported via `ReportBatchItemFailures` — retried up to 3× before landing in `OrderPlacedDLQDev`
4. Shipment Lambda consumes the payment event
   - Conditional `UpdateItem` (`status = PAID → SHIPMENT_CREATED`) — idempotent on retry
   - Saga updated to `COMPLETED / SHIPMENT_CREATED`
   - Failed records retry up to 3× before landing in `PaymentSuccessDLQDev`
5. EventBridge rule fires every 5 minutes → starts `OrderShippingStateMachineDev`
   - `ScanShipmentCreatedFunctionDev` scans `OrdersTableDev` for `SHIPMENT_CREATED` orders
   - Publishes one `OrderShippedEvent` per order to `OrderShippedQueueDev`
   - `ProcessOrderShippedFunctionDev` consumes each event (batch size 5)
   - Conditional `UpdateItem` (`status = SHIPMENT_CREATED → SHIPPED`) — idempotent on retry
   - Generates `trackingId` (`TRK-XXXXXXXX`) and records `shippedAt` timestamp
   - Failed records retry up to 3× before landing in `OrderShippedDLQDev`
6. Compensation Lambda consumes `StockCompensationEvent` (batch size 1)
   - Calls `PATCH /products/{id}/stock {"release":N}` for each item sequentially
   - On any HTTP failure → message returned to `StockCompensationQueueDev` for retry (up to 3×, then `StockCompensationDLQDev`)
   - On full success → saga updated to `COMPENSATION_COMPLETED / STOCK_RELEASED`
7. Checkout page polls `GET /orders/{id}` every 2s — status progresses PENDING → PAID → SHIPMENT_CREATED; once `SHIPMENT_CREATED` the page shows "Shipment Created" and stops polling. The order advances to `SHIPPED` asynchronously via the scheduler (visible on the `/orders` page).

## Saga State Machine

```
PlaceOrderHandler          → STARTED      / STOCK_RESERVED
ProcessPaymentHandler      → STARTED      / PAYMENT_COMPLETED   (PAID path)
                           → COMPENSATING / PAYMENT_FAILED      (FAILED path)
ProcessShipmentHandler     → COMPLETED    / SHIPMENT_CREATED
ProcessCompensationHandler → COMPENSATION_COMPLETED / STOCK_RELEASED
                             (or stays COMPENSATING → DLQ after 3 retries)
```

Order status lifecycle:
```
PENDING → PAID → SHIPMENT_CREATED → SHIPPED
                                    (set by ProcessOrderShippedFunction with trackingId)
PENDING → FAILED
          (stock released via StockCompensationQueue)
```

`SagaTableDev` records carry a 7-day TTL (`expiresAt`) and are keyed by `orderId`. All saga updates use conditional writes (`sagaStatus = :expected`) so concurrent retries are idempotent.

## Reliability Features

| Feature | Details |
|---|---|
| **Choreography Saga** | Full saga state machine in `SagaTableDev`; each service writes its own step; no orchestrator |
| **SQS-based compensation** | Payment failure publishes to `StockCompensationQueueDev` instead of a fire-and-forget HTTP call; the compensation Lambda retries up to 3× with DLQ fallback |
| **Idempotency** | `POST /orders` deduplicates on `Idempotency-Key` header; results cached 24h in DynamoDB |
| **Stock reservation** | `PATCH /products/{id}/stock {"reserve":N}` — conditional decrement; on order-placement failure, already-reserved items are released synchronously |
| **Dead Letter Queues** | `OrderPlacedDLQDev`, `PaymentSuccessDLQDev`, `StockCompensationDLQDev`, `OrderShippedDLQDev` — messages moved after 3 failed delivery attempts |
| **CloudWatch alarms** | `ApproximateNumberOfMessagesVisible > 0` on all four DLQs |
| **Scheduled shipping** | EventBridge rule (`rate(5 minutes)`) → Step Functions → `ScanShipmentCreatedFunctionDev` scans for `SHIPMENT_CREATED` orders and fans out to `OrderShippedQueueDev` |
| **Conditional DynamoDB writes** | All status transitions use condition expressions; stale retries skip silently |
| **Order ownership check** | `GET /orders/{orderId}` requires `?userId=X`; returns 403 if mismatched |
| **GSI query for order listing** | `GET /orders?userId=X` queries `userId-index` GSI — O(results), not O(table) |
| **Batch item failures** | Payment, shipment, and compensation Lambdas return `batchItemFailures` so only failed records are retried |
| **API key auth** | All order endpoints require `x-api-key: cloudcart-dev-key-2024` |
| **Input validation** | 400s for blank fields, quantity < 1, negative prices, non-numeric pagination params |
| **SDK retry** | All DynamoDB/SQS clients configured with 3 retries + exponential backoff |
| **Static SDK clients** | Clients initialised once per Lambda container; reused across warm invocations |
| **Structured logging** | JSON logs to stdout with `timestamp`, `level`, `service`, `correlationId` fields |
| **CloudWatch metrics** | EMF-format metrics: `OrderPlaced`, `StockInsufficient`, `PaymentSucceeded`, `PaymentFailed`, `ShipmentInitiated`, `OrderShipped`, `CompensationCompleted`, `SagaUpdateFailed`, and error counters |
| **Orders page** | `/orders` frontend page — lists all orders for a user with status badges, totals, dates, and tracking numbers once shipped |

## Screenshots

### Product Listing
![Product Listing](screenshots/product-listing.png)

### Cart
![Cart](screenshots/cart.png?raw=true)

### Checkout
![Checkout](screenshots/checkout.png?raw=true)

### Order Placed
![Order Placed](screenshots/place-order.png)

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

Builds all five service JARs, uploads them to S3, and deploys CloudFormation stacks in dependency order:
`cart` + `products` → `order` → `payment` → `shipment` → `gateway`

### 3. Configure the frontend

After deploy, grab the `UnifiedApiInternalUrl` from the gateway stack output and add it to `cloudcart-frontend/.env.local`:

```env
NEXT_PUBLIC_PRODUCTS_API=/api-products
NEXT_PUBLIC_CART_API=/api-cart
NEXT_PUBLIC_ORDER_API=/api-orders
NEXT_PUBLIC_UNIFIED_API_INTERNAL=http://localhost:4566/restapis/<gateway-api-id>/dev/_user_request_
```

All three frontend rewrites (`/api-products`, `/api-cart`, `/api-orders`) route through the single unified gateway — only one API ID is needed.

### 4. Start the frontend

```bash
cd cloudcart-frontend
npm install
npm run dev
```

Open **http://localhost:3000**

### 5. Seed products (optional)

Products are seeded automatically at the end of `deploy-localstack.sh`. To re-run manually:

```bash
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

## Verifying the Saga

```bash
# Scan saga state for all orders
awslocal dynamodb scan --table-name SagaTableDev --output table

# Check all DLQ alarms
awslocal cloudwatch describe-alarms \
  --alarm-names \
    cloudcart-OrderPlacedDLQ-MessagesVisible \
    cloudcart-PaymentSuccessDLQ-MessagesVisible \
    cloudcart-StockCompensationDLQ-MessagesVisible \
    cloudcart-OrderShippedDLQ-MessagesVisible
```

## Triggering the Scheduled Shipping Flow

The EventBridge rule fires every 5 minutes automatically. To trigger it manually for testing:

```bash
# Find the state machine ARN
awslocal stepfunctions list-state-machines

# Manually start an execution
awslocal stepfunctions start-execution \
  --state-machine-arn arn:aws:states:us-east-1:000000000000:stateMachine:OrderShippingStateMachineDev \
  --input '{}'

# Check a specific order for SHIPPED status + tracking number
awslocal dynamodb get-item \
  --table-name OrdersTableDev \
  --key '{"orderId": {"S": "<your-order-id>"}}' \
  --output table
```

After execution, orders that were in `SHIPMENT_CREATED` will advance to `SHIPPED` with a `trackingId` (e.g. `TRK-A3F2B8C1`) and a `shippedAt` timestamp. The `/orders` page will reflect the updated status.

## Tech Stack

- **Backend**: AWS Lambda (Java 21), DynamoDB, SQS, API Gateway (REST v1)
- **Frontend**: Next.js, TypeScript, Tailwind CSS, Axios
- **Infrastructure**: AWS CloudFormation, LocalStack Pro
- **Build**: Maven (Shade plugin for fat JARs)
