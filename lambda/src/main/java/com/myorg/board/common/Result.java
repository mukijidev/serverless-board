package com.myorg.board.common;

public record Result<T>(Outcome outcome, T value) {

    public enum Outcome {
        OK,
        NOT_FOUND,
        FORBIDDEN
    }

    public static <T> Result<T> ok(T value) {
        return new Result<>(Outcome.OK, value);
    }

    public static <T> Result<T> notFound() {
        return new Result<>(Outcome.NOT_FOUND, null);
    }

    public static <T> Result<T> forbidden() {
        return new Result<>(Outcome.FORBIDDEN, null);
    }
}
