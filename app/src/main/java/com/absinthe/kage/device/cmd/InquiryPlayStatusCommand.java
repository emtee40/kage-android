package com.absinthe.kage.device.cmd;

import com.absinthe.kage.connect.protocol.IpMessageConst;
import com.absinthe.kage.device.Command;
import com.absinthe.kage.device.CommandBuilder;
import com.absinthe.kage.device.Device;
import com.absinthe.kage.device.client.Client;

public class InquiryPlayStatusCommand extends Command {

    public InquiryPlayStatusCommand() {
        cmd = IpMessageConst.MEDIA_GET_PLAYING_STATUS;
    }

    @Override
    public String pack() {
        return new CommandBuilder()
                .with(this)
                .build();
    }

    @Override
    public void doWork(Client client, String received) {

    }

    @Override
    public boolean parseReceived(String received) {
        return true;
    }
}
