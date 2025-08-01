package org.bobj.common.response;

import lombok.Getter;

@Getter
public enum ApiStatus {
    SUCCESS("success"),
    FAIL("fail"),
    ERROR("error");
    private String status;
    ApiStatus(String status) {
        this.status = status;
    }
}