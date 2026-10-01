package dev.myriad.impl.ui;

import dev.myriad.api.setting.Setting;
import dev.myriad.api.ui.SettingWidgets;
import dev.myriad.api.ui.widget.SettingRow;
import dev.myriad.api.ui.widget.Widget;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

final class SettingWidgetsImpl implements SettingWidgets {
	private record Entry(Function<Setting<?>, Widget> factory, boolean stacked) {
	}

	private final Map<Class<?>, Entry> factories = new HashMap<>();

	@Override
	@SuppressWarnings("unchecked")
	public <S extends Setting<?>> void register(Class<S> type, Function<S, Widget> factory, boolean stacked) {
		factories.put(type, new Entry(s -> factory.apply((S) s), stacked));
	}

	@Override
	public Widget create(Setting<?> setting) {
		for (Class<?> c = setting.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
			Entry e = factories.get(c);
			if (e != null) return new SettingRow(setting, e.factory.apply(setting), e.stacked);
		}
		return null;
	}
}
