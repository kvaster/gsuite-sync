package com.kvaster.gsuite;

/**
 * Простой класс для сбора сообщений к отправке.
 */
public class Msgs {
    private final StringBuilder msgs = new StringBuilder();
    private int severity;

    private void severity(int s) {
        if (severity < s) {
            severity = s;
        }
    }

    public void info(String msg, Object... args) {
        msgs.append("[info] ").append(String.format(msg, args)).append("\n");
        severity(0);
    }

    public void warn(String msg, Object... args) {
        msgs.append("[warn] ").append(String.format(msg, args)).append("\n");
        severity(1);
    }

    public void error(String msg, Object... args) {
        msgs.append("[error] ").append(String.format(msg, args)).append("\n");
        severity(2);
    }

    public String getSubject() {
        switch (severity) {
            case 0:
                return "INFO";

            case 1:
                return "WARN";

            case 2:
                return "ERROR";

            default:
                return "FATAL";
        }
    }

    public String getMessages() {
        return msgs.toString();
    }

    public boolean hasMessages() {
        return msgs.length() > 0;
    }
}
