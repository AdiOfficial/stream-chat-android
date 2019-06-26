package com.getstream.sdk.chat.rest;

import com.getstream.sdk.chat.model.enums.Location;

public class BaseURL {
    private String urlString;
    public BaseURL(Location location){
        urlString = "//chat" + (location == null ? "" : "-") + location.get() + ".stream-io-api.com/";
    }
    public String url(Scheme scheme){
        return scheme.get() + (":") + urlString;
    }

    public enum Scheme {
        https("https"),
        webSocket("wss");
        private String value;

        Scheme(final String value) {
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
}
