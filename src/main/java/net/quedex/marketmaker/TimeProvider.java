package net.quedex.marketmaker;

@FunctionalInterface
public interface TimeProvider {
    long getCurrentTime();
}
