import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map } from 'rxjs';
import { ApiResponse } from '../core/models/api-response.model';
import { AdminDashboardSummary } from '../core/models/admin.model';

@Injectable({ providedIn: 'root' })
export class AdminDashboardService {
  constructor(private readonly http: HttpClient) {}

  getSummary() {
    return this.http
      .get<ApiResponse<AdminDashboardSummary>>('/api/admin/dashboard/summary')
      .pipe(map((response) => response.data));
  }
}
