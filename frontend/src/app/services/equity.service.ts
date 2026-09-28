import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ExerciseRequestBody,
  Grant,
  RegisterGrantRequest,
  Snapshot,
} from '../models/equity.models';

@Injectable({ providedIn: 'root' })
export class EquityService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  listGrants(): Observable<Grant[]> {
    return this.http.get<Grant[]>(`${this.base}/grants`);
  }

  registerGrant(req: RegisterGrantRequest): Observable<Grant> {
    return this.http.post<Grant>(`${this.base}/grants`, req);
  }

  snapshot(grantId: number, date: string): Observable<Snapshot> {
    return this.http.get<Snapshot>(`${this.base}/grants/${grantId}/snapshot`, {
      params: { date },
    });
  }

  satisfyCondition(
    grantId: number,
    monthIndex: number,
    satisfiedAt?: string,
  ): Observable<unknown> {
    return this.http.post(
      `${this.base}/grants/${grantId}/nodes/${monthIndex}/satisfy`,
      null,
      { params: satisfiedAt ? { satisfiedAt } : {} },
    );
  }

  requestExercise(grantId: number, body: ExerciseRequestBody): Observable<{ id: number }> {
    return this.http.post<{ id: number }>(
      `${this.base}/grants/${grantId}/exercises`,
      body,
    );
  }

  confirmExercise(requestId: number, date?: string): Observable<unknown> {
    return this.http.post(`/api/exercises/${requestId}/confirm`, null, {
      params: date ? { date } : {},
    });
  }

  cancelExercise(requestId: number, date?: string): Observable<unknown> {
    return this.http.post(`/api/exercises/${requestId}/cancel`, null, {
      params: date ? { date } : {},
    });
  }
}
