package annamod;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;

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
private PlayersQuery querythread;

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
	return "3a";
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
	if (this.querythread != null && this.querythread.isAlive()) {
		this.querythread.interrupt();
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
boolean on_command(
	User user, char[] target, char[] replytarget, char[] message, char[] cmd, char[] params)
{
	if (user != null &&
		strcmp(this.outtarget, target) &&
		strcmp(cmd, 'n','o','r','t','h','p','o','l','e'))
	{
		String m = "MODE " + new String(this.outtarget) + " +v " + new String(user.nick);
		this.anna.send_raw(m.toCharArray(), 0, m.length());
	}
	if (user != null &&
		strcmp(this.outtarget, target) &&
		strcmp(cmd, 'p','l','a','y','e','r','s'))
	{
		if (this.querythread != null && this.querythread.isAlive()) {
			this.querythread.interrupt();
		}
		this.querythread = new PlayersQuery();
		this.querythread.start();
	}
	return false;
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
			byte[] b;
			if (msg.length > 0 && msg[0] == '!') {
				byte msglen = (byte) msg.length;
				if (msglen < 0) msglen = 127;
				b = new byte[1 + 1 + 1 + user.nick.length + msglen];
				b[0] = '!';
				b[1] = (byte) user.nick.length;
				b[2] = msglen;
				for (int i = 0; i < user.nick.length; i++) {
					b[3 + i] = (byte) user.nick[i];
				}
				for (int i = 0; i < msglen; i++) {
					b[3 + user.nick.length + i] = (byte) msg[i];
				}
			} else {
				b = new byte[user.nick.length + msg.length + 6];
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
			}
			send_to_pl(b);
		}
	}
}

void send_to_pl(byte[] b)
{
	if (this.sockout != null) {
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
		try {
			for (;;) {
				try (DatagramSocket sock = new DatagramSocket(5056)) {
					sockin = sock;
					for (;;) {
						DatagramPacket pckt;
						pckt = new DatagramPacket(buf, buf.length);
						sock.receive(pckt);
						if (pckt.getAddress().isLoopbackAddress()) {
							String msg;
							msg = new String(buf, 0, pckt.getLength());
							char[] c = msg.toCharArray();
							for (int i = 0; i < c.length; i++) {
								if (c[i] == '\n' || c[i] == '\r') {
									c[i] = '#';
								}
							}
							anna.privmsg(outtarget, c);
						}
					}
				} catch (SocketException e) {
					if (this.isInterrupted()) {
						return;
					}
					anna.log_warn("plchat socket closed, restarting it");
				} catch (InterruptedIOException e) {
					return;
				} catch (IOException e) {
					anna.log_warn("plchat socket closed, restarting it");
				}
				Thread.sleep(3000);
			}
		} catch (InterruptedException e) {
		}
	}
}

private class PlayersQuery extends Thread
{
	@Override
	public void run()
	{
		try (DatagramSocket s = new DatagramSocket(new InetSocketAddress("0.0.0.0", 0))) {
			InetSocketAddress addr = new InetSocketAddress("51.178.2.56", 7777);
			byte[] data = new byte[1000];
			data[0] = 'S';
			data[1] = 'A';
			data[2] = 'M';
			data[3] = 'P';
			data[4] = 51;
			data[5] = (byte) 178;
			data[6] = 2;
			data[7] = 56;
			data[8] = 0x61;
			data[9] = 0x1E;
			data[10] = 'c';
			s.send(new DatagramPacket(data, 11, addr));
			DatagramPacket pckt = new DatagramPacket(data, data.length);
			s.receive(pckt);
			int numplayers = (data[12] << 8) | data[11];
			if (numplayers == 0) {
				anna.privmsg(outtarget, "0 players".toCharArray());
				return;
			}
			StringBuilder sb = new StringBuilder();
			sb.append(numplayers).append(" players:");
			int pos = 13;
			for (int i = 0; i < numplayers; i++) {
				int namelen = data[pos];
				if (namelen < 1) {
					break;
				}
				String playername = new String(data, pos + 1, namelen);
				int score =
					((((int) data[pos + 1 + namelen + 3]) & 0xFF) << 24) |
					((((int) data[pos + 1 + namelen + 2]) & 0xFF) << 16) |
					((((int) data[pos + 1 + namelen + 1]) & 0xFF) << 8) |
					((((int) data[pos + 1 + namelen + 0]) & 0xFF) << 0);
				sb.append(' ');
				sb.append(playername).append('(').append(score).append(')');
				pos += 5 + namelen;
			}
			anna.privmsg(outtarget, chars(sb));
		} catch (SocketException e) {
			if (this.isInterrupted()) {
				return;
			}
			anna.log_warn("failed samp query");
		} catch (InterruptedIOException e) {
			return;
		} catch (IOException e) {
			anna.log_warn("failed samp query");
		}
	}
}
}
