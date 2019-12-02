package com.getstream.sdk.chat.rest.response;

import android.text.TextUtils;
import android.util.Log;

import com.getstream.sdk.chat.model.Channel;
import com.getstream.sdk.chat.model.Member;
import com.getstream.sdk.chat.model.Watcher;
import com.getstream.sdk.chat.rest.Message;
import com.getstream.sdk.chat.rest.User;
import com.getstream.sdk.chat.rest.core.Client;
import com.getstream.sdk.chat.storage.Sync;
import com.getstream.sdk.chat.storage.converter.ChannelUserReadListConverter;
import com.getstream.sdk.chat.storage.converter.MemberListConverter;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import org.jetbrains.annotations.NotNull;

import java.util.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.*;

@Entity(tableName = "stream_channel_state")
@SuppressWarnings(RoomWarnings.PRIMARY_KEY_FROM_EMBEDDED_IS_DROPPED)
public class ChannelState {

    private static final String TAG = ChannelState.class.getSimpleName();

    @PrimaryKey
    @NonNull
    private String cid;

    // ignore since we always embed the channel state in the channel
    @Ignore
    @SerializedName("channel")
    @Expose
    private Channel channel;

    // messages are stored separately
    @Ignore
    @SerializedName("messages")
    @Expose
    public List<Message> messages;

    @Embedded(prefix = "last_message_")
    public Message lastMessage;

    @SerializedName("read")
    @Expose
    @TypeConverters({ChannelUserReadListConverter.class})
    public List<ChannelUserRead> reads;

    @SerializedName("members")
    @Expose
    @TypeConverters({MemberListConverter.class})
    public List<Member> members;

    @Ignore
    @SerializedName("watchers")
    @Expose
    public List<Watcher> watchers;

    @Ignore
    @SerializedName("watcher_count")
    public int watcherCount;

    @Ignore
    public Date lastKnownActiveWatcher;

    public ChannelState() {
        this(null);
    }

    public ChannelState(Channel channel) {
        this.channel = channel;
        if (channel == null || channel.getChannelState() == null) {
            messages = new ArrayList<>();
            reads = new ArrayList<>();
            members = new ArrayList<>();
        } else {
            messages = channel.getChannelState().messages;
            reads = channel.getChannelState().reads;
            members = channel.getChannelState().members;
        }
    }

    // endregion

    @NonNull
    public String getCid() {
        return cid;
    }

    public void setCid(@NonNull String cid) {
        this.cid = cid;
    }

    public List<Watcher> getWatchers() {
        if (watchers == null) {
            return new ArrayList<>();
        }
        return watchers;
    }

//    public void preStorage() {
//
//        this.cid = this.channel.getCid();
//        this.lastMessage = this.computeLastMessage();
//    }

    public int getWatcherCount() {
        return watcherCount;
    }

    public void setWatcherCount(int watcherCount) {
        this.watcherCount = watcherCount;
    }

    public Date getLastKnownActiveWatcher() {
        if (lastKnownActiveWatcher == null) {
            lastKnownActiveWatcher = new Date(0);
        }
        return lastKnownActiveWatcher;
    }

//    public ChannelState copy() {
//        ChannelState clone = new ChannelState(channel);
//        clone.reads = new ArrayList<>();
//        for (ChannelUserRead read : getReads()) {
//            clone.reads.add(new ChannelUserRead(read.getUser(), read.getLastRead()));
//        }
//        clone.setLastMessage(getLastMessage());
//        return clone;
//    }

    public synchronized void addWatcher(Watcher watcher) {
        if (watchers == null) {
            watchers = new ArrayList<>();
        }
        watchers.remove(watcher);
        watchers.add(watcher);
    }

    public void removeWatcher(Watcher watcher) {
        if (watchers == null) {
            watchers = new ArrayList<>();
        }
        if (watcher.getUser().getLastActive().after(getLastKnownActiveWatcher())) {
            lastKnownActiveWatcher = watcher.getUser().getLastActive();
        }
        watchers.remove(watcher);
    }

    public void addOrUpdateMember(@NotNull Member member) {
        if (members == null) {
            members = new ArrayList<>();
        }
        int index = members.indexOf(member);
        if (index >= 0) {
            members.set(index, member);
        } else {
            members.add(member);
        }
    }

    public void removeMemberById(@NotNull String userId) {
        if (members == null || members.isEmpty())
            return;

        for (Iterator<Member> it = members.iterator(); it.hasNext(); ) {
            Member member = it.next();
            if (member.getUserId().equals(userId)) {
                it.remove();
            }
        }
    }

//    public List<User> getOtherUsers() {
//
//        Log.d(TAG, "getOtherUsers");
//
//        List<User> users = new ArrayList<>();
//
//        if (members != null) {
//            for (Member m : members) {
//                if (!channel.getClient().fromCurrentUser(m)) {
//                    User user = channel.getClient().getState().getUser(m.getUser().getId());
//                    Log.d(TAG, "getOtherUsers: member: " + user);
//                    users.add(user);
//                }
//            }
//        }
//
//        if (watchers != null) {
//            for (Watcher w : watchers) {
//                if (!channel.getClient().fromCurrentUser(w)) {
//                    User user = channel.getClient().getState().getUser(w.getUser().getId());
//                    Log.d(TAG, "getOtherUsers: watcher: " + user);
//                    if (!users.contains(user))
//                        users.add(user);
//                }
//            }
//        }
//
//        return users;
//    }

    public String getOldestMessageId() {

        // TODO: we should ignore messages that haven't been sent yet
        Message message = getOldestMessage();
        if (message == null) {
            return null;
        }
        return message.getId();
    }

    // last time the channel had a message from another user or (when more recent) the time a watcher was last active
//    public Date getLastActive() {
//        Date lastActive = channel.getCreatedAt();
//        if (lastActive == null) lastActive = new Date();
//        if (getLastKnownActiveWatcher().after(lastActive)) {
//            lastActive = getLastKnownActiveWatcher();
//        }
//        Message message = getLastMessageFromOtherUser();
//        if (message != null) {
//            if (message.getCreatedAt().after(lastActive)) {
//                lastActive = message.getCreatedAt();
//            }
//        }
//        for (Watcher watcher : getWatchers()) {
//            if (watcher.getUser() == null || watcher.getUser().getLastActive() == null)
//                continue;
//            if (lastActive.before(watcher.getUser().getLastActive())) {
//                if (channel.getClient().fromCurrentUser(watcher)) continue;
//                lastActive = watcher.getUser().getLastActive();
//            }
//        }
//        return lastActive;
//    }

//    public String getChannelNameOrMembers() {
//        String channelName;
//
//        Log.i(TAG, "Channel name is" + channel.getName() + channel.getCid());
//        if (!TextUtils.isEmpty(channel.getName())) {
//            channelName = channel.getName();
//        } else {
//            List<User> users = this.getOtherUsers();
//            List<User> top3 = users.subList(0, Math.min(3, users.size()));
//            List<String> usernames = new ArrayList<>();
//            for (User u : top3) {
//                if (u == null) continue;
//                usernames.add(u.getName());
//            }
//
//            channelName = TextUtils.join(", ", usernames);
//            if (users.size() > 3) {
//                channelName += "...";
//            }
//        }
//        return channelName;
//    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    private Message getOldestMessage() {
        if (messages == null) {
            return null;
        }
        for (Message m : messages) {
            if (m.getSyncStatus() == Sync.SYNCED) {
                return m;
            }
        }
        return null;
    }

    public List<Message> getMessages() {
        if (messages == null) {
            return new ArrayList<>();
        }
        for (Message m : messages) {
            m.setCid(getCid());
        }
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public List<ChannelUserRead> getReads() {
        if (reads == null) {
            reads = new ArrayList<>();
        }
        return reads;
    }

    public void setReads(List<ChannelUserRead> reads) {
        this.reads = reads;
    }

    public Map<String, ChannelUserRead> getReadsByUser() {
        Map<String, ChannelUserRead> readsByUser = new HashMap<>();
        for (ChannelUserRead r : getReads()) {
            readsByUser.put(r.getUserId(), r);
        }
        return readsByUser;
    }

//    public synchronized List<ChannelUserRead> getLastMessageReads() {
//        Message lastMessage = this.getLastMessage();
//        List<ChannelUserRead> readLastMessage = new ArrayList<>();
//        if (reads == null || lastMessage == null) return readLastMessage;
//        Client client = this.getChannel().getClient();
//        String userID = client.getUserId();
//        for (ChannelUserRead r : reads) {
//            if (r.getUserId().equals(userID))
//                continue;
//            if (r.getLastRead().compareTo(lastMessage.getCreatedAt()) > -1) {
//                readLastMessage.add(r);
//            }
//        }
//
//        // sort the reads
//        Collections.sort(readLastMessage, (ChannelUserRead o1, ChannelUserRead o2) -> o1.getLastRead().compareTo(o2.getLastRead()));
//        return readLastMessage;
//    }

    public List<Member> getMembers() {
        if (members == null) {
            return new ArrayList<>();
        }
        return members;
    }

    public void setMembers(List<Member> members) {
        this.members = members;
    }

//    @Nullable
//    public Message getLastMessage() {
//        if (lastMessage == null) {
//            lastMessage = computeLastMessage();
//        }
//        return lastMessage;
//    }

//    public void setLastMessage(@Nullable Message lastMessage) {
//        if (lastMessage == null) return;
//
//        if (lastMessage.getDeletedAt() != null) {
//            this.lastMessage = computeLastMessage();
//            return;
//        }
//        this.lastMessage = lastMessage;
//    }

    public Message getLastMessageFromOtherUser() {
        Message lastMessage = null;
        try {
            List<Message> messages = getMessages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message message = messages.get(i);
                if (message.getDeletedAt() == null && !channel.getClient().fromCurrentUser(message)) {
                    lastMessage = message;
                    break;
                }
            }
            Message.setStartDay(Collections.singletonList(lastMessage), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lastMessage;
    }

    public User getLastReader() {
        if (this.reads == null || this.reads.isEmpty()) return null;
        User lastReadUser = null;
        for (int i = reads.size() - 1; i >= 0; i--) {
            ChannelUserRead channelUserRead = reads.get(i);
            if (!channel.getClient().fromCurrentUser(channelUserRead)) {
                lastReadUser = channelUserRead.getUser();
                break;
            }
        }
        return lastReadUser;
    }

//    private void addOrUpdateMessage(Message newMessage) {
//        if (messages.size() > 0) {
//            for (int i = messages.size() - 1; i >= 0; i--) {
//                if (messages.get(i).getId().equals(newMessage.getId())) {
//                    messages.set(i, newMessage);
//                    return;
//                }
//                if (messages.get(i).getCreatedAt().before(newMessage.getCreatedAt())) {
//                    messages.add(newMessage);
//                    return;
//                }
//            }
//        } else {
//            messages.add(newMessage);
//        }
//    }

    public void addMessageSorted(Message message) {
        List<Message> diff = new ArrayList<>();
        diff.add(message);
        addMessagesSorted(diff);
        setLastMessage(message);
    }

    private void addMessagesSorted(List<Message> messages) {
        for (Message m : messages) {
            if (m.getParentId() == null) {
                addOrUpdateMessage(m);
            }
        }
    }

    public int getCurrentUserUnreadMessageCount() {
        Client client = this.getChannel().getClient();
        String userID = client.getUserId();
        return this.getUnreadMessageCount(userID);
    }

    private int getUnreadMessageCount(String userId) {
        int unreadMessageCount = 0;
        if (this.reads == null || this.reads.isEmpty()) return unreadMessageCount;

        Date lastReadDate = getReadDateOfChannelLastMessage(userId);
        if (lastReadDate == null) return unreadMessageCount;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message.getUser().getId().equals(userId)) continue;
            if (message.getDeletedAt() != null) continue;
            if (message.getCreatedAt().getTime() > lastReadDate.getTime())
                unreadMessageCount++;
        }
        return unreadMessageCount;
    }

    public Date getReadDateOfChannelLastMessage(String userId) {
        if (this.reads == null || this.reads.isEmpty()) return null;
        Date lastReadDate = null;
        try {
            for (int i = reads.size() - 1; i >= 0; i--) {
                ChannelUserRead channelUserRead = reads.get(i);
                if (channelUserRead.getUser().getId().equals(userId)) {
                    lastReadDate = channelUserRead.getLastRead();
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return lastReadDate;
    }

    public void setReadDateOfChannelLastMessage(User user, Date readDate) {
        for (int i = 0; i < getReads().size(); i++) {
            ChannelUserRead current = reads.get(i);
            if (current.getUserId().equals(user.getId())) {
                reads.remove(i);
                current.setLastRead(readDate);
                reads.add(current);
                return;
            }
        }
        ChannelUserRead channelUserRead = new ChannelUserRead(user, readDate);
        reads.add(channelUserRead);
    }

    // if user read the last message returns true, else false.
    public boolean readLastMessage() {
        Client client = this.getChannel().getClient();
        String userID = client.getUserId();
        Date myReadDate = getReadDateOfChannelLastMessage(userID);
        if (myReadDate == null) {
            return false;
        } else if (getLastMessage() == null) {
            return true;
        } else return myReadDate.getTime() > getLastMessage().getCreatedAt().getTime();
    }

    @Override
    public String toString() {
        return "ChannelState{" +
                "cid='" + cid + '\'' +
                ", channel=" + channel +
                '}';
    }
}

