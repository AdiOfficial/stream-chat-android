package com.getstream.sdk.chat.view.fragment;

import android.app.Activity;
import androidx.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.getstream.sdk.chat.adapter.ChannelListItemAdapter;
import com.getstream.sdk.chat.databinding.FragmentChannelListBinding;
import com.getstream.sdk.chat.function.EventFunction;
import com.getstream.sdk.chat.interfaces.WSResponseHandler;
import com.getstream.sdk.chat.model.Channel;
import com.getstream.sdk.chat.model.Event;
import com.getstream.sdk.chat.model.ModelType;
import com.getstream.sdk.chat.rest.request.AddDeviceRequest;
import com.getstream.sdk.chat.rest.request.ChannelDetailRequest;
import com.getstream.sdk.chat.rest.response.AddDevicesResponse;
import com.getstream.sdk.chat.rest.response.ChannelResponse;
import com.getstream.sdk.chat.rest.response.GetChannelsResponse;
import com.getstream.sdk.chat.utils.Constant;
import com.getstream.sdk.chat.utils.Global;
import com.getstream.sdk.chat.utils.PermissionChecker;
import com.getstream.sdk.chat.utils.Utils;
import com.getstream.sdk.chat.view.activity.UsersActivity;
import com.getstream.sdk.chat.viewmodel.ChannelListViewModel;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import okio.ByteString;


/**
 * A Fragment for Channels preview.
 */
public class ChannelListFragment extends Fragment implements WSResponseHandler {

    final String TAG = ChannelListFragment.class.getSimpleName();

    private ChannelListViewModel mViewModel;
    private FragmentChannelListBinding binding;
    private ChannelListItemAdapter adapter;

    public int containerResId;

    private boolean isLastPage = false;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;
    private RecyclerView.LayoutManager mLayoutManager;

    private int channelItemLayoutId;
    private String channelItemViewHolderName;

    // region LifeCycle
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        binding = FragmentChannelListBinding.inflate(inflater, container, false);
        mViewModel = ViewModelProviders.of(this).get(ChannelListViewModel.class);
        binding.setViewModel(mViewModel);

        init();
        configUIs();
        getChannels();
        PermissionChecker.permissionCheck(getActivity(), this);
        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(ChannelListViewModel.class);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null)
            adapter.notifyDataSetChanged();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Global.webSocketService.removeWSResponseHandler(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constant.USERSLISTACTIVITY_REQUEST) {
            try {
                boolean result = data.getBooleanExtra("result", false);
                if (result) {
                    String channelId = data.getStringExtra(Constant.TAG_CHANNEL_RESPONSE_ID);
                    navigationChannelFragment(Global.getChannelResponseById(channelId));
                }
            } catch (Exception e) {
            }
        }
    }

    private Activity activity;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Activity) {
            activity = (Activity) context;
        }
    }

    //endregion

    // region Private Functions
    private void init() {
        Global.webSocketService.setWSResponseHandler(this, getContext());
        Global.channels = new ArrayList<>();
        try {
            Fresco.initialize(getContext());
        } catch (Exception e) {
        }

        pref = getActivity().getApplicationContext().getSharedPreferences("MyPref", 0);
        editor = pref.edit();
    }

    private void configUIs() {
        // Fits SystemWindows
        try {
            FrameLayout frameLayout = getActivity().findViewById(this.containerResId);
            frameLayout.setFitsSystemWindows(true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // hides Action Bar
        try {
            ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
        } catch (Exception e) {
            e.printStackTrace();
        }

        configCustomChannelItemView();

        binding.clHeader.setVisibility(Global.component.channel.isShowSearchBar() ? View.VISIBLE : View.GONE);

        configChannelListView();
        configChannelRecyclerView();


        binding.tvSend.setOnClickListener((View view) -> {
            navigateUserList();
            Utils.setButtonDelayEnable(view);
        });

        binding.etSearch.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                adapter.filter = binding.etSearch.getText().toString();
                adapter.notifyDataSetChanged();
            }

            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });
        binding.etSearch.clearFocus();
    }

    private void configChannelRecyclerView() {
        binding.listChannels.setVisibility(View.VISIBLE);
        mLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        binding.listChannels.setLayoutManager(mLayoutManager);

        binding.listChannels.setOnScrollListener(new RecyclerView.OnScrollListener() {
            private int mLastFirstVisibleItem;

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int firstVisibleItem = ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                int currentLastVisible = ((LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
                if (mLastFirstVisibleItem < firstVisibleItem) {
                    if (currentLastVisible == Global.channels.size() - 1)
                        getChannels();
                }
                if (mLastFirstVisibleItem > firstVisibleItem) {
                    Log.d(TAG, "SCROLLING UP");
                }
                mLastFirstVisibleItem = firstVisibleItem;
            }
        });
    }

    private void configCustomChannelItemView() {
        channelItemLayoutId = Global.component.channel.getChannelItemLayoutId();
        channelItemViewHolderName = Global.component.channel.getChannelItemViewHolderName();
    }

    private void setAfterFirstConnection() {
        // Initialize Channels
        Global.channels = new ArrayList<>();

        initLoadingChannels();
        getChannels();

        // get and save Device TokenService
        try {
            getDeviceToken();
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "Failed adding device token");
        }
    }

    private void initLoadingChannels() {
        isCalling = false;
        isLastPage = false;
    }

    boolean isCalling;

    /**
     * Getting channels
     */
    public void getChannels() {
        if (TextUtils.isEmpty(Global.streamChat.getClientID())) return;
        Log.d(TAG, "getChannels...");
        if (isLastPage || isCalling) return;
        binding.setShowMainProgressbar(true);
        isCalling = true;
        Global.mRestController.getChannels(getPayload(), this::progressNewChannels
                , (String errMsg, int errCode) -> {
                    binding.setShowMainProgressbar(false);
                    isCalling = false;

//                    Utils.showMessage(getContext(), errMsg);
                    Log.d(TAG, "Failed Get Channels : " + errMsg);
                });
    }

    private void progressNewChannels(GetChannelsResponse response) {
        Log.d(TAG, "channels connected");
        binding.setShowMainProgressbar(false);
        isCalling = false;
        if (response.getChannels() == null || response.getChannels().isEmpty()) {
            if (Global.channels == null || Global.channels.isEmpty())
                Utils.showMessage(getContext(), "There is no any active Channel(s)!");
            return;
        }

        if (Global.channels == null) Global.channels = new ArrayList<>();
        if (Global.channels.isEmpty()) {
            configChannelListView();
            binding.setNoConnection(false);
            Intent broadcast = new Intent();
            broadcast.setAction(Constant.BC_RECONNECT_CHANNEL);
            broadcast.addCategory(Intent.CATEGORY_DEFAULT);
            getContext().sendBroadcast(broadcast);
        }

        for (int i = 0; i < response.getChannels().size(); i++) {
            ChannelResponse channelResponse = response.getChannels().get(i);
            Global.channels.add(channelResponse);
        }

        adapter.notifyDataSetChanged();
        isLastPage = (response.getChannels().size() < Constant.CHANNEL_LIMIT);
    }

    private void getChannel(Channel channel) {
        Log.d(TAG, "Channel Connecting...");
        binding.setShowMainProgressbar(true);
        channel.setType(ModelType.channel_messaging);
        Map<String, Object> messages = new HashMap<>();
        messages.put("limit", Constant.DEFAULT_LIMIT);
        Map<String, Object> data = new HashMap<>();


        ChannelDetailRequest request = new ChannelDetailRequest(messages, data, true, true);

        Global.mRestController.channelDetailWithID(channel.getId(), request, (ChannelResponse response) -> {
            binding.setShowMainProgressbar(false);
            if (!response.getMessages().isEmpty())
                Global.setStartDay(response.getMessages(), null);
            Global.addChannelResponse(response);

            if (getActivity() != null)
                getActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());

            Gson gson = new Gson();
            Log.d(TAG, "Channel Response: " + gson.toJson(response));

        }, (String errMsg, int errCode) -> {
            binding.setShowMainProgressbar(false);
            Log.d(TAG, "Failed Connect Channel : " + errMsg);
        });
    }

    private JSONObject getPayload() {
        Map<String, Object> payload = new HashMap<>();

        // Sort Option
        if (Global.component.channel.getSortOptions() != null) {
            payload.put("sort", Collections.singletonList(Global.component.channel.getSortOptions()));
        } else {
            Map<String, Object> sort = new HashMap<>();
            sort.put("field", "last_message_at");
            sort.put("direction", -1);
            payload.put("sort", Collections.singletonList(sort));
        }

        if (Global.component.channel.getFilter() != null) {
            payload.put("filter_conditions", Global.component.channel.getFilter().getData());
        } else {
            payload.put("filter_conditions", new HashMap<>());
        }

        payload.put("message_limit", Constant.CHANNEL_MESSAGE_LIMIT);
        if (Global.channels.size() > 0)
            payload.put("offset", Global.channels.size());
        payload.put("limit", Constant.CHANNEL_LIMIT);
        payload.put("presence", false);
        payload.put("state", true);
        payload.put("subscribe", true);
        payload.put("watch", true);
        return new JSONObject(payload);
    }

    private void configChannelListView() {
        adapter = new ChannelListItemAdapter(getContext(), Global.channels, channelItemViewHolderName, channelItemLayoutId, (View view) -> {
            String channelId = view.getTag().toString();
            ChannelResponse response = Global.getChannelResponseById(channelId);
            getActivity().runOnUiThread(() -> navigationChannelFragment(response));
        }, (View view) -> {
            String channelId = view.getTag().toString();
            final AlertDialog alertDialog = new AlertDialog.Builder(getContext())
                    .setTitle("Do you want to delete this channel?")
                    .setMessage("If you delete this channel, will delete all chat history for this channel!")
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();

            alertDialog.setOnShowListener((DialogInterface dialog) -> {
                Button button = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener((View v) -> {
                    ChannelResponse response_ = Global.getChannelResponseById(channelId);
                    Global.mRestController.deleteChannel(channelId, (ChannelResponse response) -> {
                        Utils.showMessage(getContext(), "Deleted successfully!");
                        Global.channels.remove(response_);
                        adapter.notifyDataSetChanged();
                    }, (String errMsg, int errCode) -> {
                        Utils.showMessage(getContext(), errMsg);
                    });
                    alertDialog.dismiss();
                });

            });
            alertDialog.show();
            return true;
        });
        binding.listChannels.setAdapter(adapter);
    }

    private void navigationChannelFragment(ChannelResponse response) {
        ChannelFragment fragment = new ChannelFragment();
        fragment.channelIdFromChannelList = response.getChannel().getId();
        FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
        fragmentManager.addOnBackStackChangedListener(() -> {
            if (adapter != null)
                adapter.notifyDataSetChanged();
        });
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.add(containerResId, fragment);
        fragmentTransaction.addToBackStack("OK");
        fragmentTransaction.commit();
    }

    private void navigateUserList() {
        Intent intent = new Intent(getContext(), UsersActivity.class);
        startActivityForResult(intent, Constant.USERSLISTACTIVITY_REQUEST);
    }

    private void getDeviceToken() {
        String token = pref.getString("TokenService", null);
        if (token != null) {
            Log.d(TAG, "device Token: " + token);
            return;
        }

        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener((@NonNull Task<InstanceIdResult> task) -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(getActivity(), "getInstanceId failed:" + task.getException(), Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "getInstanceId failed", task.getException());
                        return;
                    }
                    String token_ = task.getResult().getToken();
                    Log.d(TAG, "device TokenService: " + token_);
                    // Save to Server
                    addDevice(token_);
                    // Save to Local
                    editor.putString("TokenService", token_);
                    editor.commit();
                });
    }

    private void addDevice(@NonNull String deviceId) {
        AddDeviceRequest request = new AddDeviceRequest(deviceId);
        Global.mRestController.addDevice(request, (AddDevicesResponse response) -> {
            Log.d(TAG, "ADDED Device!");
        }, (String errMsg, int errCode) -> {
            Log.d(TAG, "Failed ADD Device! " + errMsg);
        });
    }

    //endregion

    // region Listners

    /**
     * Handle server response
     *
     * @param event Server response
     */
    @Override
    public void handleEventWSResponse(Event event) {
        if (Global.eventFunction == null) Global.eventFunction = new EventFunction();
        Global.eventFunction.handleReceiveEvent(event);

        switch (event.getType()) {
            case Event.message_new:
            case Event.message_read:
            case Event.channel_deleted:
            case Event.channel_updated:
                if (activity != null)
                    activity.runOnUiThread(() -> adapter.notifyDataSetChanged());
                break;
            case Event.notification_added_to_channel:
                Channel channel_ = event.getChannel();
                getChannel(channel_);
                break;
            default:
                break;
        }
        Log.d(TAG, "New Event: " + new Gson().toJson(event));
    }

    @Override
    public void handleByteStringWSResponse(ByteString byteString) {

    }

    @Override
    public void handleConnection() {
        Log.d(TAG, "Reconnection!");
        setAfterFirstConnection();
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

    //endregion

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
            if (!granted) PermissionChecker.showRationalDialog(getContext(), this);
        }
    }
    // endregion
}
