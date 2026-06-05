import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { NgIf, NgFor, DecimalPipe, DatePipe, UpperCasePipe, SlicePipe } from '@angular/common';
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
  imports: [NgIf, NgFor, DecimalPipe, DatePipe, UpperCasePipe, SlicePipe, RouterLink, BaseChartDirective],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit, OnDestroy {
  userName = '';
  currentDate = new Date();
  userId = 0;

  private analytics = inject(AnalyticsService);
  private toastr = inject(ToastrService);
  private refreshTimer: any;

  // ── Raw data ──────────────────────────────────────────────────────────────
  summary: any = {};
  slaCompliance: any = {};
  bottleneckHeatmap: any[] = [];
  approverLeaderboard: any[] = [];
  departmentComparison: any[] = [];
  trendForecast: any = {};
  slaAtRisk: any[] = [];
  activityFeed: any[] = [];

  // ── SLA gauge chart ────────────────────────────────────────────────────────
  slaGaugeData: ChartData<'doughnut'> | undefined;
  slaGaugeOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    cutout: '80%',
    plugins: {
      legend: { display: false },
      tooltip: { enabled: false }
    }
  };

  // ── Department comparison chart ────────────────────────────────────────────
  deptChartData: ChartData<'bar'> | undefined;
  deptChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      y: { beginAtZero: true, ticks: { stepSize: 1 }, grid: { color: 'rgba(0,0,0,0.05)' } },
      x: { grid: { display: false } }
    }
  };

  // ── Trend forecast chart ───────────────────────────────────────────────────
  forecastChartData: ChartData<'line'> | undefined;
  forecastChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true, maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom', labels: { usePointStyle: true, font: { size: 10 } } } },
    scales: {
      y: { beginAtZero: true, ticks: { stepSize: 1 }, grid: { color: 'rgba(0,0,0,0.05)' } },
      x: { grid: { display: false } }
    },
    elements: { point: { radius: 3 } }
  };

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.userName = storage.username || 'User';
    this.userId = storage.userId || 0;
    this.loadCommandCenter();
    this.refreshTimer = setInterval(() => this.loadCommandCenter(), 30000);
  }

  ngOnDestroy() {
    if (this.refreshTimer) clearInterval(this.refreshTimer);
  }

  loadCommandCenter() {
    this.analytics.getCommandCenter().subscribe({
      next: (res: any) => {
        const data = res.data || res;
        this.summary = data.summary || {};
        this.slaCompliance = data.slaCompliance || {};
        this.bottleneckHeatmap = data.bottleneckHeatmap || [];
        this.approverLeaderboard = data.approverLeaderboard || [];
        this.departmentComparison = data.departmentComparison || [];
        this.trendForecast = data.trendForecast || {};
        this.slaAtRisk = data.slaAtRisk || [];
        this.activityFeed = data.activityFeed || [];
        this.buildCharts();
      },
      error: () => this.toastr.warning('Could not load command center')
    });
  }

  private buildCharts() {
    this.buildSlaGauge();
    this.buildDeptChart();
    this.buildForecastChart();
  }

  private buildSlaGauge() {
    const pct = this.slaCompliance.overallCompliancePct || 100;
    const remainder = Math.max(0, 100 - pct);
    const color = pct >= 90 ? '#10b981' : pct >= 70 ? '#f59e0b' : '#ef4444';
    this.slaGaugeData = {
      labels: ['Compliant', 'Remaining'],
      datasets: [{
        data: [pct, remainder],
        backgroundColor: [color, '#e5e7eb'],
        borderWidth: 0,
        circumference: 270,
        rotation: 225
      }]
    };
  }

  private buildDeptChart() {
    if (!this.departmentComparison.length) return;
    const labels = this.departmentComparison.map((d: any) => d.department);
    this.deptChartData = {
      labels,
      datasets: [
        {
          label: 'Total',
          data: this.departmentComparison.map((d: any) => d.total),
          backgroundColor: '#6366f1',
          borderRadius: 4
        },
        {
          label: 'Pending',
          data: this.departmentComparison.map((d: any) => d.pending),
          backgroundColor: '#f59e0b',
          borderRadius: 4
        },
        {
          label: 'Approved',
          data: this.departmentComparison.map((d: any) => d.approved),
          backgroundColor: '#10b981',
          borderRadius: 4
        }
      ]
    };
  }

  private buildForecastChart() {
    const history = this.trendForecast.history || [];
    const forecast = this.trendForecast.forecast || [];
    if (!history.length && !forecast.length) return;

    const labels = [...history.map((h: any) => h.label), ...forecast.map((f: any) => f.label)];
    const actualData = [...history.map((h: any) => h.count), ...forecast.map(() => null)];
    const forecastData = [...history.map(() => null), ...forecast.map((f: any) => f.forecast)];
    const lowerData = [...history.map(() => null), ...forecast.map((f: any) => f.lowerBound)];
    const upperData = [...history.map(() => null), ...forecast.map((f: any) => f.upperBound)];

    this.forecastChartData = {
      labels,
      datasets: [
        {
          label: 'Actual',
          data: actualData,
          borderColor: '#6366f1',
          backgroundColor: 'rgba(99,102,241,0.1)',
          fill: false,
          tension: 0.3,
          pointBackgroundColor: '#6366f1'
        },
        {
          label: 'Forecast',
          data: forecastData,
          borderColor: '#f59e0b',
          backgroundColor: 'rgba(245,158,11,0.1)',
          borderDash: [5, 5],
          fill: false,
          tension: 0.3,
          pointBackgroundColor: '#f59e0b'
        },
        {
          label: 'Range',
          data: upperData,
          borderColor: 'rgba(245,158,11,0.2)',
          backgroundColor: 'rgba(245,158,11,0.08)',
          fill: '+1',
          pointRadius: 0,
          borderWidth: 0
        }
      ]
    };
  }

  // ── Helpers ────────────────────────────────────────────────────────────────
  slaColor(pct: number): string {
    return pct >= 90 ? 'var(--nexmemo-success)' : pct >= 70 ? 'var(--nexmemo-warning)' : 'var(--nexmemo-danger)';
  }

  avgApprovalTimeText(): string {
    if (!this.departmentComparison.length) return '—';
    const valid = this.departmentComparison.filter((d: any) => d.avgApprovalHours > 0);
    if (!valid.length) return '—';
    const avg = valid.reduce((s: number, d: any) => s + d.avgApprovalHours, 0) / valid.length;
    return avg < 1 ? '< 1h' : avg < 24 ? `${Math.round(avg)}h` : `${(avg / 24).toFixed(1)}d`;
  }

  humanizeMinutes(minutes: number): string {
    if (!minutes && minutes !== 0) return '';
    if (minutes < 1) return '< 1m';
    if (minutes < 60) return `${minutes}m`;
    const hrs = Math.floor(minutes / 60);
    const mins = minutes % 60;
    if (hrs < 24) return mins ? `${hrs}h ${mins}m` : `${hrs}h`;
    const days = Math.floor(hrs / 24);
    const remHrs = hrs % 24;
    return remHrs ? `${days}d ${remHrs}h` : `${days}d`;
  }

  actionBadgeClass(action: string): string {
    switch (action) {
      case 'APPROVED': return 'bg-success';
      case 'REJECTED': return 'bg-danger';
      case 'PENDING_APPROVAL':
      case 'SUBMITTED': return 'bg-warning text-dark';
      case 'REVISION_REQUESTED': return 'bg-info text-dark';
      default: return 'bg-secondary';
    }
  }

  overdueCount(): number {
    return this.slaAtRisk.filter((m: any) => m.isOverdue).length;
  }

  priorityColor(p: string): string {
    switch (p) {
      case 'URGENT': return '#ef4444';
      case 'HIGH': return '#f59e0b';
      case 'MEDIUM': return '#06b6d4';
      default: return '#6b7280';
    }
  }

  heatColor(hours: number): string {
    if (hours <= 4) return 'var(--nexmemo-success)';
    if (hours <= 12) return 'var(--nexmemo-warning)';
    if (hours <= 24) return '#f97316';
    return 'var(--nexmemo-danger)';
  }

  heatBg(hours: number): string {
    if (hours <= 4) return 'rgba(16,185,129,0.12)';
    if (hours <= 12) return 'rgba(245,158,11,0.12)';
    if (hours <= 24) return 'rgba(249,115,22,0.12)';
    return 'rgba(239,68,68,0.12)';
  }

  statusIcon(action: string): string {
    switch (action) {
      case 'APPROVED': return 'fa-check-circle text-success';
      case 'REJECTED': return 'fa-times-circle text-danger';
      case 'PENDING_APPROVAL': return 'fa-clock text-warning';
      case 'SUBMITTED': return 'fa-paper-plane text-info';
      default: return 'fa-circle text-muted';
    }
  }

  priorityBadge(p: string): string {
    switch (p) {
      case 'URGENT': return 'badge bg-danger';
      case 'HIGH': return 'badge bg-warning text-dark';
      case 'MEDIUM': return 'badge bg-info text-dark';
      default: return 'badge bg-secondary';
    }
  }

  timeAgo(dateStr: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    const diff = Date.now() - d.getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    const days = Math.floor(hrs / 24);
    return `${days}d ago`;
  }
}
