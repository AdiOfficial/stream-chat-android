package com.getstream.sdk.chat.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.getstream.sdk.chat.view.MessageListView;
import com.getstream.sdk.chat.view.MessageListViewStyle;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import io.getstream.chat.android.client.models.Channel;

public abstract class BaseMessageListItemViewHolder extends RecyclerView.ViewHolder {

    public BaseMessageListItemViewHolder(int resId, ViewGroup parent) {
        super(LayoutInflater.from(parent.getContext()).inflate(resId, parent, false));
    }

    public abstract void bind(@NonNull Context context,
                              @NonNull Channel channel,
                              @NonNull MessageListItem messageListItem,
                              @NonNull MessageListViewStyle style,
                              @NonNull MessageListView.BubbleHelper bubbleHelper,
                              @NonNull MessageViewHolderFactory factory,
                              int position);
}
