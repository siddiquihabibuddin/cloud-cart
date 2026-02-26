#!/usr/bin/env bash
# Idempotent product seed — re-running is safe (duplicate titles just create new IDs).
set -euo pipefail

PRODUCTS_API="${PRODUCTS_API:-http://localhost:4566/restapis/26xqmiqwkx/dev/_user_request_}"

post() {
  curl -sf -X POST "$PRODUCTS_API/products" \
    -H "Content-Type: application/json" \
    -d "$1" | python3 -c "import sys,json; d=json.load(sys.stdin); print('  +', d.get('id','?'))"
}

echo "==> Seeding products..."

post '{"title":"Widget",             "price":9.99,  "stock":75,  "category":"tools",        "imageUrl":"https://picsum.photos/seed/widget/400/300"}'
post '{"title":"Gadget",             "price":24.99, "stock":50,  "category":"electronics",  "imageUrl":"https://picsum.photos/seed/gadget/400/300"}'
post '{"title":"Mechanical Keyboard","price":89.99, "stock":30,  "category":"electronics",  "imageUrl":"https://picsum.photos/seed/keyboard/400/300"}'
post '{"title":"Wireless Headphones","price":59.99, "stock":20,  "category":"electronics",  "imageUrl":"https://picsum.photos/seed/headphones/400/300"}'
post '{"title":"Desk Lamp",          "price":34.99, "stock":45,  "category":"home",         "imageUrl":"https://picsum.photos/seed/lamp/400/300"}'
post '{"title":"Coffee Mug",         "price":12.99, "stock":100, "category":"kitchen",      "imageUrl":"https://picsum.photos/seed/mug/400/300"}'
post '{"title":"Notebook",           "price":7.99,  "stock":200, "category":"office",       "imageUrl":"https://picsum.photos/seed/notebook/400/300"}'
post '{"title":"USB-C Hub",          "price":44.99, "stock":15,  "category":"electronics",  "imageUrl":"https://picsum.photos/seed/usbhub/400/300"}'
post '{"title":"Plant Pot",          "price":19.99, "stock":60,  "category":"home",         "imageUrl":"https://picsum.photos/seed/plantpot/400/300"}'
post '{"title":"Running Shoes",      "price":119.99,"stock":0,   "category":"apparel",      "imageUrl":"https://picsum.photos/seed/shoes/400/300"}'

echo "==> Done."
