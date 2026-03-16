module "cart" {
  source                    = "./modules/cart"
  env                       = var.env
  s3_bucket                 = var.s3_bucket
  cart_jar                  = var.cart_jar
  aws_endpoint_url          = var.aws_endpoint_url
  aws_endpoint_url_internal = var.aws_endpoint_url_internal
  aws_region                = var.aws_region
}

module "products" {
  source                    = "./modules/products"
  env                       = var.env
  s3_bucket                 = var.s3_bucket
  product_jar               = var.product_jar
  aws_endpoint_url          = var.aws_endpoint_url
  aws_endpoint_url_internal = var.aws_endpoint_url_internal
  aws_region                = var.aws_region
}

module "order" {
  source                    = "./modules/order"
  env                       = var.env
  s3_bucket                 = var.s3_bucket
  order_jar                 = var.order_jar
  aws_endpoint_url          = var.aws_endpoint_url
  aws_endpoint_url_internal = var.aws_endpoint_url_internal
  aws_region                = var.aws_region
  products_api_internal_url = module.products.product_api_internal_url
}

module "payment" {
  source                       = "./modules/payment"
  env                          = var.env
  s3_bucket                    = var.s3_bucket
  payment_jar                  = var.payment_jar
  aws_endpoint_url             = var.aws_endpoint_url
  aws_endpoint_url_internal    = var.aws_endpoint_url_internal
  aws_region                   = var.aws_region
  order_queue_arn              = module.order.order_queue_arn
  order_placed_dlq_arn         = module.order.order_placed_dlq_arn
  payment_success_queue_arn    = module.order.payment_success_queue_arn
  payment_success_queue_url    = module.order.payment_success_queue_url
  orders_table_name            = module.order.orders_table_name
  saga_table_name              = module.order.saga_table_name
  saga_table_arn               = module.order.saga_table_arn
  stock_compensation_queue_url = module.order.stock_compensation_queue_url
  stock_compensation_queue_arn = module.order.stock_compensation_queue_arn
}

module "shipment" {
  source                       = "./modules/shipment"
  env                          = var.env
  s3_bucket                    = var.s3_bucket
  shipment_jar                 = var.shipment_jar
  aws_endpoint_url             = var.aws_endpoint_url
  aws_endpoint_url_internal    = var.aws_endpoint_url_internal
  aws_region                   = var.aws_region
  payment_success_queue_arn    = module.order.payment_success_queue_arn
  payment_success_dlq_arn      = module.order.payment_success_dlq_arn
  orders_table_name            = module.order.orders_table_name
  orders_table_arn             = module.order.orders_table_arn
  saga_table_name              = module.order.saga_table_name
  saga_table_arn               = module.order.saga_table_arn
}

module "search" {
  source                    = "./modules/search"
  env                       = var.env
  s3_bucket                 = var.s3_bucket
  search_jar                = var.search_jar
  aws_endpoint_url          = var.aws_endpoint_url
  aws_endpoint_url_internal = var.aws_endpoint_url_internal
  aws_region                = var.aws_region
  products_table_name       = module.products.products_table_name
  products_table_arn        = module.products.products_table_arn
  products_table_stream_arn = module.products.products_table_stream_arn
}

module "gateway" {
  source                    = "./modules/gateway"
  env                       = var.env
  aws_endpoint_url          = var.aws_endpoint_url
  aws_region                = var.aws_region
  order_api_key_id          = module.order.order_api_key_id

  # Lambda ARNs from other modules
  list_products_function_arn    = module.products.list_products_function_arn
  create_product_function_arn   = module.products.create_product_function_arn
  get_product_function_arn      = module.products.get_product_function_arn
  update_stock_function_arn     = module.products.update_stock_function_arn

  add_to_cart_function_arn      = module.cart.add_to_cart_function_arn
  view_cart_function_arn        = module.cart.view_cart_function_arn
  remove_from_cart_function_arn = module.cart.remove_from_cart_function_arn
  update_quantity_function_arn  = module.cart.update_quantity_function_arn
  clear_cart_function_arn       = module.cart.clear_cart_function_arn

  place_order_function_arn      = module.order.place_order_function_arn
  get_order_function_arn        = module.order.get_order_function_arn
  list_orders_function_arn      = module.order.list_orders_function_arn

  search_products_function_arn  = module.search.search_products_function_arn
  bulk_reindex_function_arn     = module.search.bulk_reindex_function_arn
}
