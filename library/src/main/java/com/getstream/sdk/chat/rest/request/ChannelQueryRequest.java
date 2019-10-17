package com.getstream.sdk.chat.rest.request;

import com.getstream.sdk.chat.enums.Pagination;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

public class ChannelQueryRequest extends BaseQueryChannelRequest<ChannelQueryRequest> {

    @SerializedName("messages")
    @Expose
    protected Map<String, Object> messages;
    @SerializedName("data")
    @Expose
    private Map<String, Object> data;

    public ChannelQueryRequest() {
        this.watch = false;
        this.presence = false;
        this.state = true;
    }

    protected ChannelQueryRequest cloneOpts() {
        ChannelQueryRequest _this = new ChannelQueryRequest();
        _this.state = this.state;
        _this.watch = this.watch;
        _this.presence = this.presence;
        if (this.messages != null) {
            _this.messages = new HashMap<>(this.messages);
        }
        if (this.data != null) {
            _this.data = new HashMap<>(this.data);
        }
        return _this;
    }

    public ChannelQueryRequest withData(Map<String, Object> data) {
        ChannelQueryRequest clone = this.cloneOpts();
        clone.data = data;
        return clone;
    }

    public ChannelQueryRequest withPresence() {
        ChannelQueryRequest clone = this.cloneOpts();
        clone.presence = true;
        return clone;
    }

    public ChannelQueryRequest withMessages(int limit) {
        ChannelQueryRequest clone = this.cloneOpts();
        Map<String, Object> messages = new HashMap<>();
        messages.put("limit", limit);
        clone.messages = messages;
        return clone;
    }

    public ChannelQueryRequest withMessages(Pagination pagination) {
        ChannelQueryRequest clone = this.cloneOpts();
        Map<String, Object> messages = new HashMap<>();
        messages.put("limit", pagination.limit);
        messages.put(pagination.direction.toString(), pagination.messageId);
        clone.messages = messages;
        return clone;
    }
}
