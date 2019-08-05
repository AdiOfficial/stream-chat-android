package com.getstream.sdk.chat.adapter;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.getstream.sdk.chat.rest.Message;
import com.getstream.sdk.chat.rest.response.ChannelResponse;

import java.util.List;

public abstract class BaseMessageListItemViewHolder extends RecyclerView.ViewHolder {


    public BaseMessageListItemViewHolder(int resId, ViewGroup parent) {
        super(LayoutInflater.from(parent.getContext()).inflate(resId, parent, false));
    }

    public abstract void bind(Context context, ChannelResponse channelResponse,
                              @NonNull List<Message> messageList, int position,
                              boolean isThread, View.OnClickListener reactionListener,
                              View.OnLongClickListener longClickListener);
}
