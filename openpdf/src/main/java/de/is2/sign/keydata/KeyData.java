package de.is2.sign.keydata;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import lombok.Data;

public class KeyData {
    private PrivateKey key = null;
    private Certificate[] chain = null;
    private String message;

    public PrivateKey getKey() {
        return key;
    }

    public void setKey(PrivateKey key) {
        this.key = key;
    }

    public Certificate[] getChain() {
        return chain;
    }

    public void setChain(Certificate[] chain) {
        this.chain = chain;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
