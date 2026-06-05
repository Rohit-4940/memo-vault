package com.nexmemo.core.dto;

import java.util.HashMap;
import java.util.Map;

public class PaginatedRequest {
    private int page = 0;
    private int size = 20;
    private String sortBy = "id";
    private String sortDir = "desc";
    private String search;
    private Map<String, String> filters = new HashMap<>();

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortDir() { return sortDir; }
    public void setSortDir(String sortDir) { this.sortDir = sortDir; }
    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }
    public Map<String, String> getFilters() { return filters; }
    public void setFilters(Map<String, String> filters) { this.filters = filters; }
}
