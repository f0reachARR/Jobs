package me.f0reach.jobs.config;

/**
 * config.yml のパース失敗を示す。
 * ConfigLoader が投げ、onEnable が catch して起動失敗にする。
 */
public class ConfigException extends RuntimeException {
    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
