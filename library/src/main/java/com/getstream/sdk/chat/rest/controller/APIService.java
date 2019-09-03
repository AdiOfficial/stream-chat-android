package com.getstream.sdk.chat.rest.controller;

import com.getstream.sdk.chat.rest.request.AddDeviceRequest;
import com.getstream.sdk.chat.rest.request.ChannelQueryRequest;
import com.getstream.sdk.chat.rest.request.MarkReadRequest;
import com.getstream.sdk.chat.rest.request.ReactionRequest;
import com.getstream.sdk.chat.rest.request.SendActionRequest;
import com.getstream.sdk.chat.rest.request.SendEventRequest;
import com.getstream.sdk.chat.rest.request.SendMessageRequest;
import com.getstream.sdk.chat.rest.request.UpdateMessageRequest;
import com.getstream.sdk.chat.rest.response.ChannelState;
import com.getstream.sdk.chat.rest.response.DevicesResponse;
import com.getstream.sdk.chat.rest.response.EventResponse;
import com.getstream.sdk.chat.rest.response.FileSendResponse;
import com.getstream.sdk.chat.rest.response.FlagResponse;
import com.getstream.sdk.chat.rest.response.GetDevicesResponse;
import com.getstream.sdk.chat.rest.response.GetRepliesResponse;
import com.getstream.sdk.chat.rest.response.MessageResponse;
import com.getstream.sdk.chat.rest.response.MuteUserResponse;
import com.getstream.sdk.chat.rest.response.QueryChannelsResponse;
import com.getstream.sdk.chat.rest.response.QueryUserListResponse;

import org.json.JSONObject;

import java.util.Map;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface APIService {
    // region Channel
    @GET("/channels")
    Call<QueryChannelsResponse> queryChannels(@Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String clientID, @Query("payload") String payload);

    @POST("/channels/{type}/{id}/query")
    Call<ChannelState> queryChannel(@Path("type") String channelType, @Path("id") String channelId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String clientID, @Body ChannelQueryRequest request);

    @DELETE("/channels/{type}/{id}")
    Call<ChannelState> deleteChannel(@Path("type") String channelType, @Path("id") String channelId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String clientID);

    @POST("/channels/{type}/{id}/stop-watching")
    Call<ChannelState> stopWatching(@Path("type") String channelType, @Path("id") String channelId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String clientID, @Body Map<String, String> body);

    @POST("/channels/{type}/{id}")
    Call<ChannelState> acceptInvite(@Path("type") String channelType, @Path("id") String channelId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String clientID, @Body Map<String, Object> body);

    @POST("/channels/{type}/{id}")
    Call<ChannelState> rejectInvite(@Path("type") String channelType, @Path("id") String channelId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String clientID, @Body Map<String, Object> body);
    // endregion

    // region User
    @GET("/users")
    Call<QueryUserListResponse> queryUsers(@Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Query("payload") JSONObject payload);

    @POST("/channels/{type}/{id}")
    Call<ChannelState> addMembers(@Path("type") String channelType, @Path("id") String channelId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body JSONObject body);

    @POST("/moderation/mute")
    Call<MuteUserResponse> muteUser(@Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body Map<String, String> body);

    @POST("/moderation/unmute")
    Call<MuteUserResponse> unMuteUser(@Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body Map<String, String> body);

    @POST("/moderation/flag")
    Call<FlagResponse> flag(@Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body Map<String, String> body);

    @POST("/moderation/unflag")
    Call<FlagResponse> unFlag(@Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body Map<String, String> body);
    // endregion

    // region Message
    @POST("/channels/{type}/{id}/message")
    Call<MessageResponse> sendMessage(@Path("type") String channelType, @Path("id") String channelId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body SendMessageRequest request);

    @POST("/messages/{id}")
    Call<MessageResponse> updateMessage(@Path("id") String messageId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body UpdateMessageRequest request);

    @GET("/messages/{id}")
    Call<MessageResponse> getMessage(@Path("id") String messageId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId);

    @POST("/messages/{id}/action")
    Call<MessageResponse> sendAction(@Path("id") String messageId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body SendActionRequest request);

    @DELETE("/messages/{id}")
    Call<MessageResponse> deleteMessage(@Path("id") String messageId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId);

    @POST("/messages/{id}/reaction")
    Call<MessageResponse> sendReaction(@Path("id") String messageId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body ReactionRequest request);

    @DELETE("/messages/{id}/reaction/{type}")
    Call<MessageResponse> deleteReaction(@Path("id") String messageId, @Path("type") String reactionType, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId);

    @GET("/messages/{parent_id}/replies")
    Call<GetRepliesResponse> getReplies(@Path("parent_id") String messageId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Query("limit") String limit);

    @GET("/messages/{parent_id}/replies")
    Call<GetRepliesResponse> getRepliesMore(@Path("parent_id") String messageId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Query("limit") String limit, @Query("id_lt") String firstId);

    @POST("/channels/{type}/{id}/event")
    Call<EventResponse> sendEvent(@Path("type") String channelType, @Path("id") String channelId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body SendEventRequest request);

    @POST("/channels/{type}/{id}/read")
    Call<EventResponse> markRead(@Path("type") String channelType, @Path("id") String channelId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body MarkReadRequest request);

    @POST("/channels/read")
    Call<EventResponse> markAllRead(@Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body MarkReadRequest request);

    @Multipart
    @POST("/channels/{type}/{id}/image")
    Call<FileSendResponse> sendImage(@Path("type") String channelType, @Path("id") String channelId, @Part MultipartBody.Part file, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId);

    @Multipart
    @POST("/channels/{type}/{id}/file")
    Call<FileSendResponse> sendFile(@Path("type") String channelType, @Path("id") String channelId, @Part MultipartBody.Part file, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId);
    // endregion

    // region Device
    @GET("/devices")
    Call<GetDevicesResponse> getDevices(@Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Query("userID") Map body);

    @POST("devices")
    Call<DevicesResponse> addDevices(@Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId, @Body AddDeviceRequest request);

    @DELETE("/devices")
    Call<DevicesResponse> deleteDevice(@Query("id") String deviceId, @Query("api_key") String apiKey, @Query("user_id") String userId, @Query("client_id") String connectionId);
    // endregion
}
