import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EquityApiService } from './equity-api.service';
import { CreateGrantPayload, GrantSummary, GrantTimeline } from './models';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  private api = inject(EquityApiService);

  grants = signal<GrantSummary[]>([]);
  selectedGrantId = signal<number | null>(null);
  asOf = signal<string>(new Date().toISOString().slice(0, 10));
  timeline = signal<GrantTimeline | null>(null);
  error = signal<string>('');
  showCreate = signal(false);

  // exercise form
  exRequestNo = '';
  exQuantity: number | null = null;
  exDate = this.asOf();

  // create form
  form: CreateGrantPayload = {
    grantName: 'OPT-2025-A', grantee: 'Alex Founder', grantDate: '2025-01-31',
    totalQuantity: 4800, cliffMonths: 12, vestingMonths: 48,
    planName: 'Fictional Plan 2025', conditionalMonths: [24]
  };
  conditionalMonthsText = '24';

  // Stacked-bar percentages (vested pool expanded into its three stages).
  bar = computed(() => {
    const b = this.timeline()?.balance;
    if (!b) { return { unvested: 0, exercisable: 0, reserved: 0, exercised: 0 }; }
    const pct = (v: number) => b.total > 0 ? (v / b.total) * 100 : 0;
    return {
      unvested: pct(b.unvested),
      exercisable: pct(b.exercisable),
      reserved: pct(b.pendingReserved),
      exercised: pct(b.exercised)
    };
  });

  ngOnInit(): void {
    this.loadGrants();
  }

  loadGrants(): void {
    this.api.listGrants().subscribe({
      next: list => {
        this.grants.set(list);
        if (list.length && this.selectedGrantId() === null) {
          this.selectedGrantId.set(list[0].id);
          this.refresh();
        }
      },
      error: e => this.fail(e)
    });
  }

  onSelectGrant(): void {
    this.refresh();
  }

  refresh(): void {
    const id = this.selectedGrantId();
    if (id === null) { return; }
    this.error.set('');
    this.api.timeline(id, this.asOf()).subscribe({
      next: t => this.timeline.set(t),
      error: e => this.fail(e)
    });
  }

  createGrant(): void {
    this.error.set('');
    const payload: CreateGrantPayload = {
      ...this.form,
      conditionalMonths: this.conditionalMonthsText
        .split(',').map(s => parseInt(s.trim(), 10)).filter(n => !Number.isNaN(n))
    };
    this.api.createGrant(payload).subscribe({
      next: t => {
        this.showCreate.set(false);
        this.loadGrants();
        this.selectedGrantId.set(t.grantId);
        this.asOf.set(t.grantDate);
        this.refresh();
      },
      error: e => this.fail(e)
    });
  }

  markCondition(seqNo: number): void {
    const id = this.selectedGrantId();
    if (id === null) { return; }
    this.api.markCondition(id, seqNo, this.asOf()).subscribe({
      next: () => this.refresh(),
      error: e => this.fail(e)
    });
  }

  submitExercise(): void {
    const id = this.selectedGrantId();
    if (id === null || this.exQuantity === null) { return; }
    this.api.requestExercise(id, this.exRequestNo, this.exQuantity, this.exDate)
      .subscribe({
        next: () => {
          this.exRequestNo = '';
          this.exQuantity = null;
          this.asOf.set(this.exDate);
          this.refresh();
        },
        error: e => this.fail(e)
      });
  }

  confirm(reqId: number): void {
    this.api.confirm(reqId, this.asOf()).subscribe({
      next: () => this.refresh(),
      error: e => this.fail(e)
    });
  }

  cancel(reqId: number): void {
    this.api.cancel(reqId, this.asOf()).subscribe({
      next: () => this.refresh(),
      error: e => this.fail(e)
    });
  }

  fmt(v: number): string {
    return v.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 4 });
  }

  trackNode(_: number, n: { seqNo: number }): number { return n.seqNo; }
  trackReq(_: number, r: { id: number }): number { return r.id; }

  private fail(e: { error?: { error?: string }; message?: string }): void {
    this.error.set(e?.error?.error || e?.message || 'Request failed');
  }
}
