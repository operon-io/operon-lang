package io.operon.runner.system.inputsourcedriver.timer.cron;

// Adopted from "https://github.com/asahaf/javacron" (MIT-license)

public class InvalidExpressionException extends Exception {
    private static final long serialVersionUID = 1L;

    InvalidExpressionException() {
        super();
    }

    InvalidExpressionException(String message) {
        super(message);
    }

    InvalidExpressionException(String message, Throwable cause) {
        super(message, cause);
    }

    InvalidExpressionException(Throwable cause) {
        super(cause);
    }
}