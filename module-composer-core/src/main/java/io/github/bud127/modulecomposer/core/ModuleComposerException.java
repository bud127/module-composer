package io.github.bud127.modulecomposer.core;

public class ModuleComposerException extends RuntimeException {

    public ModuleComposerException(String message) {
        super(message);
    }

    public ModuleComposerException(String message, Throwable cause) {
        super(message, cause);
    }
}
