import { Component, OnInit } from '@angular/core';
import { NgIf, DecimalPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LocalStorageUtil } from '../../../@core/utils/local-storage-util';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [NgIf, DecimalPipe, RouterLink, DatePipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  userName = '';
  roleName = '';
  currentDate = new Date();
  stats = {
    totalMemos: 0,
    pendingApprovals: 0,
    approvedToday: 0,
    totalBudget: 0
  };
  recentActivities: any[] = [];

  ngOnInit() {
    const storage = LocalStorageUtil.getStorage();
    this.userName = storage.username || 'User';
    this.roleName = storage.roleName || '';
  }
}
