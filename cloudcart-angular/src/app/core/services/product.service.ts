import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Product, ProductListResponse, SearchResponse } from '../models/product.model';

@Injectable({ providedIn: 'root' })
export class ProductService {
  private http = inject(HttpClient);
  private base = '/api-products';
  private searchBase = '/api-search';

  listProducts(limit: number = 12, lastKey?: string | null): Observable<ProductListResponse> {
    let params = new HttpParams().set('limit', limit.toString());
    if (lastKey) params = params.set('lastKey', lastKey);
    return this.http.get<ProductListResponse>(`${this.base}/products`, { params });
  }

  getProduct(id: string): Observable<Product> {
    return this.http.get<Product>(`${this.base}/products/${id}`);
  }

  search(query: string, limit: number = 12): Observable<SearchResponse> {
    const params = new HttpParams().set('q', query).set('limit', limit.toString());
    return this.http.get<SearchResponse>(`${this.searchBase}/search`, { params });
  }
}
