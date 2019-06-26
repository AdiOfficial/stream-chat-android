package com.getstream.sdk.chat.adapter;

import android.content.Context;
import android.support.annotation.NonNull;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.getstream.sdk.chat.R;
import com.getstream.sdk.chat.model.message.Message;
import com.getstream.sdk.chat.rest.apimodel.response.ChannelResponse;

import java.util.List;

public class MessageListItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final String TAG = MessageListItemAdapter.class.getSimpleName();

    private ChannelResponse channelResponse;
    private View.OnClickListener clickListener;
    private View.OnLongClickListener longClickListener;
    private List<Message> messageList;
    private boolean isThread;
    private Context context;

    public MessageListItemAdapter(Context context, ChannelResponse channelResponse, @NonNull List<Message> messageList, boolean isThread, View.OnClickListener clickListener, View.OnLongClickListener longClickListener) {
        this.context = context;
        this.channelResponse = channelResponse;
        this.messageList = messageList;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        this.isThread = isThread;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                      int viewType) {
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.list_item_message, parent, false);
        RecyclerView.ViewHolder viewHolder = new MessageListItemViewHolder(view);
        return viewHolder;

    }


    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ((MessageListItemViewHolder) holder).bind(this.context, this.channelResponse, messageList, position, isThread, clickListener, longClickListener);

    }

    @Override
    public int getItemCount() {
        if (messageList != null) {
            return messageList.size() + 1;
        } else {
            return 0;
        }
    }
}
