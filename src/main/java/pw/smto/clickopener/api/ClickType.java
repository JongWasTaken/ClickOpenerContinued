package pw.smto.clickopener.api;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.ContainerInput;

public enum ClickType implements StringRepresentable {
	LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, DROP, CTRL_DROP,
	NONE;

	private static final Map<String, ClickType> VALUES = Arrays.stream(ClickType.values()).collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

	public static ClickType convert(ContainerInput action, int button, int slot) {
		return switch (action) {
			case PICKUP -> button == 0 ? ClickType.LEFT : ClickType.RIGHT;
			case QUICK_MOVE -> button == 0 ? ClickType.SHIFT_LEFT : ClickType.SHIFT_RIGHT;
			case THROW -> ClickType.evalThrow(slot, button);
			default -> ClickType.NONE;
		};
	}

	private static ClickType evalThrow(int slot, int button) {
		if (slot == -99) return ClickType.NONE;
		return button == 0 ? ClickType.DROP : ClickType.CTRL_DROP;
	}

	public static ClickType tryValueOf(String s) {
		return ClickType.VALUES.getOrDefault(s, ClickType.NONE);
	}

	@Override
	public String getSerializedName() {
		return this.name();
	}
}
