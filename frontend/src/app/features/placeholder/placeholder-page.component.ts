import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs';

@Component({
  selector: 'app-placeholder-page',
  template: `
    <section class="page">
      <h1>{{ title() }}</h1>
      <div class="empty">
        <p>This area is not implemented yet. It will use verified backend data when it ships.</p>
      </div>
    </section>
  `,
  styles: `
    .empty {
      margin-top: 1.25rem;
      padding: 2rem;
      border: 1px dashed var(--border);
      border-radius: 1rem;
      background: var(--surface);
      color: var(--muted);
    }
  `,
})
export class PlaceholderPageComponent {
  private readonly route = inject(ActivatedRoute);
  readonly title = toSignal(this.route.data.pipe(map((data) => (data['title'] as string) ?? '')), {
    initialValue: '',
  });
}
