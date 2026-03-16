import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { ProductService } from '../../core/services/product.service';
import { Product } from '../../core/models/product.model';
import { ProductCardComponent } from '../../shared/components/product-card/product-card.component';

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [CommonModule, FormsModule, ProductCardComponent],
  templateUrl: './products.component.html'
})
export class ProductsComponent implements OnInit, OnDestroy {
  private productService = inject(ProductService);

  products: Product[] = [];
  lastKey: string | null = null;
  loading = false;
  loadingMore = false;
  searchQuery = '';
  searchInput = '';

  searching = false;
  error: string | null = null;

  private searchSubject = new Subject<string>();
  private sub!: Subscription;

  readonly skeletons = Array(12).fill(0);

  ngOnInit(): void {
    this.loadProducts();

    this.sub = this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(q => {
      this.searchQuery = q;
      if (q.trim()) {
        this.runSearch(q);
      } else {
        this.searching = false;
        this.products = [];
        this.lastKey = null;
        this.loadProducts();
      }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  onSearchInput(value: string): void {
    this.searchInput = value;
    this.searchQuery = value;
    this.searchSubject.next(value);
  }

  loadProducts(): void {
    this.loading = true;
    this.error = null;
    this.productService.listProducts(12).subscribe({
      next: (res) => {
        this.products = res.products ?? [];
        this.lastKey = res.nextKey ?? null;
        this.loading = false;
      },
      error: () => {
        this.error = 'Failed to load products. Is LocalStack running?';
        this.loading = false;
      }
    });
  }

  loadMore(): void {
    if (!this.lastKey || this.loadingMore) return;
    this.loadingMore = true;
    this.productService.listProducts(12, this.lastKey).subscribe({
      next: (res) => {
        this.products = [...this.products, ...(res.products ?? [])];
        this.lastKey = res.nextKey ?? null;
        this.loadingMore = false;
      },
      error: () => { this.loadingMore = false; }
    });
  }

  private runSearch(q: string): void {
    this.searching = true;
    this.productService.search(q).subscribe({
      next: (res) => {
        this.products = res.results ?? [];
        this.lastKey = null;
        this.searching = false;
      },
      error: () => {
        this.products = [];
        this.searching = false;
      }
    });
  }

  clearSearch(): void {
    this.searchInput = '';
    this.searchQuery = '';
    this.searchSubject.next('');
    this.products = [];
    this.lastKey = null;
    this.loadProducts();
  }

  onSearchFocus(event: FocusEvent): void {
    const el = event.target as HTMLInputElement;
    el.style.borderColor = 'var(--cc-violet)';
    el.style.boxShadow = 'var(--cc-shadow-glow)';
  }

  onSearchBlur(event: FocusEvent): void {
    const el = event.target as HTMLInputElement;
    if (!el.value) {
      el.style.borderColor = 'rgba(99,102,241,0.25)';
      el.style.boxShadow = 'none';
    }
  }

  trackByProductId(index: number, product: Product): string {
    return product.productId;
  }

  trackByIndex(index: number, item: any): number {
    return index;
  }
}
