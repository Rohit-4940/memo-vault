package com.nexmemo.core.dto;

import lombok.Data;

/**
 * Password supplied when redeeming a protected share link. Carried in the request <b>body</b> (never
 * a query string) so it cannot leak into access logs, browser history or the {@code Referer} header.
 */
@Data
public class RedeemShareLinkRequest {
    private String password;
}
