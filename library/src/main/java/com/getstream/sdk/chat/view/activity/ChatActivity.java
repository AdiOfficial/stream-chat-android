package com.getstream.sdk.chat.view.activity;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;

import com.getstream.sdk.chat.R;
import com.getstream.sdk.chat.adapter.MessageListItemAdapter;
import com.getstream.sdk.chat.adapter.MessageListItemViewHolder;
import com.getstream.sdk.chat.databinding.ActivityChatBinding;
import com.getstream.sdk.chat.databinding.ViewThreadBinding;
import com.getstream.sdk.chat.function.AttachmentFunction;
import com.getstream.sdk.chat.function.EventFunction;
import com.getstream.sdk.chat.function.MessageFunction;
import com.getstream.sdk.chat.function.ReactionFunction;
import com.getstream.sdk.chat.function.SendFileFunction;
import com.getstream.sdk.chat.model.ModelType;
import com.getstream.sdk.chat.model.User;
import com.getstream.sdk.chat.model.channel.Channel;
import com.getstream.sdk.chat.model.channel.Event;
import com.getstream.sdk.chat.model.message.Message;
import com.getstream.sdk.chat.model.message.MessageTagModel;
import com.getstream.sdk.chat.rest.apimodel.request.MarkReadRequest;
import com.getstream.sdk.chat.rest.apimodel.request.PaginationRequest;
import com.getstream.sdk.chat.rest.apimodel.request.SendActionRequest;
import com.getstream.sdk.chat.rest.apimodel.response.ChannelResponse;
import com.getstream.sdk.chat.rest.apimodel.response.EventResponse;
import com.getstream.sdk.chat.rest.apimodel.response.GetRepliesResponse;
import com.getstream.sdk.chat.rest.apimodel.response.MessageResponse;
import com.getstream.sdk.chat.rest.controller.RestController;
import com.getstream.sdk.chat.utils.Constant;
import com.getstream.sdk.chat.utils.Global;
import com.getstream.sdk.chat.utils.Utils;
import com.getstream.sdk.chat.utils.GridSpacingItemDecoration;
import com.getstream.sdk.chat.model.message.SelectAttachmentModel;
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

public class ChatActivity extends AppCompatActivity implements EventFunction.EventHandler {

    private final String TAG = ChatActivity.class.getSimpleName();

    ChatActivityViewModel mViewModel;
    ActivityChatBinding binding;
    ViewThreadBinding threadBinding;

    private ChannelResponse channelResponse;
    private Channel channel;
    private List<Message> channelMessages, threadMessages;

    private RecyclerView.LayoutManager mLayoutManager, mLayoutManager_thread;
    private MessageListItemAdapter mAdapter, mThreadAdapter;
    private int scrollPosition = 0;
    private static int fVPosition, lVPosition;
    private boolean noHistory;
    // Functions
    private MessageFunction messageFunction;
    private EventFunction eventFunction = Global.eventFunction;
    private SendFileFunction sendFileFunction;

    // region LifeCycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_chat);
        ChatActivityViewModelFactory factory = new ChatActivityViewModelFactory(Global.channelResponse);
        mViewModel = ViewModelProviders.of(this, factory).get(ChatActivityViewModel.class);
        binding.setViewModel(mViewModel);

        threadBinding = binding.clThread;
        threadBinding.setViewModel(mViewModel);

        init();
        configDelivered();
        initUIs();
    }

    @Override
    public void onStop() {
        super.onStop();
        eventFunction.setEventHandler(null);
        eventFunction.setChannel(null);
        Global.channelResponse = null;
        stopTypingClearRepeatingTask();
        Log.d(TAG, "onStop");
    }

    @Override
    public void onResume() {
        super.onResume();
        eventFunction.setChannel(this.channel);
        eventFunction.setEventHandler(this);
        startTypingClearRepeatingTask();
        Log.d(TAG, "OnResume");
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
                    } else {
                        Log.d(TAG, "No Captured Image");
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
        if (binding.clAddFile.getVisibility() == View.VISIBLE) {
            onClickAttachmentViewClose(null);
            return;
        }
        if (binding.clSelectPhoto.getVisibility() == View.VISIBLE) {
            onClickSelectMediaViewClose(null);
            return;
        }
        super.onBackPressed();
    }
    // endregion

    // region Init
    void init() {
        channelResponse = Global.channelResponse;
        channel = channelResponse.getChannel();
        channelMessages = channelResponse.getMessages();

        eventFunction.setChannel(channel);
        messageFunction = new MessageFunction(this.channelResponse);
        sendFileFunction = new SendFileFunction(this, binding, channelResponse);
        checkReadMark();
        noHistory = channelMessages.size() < Constant.CHANNEL_MESSAGE_LIMIT;
    }

    boolean lockRVScrollListener = false;

    void initUIs() {
        // Message Composer
        binding.setActiveMessageComposer(false);
        binding.setActiveMessageSend(false);
        binding.setShowLoadMoreProgressbar(false);
        binding.setNoConnection(false);
        binding.etMessage.setOnFocusChangeListener((View view, boolean hasFocus) -> {
            binding.setActiveMessageComposer(hasFocus);
            lockRVScrollListener = hasFocus;
        });
        binding.etMessage.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                String text = binding.etMessage.getText().toString();
                binding.setActiveMessageSend(!(text.length() == 0));
                sendFileFunction.checkCommand(text);
                if (text.length() > 0) {
                    sendTypeInidcator();
                }
            }

            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });

        // Message RecyclerView
        mLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mLayoutManager_thread = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);

        mLayoutManager.scrollToPosition(channelMessages.size());
        binding.rvMessage.setLayoutManager(mLayoutManager);

        setScrollDownHideKeyboard(binding.rvMessage);
        setRecyclerViewAdapder();

        KeyboardVisibilityEvent.setEventListener(
                this, (boolean isOpen) -> {
                    if (!isOpen) {
                        binding.etMessage.clearFocus();
                    } else {
                        lockRVScrollListener = true;
                        new Handler().postDelayed(() -> {
                            lockRVScrollListener = false;
                        }, 500);
                    }
                    if (lVPosition > messages().size() - 2)
                        recyclerView().scrollToPosition(lVPosition);

                });

        // File Attachment
        binding.rvMedia.setLayoutManager(new GridLayoutManager(this, 4, LinearLayoutManager.VERTICAL, false));
        binding.rvMedia.hasFixedSize();
        int spanCount = 4;  // 4 columns
        int spacing = 2;    // 1 px
        boolean includeEdge = false;
        binding.rvMedia.addItemDecoration(new GridSpacingItemDecoration(spanCount, spacing, includeEdge));
    }

    private List<Message> messages() {
        return isThreadMode() ? threadMessages : channelMessages;
    }

    private RecyclerView recyclerView() {
        return isThreadMode() ? threadBinding.rvThread : binding.rvMessage;
    }

    void configDelivered() {
        if (!messages().get(messages().size() - 1).isIncoming())
            messages().get(messages().size() - 1).setDelivered(true);
    }

    void setRecyclerViewAdapder() {
        mAdapter = new MessageListItemAdapter(this, this.channelResponse, messages(), isThreadMode(), (View v) -> {
            Object object = v.getTag();
            Log.d(TAG, "onClick Attach : " + object);
            messageItemClickListener(object);
        }, (View v) -> {
            try {
                messageItemLongClickListener(v.getTag());
            } catch (Exception e) {
            }
            return true;
        });
        recyclerView().setAdapter(mAdapter);
        mViewModel.getChannelMessages().observe(this, (@Nullable List<Message> users) -> {
            if (scrollPosition == -1) return;

            mAdapter.notifyDataSetChanged();
            if (scrollPosition > 0) {
                recyclerView().scrollToPosition(scrollPosition);
                scrollPosition = 0;
                return;
            }
            recyclerView().scrollToPosition(messages().size());
        });
        mViewModel.setChannelMessages(messages());
    }

    private void setScrollDownHideKeyboard(RecyclerView recyclerView) {
        fVPosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
        Log.d(TAG, "fVPosition: " + fVPosition);
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

                if (currentFirstVisible < fVPosition) {
                    Utils.hideSoftKeyboard(ChatActivity.this);
                    binding.etMessage.clearFocus();
                    if (currentFirstVisible == 0 && !noHistory && !isThreadMode()) loadMore();
                }
                new Handler().postDelayed(() -> {
                    lVPosition = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
                }, 500);
                fVPosition = currentFirstVisible;
            }
        });
    }

    public void onClickBackFinish(View v) {
        finish();
    }
    // endregion

    // region Message
    private Message ephemeralMessage = null;

    public void onClickSendMessage(View v) {
        if (binding.etMessage.getTag() == null) {
            if (Global.noConnection) {
                sendOfflineMessage();
                return;
            }
            if (!isThreadMode()) {
                ephemeralMessage = createOfflineMessage(false);
                handleAction(ephemeralMessage);
            }
            messageFunction.sendMessage(binding.etMessage.getText().toString(), thread_parentMessage, sendFileFunction.getSelectedAttachments(), new MessageFunction.MessageSendListener() {
                @Override
                public void onSuccess(MessageResponse response) {
                    if (!isThreadMode()) {
                        if (Global.isCommandMessage(response.getMessage()) || response.getMessage().getType().equals(ModelType.message_error)) {
                            channelMessages.remove(ephemeralMessage);
                            response.getMessage().setDelivered(true);
                        } else {
                            ephemeralMessage.setId(response.getMessage().getId());
                        }
                    }
                    handleAction(response.getMessage());
                    Log.d(TAG, "Delivered Message");
                }

                @Override
                public void onFailed(String errMsg, int errCode) {
                    Log.d(TAG, "Failed Send message: " + errMsg);
                }
            });
            initSendMessage();
        } else
            messageFunction.updateMessage(binding.etMessage.getText().toString(), (Message) binding.etMessage.getTag(), sendFileFunction.getSelectedAttachments(), new MessageFunction.MessageSendListener() {
                @Override
                public void onSuccess(MessageResponse response) {
                    initSendMessage();
                    binding.etMessage.setTag(null);
                }

                @Override
                public void onFailed(String errMsg, int errCode) {

                }
            });
    }

    private void initSendMessage() {
        binding.etMessage.setText("");
        sendFileFunction.initSendMessage();
    }

    private void sendOfflineMessage() {
        Message message = createOfflineMessage(true);
        handleAction(message);
        initSendMessage();
    }

    private Message createOfflineMessage(boolean isOffle) {
        Message message = new Message();
        message.setId(Global.convertDateToString(new Date()));
        message.setText(binding.etMessage.getText().toString());
        message.setType(isOffle ? ModelType.message_error : ModelType.message_ephemeral);
        message.setCreated_at(Global.convertDateToString(new Date()));
        Global.setStartDay(Arrays.asList(message), getLastMessage());
        message.setUser(Global.streamChat.getUser());
        return message;
    }

    private Message getLastMessage() {
        return messages().isEmpty() ? null : messages().get(messages().size() - 1);
    }
    // endregion

    // region Typing Indicator
    boolean isTyping = false;

    void sendTypeInidcator() {
        if (isThreadMode()) return;

        if (!isTyping) {
            Log.d(TAG, "typing.start");
            eventFunction.sendEvent(Event.typing_start);
            isTyping = true;
        }
        new Handler().postDelayed(() -> {
            if (isTyping) {
                eventFunction.sendEvent(Event.typing_stop);
                isTyping = false;
                Log.d(TAG, "typing.stop");
            }
        }, 2000);
    }

    // refresh Current Typing users in this channel
    private Handler clearTyingUserHandler = new Handler();
    Runnable runnableTypingClear = new Runnable() {
        @Override
        public void run() {
            try {
                Global.typingUsers = new ArrayList<>();
                try {
                    mAdapter.notifyItemChanged(channelMessages.size());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } finally {
                clearTyingUserHandler.postDelayed(runnableTypingClear, 1000 * 3);
            }
        }
    };

    void startTypingClearRepeatingTask() {
        runnableTypingClear.run();
    }

    void stopTypingClearRepeatingTask() {
        clearTyingUserHandler.removeCallbacks(runnableTypingClear);
    }
    // endregion

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

    private void configThread(@NonNull Message message, int position) {
        mViewModel.setReplyCount(message.getReplyCount());
        cleanEditView();

        thread_parentMessage = message;
        threadMessages = new ArrayList<>();
        MessageListItemViewHolder viewHolder = new MessageListItemViewHolder((View) threadBinding.clMessageParent);
        viewHolder.bind(this, this.channelResponse, channelMessages, position, true, null, null);

        if (message.getReplyCount() == 0) {
            setThreadAdapter();
            threadBinding.setShowThread(true);
        } else {
            binding.setShowMainProgressbar(true);
            RestController.GetRepliesCallback callback = (GetRepliesResponse response) -> {
                threadMessages = response.getMessages();
                Global.setStartDay(threadMessages, null);
                setThreadAdapter();
                threadBinding.setShowThread(true);
                binding.setShowMainProgressbar(false);
            };
            Global.mRestController.getReplies(message.getId(), callback, (String errMsg, int errCode) -> {
                Utils.showMessage(ChatActivity.this, errMsg);
                thread_parentMessage = null;
                binding.setShowMainProgressbar(false);
            });
        }
        // Clean RecyclerView
    }

    public void onClickCloseThread(View v) {
        threadBinding.setShowThread(false);
        cleanEditView();
        setScrollDownHideKeyboard(binding.rvMessage);
    }

    private void setThreadAdapter() {
        if (threadMessages.size() > 0)
            mLayoutManager_thread.scrollToPosition(threadMessages.size() - 1);

        threadBinding.rvThread.setLayoutManager(mLayoutManager_thread);
        mThreadAdapter = new MessageListItemAdapter(this, this.channelResponse, threadMessages, isThreadMode(), (View v) -> {
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
        binding.etMessage.setTag(null);
        binding.etMessage.setText("");
        thread_parentMessage = null;
        threadMessages = null;
        lVPosition = 0;
        fVPosition = 0;
        binding.rvMessage.clearOnScrollListeners();
        threadBinding.rvThread.clearOnScrollListeners();
        Utils.hideSoftKeyboard(this);
    }
    // endregion

    // region Listener

    // region Message Item Touch Listener
    private void messageItemClickListener(Object object) {
        if (object.getClass().equals(SelectAttachmentModel.class)) {
            new AttachmentFunction().progressAttachment((SelectAttachmentModel) object, this);
            return;
        }

        if (object.getClass().equals(MessageTagModel.class)) {
            MessageTagModel tag = (MessageTagModel) object;
            Message message = messages().get(tag.position);
            switch (tag.type) {
                case Constant.TAG_MOREACTION_REPLY:
                    configThread(channelMessages.get(tag.position), tag.position);
                    Log.d(TAG, "Click Reply : " + tag.position);
                    break;
                case Constant.TAG_ACTION_SEND:
                case Constant.TAG_ACTION_SHUFFLE:
                    Map<String, String> map = new HashMap<>();
                    if (tag.type.equals(Constant.TAG_ACTION_SEND))
                        map.put("image_action", ModelType.action_send);
                    else
                        map.put("image_action", ModelType.action_shuffle);

                    SendActionRequest request = new SendActionRequest(channel.getId(), message.getId(), ModelType.channel_messaging, map);
                    RestController.SendMessageCallback callback = (MessageResponse response) -> {
                        handleAction(message);
                        response.getMessage().setDelivered(true);
                        handleAction(response.getMessage());
                    };
                    Global.mRestController.sendAction(message.getId(), request, callback, (String errMsg, int errCode) -> {
                        Log.d(TAG, errMsg);
                    });
                    Log.d(TAG, "Click ACTION_SEND : " + tag.position);
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
                    Log.d(TAG, "Origin Y-" + originY);
                    break;
                case Constant.TAG_MESSAGE_RESEND:
                    if (Global.noConnection) {
                        Utils.showMessage(this, "No internet connection!");
                        break;
                    }
                    handleAction(message);
                    messageFunction.sendMessage(message.getText(), isThreadMode() ? thread_parentMessage : null, null, new MessageFunction.MessageSendListener() {
                        @Override
                        public void onSuccess(MessageResponse response) {
                            initSendMessage();
                            Log.d(TAG, "Failed Message Sent!");
                        }

                        @Override
                        public void onFailed(String errMsg, int errCode) {
                            initSendMessage();
                            Log.d(TAG, "Failed Message Sending Failed!");
                        }
                    });
                    break;
                case Constant.TAG_MESSAGE_INVALID_COMMAND:
                    handleAction(message);
                    binding.etMessage.setText("/");
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
                    binding.etMessage.setTag(message);
                    binding.etMessage.setText(message.getText());
                    binding.etMessage.requestFocus();
                    binding.etMessage.setSelection(binding.etMessage.getText().length());
                    break;
                case Constant.TAG_MOREACTION_DELETE:
                    messageFunction.deleteMessage(binding.etMessage, message);
                    break;
                case Constant.TAG_MOREACTION_REPLY:
                    if (!isThreadMode())
                        configThread(message, position);
                    break;
                default:
                    break;
            }
        });
    }

    private void showAlertReadUsers(Message message) {
        List<User> readUsers = Global.getReadUsers(channelResponse, message);
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
            Log.d(TAG, "Deliever Indicator 2");
        } else {
            if (message.isDelivered()) {
                msg = "Delivered";
                Log.d(TAG, "Deliever Indicator 3");
            } else {
                msg = "sending...";
                Log.d(TAG, "Deliever Indicator 4");
            }
        }
        Utils.showMessage(this, msg);
    }
    // endregion

    // Event Listener
    @Override
    public void handleEvent(final Event event) {
        this.runOnUiThread(() -> {
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
                default:
                    break;
            }
        });
        Gson gson = new Gson();
        String eventStr = gson.toJson(event);
        Log.d(TAG, "New Event: " + eventStr);
    }

    @Override
    public void handleReconnection(boolean disconnect) {
        binding.setNoConnection(disconnect);
        if (!disconnect) {
            reconnectionHandler();
        }
    }

    private void messageEvent(Event event) {
        Message message = event.getMessage();
        if (message == null) return;

        switch (event.getType()) {
            case Event.message_new:
                Global.setStartDay(Arrays.asList(message), getLastMessage());
                switch (message.getType()) {
                    case ModelType.message_regular:
                        if (!message.isIncoming()) message.setDelivered(true);
                        try {
                            channelMessages.remove(ephemeralMessage);
                        } catch (Exception e) {
                        }
                        eventFunction.newMessage(channelResponse, message);
                        scrollPosition = 0;
                        mViewModel.setChannelMessages(channelMessages);
                        messageReadMark();
                        break;
                    case ModelType.message_ephemeral:
                    case ModelType.message_error:
                        boolean isContain = false;
                        Global.setEphemeralMessage(channel.getId(), message);
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

                break;
            case Event.message_updated:
                if (isThreadMode() && message.getId().equals(thread_parentMessage.getId())) {
                    mViewModel.setReplyCount(message.getReplyCount());
                }
            case Event.message_deleted:
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
                    mAdapter.notifyItemChanged(changedIndex);
                }
                break;
            default:
                break;
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
                mAdapter.notifyItemChanged(changedIndex);
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
        mAdapter.notifyItemChanged(channelMessages.size());
    }

    void messageReadEvent(Event event) {
        eventFunction.readMessage(channelResponse, event);
        if (!channelResponse.getLastMessage().isIncoming()) {
            mAdapter.notifyItemChanged(channelMessages.size() - 1);
        }
    }
    // endregion

    // endregion

    // region Pagination

    boolean isCalling;

    private void loadMore() {
        if (noHistory || isCalling) return;
        Log.d(TAG, "Next pagination...");
        isCalling = true;
        binding.setShowLoadMoreProgressbar(true);
        PaginationRequest request = new PaginationRequest(Constant.DEFAULT_LIMIT, channelMessages.get(0).getId(), this.channel);

        RestController.ChannelDetailCallback callback = (ChannelResponse response) -> {
            binding.setShowLoadMoreProgressbar(false);

            List<Message> newMessages = new ArrayList<>(response.getMessages());
            Log.d(TAG, "new Message Count: " + newMessages.size());
            if (newMessages.size() < Constant.DEFAULT_LIMIT) noHistory = true;

            // Set Date Time
            Global.setStartDay(newMessages, null);
            // Add new to current Message List
            for (int i = newMessages.size() - 1; i > -1; i--) {
                channelMessages.add(0, newMessages.get(i));
            }
            scrollPosition = ((LinearLayoutManager) binding.rvMessage.getLayoutManager()).findLastCompletelyVisibleItemPosition() + response.getMessages().size();
            mViewModel.setChannelMessages(channelMessages);
            isCalling = false;
        };

        Global.mRestController.pagination(channel.getId(), request, callback, (String errMsg, int errCode) -> {
            Log.d(TAG, errMsg);
            isCalling = false;
            binding.setShowLoadMoreProgressbar(false);
        });
    }

    // endregion

    // region Check Message Read

    private void checkReadMark() {
        if (channelResponse.getLastMessage() == null) return;
        if (!Global.readMessage(channelResponse.getReadDateOfChannelLastMessage(true), channelResponse.getLastMessage().getCreated_at())) {
            messageReadMark();
        }
    }

    private void messageReadMark() {
        MarkReadRequest request = new MarkReadRequest(channelMessages.get(channelMessages.size() - 1).getId());
        RestController.EventCallback callback = (EventResponse response) -> {

        };
        Global.mRestController.readMark(channel.getId(), request, callback, (String errMsg, int errCode) -> {
            Utils.showMessage(ChatActivity.this, errMsg);
        });
    }

    // endregion

    // region Attachment

    public void onClickAttachmentViewOpen(View v) {
        sendFileFunction.onClickAttachmentViewOpen(v);
    }

    public void onClickAttachmentViewClose(View v) {
        sendFileFunction.onClickAttachmentViewClose(v);
    }

    public void onClickSelectMediaViewOpen(View v) {
        sendFileFunction.onClickSelectMediaViewOpen(v);
    }

    public void onClickSelectMediaViewClose(View v) {
        sendFileFunction.onClickSelectMediaViewClose(v);
    }

    public void onClickSelectFileViewOpen(View v) {
        sendFileFunction.onClickSelectFileViewOpen(v);
    }

    public void onClickTakePicture(View v) {
        sendFileFunction.onClickAttachmentViewClose(v);
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        Intent chooserIntent = Intent.createChooser(takePictureIntent, "Capture Image or Video");
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{takeVideoIntent});
        startActivityForResult(chooserIntent, Constant.CAPTURE_IMAGE_REQUEST_CODE);
    }

    // endregion

    // region Reconnection
    private void reconnectionHandler() {
        for (ChannelResponse channelResponse : Global.channels) {
            if (channelResponse.getChannel().getId().equals(channel.getId())) {
                Global.channelResponse = channelResponse;
                init();
                List<Message> ephemeralMessages = Global.getEphemeralMessages(channel.getId());
                if (ephemeralMessages != null && !ephemeralMessages.isEmpty())
                    for (int i = 0; i < ephemeralMessages.size(); i++) {
                        channelMessages.add(ephemeralMessages.get(i));
                    }

                setRecyclerViewAdapder();
                Log.d(TAG, "Refresh reconnection!");
                break;
            }
        }
    }
    // end region
}
