package com.example.lightscript.agent;

/**
 * Token无效异常，表示需要重新注册
 */
class TokenInvalidException extends Exception {
    public TokenInvalidException(String message) {
        super(message);
    }
}
