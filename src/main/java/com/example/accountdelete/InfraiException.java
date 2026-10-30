package com.example.accountdelete;

public final class InfraiException extends RuntimeException {
    private final String code;
    private final int status;
    public InfraiException(String code, String detail, int status) {
        super(detail);
        this.code = code;
        this.status = status;
    }
    public String code() { return code; }
    public int status() { return status; }
}
