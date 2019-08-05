package com.getstream.sdk.chat.view.activity;

import androidx.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import androidx.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.getstream.sdk.chat.R;
import com.getstream.sdk.chat.adapter.MessageListItemAdapter;
import com.getstream.sdk.chat.databinding.ActivityChatBinding;
import com.getstream.sdk.chat.databinding.ViewThreadBinding;
import com.getstream.sdk.chat.function.AttachmentFunction;
import com.getstream.sdk.chat.function.EventFunction;
import com.getstream.sdk.chat.function.MessageFunction;
import com.getstream.sdk.chat.function.ReactionFunction;
import com.getstream.sdk.chat.function.SendFileFunction;
import com.getstream.sdk.chat.interfaces.MessageSendListener;
import com.getstream.sdk.chat.interfaces.WSResponseHandler;
import com.getstream.sdk.chat.model.Attachment;
import com.getstream.sdk.chat.model.ModelType;
import com.getstream.sdk.chat.rest.User;
import com.getstream.sdk.chat.model.Channel;
import com.getstream.sdk.chat.model.Event;
import com.getstream.sdk.chat.rest.Message;
import com.getstream.sdk.chat.model.MessageTagModel;
import com.getstream.sdk.chat.rest.request.ChannelDetailRequest;
import com.getstream.sdk.chat.rest.request.MarkReadRequest;
import com.getstream.sdk.chat.rest.request.PaginationRequest;
import com.getstream.sdk.chat.rest.request.SendActionRequest;
import com.getstream.sdk.chat.rest.response.ChannelResponse;
import com.getstream.sdk.chat.rest.response.EventResponse;
import com.getstream.sdk.chat.rest.response.GetRepliesResponse;
import com.getstream.sdk.chat.rest.response.MessageResponse;
import com.getstream.sdk.chat.utils.Constant;
import com.getstream.sdk.chat.utils.Global;
import com.getstream.sdk.chat.utils.PermissionChecker;
import com.getstream.sdk.chat.utils.StringUtility;
import com.getstream.sdk.chat.utils.Utils;
import com.getstream.sdk.chat.model.SelectAttachmentModel;
import com.getstream.sdk.chat.view.MessageInputView;
import com.getstream.sdk.chat.viewmodel.ChatActivityViewModel;
import com.getstream.sdk.chat.viewmodel.ChatActivityViewModelFactory;
import com.google.gson.Gson;

import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okio.ByteString;

/**
 * An Activity of a channel.
 */
public class ChatActivity extends AppCompatActivity implements WSResponseHandler {

    final String TAG = ChatActivity.class.getSimpleName();
    // ViewModel & Binding
    private ChatActivityViewModel mViewModel;
    private ActivityChatBinding binding;
    private ViewThreadBinding threadBinding;
    // Arguments for Channel
    private ChannelResponse channelResponse;
    private Channel channel;
    private List<Message> channelMessages, threadMessages;
    // Adapter & LayoutManager
    private MessageListItemAdapter mChannelMessageAdapter, mThreadAdapter;
    private RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
    private RecyclerView.LayoutManager mLayoutManager_thread = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
    private RecyclerView.LayoutManager mLayoutManager_thread_header = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
    // Functions
    private MessageFunction messageFunction;
    private SendFileFunction sendFileFunction;
    // Customization MessageItemView
    private int messageItemLayoutId;
    private String messageItemViewHolderName;
    // Misc
    private boolean singleConversation;
    private boolean isShowLastMessage;
    private int scrollPosition = 0;
    private static int fVPosition, lVPosition;
    private boolean noHistory, noHistoryThread;

    // region LifeCycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();

        if (!singleConversation) {
            setDeliverLastMessage();
            configUIs();
        } else {
            if (TextUtils.isEmpty(Global.streamChat.getClientID())) {
                binding.setShowMainProgressbar(true);
            } else {
                getChannel(Global.streamChat.getChannel());
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (Global.eventFunction == null) Global.eventFunction = new EventFunction();
        Global.eventFunction.setChannel(this.channel);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Constant.BC_RECONNECT_CHANNEL);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        registerReceiver(receiver, filter);
    }

    @Override
    public void onStop() {
        super.onStop();
        Global.streamChat.setChannel(null);
        Global.webSocketService.removeWSResponseHandler(this);
        Global.eventFunction.setChannel(null);

        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Receiver not registered")) {
                Log.w(TAG, "Tried to unregister the reciver when it's not registered");
            } else {
                throw e;
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == Constant.CAPTURE_IMAGE_REQUEST_CODE) {
                try {
                    Object object = data.getExtras().get("data");
                    if (object.getClass().equals(Bitmap.class)) {
                        Bitmap bitmap = (Bitmap) object;
                        Uri uri = Utils.getUriFromBitmap(this, bitmap);
                        sendFileFunction.progressCapturedMedia(this, uri, true);
                    }
                } catch (Exception e) {
                    Uri uri = data.getData();
                    if (uri == null) return;
                    sendFileFunction.progressCapturedMedia(this, uri, false);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Close if File Attach View is opened.
//       TODO: implement me
//        if (binding.clAddFile.getVisibility() == View.VISIBLE) {
//            sendFileFunction.onClickAttachmentViewClose(null);
//            return;
//        }
        // Close if Selecting Photo View is opened.
//       // TODO
//        if (binding.clSelectPhoto.getVisibility() == View.VISIBLE) {
//            sendFileFunction.onClickSelectMediaViewClose(null);
//            return;
//        }
        // Close if Thread View is opened.
        if (isThreadMode()) {
            onClickCloseThread(null);
            return;
        }
        // Cancel if editing message.
        if (binding.messageInput.IsEditing()) {
            cancelEditMessage();
            return;
        }
        super.onBackPressed();
    }
    // endregion

    // region Init
    private void init() {
        // set binding
        binding = DataBindingUtil.setContentView(this, R.layout.activity_chat);
        // set WS handler
        Global.webSocketService.setWSResponseHandler(this, getApplicationContext());

        singleConversation = (Global.streamChat.getChannel() != null);
        // Permission Check
        PermissionChecker.permissionCheck(this, null);
        try {
            Fresco.initialize(getApplicationContext());
        } catch (Exception e) {
        }

        String channelId = null;
        Bundle b = getIntent().getExtras();
        if (b != null)
            channelId = (String) b.get(Constant.TAG_CHANNEL_RESPONSE_ID);

        threadBinding = binding.clThread;
        if (channelResponse == null && channelId != null)
            channelResponse = Global.getChannelResponseById(channelId);
        else {
            return;
        }
        initReconnection();
    }

    private void initReconnection() {
        ChatActivityViewModelFactory factory = new ChatActivityViewModelFactory(channelResponse);
        mViewModel = ViewModelProviders.of(this, factory).get(ChatActivityViewModel.class);
        binding.setViewModel(mViewModel);
//        threadBinding.setViewModel(mViewModel);
        channel = channelResponse.getChannel();
        channelMessages = channelResponse.getMessages();
        checkEphemeralMessages();
        messageFunction = new MessageFunction(this.channelResponse);
//        sendFileFunction = new SendFileFunction(this, binding, channelResponse);
        checkReadMark();
        noHistory = channelMessages.size() < Constant.CHANNEL_MESSAGE_LIMIT;
        noHistoryThread = false;
        Global.eventFunction.setChannel(channel);
    }

    private void checkEphemeralMessages() {
        List<Message> ephemeralMainMessages = Global.getEphemeralMessages(channel.getId(), null);
        if (ephemeralMainMessages != null && !ephemeralMainMessages.isEmpty()) {
            for (int i = 0; i < ephemeralMainMessages.size(); i++) {
                Message message = ephemeralMainMessages.get(i);
                if (channelMessages.contains(message)) continue;
                channelMessages.add(message);
            }
        }
    }

    private boolean lockRVScrollListener = false;

    private void getChannel(Channel channel_) {

        binding.setShowMainProgressbar(true);
        channel_.setType(ModelType.channel_messaging);
        Map<String, Object> messages = new HashMap<>();
        messages.put("limit", Constant.DEFAULT_LIMIT);

        // Additional Field
        Map<String, Object> data = new HashMap<>();
        if (channel_.getExtraData() != null) {
            Set<String> keys = channel_.getExtraData().keySet();
            for (String key : keys) {
                Object value = channel_.getExtraData().get(key);
                if (value != null)
                    data.put(key, value);
            }
        }
        Log.d(TAG, "Channel Connecting...");

        ChannelDetailRequest request = new ChannelDetailRequest(messages, data, true, true);

        Global.mRestController.channelDetailWithID(channel_.getId(), request, (ChannelResponse response) -> {
            Log.d(TAG, "Channel Connected");
            binding.setShowMainProgressbar(false);
            if (!response.getMessages().isEmpty())
                Global.setStartDay(response.getMessages(), null);
            Global.addChannelResponse(response);
            channelResponse = response;
            initReconnection();
            setDeliverLastMessage();
            configUIs();

        }, (String errMsg, int errCode) -> {
            binding.setShowMainProgressbar(false);
            Log.d(TAG, "Failed Connect Channel : " + errMsg);
        });
    }

    private List<Message> messages() {
        return isThreadMode() ? threadMessages : channelMessages;
    }

    private RecyclerView recyclerView() {
        return isThreadMode() ? threadBinding.rvThread : binding.rvMessage;
    }

    private boolean isNoHistory() {
        return isThreadMode() ? noHistoryThread : noHistory;
    }

    // endregion

    // region Config UIs
    private void configUIs() {
        configActionBar(); // Hides Action Bar
        configHeaderView(); // Header View
        configCustomMessageItemView(); // custom MessageItemView
        configMessageInputView(); // Message Input box
        configMessageRecyclerView(); // Message RecyclerView
        // Bottom View
//        binding.tvNewMessage.setVisibility(View.GONE);
//        binding.tvNewMessage.setOnClickListener((View v) -> {
//            scrollPosition = 0;
//            recyclerView().scrollToPosition(messages().size());
//            binding.tvNewMessage.setVisibility(View.GONE);
//        });
        // File Attachment
    }

    private void configActionBar() {
        try {
            getSupportActionBar().hide();
        } catch (Exception e) {
        }
    }

    private void configCustomMessageItemView() {
        messageItemLayoutId = Global.component.message.getMessageItemLayoutId();
        messageItemViewHolderName = Global.component.message.getMessageItemViewHolderName();
    }

    private void configHeaderView() {
        // Avatar
        if (!TextUtils.isEmpty(channel.getName())) {
            binding.tvChannelInitial.setText(channel.getInitials());
            Utils.circleImageLoad(binding.ivHeaderAvatar, channel.getImage());
            if (StringUtility.isValidImageUrl(channel.getImage())) {
                binding.ivHeaderAvatar.setVisibility(View.VISIBLE);
                binding.tvChannelInitial.setVisibility(View.INVISIBLE);
            } else {
                binding.ivHeaderAvatar.setVisibility(View.INVISIBLE);
                binding.tvChannelInitial.setVisibility(View.VISIBLE);
            }
        } else {
            User opponent = Global.getOpponentUser(channelResponse);
            if (opponent != null) {
                binding.tvChannelInitial.setText(opponent.getUserInitials());
                Utils.circleImageLoad(binding.ivHeaderAvatar, opponent.getImage());
                binding.tvChannelInitial.setVisibility(View.VISIBLE);
                binding.ivHeaderAvatar.setVisibility(View.VISIBLE);
            } else {
                binding.tvChannelInitial.setVisibility(View.VISIBLE);
                binding.ivHeaderAvatar.setVisibility(View.INVISIBLE);
            }
        }
        // Channel name
        String channelName = "";

        if (!TextUtils.isEmpty(channelResponse.getChannel().getName())) {
            channelName = channelResponse.getChannel().getName();
        } else {
            User opponent = Global.getOpponentUser(channelResponse);
            if (opponent != null) {
                channelName = opponent.getName();
            }
        }

        binding.tvChannelName.setText(channelName);

        // Last Active
        Message lastMessage = channelResponse.getOpponentLastMessage();
        configHeaderLastActive(lastMessage);
        // Online Mark
        try {
            if (Global.getOpponentUser(channelResponse) == null)
                binding.ivActiveMark.setVisibility(View.GONE);
            else {
                if (Global.getOpponentUser(channelResponse).getOnline()) {
                    binding.ivActiveMark.setVisibility(View.VISIBLE);
                } else {
                    binding.ivActiveMark.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
            binding.ivActiveMark.setVisibility(View.GONE);
        }

        binding.tvBack.setVisibility(singleConversation ? View.INVISIBLE : View.VISIBLE);
        binding.tvBack.setOnClickListener((View v) -> finish());
    }

    private void configMessageInputView() {

        binding.setShowLoadMoreProgressbar(false);
        binding.setNoConnection(Global.noConnection);
        binding.messageInput.setOnFocusChangeListener((View view, boolean hasFocus) -> {
            lockRVScrollListener = hasFocus;
        });

        KeyboardVisibilityEvent.setEventListener(
                this, (boolean isOpen) -> {
                    if (!isOpen) {
                        binding.messageInput.clearFocus();
                    } else {
                        lockRVScrollListener = true;
                        new Handler().postDelayed(() -> {
                            lockRVScrollListener = false;
                        }, 500);
                    }
                    if (lVPosition > messages().size() - 2)
                        recyclerView().scrollToPosition(lVPosition);

                });

        ChatActivity a = this;

        binding.messageInput.setOnSendMessageListener(new MessageInputView.SendMessageListener() {
            @Override
            public void onSendMessage(String input) {
                // TODO send the message
                Log.i(TAG, "actually sending a message, epic");
                a.sendMessage(input);
            }
        });

        binding.messageInput.setTypingListener(new MessageInputView.TypingListener() {
            @Override
            public void onKeystroke() {
                // TODO: forward to the client
                Log.i(TAG, "i entered a letter, awesome");
            }

            @Override
            public void onStopTyping() {
                // TODO:  forward to the client
                Log.i(TAG, "stop typing");
            }
        });
    }

    private void configMessageRecyclerView() {
        mLayoutManager.scrollToPosition(channelMessages.size());
        binding.rvMessage.setLayoutManager(mLayoutManager);
        setScrollDownHideKeyboard(binding.rvMessage);
        setChannelMessageRecyclerViewAdapder();
    }

    private void configHeaderLastActive(@Nullable Message message) {
        if (message == null || message.getUser().isMe()) {
            return;
        }
        binding.tvActive.setVisibility(View.GONE);
        String lastActive = null;
        if (message != null) {
            if (!TextUtils.isEmpty(Global.differentTime(message.getCreated_at()))) {
                lastActive = Global.differentTime(message.getCreated_at());
            }
        }

        if (TextUtils.isEmpty(lastActive)) {
            binding.tvActive.setVisibility(View.GONE);
        } else {
            binding.tvActive.setVisibility(View.VISIBLE);
            binding.tvActive.setText(lastActive);
        }
    }



    private void setChannelMessageRecyclerViewAdapder() {
        mChannelMessageAdapter = new MessageListItemAdapter(this, this.channelResponse, channelMessages,
                false, messageItemViewHolderName, messageItemLayoutId,
                (View v) -> {
                    Object object = v.getTag();
                    messageItemClickListener(object);
                }, (View v) -> {
            try {
                messageItemLongClickListener(v.getTag());
            } catch (Exception e) {
            }
            return true;
        });
        binding.rvMessage.setAdapter(mChannelMessageAdapter);
        mViewModel.getChannelMessages().observe(this, (@Nullable List<Message> users) -> {
            if (scrollPosition == -1) return;

            mChannelMessageAdapter.notifyDataSetChanged();
            if (scrollPosition > 0) {
                binding.rvMessage.scrollToPosition(scrollPosition);
                scrollPosition = 0;
                return;
            }
            binding.rvMessage.scrollToPosition(channelMessages.size());
        });
        mViewModel.setChannelMessages(channelMessages);
    }

    private void setScrollDownHideKeyboard(RecyclerView recyclerView) {
        fVPosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
        recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (lockRVScrollListener) return;
                int currentFirstVisible = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                int currentLastVisible = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
                if (!isThreadMode()) {
                    if (currentLastVisible >= messages().size() - 1) isShowLastMessage = true;
                    else isShowLastMessage = false;
                }

                if (currentFirstVisible < fVPosition) {
                    Utils.hideSoftKeyboard(ChatActivity.this);
                    binding.messageInput.clearFocus();
                    if (currentFirstVisible == 0 && !isNoHistory()) loadMore();
//                    if (currentLastVisible >= messages().size() - 1)
//                        binding.tvNewMessage.setVisibility(View.GONE);
                }

                new Handler().postDelayed(() -> {
                    lVPosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
                }, 500);
                fVPosition = currentFirstVisible;
            }
        });
    }

    // endregion

    // region Send Message
    private Message ephemeralMessage = null;

    /**
     * Send Message - Send a message to this channel
     */
    public void sendMessage(String messageText) {
//        if (Global.noConnection) {
//            Utils.showMessage(this, Constant.NO_INTERNET);
//            return;
//        }
        if (binding.messageInput.IsEditing()) {
            updateMessage(binding.messageInput.getMessageText());
        } else {
            sendNewMessage(messageText, binding.messageInput.GetAttachments(), null);
        }

    }

    public void sendNewMessage(String text, List<Attachment> attachments, String resendMessageId) {
        if (Global.noConnection) {
            sendOfflineMessage();
            return;
        }
        if (resendMessageId == null) {
            ephemeralMessage = createEphemeralMessage(false);
            handleAction(ephemeralMessage);
        }
        binding.messageInput.setEnabled(false);
        messageFunction.sendMessage(text,
                attachments,
                isThreadMode() ? thread_parentMessage.getId() : null,
                new MessageSendListener() {
                    @Override
                    public void onSuccess(MessageResponse response) {
                        binding.messageInput.setEnabled(true);
                        progressSendMessage(response.getMessage(), resendMessageId);
                    }

                    @Override
                    public void onFailed(String errMsg, int errCode) {
                        binding.messageInput.setEnabled(true);
                        Utils.showMessage(ChatActivity.this, errMsg);
                    }
                });
    }

    public void updateMessage(String messageText) {
        if (Global.noConnection) {
            Utils.showMessage(this, "No internet connection!");
            return;
        }
        binding.messageInput.setEnabled(false);
        messageFunction.updateMessage(messageText,
                (Message) binding.messageInput.GetEditMessage(),
                sendFileFunction.getSelectedAttachments(),
                new MessageSendListener() {
                    @Override
                    public void onSuccess(MessageResponse response) {
                        response.getMessage().setDelivered(true);
                        binding.messageInput.CancelEditMessage();
                        binding.messageInput.setEnabled(true);
                    }

                    @Override
                    public void onFailed(String errMsg, int errCode) {
                        binding.messageInput.setEnabled(true);
                        Utils.showMessage(ChatActivity.this, errMsg);
                    }
                });
    }

    public void resendMessage(Message message) {
        if (Global.noConnection) {
            Utils.showMessage(this, Constant.NO_INTERNET);
            return;
        }
        handleAction(message);
        sendNewMessage(message.getText(), null, message.getId());
    }

    public void sendGiphy(String type, Message message) {
        Map<String, String> map = new HashMap<>();
        if (type.equals(Constant.TAG_ACTION_SEND))
            map.put("image_action", ModelType.action_send);
        else
            map.put("image_action", ModelType.action_shuffle);

        SendActionRequest request = new SendActionRequest(channel.getId(), message.getId(), ModelType.channel_messaging, map);
        Global.mRestController.sendAction(message.getId(), request, (MessageResponse response) -> {
            handleAction(message);
            response.getMessage().setDelivered(true);
            handleAction(response.getMessage());
        }, (String errMsg, int errCode) -> {
            Log.d(TAG, errMsg);
        });
    }

    public void progressSendMessage(Message message, String resendMessageId) {
        if (resendMessageId != null) {
            Global.removeEphemeralMessage(channel.getId(), resendMessageId);
        } else {
            if (Global.isCommandMessage(message) ||
                    message.getType().equals(ModelType.message_error)) {
                channelMessages.remove(ephemeralMessage);
                message.setDelivered(true);
            } else {
                ephemeralMessage.setId(message.getId());
            }

            handleAction(message);
        }
    }

    private void setDeliverLastMessage() {
        if (messages() == null || messages().isEmpty()) return;
        if (!messages().get(messages().size() - 1).isIncoming())
            messages().get(messages().size() - 1).setDelivered(true);
    }


    private void sendOfflineMessage() {
        handleAction(createEphemeralMessage(true));
    }

    private Message createEphemeralMessage(boolean isOffle) {
        Message message = new Message();
        message.setId(Global.convertDateToString(new Date()));
        message.setText(binding.messageInput.getMessageText());
        message.setType(isOffle ? ModelType.message_error : ModelType.message_ephemeral);
        message.setCreated_at(Global.convertDateToString(new Date()));
        Global.setStartDay(Arrays.asList(message), getLastMessage());
        message.setUser(Global.streamChat.getUser());
        if (isThreadMode())
            message.setParent_id(thread_parentMessage.getId());
        if (isOffle)
            Global.setEphemeralMessage(channel.getId(), message);
        return message;
    }

    private Message getLastMessage() {
        return messages().isEmpty() ? null : messages().get(messages().size() - 1);
    }

    // Edit
    private void editMessage(Message message) {
        binding.messageInput.EditMessage(message);
        binding.messageInput.requestInputFocus();
        if (!TextUtils.isEmpty(message.getText())) {
            // TODO: figure out what this does...
//            binding.etMessage.setText(message.getText());
//            binding.etMessage.setSelection(binding.etMessage.getText().length());
        }
        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            for (Attachment attachment : message.getAttachments())
                attachment.config.setUploaded(true);

            if (message.getAttachments().get(0).getType().equals(ModelType.attach_file)) {
                String fileType = message.getAttachments().get(0).getMime_type();
                if (fileType.equals(ModelType.attach_mime_mov) ||
                        fileType.equals(ModelType.attach_mime_mp4)) {
                    sendFileFunction.onClickSelectMediaViewOpen(null, message.getAttachments());
                } else {
                    sendFileFunction.onClickSelectFileViewOpen(null, message.getAttachments());
                }
            } else {
                sendFileFunction.onClickSelectMediaViewOpen(null, message.getAttachments());
            }
        }
    }

    private void cancelEditMessage() {
        binding.messageInput.CancelEditMessage();

    }

    // endregion

    // region Message Item Touch Action
    private void messageItemClickListener(Object object) {
        if (isCallingThread) return;
        if (object.getClass().equals(SelectAttachmentModel.class)) {
            new AttachmentFunction().progressAttachment((SelectAttachmentModel) object, this);
            return;
        }

        if (object.getClass().equals(MessageTagModel.class)) {
            MessageTagModel tag = (MessageTagModel) object;
            Message message = messages().get(tag.position);
            switch (tag.type) {
                case Constant.TAG_MOREACTION_REPLY:
                    configThread(channelMessages.get(tag.position));
                    break;
                case Constant.TAG_ACTION_SEND:
                case Constant.TAG_ACTION_SHUFFLE:
                    sendGiphy(tag.type, message);
                    break;
                case Constant.TAG_ACTION_CANCEL:
                    handleAction(messages().get(tag.position));
                    break;
                case Constant.TAG_MESSAGE_REACTION:
                    int firstListItemPosition = ((LinearLayoutManager) recyclerView().getLayoutManager()).findFirstVisibleItemPosition();
                    final int lastListItemPosition = firstListItemPosition + recyclerView().getChildCount() - 1;
                    int childIndex;
                    if (tag.position < firstListItemPosition || tag.position > lastListItemPosition) {
                        childIndex = tag.position;
                    } else {
                        childIndex = tag.position - firstListItemPosition;
                    }
                    int originY = recyclerView().getChildAt(childIndex).getBottom();
                    ReactionFunction.showReactionDialog(this, message, originY);
                    break;
                case Constant.TAG_MESSAGE_RESEND:
                    resendMessage(message);
                    break;
                case Constant.TAG_MESSAGE_INVALID_COMMAND:
                    handleAction(message);
                    //binding.etMessage.setText("/");
                    break;
                case Constant.TAG_MESSAGE_CHECK_DELIVERED:
                    showAlertReadUsers(message);
                    break;
                default:
                    break;
            }
        }
    }

    private void messageItemLongClickListener(Object object) {
        final int position = Integer.parseInt(object.toString());
        final Message message = messages().get(position);

        ReactionFunction.showMoreActionDialog(this, message, (View v) -> {
            String type = (String) v.getTag();
            switch (type) {
                case Constant.TAG_MOREACTION_EDIT:
                    editMessage(message);
                    break;
                case Constant.TAG_MOREACTION_DELETE:
                    messageFunction.deleteMessage(message,
                            new MessageSendListener() {
                                @Override
                                public void onSuccess(MessageResponse response) {
                                    Utils.showMessage(ChatActivity.this, "Deleted Successfully");
                                }

                                @Override
                                public void onFailed(String errMsg, int errCode) {
                                    Log.d(TAG, "Failed DeleteMessage : " + errMsg);
                                }
                            });
                    break;
                case Constant.TAG_MOREACTION_REPLY:
                    if (!isThreadMode())
                        configThread(message);
                    break;
                default:
                    break;
            }
        });
    }

    private void showAlertReadUsers(Message message) {
        List<User> readUsers = Global.getReadUsers(channelResponse, message);
        if (readUsers == null) return;
        String msg = "";
        if (readUsers.size() > 0) {
            if (readUsers.size() == 1) msg = readUsers.get(0).getName();
            else {
                for (int i = 0; i < readUsers.size(); i++) {
                    User user = readUsers.get(i);
                    if (i == readUsers.size() - 2) msg += user.getName() + " and ";
                    else if (i == readUsers.size() - 1) msg += user.getName();
                    else msg += user.getName() + ", ";
                }
            }
        } else {
            if (message.isDelivered()) {
                msg = "Delivered";
            } else {
                msg = "sending...";
            }
        }
        Utils.showMessage(this, msg);
    }






    // refresh Current Typing users in this channel
    private Handler clearTyingUserHandler = new Handler();
    private Runnable runnableTypingClear = new Runnable() {
        @Override
        public void run() {
            try {
                Global.typingUsers = new ArrayList<>();
                try {
                    mChannelMessageAdapter.notifyItemChanged(channelMessages.size());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } finally {
//                eventFunction.sendEvent(Event.typing_stop);
                clearTyingUserHandler.postDelayed(runnableTypingClear, Constant.TYPYING_CLEAN_INTERVAL);
            }
        }
    };


    // region Action
    private void handleAction(Message message) {
        switch (message.getType()) {
            case ModelType.message_ephemeral:
            case ModelType.message_error:
                Event event = new Event();
                event.setType(Event.message_new);
                event.setMessage(message);
                handleEvent(event);
                break;
            default:
                break;
        }
    }
    // endregion

    // region Thread
    private Message thread_parentMessage = null;
    private boolean isCallingThread = false;

    private void configThread(@NonNull Message message) {

        mViewModel.setReplyCount(message.getReplyCount());
        cleanEditView();

        thread_parentMessage = message;
        threadMessages = new ArrayList<>();

        threadBinding.rvHeader.setLayoutManager(mLayoutManager_thread_header);
        MessageListItemAdapter mThreadHeaderAdapter = new MessageListItemAdapter(this, this.channelResponse, Arrays.asList(thread_parentMessage), isThreadMode(), messageItemViewHolderName, messageItemLayoutId, null, null);
        threadBinding.rvHeader.setAdapter(mThreadHeaderAdapter);

        if (message.getReplyCount() == 0) {
            setThreadAdapter();
            threadBinding.setShowThread(true);
        } else {
            binding.setShowMainProgressbar(true);

            if (isCallingThread) return;
            isCallingThread = true;
            Global.mRestController.getReplies(message.getId(), String.valueOf(Constant.THREAD_MESSAGE_LIMIT), null, (GetRepliesResponse response) -> {
                threadMessages = response.getMessages();

                List<Message> ephemeralThreadMessages = Global.getEphemeralMessages(channel.getId(), thread_parentMessage.getId());
                if (ephemeralThreadMessages != null && !ephemeralThreadMessages.isEmpty()) {
                    for (int i = 0; i < ephemeralThreadMessages.size(); i++) {
                        threadMessages.add(ephemeralThreadMessages.get(i));
                    }
                }

                Global.setStartDay(threadMessages, null);
                setThreadAdapter();
                threadBinding.setShowThread(true);
                binding.setShowMainProgressbar(false);
                if (threadMessages.size() < Constant.THREAD_MESSAGE_LIMIT) noHistoryThread = true;
                isCallingThread = false;
            }, (String errMsg, int errCode) -> {
                Utils.showMessage(ChatActivity.this, errMsg);
                thread_parentMessage = null;
                binding.setShowMainProgressbar(false);
                isCallingThread = false;
            });
        }
        // Clean RecyclerView
    }

    /**
     * Hide thread view
     */
    public void onClickCloseThread(View v) {
        threadBinding.setShowThread(false);
        cleanEditView();
        setScrollDownHideKeyboard(binding.rvMessage);
        isCallingThread = false;
    }

    private void setThreadAdapter() {
        if (threadMessages.size() > 0)
            mLayoutManager_thread.scrollToPosition(threadMessages.size() - 1);

        threadBinding.rvThread.setLayoutManager(mLayoutManager_thread);
        mThreadAdapter = new MessageListItemAdapter(this, this.channelResponse, threadMessages, isThreadMode(), messageItemViewHolderName, messageItemLayoutId, (View v) -> {
            Object object = v.getTag();
            messageItemClickListener(object);
        }, (View v) -> {
            try {
                messageItemLongClickListener(v.getTag());
            } catch (Exception e) {
            }
            return true;
        });
        threadBinding.rvThread.setAdapter(mThreadAdapter);
        setScrollDownHideKeyboard(threadBinding.rvThread);
    }

    private boolean isThreadMode() {
        if (thread_parentMessage == null) return false;
        return true;
    }

    private void cleanEditView() {
        binding.messageInput.CancelEditMessage();
        thread_parentMessage = null;
        threadMessages = null;
        lVPosition = 0;
        fVPosition = 0;
        noHistoryThread = false;
        binding.rvMessage.clearOnScrollListeners();
        threadBinding.rvThread.clearOnScrollListeners();
        Utils.hideSoftKeyboard(this);
    }
    // endregion

    // region Listener

    // region WebSocket Listener

    /**
     * Handle server response
     *
     * @param event Server response
     */
    @Override
    public void handleEventWSResponse(Event event) {
        if (Global.eventFunction == null)
            Global.eventFunction = new EventFunction();
        Global.eventFunction.handleReceiveEvent(event);

        String channelId = null;
        try {
            String[] array = event.getCid().split(":");
            channelId = array[1];
        } catch (Exception e) {
        }
        if (channelId == null || channel == null) return;

        if (channel.getId().equals(channelId))
            handleEvent(event);

        Log.d(TAG, "New Event: " + new Gson().toJson(event));
    }

    @Override
    public void handleByteStringWSResponse(ByteString byteString) {

    }

    @Override
    public void handleConnection() {
        Log.d(TAG, "Connected Websocket!");
        if (singleConversation && Global.streamChat.getChannel() != null)
            getChannel(Global.streamChat.getChannel());
    }

    /**
     * Handle server response failures.
     *
     * @param errMsg  Error message
     * @param errCode Error code
     */
    @Override
    public void onFailed(String errMsg, int errCode) {
        binding.setNoConnection(true);
        binding.setShowMainProgressbar(false);
    }

    // Event Listener

    /**
     * Handle Event
     *
     * @param event Event for Server response
     */
//    @Override
    public void handleEvent(final Event event) {
        runOnUiThread(() -> {
            switch (event.getType()) {
                case Event.health_check:
                    break;
                case Event.message_new:
                case Event.message_updated:
                case Event.message_deleted:
                    messageEvent(event);
                    break;
                case Event.message_read:
                    messageReadEvent(event);
                    break;
                case Event.typing_start:
                case Event.typing_stop:
                    footerEvent(event);
                    break;
                case Event.user_updated:
                    break;
                case Event.user_presence_changed:
                    break;
                case Event.user_watching_start:
                    break;
                case Event.user_watching_stop:
                    break;
                case Event.reaction_new:
                case Event.reaction_deleted:
                    reactionEvent(event);
                    break;
                case Event.channel_updated:
                case Event.channel_deleted:
                    Global.eventFunction.handleChannelEvent(channelResponse, event);
                    if (event.getType().equals(Event.channel_deleted)) {
                        Utils.showMessage(this, "Channel Owner just removed this channel!");
                        finish();
                    }
                    break;
                default:
                    break;
            }
        });
    }

    private void messageEvent(Event event) {
        Message message = event.getMessage();
        if (message == null) return;

        switch (event.getType()) {
            case Event.message_new:
                newMessageEvent(message);
                break;
            case Event.message_updated:
                if (isThreadMode() && message.getId().equals(thread_parentMessage.getId()))
                    mViewModel.setReplyCount(message.getReplyCount());
            case Event.message_deleted:
                updateOrDeleteMessageEvent(event, message);
                break;
            default:
                break;
        }
    }

    private void newMessageEvent(Message message) {
        configHeaderLastActive(message);
        Global.setStartDay(Arrays.asList(message), getLastMessage());

        switch (message.getType()) {
            case ModelType.message_regular:
                if (!message.isIncoming())
                    message.setDelivered(true);

                messages().remove(ephemeralMessage);
                Global.eventFunction.newMessage(channelResponse, message);

                if (message.isIncoming() && !isShowLastMessage) {
                    scrollPosition = -1;
//                    binding.tvNewMessage.setVisibility(View.VISIBLE);
                } else {
                    scrollPosition = 0;
                }
                mViewModel.setChannelMessages(channelMessages);
                messageReadMark();
                break;
            case ModelType.message_ephemeral:
            case ModelType.message_error:
                boolean isContain = false;
                for (int i = messages().size() - 1; i >= 0; i--) {
                    Message message1 = messages().get(i);
                    if (message1.getId().equals(message.getId())) {
                        messages().remove(message1);
                        isContain = true;
                        break;
                    }
                }
                if (!isContain) messages().add(message);
                scrollPosition = 0;
                if (isThreadMode()) {
                    mThreadAdapter.notifyDataSetChanged();
                    threadBinding.rvThread.scrollToPosition(threadMessages.size() - 1);
                } else {
                    mViewModel.setChannelMessages(messages());
                }
                break;
            case ModelType.message_reply:
                if (isThreadMode() && message.getParent_id().equals(thread_parentMessage.getId())) {
                    messages().remove(ephemeralMessage);
                    threadMessages.add(message);
                    mThreadAdapter.notifyDataSetChanged();
                    threadBinding.rvThread.scrollToPosition(threadMessages.size() - 1);
                }
                break;
            case ModelType.message_system:
                break;
            default:
                break;
        }
    }

    private void updateOrDeleteMessageEvent(Event event, Message message) {
        if (!message.isIncoming())
            message.setDelivered(true);
        int changedIndex_ = 0;
        if (message.getType().equals(ModelType.message_reply)) {
            if (!isThreadMode()) return;
            for (int i = 0; i < threadMessages.size(); i++) {
                if (message.getId().equals(threadMessages.get(i).getId())) {
                    if (event.getType().equals(Event.message_deleted))
                        message.setText(Constant.MESSAGE_DELETED);
                    changedIndex_ = i;
                    threadMessages.set(i, message);
                    break;
                }
            }
            final int changedIndex = changedIndex_;
            mThreadAdapter.notifyItemChanged(changedIndex);
        } else {
            for (int i = 0; i < channelMessages.size(); i++) {
                if (message.getId().equals(channelMessages.get(i).getId())) {
                    if (event.getType().equals(Event.message_deleted))
                        message.setText(Constant.MESSAGE_DELETED);
                    changedIndex_ = i;
                    channelMessages.set(i, message);
                    break;
                }
            }
            final int changedIndex = changedIndex_;
            scrollPosition = -1;
            mViewModel.setChannelMessages(channelMessages);
            mChannelMessageAdapter.notifyItemChanged(changedIndex);
        }
    }

    private void reactionEvent(Event event) {
        Message message = event.getMessage();
        if (message == null) return;
        int changedIndex_ = 0;
        if (message.getType().equals(ModelType.message_regular)) {
            for (int i = 0; i < channelMessages.size(); i++) {
                if (message.getId().equals(channelMessages.get(i).getId())) {
                    changedIndex_ = i;
                    channelMessages.set(i, message);
                    break;
                }
            }
            final int changedIndex = changedIndex_;
            this.runOnUiThread(() -> {
                scrollPosition = -1;
                mViewModel.setChannelMessages(channelMessages);
                mChannelMessageAdapter.notifyItemChanged(changedIndex);
            });
        } else if (message.getType().equals(ModelType.message_reply)) {
            if (thread_parentMessage == null) return;
            if (!message.getParent_id().equals(thread_parentMessage.getId())) return;

            for (int i = 0; i < threadMessages.size(); i++) {
                if (message.getId().equals(threadMessages.get(i).getId())) {
                    changedIndex_ = i;
                    threadMessages.set(i, message);
                    break;
                }
            }
            final int changedIndex = changedIndex_;
            this.runOnUiThread(() -> {
                mThreadAdapter.notifyItemChanged(changedIndex);
            });
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(Constant.BC_RECONNECT_CHANNEL))
                return;

            Log.d(TAG, "Reconnection!");
            channelResponse = Global.getChannelResponseById(channel.getId());
            Global.setStartDay(channelResponse.getMessages(), null);
            initReconnection();
            // Check Ephemeral Messages
            checkEphemeralMessages();

            runOnUiThread(() -> {
                setDeliverLastMessage();
                configUIs();
                if (isThreadMode())
                    configThread(thread_parentMessage);
            });
        }
    };
    // endregion

    // region Footer Event

    private void footerEvent(Event event) {
        User user = event.getUser();
        if (user == null) return;
        if (user.getId().equals(Global.streamChat.getUser().getId())) return;

        switch (event.getType()) {
            case Event.typing_start:
                boolean isAdded = false; // If user already exits in typingUsers
                for (int i = 0; i < Global.typingUsers.size(); i++) {
                    User user1 = Global.typingUsers.get(i);
                    if (user1.getId().equals(user.getId())) {
                        isAdded = true;
                        break;
                    }
                }
                if (!isAdded)
                    Global.typingUsers.add(user);

                break;
            case Event.typing_stop:
                int index1 = -1; // If user already exits in typingUsers
                for (int i = 0; i < Global.typingUsers.size(); i++) {
                    User user1 = Global.typingUsers.get(i);
                    if (user1.getId().equals(user.getId())) {
                        index1 = i;
                        break;
                    }
                }
                if (index1 != -1)
                    Global.typingUsers.remove(index1);
                break;
            default:
                break;
        }
        mChannelMessageAdapter.notifyItemChanged(channelMessages.size());
    }

    private void messageReadEvent(Event event) {
        Global.eventFunction.readMessage(channelResponse, event);
        if (!channelResponse.getLastMessage().isIncoming()) {
            mChannelMessageAdapter.notifyItemChanged(channelMessages.size() - 1);
        }
    }
    // endregion

    // endregion

    // region Pagination

    private boolean isCalling;

    private void loadMore() {
        if (isNoHistory() || isCalling) return;
        Log.d(TAG, "Next pagination...");
        isCalling = true;
        if (!isThreadMode()) {
            binding.setShowLoadMoreProgressbar(true);
            PaginationRequest request = new PaginationRequest(Constant.DEFAULT_LIMIT, channelMessages.get(0).getId(), this.channel);
            Global.mRestController.pagination(channel.getId(), request, (ChannelResponse response) -> {

                binding.setShowLoadMoreProgressbar(false);
                List<Message> newMessages = new ArrayList<>(response.getMessages());
                if (newMessages.size() < Constant.DEFAULT_LIMIT) noHistory = true;

                // Set Date Time
                Global.setStartDay(newMessages, null);
                // Add new to current Message List
                for (int i = newMessages.size() - 1; i > -1; i--)
                    channelMessages.add(0, newMessages.get(i));

                scrollPosition = ((LinearLayoutManager) binding.rvMessage.getLayoutManager()).findLastCompletelyVisibleItemPosition() + response.getMessages().size();
                mViewModel.setChannelMessages(channelMessages);
                isCalling = false;

            }, (String errMsg, int errCode) -> {

                Utils.showMessage(ChatActivity.this, errMsg);
                isCalling = false;
                binding.setShowLoadMoreProgressbar(false);

            });
        } else {
            binding.setShowMainProgressbar(true);
            Global.mRestController.getReplies(thread_parentMessage.getId(),
                    String.valueOf(Constant.THREAD_MESSAGE_LIMIT),
                    threadMessages.get(0).getId(),
                    (GetRepliesResponse response) -> {

                        binding.setShowMainProgressbar(false);
                        List<Message> newMessages = new ArrayList<>(response.getMessages());
                        if (newMessages.size() < Constant.THREAD_MESSAGE_LIMIT)
                            noHistoryThread = true;

                        Global.setStartDay(newMessages, null);
                        // Add new to current Message List
                        for (int i = newMessages.size() - 1; i > -1; i--) {
                            threadMessages.add(0, newMessages.get(i));
                        }
                        int scrollPosition = ((LinearLayoutManager) recyclerView().getLayoutManager()).findLastCompletelyVisibleItemPosition() + response.getMessages().size();
                        mThreadAdapter.notifyDataSetChanged();
                        recyclerView().scrollToPosition(scrollPosition);
                        isCalling = false;

                    }, (String errMsg, int errCode) -> {

                        Utils.showMessage(ChatActivity.this, errMsg);
                        isCalling = false;
                        binding.setShowMainProgressbar(false);

                    });
        }
    }

    // endregion

    // region Check Message Read

    private void checkReadMark() {
        if (channelResponse.getLastMessage() == null) return;
        if (!Global.readMessage(channelResponse.getReadDateOfChannelLastMessage(true),
                channelResponse.getLastMessage().getCreated_at())) {
            messageReadMark();
        }
    }

    private void messageReadMark() {
        MarkReadRequest request = new MarkReadRequest(channelMessages.get(channelMessages.size() - 1).getId());
        Global.mRestController.markRead(channel.getId(), request, (EventResponse response) -> {

        }, (String errMsg, int errCode) -> {
            Utils.showMessage(ChatActivity.this, errMsg);
        });
    }

    // endregion

    // region Permission

    /**
     * Permission check
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == Constant.PERMISSIONS_REQUEST) {
            boolean granted = true;
            for (int grantResult : grantResults)
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            if (!granted) PermissionChecker.showRationalDialog(this, null);
        }
    }
    // endregion
}
