package net.quedex.marketmaker;

public final class RealTimeProvider implements TimeProvider {
    @Override
    public long getCurrentTime() {
        return System.currentTimeMillis();
    }
}
