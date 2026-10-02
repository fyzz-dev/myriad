package dev.myriad.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

/** Chat lines sent with an id, so a newer message with the same id replaces the old one. Render thread only. */
public final class ChatLines {
	private static final Map<String, ChatHudLine> byId = new HashMap<>();
	private static String pending;

	private ChatLines() {
	}

	/** Adds {@code message} to the chat, removing the last line added with {@code id}. */
	public static void add(Text message, String id) {
		ChatHud hud = MinecraftClient.getInstance().inGameHud.getChatHud();
		ChatHudLine previous = byId.remove(id);
		if (previous != null && hud.messages.remove(previous)) hud.refresh();
		pending = id;
		try {
			hud.addMessage(message);
		} finally {
			pending = null;
		}
	}

	/** Called by ChatHudMixin as each line is stored. */
	public static void onLineAdded(ChatHudLine line) {
		if (pending != null) byId.put(pending, line);
	}
}
