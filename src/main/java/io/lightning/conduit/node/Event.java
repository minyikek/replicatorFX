package io.lightning.conduit.node;

public class Event<T> {
    private int type;
    private T payload;

    public int getType()          { return type; }
    public T   getPayload()       { return payload; }
    public void setType(int t)    { this.type = t; }
    public void setPayload(T p)   { this.payload = p; }

    public void set(T payload, int type) {
        this.payload = payload;
        this.type    = type;
    }
}
