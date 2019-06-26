package com.getstream.sdk.chat.model.enums;

public enum Location {
    usEast("us-east-1");

    private String value;

    Location(final String value) {
        this.value = value;
    }

    public String get() {
        return value;
    }

    @Override
    public String toString() {
        return this.get();
    }
}
