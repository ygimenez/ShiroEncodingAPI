/*
 * This file is part of Shiro J Bot.
 * Copyright (C) 2020  Yago Gimenez (KuuHaKu)
 *
 * Shiro J Bot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Shiro J Bot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Shiro J Bot.  If not, see <https://www.gnu.org/licenses/>
 */

package api.handler;

import api.Application;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

public class EncoderSocket extends WebSocketServer {
	private final Map<String, VideoData> pending = new HashMap<>();
	private final EncodingQueue queue = new EncodingQueue();
	private WebSocket client = null;

	public EncoderSocket(InetSocketAddress address) {
		super(address);
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		if (client != null) {
			conn.close(401, "Another client is already connected to socket");
			return;
		}
		client = conn;

		Application.logger.info("Connection estabilished: " + conn.getLocalSocketAddress().toString());
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		client = null;
		Application.logger.info("Connection undone");
	}

	@Override
	public void onMessage(WebSocket conn, String payload) {
		JSONObject data = new JSONObject(payload);

		String hash = data.getString("hash");
		int size = data.getInt("size");
		int index = data.getInt("index");
		DataType type = data.getEnum(DataType.class, "type");
		switch (type) {
			case BEGIN -> {
				pending.put(hash, new VideoData(hash, size, data.getInt("width"), data.getInt("height")));
				Application.logger.info("Received payload (" + index + "/" + size + " ) with hash " + hash + ": Data stream START");
			}
			case NEXT -> {
				pending.get(hash).getFrames().add(data.getString("data"));
				Application.logger.info("Received payload (" + index + "/" + size + " ) with hash " + hash + ": Data stream NEXT");
			}
			case END -> {
				queue.queue(pending.remove(hash));
				Application.logger.info("Received payload (" + index + "/" + size + " ) with hash " + hash + ": Data stream END");
			}
		}

		conn.send(new JSONObject(){{
			put("hash", hash);
			put("type", type);
		}}.toString());
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {

	}

	@Override
	public void onStart() {
		Application.logger.info("WebSocket \"encoder\" iniciado na porta " + this.getPort());
	}

	public WebSocket getClient() {
		return client;
	}
}