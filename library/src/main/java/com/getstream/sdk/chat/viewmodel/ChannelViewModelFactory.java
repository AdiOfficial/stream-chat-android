package com.getstream.sdk.chat.viewmodel;

import android.app.Application;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;

import com.getstream.sdk.chat.model.Channel;

public class ChannelViewModelFactory implements ViewModelProvider.Factory {
    private Application mApplication;
    private Channel mChannel;


    public ChannelViewModelFactory(Application application, Channel channel) {
        mApplication = application;
        mChannel = channel;
    }

    @Override
    public <T extends ViewModel> T create(Class<T> modelClass) {
        return (T) new ChannelViewModel(mApplication, mChannel);
    }
}