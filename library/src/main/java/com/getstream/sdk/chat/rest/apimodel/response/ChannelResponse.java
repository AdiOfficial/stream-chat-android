package com.getstream.sdk.chat.rest.apimodel.response;

import android.text.TextUtils;

import com.getstream.sdk.chat.model.User;
import com.getstream.sdk.chat.model.channel.Channel;
import com.getstream.sdk.chat.model.channel.Member;
import com.getstream.sdk.chat.model.message.Message;
import com.getstream.sdk.chat.utils.Global;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.Arrays;
import java.util.List;

public class ChannelResponse {

    private final String TAG = ChannelResponse.class.getSimpleName();

    @SerializedName("channel")
    @Expose
    private Channel channel;

    @SerializedName("messages")
    @Expose
    private List<Message> messages;

    @SerializedName("read")
    @Expose
    private List<ChannelUserRead> reads;

    @SerializedName("members")
    @Expose
    private List<Member> members;

    private boolean isSorted = false;

    public Channel getChannel() {
        return channel;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public List<ChannelUserRead> getReads() {
        return reads;
    }

    public List<Member> getMembers() {
        return members;
    }

    public Message getLastMessage() {
        Message lastMessage = null;
        try {
            List<Message> messages = getMessages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message message = messages.get(i);
                if (TextUtils.isEmpty(message.getDeleted_at())) {
                    lastMessage = message;
                    break;
                }
            }
            Global.setStartDay(Arrays.asList(lastMessage), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lastMessage;
    }

    public User getLastReadUser() {
        User lastReadUser = null;
        try {
            if (!isSorted && this.reads != null) {
                Global.sortUserReads(this.reads);
                isSorted = true;
            }
            for (int i = reads.size() - 1; i >= 0; i--) {
                ChannelUserRead channelUserRead = reads.get(i);
                if (!channelUserRead.getUser().getId().equals(Global.streamChat.getUser().getId())) {
                    lastReadUser = channelUserRead.getUser();
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lastReadUser;
    }

    public String getReadDateOfChannelLastMessage(boolean isMyRead) {
        String lastReadDate = null;
        if (this.reads == null) return lastReadDate;
        if (!isSorted) {
            Global.sortUserReads(this.reads);
            isSorted = true;
        }

        try {
            for (int i = reads.size() - 1; i >= 0; i--) {
                ChannelUserRead channelUserRead = reads.get(i);
                if (isMyRead) {
                    if (channelUserRead.getUser().getId().equals(Global.streamChat.getUser().getId())) {
                        lastReadDate = channelUserRead.getLast_read();
                        break;
                    }
                } else {
                    if (!channelUserRead.getUser().getId().equals(Global.streamChat.getUser().getId())) {
                        lastReadDate = channelUserRead.getLast_read();
                        break;
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return lastReadDate;
    }

    public void setReadDateOfChannelLastMessage(User user, String readDate) {
        boolean isSet = false;
        for (final ChannelUserRead userLastRead : this.reads) {
            try {
                User user_ = userLastRead.getUser();
                if (user_.getId().equals(user.getId())) {

                    userLastRead.setLast_read(readDate);
                    // Change Order
                    this.reads.remove(userLastRead);
                    this.reads.add(userLastRead);
                    isSet = true;
                    break;
                }
            } catch (Exception e) {
            }
        }
        if (!isSet) {
            ChannelUserRead channelUserRead = new ChannelUserRead();
            channelUserRead.setUser(user);
            channelUserRead.setLast_read(readDate);
            this.reads.add(channelUserRead);
        }
    }
}

