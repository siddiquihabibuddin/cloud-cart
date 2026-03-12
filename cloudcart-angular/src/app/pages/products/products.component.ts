import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { ProductService } from '../../core/services/product.service';
import { Product } from '../../core/models/product.model';
import { ProductCardComponent } from '../../shared/components/product-card/product-card.component';

@Component({
  selector: 'app-products',
  standalone: true,
  imports: [FormsModule, ProductCardComponent],
  templateUrl: './products.component.html'
})
export class ProductsComponent implements OnInit, OnDestroy {
  private productService = inject(ProductService);

  products = signal<Product[]>([]);
  lastKey = signal<string | null>(null);
  loading = signal<boolean>(false);
  loadingMore = signal<boolean>(false);
  searchQuery = signal<string>('');
  searchInput = '';

  searching = signal<boolean>(false);
  error = signal<string | null>(null);

  private searchSubject = new Subject<string>();
  private sub!: Subscription;

  readonly skeletons = Array(12).fill(0);

  ngOnInit(): void {
    this.loadProducts();

    this.sub = this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(q => {
      this.searchQuery.set(q);
      if (q.trim()) {
        this.runSearch(q);
      } else {
        this.searching.set(false);
        this.products.set([]);
        this.lastKey.set(null);
        this.loadProducts();
      }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  onSearchInput(value: string): void {
    this.searchInput = value;
    this.searchQuery.set(value);
    this.searchSubject.next(value);
  }

  loadProducts(): void {
    this.loading.set(true);
    this.error.set(null);
    this.productService.listProducts(12).subscribe({
      next: (res) => {
        this.products.set(res.products ?? []);
        this.lastKey.set(res.nextKey ?? null);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load products. Is LocalStack running?');
        this.loading.set(false);
      }
    });
  }

  loadMore(): void {
    if (!this.lastKey() || this.loadingMore()) return;
    this.loadingMore.set(true);
    this.productService.listProducts(12, this.lastKey()).subscribe({
      next: (res) => {
        this.products.update(p => [...p, ...(res.products ?? [])]);
        this.lastKey.set(res.nextKey ?? null);
        this.loadingMore.set(false);
      },
      error: () => this.loadingMore.set(false)
    });
  }

  private runSearch(q: string): void {
    this.searching.set(true);
    this.productService.search(q).subscribe({
      next: (res) => {
        this.products.set(res.results ?? []);
        this.lastKey.set(null);
        this.searching.set(false);
      },
      error: () => {
        this.products.set([]);
        this.searching.set(false);
      }
    });
  }

  clearSearch(): void {
    this.searchInput = '';
    this.searchQuery.set('');
    this.searchSubject.next('');
    this.products.set([]);
    this.lastKey.set(null);
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

}
