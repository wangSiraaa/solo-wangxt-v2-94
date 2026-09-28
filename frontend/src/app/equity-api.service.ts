import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateGrantPayload, GrantSummary, GrantTimeline } from './models';

@Injectable({ providedIn: 'root' })
export class EquityApiService {
  private http = inject(HttpClient);
  private base = '/api';

  listGrants(): Observable<GrantSummary[]> {
    return this.http.get<GrantSummary[]>(`${this.base}/grants`);
  }

  createGrant(payload: CreateGrantPayload): Observable<GrantTimeline> {
    return this.http.post<GrantTimeline>(`${this.base}/grants`, payload);
  }

  timeline(grantId: number, asOf: string): Observable<GrantTimeline> {
    return this.http.get<GrantTimeline>(
      `${this.base}/grants/${grantId}/timeline`, { params: { asOf } });
  }

  markCondition(grantId: number, seqNo: number, metDate: string): Observable<void> {
    return this.http.post<void>(
      `${this.base}/grants/${grantId}/nodes/${seqNo}/mark-condition`, { metDate });
  }

  requestExercise(grantId: number, requestNo: string, quantity: number, requestDate: string) {
    return this.http.post<GrantTimeline>(
      `${this.base}/grants/${grantId}/exercises`, { requestNo, quantity, requestDate });
  }

  confirm(requestId: number, date: string) {
    return this.http.post<GrantTimeline>(
      `${this.base}/exercises/${requestId}/confirm`, null, { params: { date } });
  }

  cancel(requestId: number, date: string) {
    return this.http.post<GrantTimeline>(
      `${this.base}/exercises/${requestId}/cancel`, null, { params: { date } });
  }
}
