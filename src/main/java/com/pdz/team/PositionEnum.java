package com.pdz.team;

public enum PositionEnum {
    DEV(1),
    TLZ(2),
    TLI(3),
    SRE(4),
    QA(5);

    private final int id;

    PositionEnum(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
