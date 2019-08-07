package com.getstream.sdk.chat.function;

import android.app.Activity;

import com.getstream.sdk.chat.adapter.CommandListItemAdapter;
import com.getstream.sdk.chat.databinding.ActivityChatBinding;
import com.getstream.sdk.chat.model.Command;
import com.getstream.sdk.chat.model.Channel;

import java.util.List;

public class CommandFunction {
    private static final String TAG = SendFileFunction.class.getSimpleName();

    CommandListItemAdapter adapter = null;

    List<Command> commands = null;
    ActivityChatBinding binding;

    Activity activity;
    Channel channel;

    public CommandFunction(Activity activity, ActivityChatBinding binding, Channel channel) {
        this.activity = activity;
        this.binding = binding;
        this.channel = channel;
    }
}
