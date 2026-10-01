package dev.myriad.api.setting;

import dev.myriad.api.util.MyriadId;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.registry.Registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A named, collapsible section of settings. The factory methods return builders that add the built setting to this
 * group:
 *
 * <pre>{@code
 * private final DoubleSetting range = sgGeneral.doubleSetting("Range").defaultValue(4.5).range(0, 6).build();
 * }</pre>
 */
public class SettingGroup {
	private final Settings owner;
	private final String name;
	private final String id;
	private final List<Setting<?>> settings = new ArrayList<>();
	private boolean expanded = true;

	SettingGroup(Settings owner, String name) {
		this.owner = owner;
		this.name = name;
		this.id = MyriadId.toPath(name);
	}

	public String name() {
		return name;
	}

	public String id() {
		return id;
	}

	public List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	/** Adds a pre-built setting (for custom setting types). */
	public <S extends Setting<?>> S add(S setting) {
		owner.checkUnique(this, setting);
		settings.add(setting);
		setting.setGroup(this);
		return setting;
	}

	private <B extends Setting.Builder<?, ?, ?>> B bind(B builder) {
		builder.group = this;
		return builder;
	}

	public BoolSetting.Builder bool(String name) {
		return bind(new BoolSetting.Builder(name));
	}

	public IntSetting.Builder intSetting(String name) {
		return bind(new IntSetting.Builder(name));
	}

	public DoubleSetting.Builder doubleSetting(String name) {
		return bind(new DoubleSetting.Builder(name));
	}

	public <E extends Enum<E>> EnumSetting.Builder<E> enumSetting(String name, E defaultValue) {
		return bind(new EnumSetting.Builder<>(name, defaultValue));
	}

	public StringSetting.Builder string(String name) {
		return bind(new StringSetting.Builder(name));
	}

	public StringListSetting.Builder stringList(String name) {
		return bind(new StringListSetting.Builder(name));
	}

	public ColorSetting.Builder color(String name) {
		return bind(new ColorSetting.Builder(name));
	}

	public KeybindSetting.Builder keybind(String name) {
		return bind(new KeybindSetting.Builder(name));
	}

	public ActionSetting.Builder action(String name, Runnable action) {
		return bind(new ActionSetting.Builder(name, action));
	}

	public <T> RegistryListSetting.Builder<T> registryList(String name, Registry<T> registry) {
		return bind(new RegistryListSetting.Builder<>(name, registry));
	}

	public RegistryListSetting.Builder<Block> blocks(String name) {
		return bind(RegistryListSetting.blocks(name));
	}

	public RegistryListSetting.Builder<Item> items(String name) {
		return bind(RegistryListSetting.items(name));
	}

	public RegistryListSetting.Builder<EntityType<?>> entityTypes(String name) {
		return bind(RegistryListSetting.entityTypes(name));
	}

	public RegistryListSetting.Builder<StatusEffect> statusEffects(String name) {
		return bind(RegistryListSetting.statusEffects(name));
	}
}
