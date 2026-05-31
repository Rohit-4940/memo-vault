import { Component, OnInit, inject } from '@angular/core';
import { NgIf, DecimalPipe, DatePipe, NgFor } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';
import { AnalyticsService } from '../../../shared-services/api-service/analytics.service';
import { BaseChartDirective } from 'ng2-charts';
import { Chart, ChartConfiguration, ChartData, registerables } from 'chart.js';
import { ToastrService } from 'ngx-toastr';

Chart.register(...registerables);

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [NgIf, DecimalPipe, DatePipe, RouterLink, BaseChartDirective, NgFor],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  userName = '';
  roleName = '';
  currentDate = new Date();
  userId: number = 0;

  summary: any = {};
  recentMemos: any[] = [];
  expenseCategories: any[] = [];
  monthlyTrend: any[] = [];

  statusChartData: ChartData<'doughnut'> | undefined = undefined;
  statusChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom', labels: { padding: 12, usePointStyle: true, font: { size: 11 } } } },
    cutout: '65%'
  };

  trendChartData: ChartData<'bar'> | undefined = undefined;
  trendChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } }, x: { grid: { display: false } } }
  };

  expenseChartData: ChartData<'pie'> | undefined = undefined;
  expenseChartOptions: ChartConfiguration<'pie'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom', labels: { padding: 10, usePointStyle: true, font: { size: 10 } } } }
  };

  private analyticsService = inject(AnalyticsService);
  private toastr = inject(ToastrService);

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.userName = storage.username || 'User';
    this.roleName = storage.roleName || '';
    this.userId = storage.userId || 0;
    this.loadData();
  }

  loadData() {
    this.analyticsService.getDashboardSummary().subscribe({
      next: (data) => { this.summary = data; },
      error: () => this.toastr.warning('Could not load dashboard data')
    });
    this.analyticsService.getStatusBreakdown().subscribe({
      next: (data) => { this.buildStatusChart(data); }
    });
    this.analyticsService.getMonthlyTrend().subscribe({
      next: (data) => { this.monthlyTrend = data; this.buildTrendChart(data); }
    });
    this.analyticsService.getExpenseByCategory().subscribe({
      next: (data) => { this.expenseCategories = data; this.buildExpenseChart(data); }
    });
  }

  buildStatusChart(data: any[]) {
    const colors: string[] = ['#6366f1', '#f59e0b', '#10b981', '#ef4444', '#6b7280', '#3b82f6'];
    this.statusChartData = {
      labels: data.map((d: any) => d.label),
      datasets: [{ data: data.map((d: any) => d.count), backgroundColor: data.map((_: any, i: number) => colors[i % colors.length]), borderWidth: 0 }]
    };
  }

  buildTrendChart(data: any[]) {
    this.trendChartData = {
      labels: data.map((d: any) => d.label),
      datasets: [{ data: data.map((d: any) => d.count), backgroundColor: '#6366f1', borderRadius: 6 }]
    };
  }

  buildExpenseChart(data: any[]) {
    if (!data || data.length === 0) return;
    const colors = ['#6366f1', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6', '#f97316'];
    this.expenseChartData = {
      labels: data.map((d: any) => d.categoryName),
      datasets: [{ data: data.map((d: any) => d.memoCount), backgroundColor: data.map((_: any, i: number) => colors[i % colors.length]), borderWidth: 0 }]
    };
  }

  getStatusClass(status: string): string {
    const map: Record<string, string> = { 'DRAFT': 'badge-secondary', 'PENDING_APPROVAL': 'badge-warning', 'APPROVED': 'badge-success', 'REJECTED': 'badge-danger', 'CANCELLED': 'badge-dark', 'REVISION_REQUESTED': 'badge-info' };
    return map[status] || 'badge-secondary';
  }
}
