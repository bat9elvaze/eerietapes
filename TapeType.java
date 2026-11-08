package com.eerietapes;

public enum TapeType {
    /** «Как пережить вечер дома одному» — сидишь в доме, слушаешь правила. */
    HOUSE(6000),
    /** «Ты не один в лесу» — Наблюдатели двигаются, только когда на них не смотрят. */
    FOREST(7200);

    public final int durationTicks;

    TapeType(int durationTicks) {
        this.durationTicks = durationTicks;
    }
}
