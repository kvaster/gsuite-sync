package com.kvaster.utils.email;

public interface Email {
    Email attach(byte[] data, String name, String mimeType);

    Email setMessage(String message);

    Email setSubject(String subject);

    Email setFrom(String name);

    Email addTo(String address);

    void send();
}
