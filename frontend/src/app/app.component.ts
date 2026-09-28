import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Component, OnInit, inject, signal } from '@angular/core';
import { EquityService } from './services/equity.service';
import {
  Grant,
  Quantities,
  RegisterGrantRequest,
  Snapshot,
} from './models/equity.models';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
})
export class AppComponent implements OnInit {
  private readonly api = inject(EquityService);

  readonly grants = signal<Grant[]>([]);
  readonly snapshot = signal<Snapshot | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly actionError = signal('');
  readonly actionOk = signal('');

  selectedGrantId: number | null = null;
  asOfDate = this.isoDate(new Date());

  // 登记表单
  form = this.emptyForm();

  // 行权 / 条件操作表单
  exerciseQty = 100;
  exerciseDate = this.isoDate(new Date());
  satisfyDate = this.isoDate(new Date());
  actionDate = this.isoDate(new Date());

  private emptyForm(): RegisterGrantRequest {
    return {
      name: '',
      grantee: '',
      grantDate: this.isoDate(new Date()),
      totalShares: 4800,
      cliffMonths: 12,
      vestingMonths: 48,
      conditionalNodes: [],
    };
  }

  ngOnInit(): void {
    this.reloadGrants();
  }

  reloadGrants(): void {
    this.api.listGrants().subscribe({
      next: (list) => {
        this.grants.set(list);
        if (this.selectedGrantId === null && list.length > 0) {
          this.selectedGrantId = list[0].id;
          this.loadSnapshot();
        }
      },
      error: (e) => this.error.set(this.msg(e)),
    });
  }

  selectGrant(id: number): void {
    this.selectedGrantId = id;
    this.loadSnapshot();
  }

  loadSnapshot(): void {
    if (this.selectedGrantId === null) {
      return;
    }
    this.loading.set(true);
    this.actionError.set('');
    this.api.snapshot(this.selectedGrantId, this.asOfDate).subscribe({
      next: (s) => {
        this.snapshot.set(s);
        this.loading.set(false);
      },
      error: (e) => {
        this.error.set(this.msg(e));
        this.loading.set(false);
      },
    });
  }

  register(): void {
    this.actionError.set('');
    const req: RegisterGrantRequest = {
      ...this.form,
      conditionalNodes: (this.form.conditionalNodes ?? []).filter(
        (c) => c.monthIndex > 0 && c.conditionLabel.trim() !== '',
      ),
    };
    this.api.registerGrant(req).subscribe({
      next: (g) => {
        this.form = this.emptyForm();
        this.actionOk.set(`已登记授予 ${g.name}`);
        this.reloadGrants();
        this.selectedGrantId = g.id;
        this.loadSnapshot();
      },
      error: (e) => this.actionError.set(this.msg(e)),
    });
  }

  satisfy(monthIndex: number): void {
    if (this.selectedGrantId === null) return;
    this.actionError.set('');
    this.api
      .satisfyCondition(this.selectedGrantId, monthIndex, this.satisfyDate)
      .subscribe({
        next: () => {
          this.actionOk.set(`已标记第 ${monthIndex} 个月批次条件于 ${this.satisfyDate} 满足`);
          this.loadSnapshot();
        },
        error: (e) => this.actionError.set(this.msg(e)),
      });
  }

  requestExercise(): void {
    if (this.selectedGrantId === null) return;
    this.actionError.set('');
    this.api
      .requestExercise(this.selectedGrantId, {
        quantity: this.exerciseQty,
        asOf: this.exerciseDate,
      })
      .subscribe({
        next: () => {
          this.actionOk.set(`已提交 ${this.exerciseQty} 股行权申请（待确认，已占用额度）`);
          this.loadSnapshot();
        },
        error: (e) => this.actionError.set(this.msg(e)),
      });
  }

  confirm(id: number): void {
    this.actionError.set('');
    this.api.confirmExercise(id, this.actionDate).subscribe({
      next: () => {
        this.actionOk.set(`申请 #${id} 已于 ${this.actionDate} 确认`);
        this.loadSnapshot();
      },
      error: (e) => this.actionError.set(this.msg(e)),
    });
  }

  cancel(id: number): void {
    this.actionError.set('');
    this.api.cancelExercise(id, this.actionDate).subscribe({
      next: () => {
        this.actionOk.set(`申请 #${id} 已于 ${this.actionDate} 取消，额度释放`);
        this.loadSnapshot();
      },
      error: (e) => this.actionError.set(this.msg(e)),
    });
  }

  addConditionalRow(): void {
    this.form.conditionalNodes = [
      ...(this.form.conditionalNodes ?? []),
      { monthIndex: this.form.cliffMonths || 1, conditionLabel: '' },
    ];
  }

  removeConditionalRow(i: number): void {
    const rows = [...(this.form.conditionalNodes ?? [])];
    rows.splice(i, 1);
    this.form.conditionalNodes = rows;
  }

  qty(q: Quantities | null, key: keyof Quantities): number {
    return q ? Number(q[key]) : 0;
  }

  toInt(v: string | number): number {
    const n = Number(v);
    return Number.isFinite(n) ? Math.trunc(n) : 0;
  }

  private isoDate(d: Date): string {
    return d.toISOString().slice(0, 10);
  }

  private msg(e: unknown): string {
    const err = e as { error?: { message?: string; code?: string }; message?: string };
    return err?.error?.message ?? err?.message ?? '请求失败';
  }
}
