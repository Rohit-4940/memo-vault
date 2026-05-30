package com.nexmemo.core.constant;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-10, Sunday
 **/
public class CurrentDbServer {

    private static final String MSSQL    = "/patch_ms_sql";
    private static final String MYSQL    = "/patch_my_sql";
    private static final String ORACLE   = "/patch_oracle";
    private static final String POSTGRES = "/patch_post_gres";

    private CurrentDbServer() {}

    public static String currentConnectedDb(String jdbcUrl) {
        if (jdbcUrl == null) throw new RuntimeException("Cannot detect DB — JDBC URL is null");

        if (jdbcUrl.contains("sqlserver")) return MSSQL;
        if (jdbcUrl.contains("mysql"))     return MYSQL;
        if (jdbcUrl.contains("oracle"))    return ORACLE;
        if (jdbcUrl.contains("postgres") || jdbcUrl.contains("postgresql")) return POSTGRES;

        throw new RuntimeException("Unsupported database driver in URL: " + jdbcUrl);
    }
}
