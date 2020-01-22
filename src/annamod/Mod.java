package annamod;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;

import net.basdon.anna.api.*;
import net.basdon.anna.api.IAnna.Output;

import static net.basdon.anna.api.Util.*;

public class Mod implements IMod
{
static final InetAddress ADDR_LOCAL;

static
{
	InetAddress addr = null;
	try {
		addr = Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 });
	} catch (Exception e) {}
	ADDR_LOCAL = addr;
}

private IAnna anna;
private char[] outtarget;
private DatagramSocket sockout, sockin;
private RecvThread recvthread;

@Override
public
String getName()
{
	return "mod_plchat";
}

@Override
public
String getVersion()
{
	return "0a";
}

@Override
public
String getDescription()
{
	return "echo service for pl sa-mp server";
}

@Override
public
void print_stats(Output output)
throws IOException
{
}

@Override
public
boolean on_enable(IAnna anna, char[] replytarget)
{
	this.anna = anna;
	this.outtarget = "#pl.echo".toCharArray();
	this.anna.join(this.outtarget);
	try {
		this.sockout = new DatagramSocket();
		this.sockin = new DatagramSocket(5056);
		this.recvthread = new RecvThread();
		this.recvthread.start();
		return true;
	} catch (Exception e) {
		return false;
	}
}

@Override
public
void on_disable()
{
	if (this.recvthread != null && this.recvthread.isAlive()) {
		this.recvthread.interrupt();
	}
	if (this.sockin != null) {
		try {
			this.sockin.close();
		} catch (Throwable t) {}
	}
	if (this.sockout != null) {
		try {
			this.sockout.close();
		} catch (Throwable t) {}
	}
}

@Override
public
void on_action(User user, char[] target, char[] replytarget, char[] action)
{
	if (user != null && strcmp(this.outtarget, target)) {
		ChannelUser cu = anna.find_user(target, user.nick);
		if (cu != null &&
			has_user_mode_or_higher(cu, this.anna.get_user_channel_modes(), 'v'))
		{
			byte[] b = new byte[user.nick.length + action.length + 6];
			b[0] = 'I';
			b[1] = 'R';
			b[2] = 'C';
			b[3] = ' ';
			b[4] = '*';
			for (int i = 0; i < user.nick.length; i++) {
				b[5 + i] = (byte) user.nick[i];
			}
			b[5 + user.nick.length] = ' ';
			for (int i = 0; i < action.length; i++) {
				b[6 + user.nick.length + i] = (byte) action[i];
			}
			send_to_pl(b);
		}
	}
}

@Override
public
void on_message(User user, char[] target, char[] replytarget, char[] msg)
{
	ChannelUser cu;
	if (user != null && strcmp(this.outtarget, target) &&
		(cu = this.anna.find_user(target, user.nick)) != null)
	{
		if (has_user_mode_or_higher(cu, this.anna.get_user_channel_modes(), 'v')) {
			byte[] b = new byte[user.nick.length + msg.length + 6];
			b[0] = 'I';
			b[1] = 'R';
			b[2] = 'C';
			b[3] = ' ';
			for (int i = 0; i < user.nick.length; i++) {
				b[4 + i] = (byte) user.nick[i];
			}
			b[4 + user.nick.length] = ':';
			b[5 + user.nick.length] = ' ';
			for (int i = 0; i < msg.length; i++) {
				b[6 + user.nick.length + i] = (byte) msg[i];
			}
			send_to_pl(b);
		}
	}
}

void send_to_pl(byte[] b)
{
	if (sockout != null) {
		try {
			this.sockout.send(new DatagramPacket(b, b.length, ADDR_LOCAL, 5055));
		} catch (Exception e) {
		}
	}
}

private class RecvThread extends Thread
{
	@Override
	public void run()
	{
		byte buf[] = new byte[200];
		for (;;) {
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			try {
				sockin.receive(packet);
				if (packet.getAddress().isLoopbackAddress()) {
					String msg = new String(buf, 0, packet.getLength());
					anna.privmsg(outtarget, msg.toCharArray());
				}
			} catch (InterruptedIOException e) {
				return;
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}
}
}