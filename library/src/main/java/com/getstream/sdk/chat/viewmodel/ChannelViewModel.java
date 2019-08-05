package com.getstream.sdk.chat.viewmodel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.databinding.ObservableField;
import android.text.TextUtils;

import com.getstream.sdk.chat.rest.Message;
import com.getstream.sdk.chat.rest.User;
import com.getstream.sdk.chat.rest.response.ChannelResponse;
import com.getstream.sdk.chat.utils.Global;

import java.util.List;

public class ChannelViewModel extends ViewModel {
    // TODO: Implement the ViewModel
    private ChannelResponse channelResponse;

    public ChannelViewModel(ChannelResponse channelResponse) {
        this.channelResponse = channelResponse;
    }

    public ChannelResponse getChannelResponse() {
        return channelResponse;
    }

    private MutableLiveData<List<Message>> channelMessages = new MutableLiveData<>();

    public MutableLiveData<List<Message>> getChannelMessages() {
        return channelMessages;
    }

    public void setChannelMessages(List<Message> channelMessages) {
        this.channelMessages.setValue(channelMessages);
    }

    public boolean isOnline() {
        if (channelResponse == null) return false;
        try {
            if (Global.getOpponentUser(channelResponse) == null)
                return false;

            return Global.getOpponentUser(channelResponse).getOnline();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    public String channelName() {
        if (channelResponse == null) return null;
        if (!TextUtils.isEmpty(channelResponse.getChannel().getName())) {
            return channelResponse.getChannel().getName();
        } else {
            User opponent = Global.getOpponentUser(channelResponse);
            if (opponent != null) {
                return opponent.getName();
            }
        }
        return null;
    }
    public boolean isVisibleLastActive() {
        if (channelResponse == null) return false;
        User opponent = Global.getOpponentUser(channelResponse);
        if (opponent != null) {
            if (TextUtils.isEmpty(Global.differentTime(opponent.getLast_active())))
                return false;
            else {

                return true;
            }
        }
        return false;
    }
    public String lastActive(){
        if (channelResponse == null) return null;
        // Last Active
        User opponent = Global.getOpponentUser(channelResponse);
        if (opponent != null) {
            if (TextUtils.isEmpty(Global.differentTime(opponent.getLast_active())))
                return null;
            else {

                return Global.differentTime(opponent.getLast_active());
            }
        }
        return null;
    }


    private ObservableField<Integer> replyCount = new ObservableField<>();

    public ObservableField<Integer> getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(int replyCount) {
        this.replyCount.set(replyCount);
    }
// endregion
}
