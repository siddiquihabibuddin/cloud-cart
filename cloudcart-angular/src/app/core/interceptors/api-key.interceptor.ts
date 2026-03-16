import { HttpInterceptorFn } from '@angular/common/http';

export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.includes('/api-orders')) {
    return next(req.clone({
      setHeaders: { 'x-api-key': 'cloudcart-dev-key-2024' }
    }));
  }
  return next(req);
};
