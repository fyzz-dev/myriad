package dev.myriad.api.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import dev.myriad.api.util.Keybind;
import java.util.Locale;
import java.util.function.Supplier;

public class KeybindSetting extends Setting<Keybind> {
	public KeybindSetting(String name, String description, Keybind defaultValue, Supplier<Boolean> visible) {
		super(name, description, defaultValue, visible);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value.serialize());
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive()) set(Keybind.deserialize(json.getAsString()));
	}

	@Override
	public boolean parse(String input) {
		String in = input.trim().toLowerCase(Locale.ROOT);
		if (in.equals("none") || in.equals("unbind")) {
			set(Keybind.NONE);
			return true;
		}
		try {
			InputConstants.Key key = InputConstants.getKey("key.keyboard." + in);
			if (key.getValue() < 0) return false;
			set(Keybind.key(key.getValue()));
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	@Override
	public String valueString() {
		return value.displayName();
	}

	public static class Builder extends Setting.Builder<dev.myriad.api.setting.KeybindSetting.Builder, Keybind, KeybindSetting> {
		public Builder(String name) {
			super(name, Keybind.NONE);
		}

		@Override
		protected KeybindSetting create() {
			return new KeybindSetting(name, description, defaultValue, visible);
		}
	}
}
