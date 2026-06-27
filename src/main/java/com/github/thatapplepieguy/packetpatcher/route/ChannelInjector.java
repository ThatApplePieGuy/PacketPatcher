package com.github.thatapplepieguy.packetpatcher.route;

import io.netty.channel.Channel;

public interface ChannelInjector {

    void inject(Channel channel);

    void uninject(Channel channel);
}
