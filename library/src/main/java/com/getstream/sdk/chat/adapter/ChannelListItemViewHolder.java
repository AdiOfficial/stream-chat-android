package com.getstream.sdk.chat.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.core.graphics.drawable.DrawableCompat;

import com.getstream.sdk.chat.MarkdownImpl;
import com.getstream.sdk.chat.R;
import com.getstream.sdk.chat.StreamChat;
import com.getstream.sdk.chat.channels.GetChannelNameOrMembers;
import com.getstream.sdk.chat.model.Attachment;
import com.getstream.sdk.chat.model.Channel;
import com.getstream.sdk.chat.model.ModelType;
import com.getstream.sdk.chat.rest.Message;
import com.getstream.sdk.chat.rest.User;
import com.getstream.sdk.chat.rest.response.ChannelState;
import com.getstream.sdk.chat.rest.response.ChannelUserRead;
import com.getstream.sdk.chat.style.FontsManager;
import com.getstream.sdk.chat.users.GetOtherUsers;
import com.getstream.sdk.chat.utils.StringUtility;
import com.getstream.sdk.chat.view.AvatarGroupView;
import com.getstream.sdk.chat.view.ChannelListView;
import com.getstream.sdk.chat.view.ChannelListViewStyle;
import com.getstream.sdk.chat.view.ReadStateView;

import java.text.SimpleDateFormat;
import java.util.List;

public class ChannelListItemViewHolder extends BaseChannelListItemViewHolder {

    static final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d");

    private TextView tv_name, tv_last_message, tv_date;
    private ReadStateView<ChannelListViewStyle> read_state;
    private AvatarGroupView<ChannelListViewStyle> avatarGroupView;
    private ImageView iv_attachment_type;
    private View click_area;
    private Context context;

    private ChannelListView.UserClickListener userClickListener;
    private ChannelListView.ChannelClickListener channelClickListener;
    private ChannelListView.ChannelClickListener channelLongClickListener;
    private ChannelListViewStyle style;

    private MarkdownImpl.MarkdownListener markdownListener;

    public ChannelListItemViewHolder(@NonNull View itemView) {
        super(itemView);
        findReferences();
    }


    public void setUserClickListener(ChannelListView.UserClickListener l) {
        userClickListener = l;
    }

    public void setChannelClickListener(ChannelListView.ChannelClickListener l) {
        channelClickListener = l;
    }

    public void setChannelLongClickListener(ChannelListView.ChannelClickListener l) {
        channelLongClickListener = l;
    }

    private void findReferences() {
        tv_name = itemView.findViewById(R.id.tv_name);
        tv_last_message = itemView.findViewById(R.id.tv_last_message);
        iv_attachment_type = itemView.findViewById(R.id.iv_attachment_type);
        tv_date = itemView.findViewById(R.id.tv_date);

        click_area = itemView.findViewById(R.id.click_area);
        avatarGroupView = itemView.findViewById(R.id.avatar_group);
        read_state = itemView.findViewById(R.id.read_state);
    }

    public void setStyle(ChannelListViewStyle style) {
        this.style = style;
    }

    public void setMarkdownListener(MarkdownImpl.MarkdownListener markdownListener) {
        this.markdownListener = markdownListener;
    }

    @Override
    public void bind(Context context, @NonNull ChannelState channelState, int position) {

        // setup the click listeners and the markdown builder
        this.context = context;

        // the UI depends on the
        // - lastMessage
        // - unread count
        // - read state for this channel

        // set the channel name
        configChannelName(channelState);
        // set the data for the avatar
        configAvatarView(channelState);
        // set the lastMessage
        configLastMessage(channelState);
        // set last message date
        configLastMessageDate(channelState);
        // read indicators
        configReadState(channelState);
        // set Click listeners
        configClickListeners(channelState);
        // apply style
        applyStyle(channelState);
    }

    // set the channel name
    private void configChannelName(ChannelState channelState) {
        String channelName = new GetChannelNameOrMembers(new GetOtherUsers(StreamChat.getUsersRepository())).getChannelNameOrMembers(channelState.getChannel());
        tv_name.setText((!TextUtils.isEmpty(channelName)? channelName : style.getChannelWithoutNameText()));
    }

    private void configAvatarView(ChannelState channelState){
        Channel channel = channelState.getChannel();
        List<User> otherUsers = new GetOtherUsers(StreamChat.getUsersRepository()).getOtherUsers(channel);
        avatarGroupView.setChannelAndLastActiveUsers(channelState.getChannel(), otherUsers, style);
        // click listeners
        avatarGroupView.setOnClickListener(view -> {
            // if there is 1 user
            if (otherUsers.size() == 1 && this.userClickListener != null) {
                this.userClickListener.onUserClick(otherUsers.get(0));
            } else if (this.channelClickListener != null) {
                this.channelClickListener.onClick(channel);
            }
        });
    }

    @SuppressLint("ResourceType")
    private void configLastMessage(ChannelState channelState){
        Message lastMessage = channelState.getLastMessage();
        iv_attachment_type.setVisibility(View.GONE);
        if (lastMessage == null){
            tv_last_message.setText("");
            return;
        }

        if (!TextUtils.isEmpty(lastMessage.getText())) {
            if (markdownListener != null)
                markdownListener.setText(tv_last_message, StringUtility.getDeletedOrMentionedText(lastMessage));
            else
                MarkdownImpl.getInstance(context).setMarkdown(tv_last_message, StringUtility.getDeletedOrMentionedText(lastMessage));

            return;
        }

        if (lastMessage.getAttachments().isEmpty()){
            tv_last_message.setText("");
            return;
        }

        Attachment attachment = lastMessage.getAttachments().get(0);
        iv_attachment_type.setVisibility(View.VISIBLE);

        String lastMessageText;
        @IdRes int attachmentType;

        switch (attachment.getType()) {
            case ModelType.attach_image:
                lastMessageText = context.getResources().getString(R.string.stream_last_message_attachment_photo);
                attachmentType = R.drawable.stream_ic_image;
                break;
            case ModelType.attach_file:
                if (attachment.getMime_type() != null && attachment.getMime_type().contains("video")){
                    lastMessageText = context.getResources().getString(R.string.stream_last_message_attachment_video);
                    attachmentType = R.drawable.stream_ic_video;
                }else{
                    lastMessageText = !TextUtils.isEmpty(attachment.getTitle()) ? attachment.getTitle() : attachment.getFallback();
                    attachmentType = R.drawable.stream_ic_file;
                }
                break;
            case ModelType.attach_giphy:
                lastMessageText = context.getResources().getString(R.string.stream_last_message_attachment_giphy);
                attachmentType = R.drawable.stream_ic_gif;
                break;
            default:
                lastMessageText = !TextUtils.isEmpty(attachment.getTitle()) ? attachment.getTitle() : attachment.getFallback();
                attachmentType = R.drawable.stream_ic_file;
                break;
        }

        tv_last_message.setText(lastMessageText);
        iv_attachment_type.setImageDrawable(context.getDrawable(attachmentType));
    }

    private void configLastMessageDate(ChannelState channelState){
        Message lastMessage = channelState.getLastMessage();
        if (lastMessage == null) {
            tv_date.setText("");
            return;
        }

        if (lastMessage.isToday())
            tv_date.setText(lastMessage.getTime());
        else
            tv_date.setText(dateFormat.format(lastMessage.getCreatedAt()));
    }

    private void configReadState(ChannelState channelState){
        List<ChannelUserRead> lastMessageReads = channelState.getLastMessageReads();
        read_state.setReads(lastMessageReads, true, style);
    }

    private void configClickListeners(ChannelState channelState){
        Channel channel = channelState.getChannel();
        click_area.setOnClickListener(view -> {
            if (this.channelClickListener != null)
                this.channelClickListener.onClick(channel);
        });

        click_area.setOnLongClickListener(view -> {
            if (this.channelLongClickListener != null)
                this.channelLongClickListener.onClick(channel);

            return true;
        });
    }

    private void applyStyle(ChannelState channelState){
        tv_name.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.channelTitleText.size);
        tv_last_message.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.lastMessage.size);
        tv_date.setTextSize(TypedValue.COMPLEX_UNIT_PX, style.lastMessageDateText.size);

        Message lastMessage = channelState.getLastMessage();

        boolean outgoing = (lastMessage != null && lastMessage.getUserId().equals(StreamChat.getUsersRepository().getCurrentId()));
        if (channelState.readLastMessage() || outgoing)
            applyReadStyle();
        else
            applyUnreadStyle();
    }

    private void applyReadStyle() {
        // channel name
        style.channelTitleText.apply(tv_name);
        // last messsage
        style.lastMessage.apply(tv_last_message);
        style.lastMessageDateText.apply(tv_date);
        // last Message Attachment Type
        if (iv_attachment_type.getDrawable() != null)
            DrawableCompat.setTint(iv_attachment_type.getDrawable(), style.lastMessage.color);
    }

    private void applyUnreadStyle() {
        // channel name
        style.channelTitleUnreadText.apply(tv_name);
        // last message
        style.lastMessageUnread.apply(tv_last_message);
        style.lastMessageDateUnreadText.apply(tv_date);
        // last Message Attachment Type
        if (iv_attachment_type.getDrawable() != null)
            DrawableCompat.setTint(iv_attachment_type.getDrawable(), style.lastMessageUnread.color);
    }

}
